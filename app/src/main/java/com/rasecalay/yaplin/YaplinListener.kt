package com.rasecalay.yaplin

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class YaplinListener : NotificationListenerService() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaylist: List<Int> = emptyList()
    private var playlistIndex = 0

    companion object {
        private const val TAG = "YaplinListener"
        private const val PKG_YAPE = "com.bcp.innovacxion.yapeapp"
        private const val PKG_PLIN_BBVA = "com.bbva.nxt_peru"
        private const val PKG_PLIN_INTERBANK = "pe.com.interbank.mobilebanking"
        private const val PKG_PLIN_SCOTIA = "com.scotiabank.peru.mobile"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        val isYape = pkg == PKG_YAPE
        val isPlin = pkg == PKG_PLIN_BBVA || pkg == PKG_PLIN_INTERBANK || pkg == PKG_PLIN_SCOTIA

        if (!isYape && !isPlin) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullContent = "$title $text"

        // Extraer el monto en soles
        val pattern = Pattern.compile("(?i)S\\/?\\.?\\s*(\\d+(?:[.,]\\d{1,2})?)")
        val matcher = pattern.matcher(fullContent)

        if (matcher.find()) {
            val rawAmount = matcher.group(1)?.replace(",", ".") ?: "0"
            val amountValue = rawAmount.toDoubleOrNull() ?: 0.0

            if (amountValue > 0.0) {
                vibrateAlert()
                playAmountSequence(amountValue, isYape)

                // Enviar broadcast a MainActivity para actualizar la tarjeta de último pago
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val currentTime = timeFormat.format(Date())

                val broadcastIntent = Intent("com.rasecalay.yaplin.NUEVO_PAGO").apply {
                    setPackage(packageName)
                    putExtra("service", if (isYape) "Yape" else "Plin")
                    putExtra("amount", String.format(Locale.US, "%.2f", amountValue))
                    putExtra("time", currentTime)
                }
                sendBroadcast(broadcastIntent)
            }
        }
    }

    private fun playAmountSequence(amount: Double, isYape: Boolean) {
        stopPlayback()
        currentPlaylist = AudioAmountBuilder.buildAudioPlaylist(amount, isYape)
        playlistIndex = 0
        playNextTrack()
    }

    private fun playNextTrack() {
        if (playlistIndex >= currentPlaylist.size) {
            stopPlayback()
            return
        }

        val resId = currentPlaylist[playlistIndex]
        playlistIndex++

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )

                val afd = resources.openRawResourceFd(resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                prepare()
                start()

                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                    playNextTrack()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir audio: ${e.message}")
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignorar
        }
        mediaPlayer = null
    }

    private fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(400)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al vibrar: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
