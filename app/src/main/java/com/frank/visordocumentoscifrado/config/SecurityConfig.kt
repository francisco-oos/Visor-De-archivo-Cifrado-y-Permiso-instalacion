package com.frank.visordocumentoscifrado.config

/** Políticas de seguridad ajustables. */
object SecurityConfig {
    /** Bloquea capturas y grabación de pantalla donde Android lo respeta. */
    const val BLOCK_SCREENSHOTS = true

    /** Si está activo, bloquea uso cuando detecta root/emulador. Si está falso, solo advierte. */
    const val STRICT_ROOT_BLOCK = false

    /** Obliga que toda licencia tenga fecha de caducidad. */
    const val REQUIRE_LICENSE_EXPIRATION = true

    /** Algoritmos activos/objetivo. */
    const val PDF_CRYPTO_ALGORITHM = "AES-256-GCM (VSDOC1/VSDOC2 legado)"
    const val LICENSE_SIGNATURE_ALGORITHM = "ECDSA-P256-SHA256 (VISOR_LICENSE_V2)"
}
