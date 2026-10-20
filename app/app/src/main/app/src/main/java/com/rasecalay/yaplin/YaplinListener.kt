package com.rasecalay.yaplin

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class YaplinListener : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsListo = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts?.setLanguage(Locale("es", "PE"))
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            ttsListo = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val titulo = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val todoElTexto = "$titulo $texto"

        val esYape = pkg == "com.bcp.innovacxion.yapeapp"
        val esPlin = pkg in listOf(
            "pe.com.interbank.mobilebanking",
            "com.bbva.nxt_peru",
            "pe.scotiabank.peru.bcp"
        )

        if (esYape) {
            hablar("Nuevo pago de Yape. $texto")
        } else if (esPlin) {
            if (todoElTexto.contains("Plin", ignoreCase = true) || 
                todoElTexto.contains("te transfirió", ignoreCase = true) || 
                todoElTexto.contains("te envió", ignoreCase = true)) {
                hablar("Nuevo pago de Plin. $texto")
            }
        }
    }

    private fun hablar(mensaje: String) {
        if (ttsListo && tts != null) {
            tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "YaplinID")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}