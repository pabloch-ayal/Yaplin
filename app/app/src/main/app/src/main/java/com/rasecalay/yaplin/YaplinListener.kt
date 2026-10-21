package com.rasecalay.yaplin

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
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            isTtsReady = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Revisamos si proviene de Yape o Plin (o si el texto menciona pago/yape)
        val esYapeOPlin = packageName.contains("yape", ignoreCase = true) ||
                          packageName.contains("plin", ignoreCase = true) ||
                          title.contains("Yape", ignoreCase = true) ||
                          title.contains("Confirmación de Pago", ignoreCase = true)

        if (!esYapeOPlin) return

        var mensajeVoz = ""

        // Formato actual: "Hermelinda Tic* te envió un pago por S/ 0.5. El cód. de seguridad es: 425"
        if (text.contains("te envió un pago por", ignoreCase = true)) {
            val regex = Regex("""(.*?) te envió un pago por (S/\s*[\d.]+)""")
            val match = regex.find(text)
            if (match != null) {
                val nombre = match.groupValues[1].replace("*", "").trim()
                val monto = match.groupValues[2].replace("S/", "soles").trim()
                mensajeVoz = "Pago recibido de $nombre por $monto"
            } else {
                val parteLimpia = text.split(".")[0].replace("*", "")
                mensajeVoz = parteLimpia
            }
        } else if (text.contains("te yapeó", ignoreCase = true) || text.contains("yape", ignoreCase = true)) {
            mensajeVoz = text.replace("*", "")
        }

        // Si se detectó el pago, activar la voz
        if (mensajeVoz.isNotEmpty()) {
            hablar(mensajeVoz)
        }
    }

    private fun hablar(mensaje: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "yaplin_alert")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
