package com.frank.visordocumentoscifrado.telegram

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.frank.visordocumentoscifrado.config.TelegramConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Transporte Telegram para eventos de la APK.
 *
 * Telegram se usa como puente temporal sin dominio/servidor:
 * - INSTALL_EVENT: instalación detectada.
 * - ACCESS_REQUEST: solicitud de acceso enviada por el usuario.
 *
 * El usuario NO ve JSON, archivos ni detalles del canal usado.
 * La aprobación/rechazo se hará después desde otro proyecto Frontend/API.
 */
object TelegramRequestSender {
    fun sendEvent(
        context: Context,
        event: JSONObject,
        callback: (Boolean, String) -> Unit
    ) {
        val main = Handler(Looper.getMainLooper())
        if (!TelegramConfig.isConfigured()) {
            callback(false, "Canal de solicitudes no configurado.")
            return
        }
        Thread {
            try {
                val requestId = event.optString("request_id", "SIN_ID")
                val eventType = event.optString("event_type", "EVENT")
                val filename = "${eventType}_${requestId}.req"
                val payload = "VISOR_EVENT_V1:" + android.util.Base64.encodeToString(
                    event.toString(2).toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                val caption = buildCaption(event)
                postDocument(filename, payload.toByteArray(Charsets.UTF_8), caption)
                main.post { callback(true, "Evento enviado correctamente.") }
            } catch (e: Exception) {
                main.post { callback(false, e.message ?: "Error desconocido") }
            }
        }.start()
    }

    private fun buildCaption(event: JSONObject): String {
        return when (event.optString("event_type")) {
            "INSTALL_EVENT" ->
                "📲 INSTALL_EVENT\n" +
                    "App: ${event.optString("app_name")}\n" +
                    "Versión: ${event.optString("app_version")}\n" +
                    "Equipo: ${event.optString("brand")} ${event.optString("model")}\n" +
                    "Android: ${event.optString("android")}\n" +
                    "Install ID: ${event.optString("install_id")}\n" +
                    "Hash: ${event.optString("device_hash").take(18)}..."

            "ACCESS_REQUEST" ->
                "🔐 ACCESS_REQUEST\n" +
                    "Empleado: ${event.optString("employee_name")}\n" +
                    "ID: ${event.optString("employee_id")}\n" +
                    "Área: ${event.optString("area")}\n" +
                    "Puesto: ${event.optString("position")}\n" +
                    "Proyecto: ${event.optString("project")}\n" +
                    "Request ID: ${event.optString("request_id")}\n" +
                    "Hash: ${event.optString("device_hash").take(18)}..."

            else ->
                "📌 ${event.optString("event_type", "EVENT")}\n" +
                    "Request ID: ${event.optString("request_id")}"
        }
    }

    private fun postDocument(filename: String, bytes: ByteArray, caption: String) {
        val boundary = "----VisorSeguro${UUID.randomUUID()}"
        val url = URL("https://api.telegram.org/bot${TelegramConfig.BOT_TOKEN}/sendDocument")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            body.write("--$boundary\r\n".toByteArray())
            body.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            body.write(value.toByteArray(Charsets.UTF_8))
            body.write("\r\n".toByteArray())
        }
        field("chat_id", TelegramConfig.ADMIN_CHAT_ID)
        field("caption", caption)
        body.write("--$boundary\r\n".toByteArray())
        body.write("Content-Disposition: form-data; name=\"document\"; filename=\"$filename\"\r\n".toByteArray())
        body.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
        body.write(bytes)
        body.write("\r\n--$boundary--\r\n".toByteArray())
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        if (code !in 200..299 || !responseText.contains("\"ok\":true")) {
            throw IllegalStateException("Telegram HTTP $code: $responseText")
        }
    }
}
