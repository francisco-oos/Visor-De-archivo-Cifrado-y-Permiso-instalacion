package com.frank.visordocumentoscifrado.activation

import android.content.Context
import org.json.JSONObject

/** Resultado neutral del canal de activación; la UI no conoce Telegram/API. */
data class ActivationTransportStatus(
    val found: Boolean,
    val status: String,
    val message: String,
    val licenseText: String? = null
)

/**
 * Contrato del transporte de activación.
 *
 * Hoy puede estar respaldado por Telegram en debug. Mañana la implementación
 * productiva será HTTP/API sin cambiar MainActivity ni LicenseManager.
 */
interface ActivationTransport {
    val name: String

    fun isConfigured(): Boolean

    fun sendEvent(
        context: Context,
        event: JSONObject,
        callback: (Boolean, String) -> Unit
    )

    fun checkStatus(
        context: Context,
        callback: (ActivationTransportStatus) -> Unit
    )
}
