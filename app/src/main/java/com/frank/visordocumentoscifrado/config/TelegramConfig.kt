package com.frank.visordocumentoscifrado.config

/**
 * Canal Telegram de la APK.
 *
 * Esta V1 usa Telegram solo para transportar:
 * - aviso de instalación,
 * - solicitud de acceso.
 *
 * La generación/aprobación de licencia.key NO vive en esta APK ni en este repositorio.
 * Ese flujo se construirá después en un frontend/API independiente.
 *
 * Seguridad:
 * - Este token es de prueba. Revócalo en BotFather antes de producción.
 * - Para producción, mover el token a una API intermedia para no exponerlo en la APK.
 */
object TelegramConfig {
    const val ENABLED = true
    const val BOT_TOKEN = "8654869870:AAFOKWcvaWHRYgFvSwIyBMVYBZGwNANLLYE"
    const val ADMIN_CHAT_ID = "7483194146"

    fun isConfigured(): Boolean =
        ENABLED && BOT_TOKEN.isNotBlank() && ADMIN_CHAT_ID.isNotBlank()
}
