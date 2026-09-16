package com.frank.visordocumentoscifrado.license

import android.content.Context
import com.frank.visordocumentoscifrado.activation.ActivationTransportProvider
import com.frank.visordocumentoscifrado.config.AppConfig

/**
 * Punto único para consultar el estado de activación del equipo.
 *
 * MainActivity no conoce Telegram ni conocerá la futura API. Sólo este cliente y
 * ActivationTransportProvider resuelven el canal disponible.
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
            // Reservado para ApiActivationTransport en la siguiente etapa del proyecto.
            callback(ActivationStatusResult("PENDING", "La API de activación aún no está implementada."))
            return
        }

        val transport = ActivationTransportProvider.current()
        transport.checkStatus(context) { response ->
            if (response.status == "APPROVED" && !response.licenseText.isNullOrBlank()) {
                val saved = LicenseManager.save(context, response.licenseText)
                if (saved.first) {
                    callback(ActivationStatusResult("APPROVED", "Acceso aprobado. Licencia guardada."))
                } else {
                    callback(
                        ActivationStatusResult(
                            "PENDING",
                            "Se recibió aprobación, pero la licencia no es válida: ${saved.second}"
                        )
                    )
                }
            } else {
                callback(
                    ActivationStatusResult(
                        response.status,
                        response.message,
                        response.licenseText
                    )
                )
            }
        }
    }
}
