package com.frank.visordocumentoscifrado.ui

import kotlin.math.abs

/**
 * Política matemática de zoom y cambio de página del visor.
 *
 * Esta clase no conoce Android ni dibuja nada. Su propósito es concentrar las reglas
 * que deben mantenerse estables aunque después cambiemos ImageView, PdfRenderer o
 * implementemos render por tiles. Al estar desacoplada se puede probar con JUnit.
 */
object ViewerZoomPolicy {
    /** Escala máxima relativa a la escala "ajustar página". */
    const val MAX_ZOOM_RATIO = 5.0f

    /** Zoom elegido para el doble toque: suficiente para leer texto sin un salto brusco. */
    const val DOUBLE_TAP_ZOOM_RATIO = 2.35f

    /** Tolerancia para considerar que el documento está otra vez en escala ajustada. */
    private const val FIT_EPSILON = 0.04f

    /** Devuelve la relación de zoom respecto a la escala mínima/ajustada. */
    fun zoomRatio(currentScale: Float, minScale: Float): Float {
        if (minScale <= 0f) return 1f
        return currentScale / minScale
    }

    /** Limita cualquier escala a un rango relativo estable, independiente del tamaño del PDF. */
    fun clampScale(targetScale: Float, minScale: Float): Float {
        val safeMin = minScale.coerceAtLeast(0.0001f)
        return targetScale.coerceIn(safeMin, safeMin * MAX_ZOOM_RATIO)
    }

    /** Escala destino para el zoom rápido por doble toque. */
    fun doubleTapTargetScale(minScale: Float): Float =
        clampScale(minScale * DOUBLE_TAP_ZOOM_RATIO, minScale)

    /** Verdadero cuando la página está suficientemente cerca del ajuste completo. */
    fun isAtFitScale(currentScale: Float, minScale: Float): Boolean =
        zoomRatio(currentScale, minScale) <= 1f + FIT_EPSILON

    /**
     * Decide si un fling horizontal debe interpretarse como cambio de página.
     *
     * Regla importante: nunca se cambia de página mientras el usuario está ampliado;
     * en ese caso el mismo gesto pertenece al paneo del documento.
     */
    fun shouldChangePage(
        dx: Float,
        dy: Float,
        velocityX: Float,
        currentScale: Float,
        minScale: Float,
        minDistance: Float,
        minVelocity: Float
    ): Boolean {
        if (!isAtFitScale(currentScale, minScale)) return false
        return abs(dx) >= minDistance &&
            abs(dx) > abs(dy) * 1.15f &&
            abs(velocityX) >= minVelocity
    }
}
