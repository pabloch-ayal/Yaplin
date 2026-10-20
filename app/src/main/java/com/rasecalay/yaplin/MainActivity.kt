package com.rasecalay.yaplin

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#F5F6FA"))
        }

        val txtTitulo = TextView(this).apply {
            text = "Yaplin"
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#5A189A"))
            gravity = Gravity.CENTER
        }

        val txtSubtitulo = TextView(this).apply {
            text = "Avisos de cobro por voz (Yape y Plin)\n"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        txtEstado = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 40)
        }

        val btnPermiso = Button(this).apply {
            text = "Activar avisos de cobro"
            setBackgroundColor(Color.parseColor("#5A189A"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(40, 25, 40, 25)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val txtCreditos = TextView(this).apply {
            text = "\n\n\nCreado por: rasec-alay\nVersión 1.0"
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(txtTitulo)
        layout.addView(txtSubtitulo)
        layout.addView(txtEstado)
        layout.addView(btnPermiso)
        layout.addView(txtCreditos)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        verificarPermiso()
    }

    private fun verificarPermiso() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val miComponente = ComponentName(this, YaplinListener::class.java).flattenToString()
        val activo = listeners != null && listeners.contains(miComponente)

        if (activo) {
            txtEstado.text = "✅ Servicio ACTIVO\nEscuchando pagos de Yape y Plin"
            txtEstado.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            txtEstado.text = "⚠️ Servicio apagado\nPresiona el botón para activarlo"
            txtEstado.setTextColor(Color.parseColor("#C62828"))
        }
    }
}