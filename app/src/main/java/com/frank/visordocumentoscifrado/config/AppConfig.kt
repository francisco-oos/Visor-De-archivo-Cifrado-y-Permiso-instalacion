package com.frank.visordocumentoscifrado.config

/**
 * Configuración central de la APK.
 *
 * Mantén aquí lo que normalmente cambia entre versiones:
 * nombre comercial, vigencia propia de la app, modo debug y rutas de assets.
 *
 * Importante:
 * - APP_EXPIRES_AT protege la versión/producto.
 * - La caducidad de cada empleado pertenece a licencia.key y se validará aparte.
 */
object AppConfig {
    const val APP_DISPLAY_NAME = "Visor Seguro de Manuales"
    const val COMPANY_NAME = "Empresa"
    const val PROJECT_NAME = "ALACTE"

    const val VERSION_NAME = "1.1.0"
    const val VERSION_CODE = 10

    /** Vigencia de la APK. Diferente a la vigencia de licencia.key. */
    const val APP_EXPIRES_AT = "2026-12-31"

    /** Assets generados por tools/document_encryptor.py. */
    const val ENCRYPTED_MANUALS_ASSET_DIR = "manuales"
    const val MANUAL_INDEX_FILE = "manuales/index.json"

    const val LICENSE_FILE_NAME = "license.sec"
    const val INSTALL_ID_KEY = "install_id"

    /**
     * DEBUG_MODE:
     * - true: permite ver todos los documentos sin licencia para probar cifrado/visor.
     * - false: flujo real: instalación -> aviso bot -> solicitud -> espera aprobación.
     */
    const val DEBUG_MODE = true

    /**
     * Preparado para el proyecto futuro de licencias.
     * En esta APK limpia queda apagado; Telegram solo se usa para enviar eventos.
     */
    const val LICENSE_STATUS_API_ENABLED = false
    const val LICENSE_STATUS_API_URL = ""
}
