package com.frank.visordocumentoscifrado.config

import com.frank.visordocumentoscifrado.BuildConfig

/**
 * Configuración central de la APK.
 *
 * La versión y el modo de depuración ya no se duplican aquí: Gradle/BuildConfig
 * es la única fuente de verdad. Así una APK release nunca puede quedar por error
 * con el bypass de licencia usado durante desarrollo.
 */
object AppConfig {
    const val APP_DISPLAY_NAME = "Visor Seguro de Manuales"
    const val COMPANY_NAME = "Empresa"
    const val PROJECT_NAME = "ALACTE"

    val VERSION_NAME: String get() = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int get() = BuildConfig.VERSION_CODE

    /** Vigencia de la APK. Diferente a la vigencia de cada licencia. */
    const val APP_EXPIRES_AT = "2026-12-31"

    /** Assets generados por tools/document_encryptor.py. */
    const val ENCRYPTED_MANUALS_ASSET_DIR = "manuales"
    const val MANUAL_INDEX_FILE = "manuales/index.json"

    const val LICENSE_FILE_NAME = "license.sec"
    const val INSTALL_ID_KEY = "install_id"

    /**
     * Sólo un build debug con ALLOW_LICENSE_BYPASS=true puede omitir licencia.
     * La variante release fija ese campo a false desde Gradle.
     */
    val DEBUG_MODE: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.ALLOW_LICENSE_BYPASS

    /** Punto reservado para el transporte HTTP/API futuro. */
    const val LICENSE_STATUS_API_ENABLED = false
    const val LICENSE_STATUS_API_URL = ""
}
