package com.frank.visordocumentoscifrado.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AreaCatalogTest {
    @Test
    fun normalize_handlesAccentsAndAliases() {
        assertEquals(AreaCatalog.ADQUISICION, AreaCatalog.normalize("Adquisición"))
        assertEquals(AreaCatalog.OPERACIONES, AreaCatalog.normalize("Jefatura"))
        assertEquals(AreaCatalog.ALL, AreaCatalog.normalize("Todos"))
    }

    @Test
    fun explicitOtro_keepsHistoricalAllSemantics() {
        assertEquals(AreaCatalog.ALL, AreaCatalog.normalize(AreaCatalog.OTRO))
    }
}
