package com.frank.visordocumentoscifrado.documents

import android.content.Context
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.config.AreaCatalog
import com.frank.visordocumentoscifrado.license.LicenseManager

/**
 * Punto único de autorización de documentos.
 *
 * Regla importante: la UI puede ocultar documentos, pero este guard vuelve a validar
 * justo antes de descifrar. Así, aunque alguien fuerce una apertura, no se entrega PDF
 * si el área no pertenece a la licencia.
 */
object DocumentAccessGuard {
    fun ensureCanOpen(context: Context, doc: SecureDocument, embeddedAreaId: String? = null) {
        if (AppConfig.DEBUG_MODE) return
        val lic = LicenseManager.current(context) ?: throw SecurityException("Sin licencia válida.")
        val indexArea = AreaCatalog.normalize(doc.areaId)
        val internalArea = AreaCatalog.normalize(embeddedAreaId ?: doc.areaId)
        if (indexArea != internalArea) {
            throw SecurityException("El área interna del documento no coincide con el catálogo. Archivo alterado o index.json incorrecto.")
        }
        if (!LicenseManager.canAccessArea(lic, internalArea)) {
            throw SecurityException("Acceso denegado. Tu licencia no permite abrir documentos de ${AreaCatalog.displayName(internalArea)}.")
        }
    }
}
