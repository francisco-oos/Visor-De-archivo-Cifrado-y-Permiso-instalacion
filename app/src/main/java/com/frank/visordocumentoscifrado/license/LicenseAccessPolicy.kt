package com.frank.visordocumentoscifrado.license

import com.frank.visordocumentoscifrado.config.AreaCatalog

/** Reglas puras de autorización por área, separadas de almacenamiento y UI. */
object LicenseAccessPolicy {
    fun hasAllAccess(license: LicenseData): Boolean {
        val mode = AreaCatalog.normalize(license.accessMode)
        if (mode == AreaCatalog.ALL) return true
        if (AreaCatalog.normalize(license.area) == AreaCatalog.ALL) return true
        return license.allowedAreas.any { AreaCatalog.normalize(it) == AreaCatalog.ALL }
    }

    fun canAccessArea(license: LicenseData, areaId: String): Boolean {
        if (hasAllAccess(license)) return true
        val normalized = AreaCatalog.normalize(areaId)
        return license.allowedAreas.any { AreaCatalog.normalize(it) == normalized }
    }
}
