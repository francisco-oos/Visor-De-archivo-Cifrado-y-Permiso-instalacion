package com.frank.visordocumentoscifrado.config

import com.frank.visordocumentoscifrado.BuildConfig

/**
 * Compatibilidad transitoria VSDOC1/VSDOC2.
 *
 * La clave ya NO vive en Git. tools/document_encryptor.py la guarda en
 * visor-secrets.properties (ignorado por Git) y Gradle la inyecta al compilar.
 *
 * Advertencia arquitectónica: la clave todavía termina dentro de la APK y por ello
 * puede extraerse con ingeniería inversa. VSDOC3 reemplazará este mecanismo por
 * claves de contenido protegidas por instalación/Android Keystore.
 */
object DocumentKeyConfig {
    val EMBEDDED_DOCUMENT_KEY_B64: String
        get() = BuildConfig.DOCUMENT_KEY_B64

    val DOCUMENT_KEY_SHA256: String
        get() = BuildConfig.DOCUMENT_KEY_SHA256
}
