package com.frank.visordocumentoscifrado.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de las reglas de zoom/swipe sin depender de Android. */
class ViewerZoomPolicyTest {
    @Test
    fun clampScale_usaLimiteRelativoAlAjuste() {
        val minScale = 0.4f
        assertEquals(0.4f, ViewerZoomPolicy.clampScale(0.1f, minScale), 0.0001f)
        assertEquals(2.0f, ViewerZoomPolicy.clampScale(9.0f, minScale), 0.0001f)
    }

    @Test
    fun doubleTap_esRelativoYNoAbsoluto() {
        val minScale = 0.5f
        assertEquals(
            minScale * ViewerZoomPolicy.DOUBLE_TAP_ZOOM_RATIO,
            ViewerZoomPolicy.doubleTapTargetScale(minScale),
            0.0001f
        )
    }

    @Test
    fun swipeDePagina_soloOcurreEnEscalaAjustada() {
        assertTrue(
            ViewerZoomPolicy.shouldChangePage(
                dx = -200f,
                dy = 20f,
                velocityX = -1600f,
                currentScale = 0.5f,
                minScale = 0.5f,
                minDistance = 100f,
                minVelocity = 800f
            )
        )

        assertFalse(
            ViewerZoomPolicy.shouldChangePage(
                dx = -200f,
                dy = 20f,
                velocityX = -1600f,
                currentScale = 1.0f,
                minScale = 0.5f,
                minDistance = 100f,
                minVelocity = 800f
            )
        )
    }

    @Test
    fun gestoVertical_noSeConfundeConCambioDePagina() {
        assertFalse(
            ViewerZoomPolicy.shouldChangePage(
                dx = 140f,
                dy = 190f,
                velocityX = 1500f,
                currentScale = 0.5f,
                minScale = 0.5f,
                minDistance = 100f,
                minVelocity = 800f
            )
        )
    }
}
