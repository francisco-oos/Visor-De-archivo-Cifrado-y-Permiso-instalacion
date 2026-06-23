package com.frank.visordocumentoscifrado.license

import android.content.Context
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.telegram.TelegramStatusClient

/**
 * Punto único para consultar el estado de activación del equipo.
 *
 * Arquitectura limpia:
 * - Hoy consulta Telegram como canal temporal sin dominio.
 * - Mañana puede consultar una API real sin cambiar MainActivity.
 * - Si llega APPROVED con license_text, guarda la licencia localmente.
 */
data class ActivationStatusResult(
    val status: String,
    val message: String,
    val licenseText: String? = null
)

object ActivationStatusClient {
    fun check(context: Context, callback: (ActivationStatusResult) -> Unit) {
        val current = LicenseManager.current(context)
        if (current != null) {
            callback(ActivationStatusResult("APPROVED", "Licencia activa hasta ${current.expiresAt}."))
            return
        }

        if (AppConfig.LICENSE_STATUS_API_ENABLED) {
            // Proyecto futuro Frontend/API:
            // POST AppConfig.LICENSE_STATUS_API_URL con device_hash + install_id.
            callback(ActivationStatusResult("PENDING", "Consulta API aún no implementada."))
            return
        }

        TelegramStatusClient.check(context) { response ->
            if (response.status == "APPROVED" && !response.licenseText.isNullOrBlank()) {
                val saved = LicenseManager.save(context, response.licenseText)
                if (saved.first) {
                    callback(ActivationStatusResult("APPROVED", "Acceso aprobado. Licencia guardada."))
                } else {
                    callback(ActivationStatusResult("PENDING", "Se recibió aprobación, pero la licencia no es válida: ${saved.second}"))
                }
            } else {
                callback(ActivationStatusResult(response.status, response.message, response.licenseText))
            }
        }
    }
}
