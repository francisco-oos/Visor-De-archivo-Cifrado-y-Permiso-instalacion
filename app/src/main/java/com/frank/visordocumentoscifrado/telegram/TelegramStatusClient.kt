package com.frank.visordocumentoscifrado.telegram

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.frank.visordocumentoscifrado.config.TelegramConfig
import com.frank.visordocumentoscifrado.security.DeviceIdentity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de consulta de estado usando Telegram como canal temporal.
 *
 * Esta clase permite que la APK pregunte al bot si ya existe una respuesta para su
 * device_hash/install_id sin mostrar al usuario JSON, archivos o detalles de Telegram.
 *
 * Protocolo esperado para el proyecto futuro de licencias:
 * El frontend o administrador deberá enviar al bot un mensaje/documento con:
 *
 * VISOR_STATUS_V1:<base64(json_utf8)>
 *
 * JSON mínimo:
 * {
 *   "schema":"VISOR_STATUS_V1",
 *   "device_hash":"...",
 *   "install_id":"...",
 *   "status":"APPROVED|REJECTED|PENDING|EXPIRED",
 *   "message":"Texto visible opcional",
 *   "license_text":"contenido completo de licencia.key si status=APPROVED"
 * }
 *
 * Nota técnica:
 * - getUpdates devuelve mensajes que usuarios mandan al bot.
 * - Los mensajes que el mismo bot envía al administrador no regresan por getUpdates.
 * - Por eso esta integración queda como puente temporal hasta tener API propia.
 */
data class TelegramStatusResponse(
    val found: Boolean,
    val status: String,
    val message: String,
    val licenseText: String? = null
)

object TelegramStatusClient {
    private const val STATUS_PREFIX = "VISOR_STATUS_V1:"

    fun check(context: Context, callback: (TelegramStatusResponse) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        if (!TelegramConfig.isConfigured()) {
            callback(TelegramStatusResponse(false, "PENDING", "Canal de estado no configurado."))
            return
        }

        Thread {
            val result = try {
                val info = DeviceIdentity.info(context)
                val myHash = info["device_hash"].orEmpty()
                val myInstall = info["install_id"].orEmpty()
                val updates = getUpdatesText()
                findStatusForDevice(updates, myHash, myInstall)
                    ?: TelegramStatusResponse(false, "PENDING", "Aún no hay respuesta para este equipo.")
            } catch (e: Exception) {
                TelegramStatusResponse(false, "PENDING", "No se pudo consultar estado: ${e.message}")
            }
            main.post { callback(result) }
        }.start()
    }

    private fun getUpdatesText(): String {
        val url = URL("https://api.telegram.org/bot${TelegramConfig.BOT_TOKEN}/getUpdates")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
        }
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText().orEmpty()
        }
        if (code !in 200..299 || !body.contains("\"ok\":true")) {
            throw IllegalStateException("Telegram HTTP $code: $body")
        }
        return body
    }

    private fun findStatusForDevice(rawUpdates: String, myHash: String, myInstall: String): TelegramStatusResponse? {
        val root = JSONObject(rawUpdates)
        val result = root.optJSONArray("result") ?: return null
        var latest: TelegramStatusResponse? = null

        for (i in 0 until result.length()) {
            val update = result.optJSONObject(i) ?: continue
            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
            val candidates = listOfNotNull(
                if (msg.has("text")) msg.optString("text") else null,
                if (msg.has("caption")) msg.optString("caption") else null
            )
            for (candidate in candidates) {
                val status = parseStatusPayload(candidate, myHash, myInstall)
                if (status != null) latest = status
            }
        }
        return latest
    }

    private fun parseStatusPayload(text: String, myHash: String, myInstall: String): TelegramStatusResponse? {
        val idx = text.indexOf(STATUS_PREFIX)
        if (idx < 0) return null
        val encoded = text.substring(idx + STATUS_PREFIX.length).trim()
        if (encoded.isBlank()) return null
        val json = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        val obj = JSONObject(json)
        if (obj.optString("schema") != "VISOR_STATUS_V1") return null

        val hash = obj.optString("device_hash")
        val installId = obj.optString("install_id")
        if (hash != myHash || installId != myInstall) return null

        val status = obj.optString("status", "PENDING").uppercase()
        val message = obj.optString("message", when (status) {
            "APPROVED" -> "Solicitud aprobada."
            "REJECTED" -> "Solicitud rechazada."
            "EXPIRED" -> "Licencia vencida."
            else -> "Solicitud en revisión."
        })
        val licenseText = obj.optString("license_text", "").ifBlank { null }
        return TelegramStatusResponse(true, status, message, licenseText)
    }
}
