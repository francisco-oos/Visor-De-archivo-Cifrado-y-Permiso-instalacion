package com.frank.visordocumentoscifrado.activation

import android.content.Context
import org.json.JSONObject

/** Transporte nulo seguro cuando no existe backend configurado. */
private object DisabledActivationTransport : ActivationTransport {
    override val name: String = "disabled"

    override fun isConfigured(): Boolean = false

    override fun sendEvent(
        context: Context,
        event: JSONObject,
        callback: (Boolean, String) -> Unit
    ) {
        callback(false, "El canal de activación todavía no está configurado.")
    }

    override fun checkStatus(
        context: Context,
        callback: (ActivationTransportStatus) -> Unit
    ) {
        callback(
            ActivationTransportStatus(
                found = false,
                status = "PENDING",
                message = "El canal de activación todavía no está configurado."
            )
        )
    }
}

/**
 * Único selector de transporte.
 *
 * Cuando se implemente la API productiva se añadirá aquí su implementación y la UI
 * seguirá consumiendo exactamente el mismo contrato.
 */
object ActivationTransportProvider {
    fun current(): ActivationTransport =
        if (TelegramActivationTransport.isConfigured()) {
            TelegramActivationTransport
        } else {
            DisabledActivationTransport
        }
}
