package com.rasecalay.yaplin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class YaplinListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        inicializarTTS()
    }

    private fun inicializarTTS() {
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e("Yaplin", "Error al crear TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts?.setLanguage(Locale("es", "ES"))
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
            }

            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName?.lowercase(Locale.ROOT) ?: ""
        val extras: Bundle = sbn.notification.extras ?: Bundle()

        val title = extras.getString("android.title") ?: ""
        val textSimple = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        // Unimos todo el contenido de texto que traiga la notificación
        val cuerpoAlerta = if (bigText.isNotEmpty()) bigText else textSimple
        val textoCompleto = "$title $cuerpoAlerta $textSimple".lowercase(Locale.ROOT)

        // Detección amplia: por paquete o por cualquier indicio de cobro / Yape
        val esNotificacionYape = packageName.contains("yape") ||
                                 packageName.contains("plin") ||
                                 packageName.contains("bcp") ||
                                 textoCompleto.contains("confirmación de pago") ||
                                 textoCompleto.contains("te envió un pago") ||
                                 textoCompleto.contains("te yapeó") ||
                                 textoCompleto.contains("pago recibido")

        if (!esNotificacionYape) return

        // 1. Emitir pitido físico y vibración inmediata de confirmación
        hacerPitidoYVibrar()

        // 2. Extraer el texto a pronunciar
        var fraseVoz = ""

        val regex = Regex("""(.*?) te envió un pago por (S/\s*[\d.]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(cuerpoAlerta)

        if (match != null) {
            val remitente = match.groupValues[1].replace("*", "").trim()
            val monto = match.groupValues[2].replace("S/", "soles").trim()
            fraseVoz = "Pago recibido de $remitente por $monto"
        } else if (cuerpoAlerta.isNotEmpty()) {
            val parteLimpia = cuerpoAlerta.split(".")[0].replace("*", "").trim()
            fraseVoz = "Pago: $parteLimpia"
        } else {
            fraseVoz = "Has recibido un pago de Yape"
        }

        // 3. Forzar reproducción de voz
        reproducirAudioForzado(fraseVoz)
    }

    private fun hacerPitidoYVibrar() {
        try {
            // Pitido de sistema (funciona independientemente de TTS)
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)

            // Vibración
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        } catch (e: Exception) {
            Log.e("Yaplin", "Error en pitido", e)
        }
    }

    private fun reproducirAudioForzado(texto: String) {
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)

        if (isTtsReady && tts != null) {
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "yaplin_alerta")
        } else {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "yaplin_alerta")
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("Yaplin", "Error al destruir", e)
        }
        super.onDestroy()
    }
}
