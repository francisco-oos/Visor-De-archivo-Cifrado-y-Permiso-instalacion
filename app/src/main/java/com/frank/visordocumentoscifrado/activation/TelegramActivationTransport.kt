package com.frank.visordocumentoscifrado.activation

import android.content.Context
import com.frank.visordocumentoscifrado.config.TelegramConfig
import com.frank.visordocumentoscifrado.telegram.TelegramRequestSender
import com.frank.visordocumentoscifrado.telegram.TelegramStatusClient
import org.json.JSONObject

/** Implementación temporal disponible únicamente cuando el build debug la habilita. */
object TelegramActivationTransport : ActivationTransport {
    override val name: String = "telegram-debug"

    override fun isConfigured(): Boolean = TelegramConfig.isConfigured()

    override fun sendEvent(
        context: Context,
        event: JSONObject,
        callback: (Boolean, String) -> Unit
    ) {
        TelegramRequestSender.sendEvent(context, event, callback)
    }

    override fun checkStatus(
        context: Context,
        callback: (ActivationTransportStatus) -> Unit
    ) {
        TelegramStatusClient.check(context) { response ->
            callback(
                ActivationTransportStatus(
                    found = response.found,
                    status = response.status,
                    message = response.message,
                    licenseText = response.licenseText
                )
            )
        }
    }
}
