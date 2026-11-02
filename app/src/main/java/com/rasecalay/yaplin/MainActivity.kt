package com.rasecalay.yaplin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var paymentReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTestAudio = findViewById<Button>(R.id.btnTestAudio)
        val btnBatterySettings = findViewById<Button>(R.id.btnBatterySettings)
        val tvStatusBadge = findViewById<TextView>(R.id.tvStatusBadge)

        tvStatusBadge.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnTestAudio.setOnClickListener {
            playTestSequence()
        }

        btnBatterySettings.setOnClickListener {
            openBatterySettings()
        }

        paymentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val service = intent?.getStringExtra("service") ?: "Pago"
                val amount = intent?.getStringExtra("amount") ?: "0.00"
                val time = intent?.getStringExtra("time") ?: ""

                findViewById<TextView>(R.id.tvLastAmount)?.text = "S/ $amount"
                findViewById<TextView>(R.id.tvLastServiceDetail)?.text = "$service • $time"
            }
        }

        val filter = IntentFilter("com.rasecalay.yaplin.NUEVO_PAGO")
        registerReceiver(paymentReceiver, filter)
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
            tvStatusBadge.text = "● ESTADO: ACTIVO"
            tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            tvStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            tvStatusSub.text = "Escuchando pagos en segundo plano"
        } else {
            tvStatusBadge.text = "● ESTADO: INACTIVO (Toca aquí)"
            tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#C62828"))
            tvStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
            tvStatusSub.text = "Toca para conceder permiso de notificaciones"
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

        val playlist = AudioAmountBuilder.buildAudioPlaylist(12.0, isYape = true)
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
        try {
            paymentReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignorar si ya estaba desregistrado
        }
        super.onDestroy()
    }
}
