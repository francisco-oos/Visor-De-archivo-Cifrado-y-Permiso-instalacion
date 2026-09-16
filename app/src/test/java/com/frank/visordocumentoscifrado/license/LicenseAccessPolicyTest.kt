package com.frank.visordocumentoscifrado.license

import com.frank.visordocumentoscifrado.config.AreaCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseAccessPolicyTest {
    private fun license(
        area: String = AreaCatalog.ADQUISICION,
        accessMode: String = "AREA",
        allowed: List<String> = listOf(AreaCatalog.ADQUISICION)
    ) = LicenseData(
        employeeName = "Prueba",
        employeeId = "1",
        project = "TEST",
        area = area,
        position = "Tester",
        deviceHash = "HASH",
        installId = "INSTALL",
        expiresAt = "2099-12-31",
        issuedAt = "2026-01-01",
        accessMode = accessMode,
        allowedAreas = allowed
    )

    @Test
    fun areaLicense_allowsOnlyAssignedArea() {
        val lic = license()
        assertTrue(LicenseAccessPolicy.canAccessArea(lic, AreaCatalog.ADQUISICION))
        assertFalse(LicenseAccessPolicy.canAccessArea(lic, AreaCatalog.QC))
    }

    @Test
    fun allMode_allowsEveryCatalogArea() {
        val lic = license(accessMode = AreaCatalog.ALL, allowed = emptyList())
        assertTrue(LicenseAccessPolicy.hasAllAccess(lic))
        assertTrue(LicenseAccessPolicy.canAccessArea(lic, AreaCatalog.QC))
        assertTrue(LicenseAccessPolicy.canAccessArea(lic, AreaCatalog.PERFORACION))
    }

    @Test
    fun blankAreaAndNoAllowedAreas_doNotGrantAllAccess() {
        val lic = license(area = "", allowed = emptyList())
        assertFalse(LicenseAccessPolicy.hasAllAccess(lic))
        assertFalse(LicenseAccessPolicy.canAccessArea(lic, AreaCatalog.QC))
    }
}
