package com.frank.visordocumentoscifrado.config

import com.frank.visordocumentoscifrado.BuildConfig

/**
 * Material de verificación de licencias.
 *
 * VISOR_LICENSE_V2 usa firma asimétrica ECDSA P-256/SHA-256:
 * - la herramienta/servidor conserva la clave PRIVADA;
 * - la APK sólo necesita la clave PÚBLICA.
 *
 * VISOR_LICENSE_V1/HMAC queda únicamente para migración en builds debug.
 */
object LicenseSecurityConfig {
    const val SIGNED_SCHEMA = "VISOR_LICENSE_V2"
    const val LEGACY_SCHEMA = "VISOR_LICENSE_V1"

    val VERIFY_PUBLIC_KEY_B64: String
        get() = BuildConfig.LICENSE_VERIFY_PUBLIC_KEY_B64

    val LEGACY_HMAC_SECRET: String
        get() = BuildConfig.LEGACY_LICENSE_HMAC_SECRET
}
