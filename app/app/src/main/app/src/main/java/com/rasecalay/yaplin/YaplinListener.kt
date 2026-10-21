package com.rasecalay.yaplin

import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
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
        iniciarTTS()
    }

    private fun iniciarTTS() {
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e("Yaplin", "Error al crear TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Compatibilidad de canal de audio para celulares modernos y antiguos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
            }

            val resultado = tts?.setLanguage(Locale("es", "PE"))
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale("es", "ES")
            }
            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val paquete = sbn.packageName?.lowercase(Locale.ROOT) ?: ""
        val extras: Bundle = sbn.notification.extras ?: Bundle()

        // Leer todos los campos posibles de la notificación
        val titulo = extras.getString("android.title") ?: ""
        val textoSimple = extras.getCharSequence("android.text")?.toString() ?: ""
        val textoGrande = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val cuerpoAlerta = if (textoGrande.isNotEmpty()) textoGrande else textoSimple
        val textoCompleto = "$titulo $cuerpoAlerta".trim()

        // Filtro universal para Yape, Plin o mensajes de confirmación de pago
        val esYapeOPlin = paquete.contains("yape") ||
                         paquete.contains("plin") ||
                         titulo.contains("Confirmación de Pago", ignoreCase = true) ||
                         textoCompleto.contains("Confirmación de Pago", ignoreCase = true) ||
                         textoCompleto.contains("te envió un pago", ignoreCase = true) ||
                         textoCompleto.contains("te yapeó", ignoreCase = true)

        if (!esYapeOPlin) return

        var fraseVoz = ""

        // Reconocer: "Hermelinda Tic* te envió un pago por S/ 0.5..."
        val regex = Regex("""(.*?) te envió un pago por (S/\s*[\d.]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(cuerpoAlerta)

        if (match != null) {
            val remitente = match.groupValues[1].replace("*", "").trim()
            val monto = match.groupValues[2].replace("S/", "soles").trim()
            fraseVoz = "Pago recibido de $remitente por $monto"
        } else if (cuerpoAlerta.contains("te yapeó", ignoreCase = true)) {
            fraseVoz = cuerpoAlerta.replace("*", "").trim()
        } else if (cuerpoAlerta.isNotEmpty()) {
            val fraseCorta = cuerpoAlerta.split(".")[0].replace("*", "").trim()
            fraseVoz = "Yape: $fraseCorta"
        }

        if (fraseVoz.isNotEmpty()) {
            hablar(fraseVoz)
        }
    }

    private fun hablar(mensaje: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "yaplin_alerta")
        } else {
            // Si el motor estaba dormido, lo reactiva y habla de inmediato
            tts = TextToSpeech(applicationContext) { estado ->
                if (estado == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "yaplin_alerta")
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("Yaplin", "Error al cerrar", e)
        }
        super.onDestroy()
    }
}
