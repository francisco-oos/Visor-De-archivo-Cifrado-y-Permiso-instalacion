package com.frank.visordocumentoscifrado.config

import com.frank.visordocumentoscifrado.BuildConfig

/**
 * Configuración del transporte Telegram legado.
 *
 * Telegram queda únicamente como puente de DESARROLLO. Las credenciales se leen
 * de visor-secrets.properties/variables de entorno y Gradle sólo las inyecta en
 * la variante debug. Una APK release siempre recibe campos vacíos y ENABLED=false.
 *
 * El transporte productivo se moverá a la API de activación sin cambiar la UI.
 */
object TelegramConfig {
    val ENABLED: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.TELEGRAM_DIRECT_ENABLED

    val BOT_TOKEN: String
        get() = BuildConfig.TELEGRAM_BOT_TOKEN

    val ADMIN_CHAT_ID: String
        get() = BuildConfig.TELEGRAM_ADMIN_CHAT_ID

    fun isConfigured(): Boolean =
        ENABLED && BOT_TOKEN.isNotBlank() && ADMIN_CHAT_ID.isNotBlank()
}
