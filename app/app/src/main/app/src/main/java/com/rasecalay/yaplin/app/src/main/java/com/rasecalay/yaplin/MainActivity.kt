package com.rasecalay.yaplin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var testPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatusBadge = findViewById<TextView>(R.id.tvStatusBadge)
        val tvStatusSub = findViewById<TextView>(R.id.tvStatusSub)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnTestAudio = findViewById<Button>(R.id.btnTestAudio)
        val btnBatterySettings = findViewById<Button>(R.id.btnBatterySettings)

        // 1. Abrir ajustes de permisos de notificación
        btnPermissions.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // 2. Probar sonido de Yape (S/ 10.00)
        btnTestAudio.setOnClickListener {
            playTestSequence()
        }

        // 3. Ajustes de batería sin restricciones
        btnBatterySettings.setOnClickListener {
            openBatterySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val tvStatusBadge = findViewById<TextView>(R.id.tvStatusBadge)
        val tvStatusSub = findViewById<TextView>(R.id.tvStatusSub)
        val hasPermission = isNotificationServiceEnabled()

        if (hasPermission) {
            tvStatusBadge.text = "● Servicio ACTIVO"
            tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            tvStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            tvStatusSub.text = "Escuchando notificaciones en segundo plano"
        } else {
            tvStatusBadge.text = "● Servicio DETENIDO"
            tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#C62828"))
            tvStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
            tvStatusSub.text = "Falta conceder permiso de lectura de notificaciones"
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, YaplinListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun playTestSequence() {
        testPlayer?.release()
        testPlayer = null

        val playlist = AudioAmountBuilder.buildAudioPlaylist(10.0, isYape = true)
        var index = 0

        fun playNext() {
            if (index >= playlist.size) {
                testPlayer?.release()
                testPlayer = null
                return
            }

            val resId = playlist[index++]
            try {
                testPlayer = MediaPlayer().apply {
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
                    setOnCompletionListener {
                        it.release()
                        testPlayer = null
                        playNext()
                    }
                }
            } catch (e: Exception) {
                testPlayer?.release()
                testPlayer = null
            }
        }

        playNext()
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    intent.data = Uri.parse("package:$packageName")
                } else {
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.parse("package:$packageName")
                }
            } else {
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            startActivity(fallback)
        }
    }

    override fun onDestroy() {
        testPlayer?.release()
        testPlayer = null
        super.onDestroy()
    }
}
