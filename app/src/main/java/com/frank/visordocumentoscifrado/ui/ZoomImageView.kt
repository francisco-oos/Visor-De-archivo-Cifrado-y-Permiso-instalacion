package com.frank.visordocumentoscifrado.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Superficie gestual del lector PDF.
 *
 * Responsabilidades:
 * - Ajustar la página completa al área visible.
 * - Pellizcar para ampliar/reducir manteniendo el foco bajo los dedos.
 * - Doble toque para alternar entre ajuste y zoom de lectura.
 * - Arrastrar la página cuando está ampliada.
 * - Aplicar inercia al paneo mediante OverScroller.
 * - Cambiar de página con fling horizontal sólo cuando la página está ajustada.
 *
 * Esta clase NO sabe qué PDF se está leyendo ni qué página toca renderizar. Esa
 * separación permite reemplazar más adelante el bitmap completo por tiles sin
 * reescribir la semántica de los gestos.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val drawMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val scroller = OverScroller(context)
    private val viewConfiguration = ViewConfiguration.get(context)
    private val density = resources.displayMetrics.density

    private var minScale = 1f
    private var currentScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragging = false
    private var zoomAnimator: ValueAnimator? = null

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onSingleTap: (() -> Unit)? = null
    var onInteraction: (() -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null

    /**
     * Detector dedicado al pinch-to-zoom.
     *
     * La escala máxima se expresa de forma RELATIVA a la escala de ajuste de página.
     * Así un PDF grande o pequeño se siente igual al usuario y evitamos el salto de
     * escala absoluta que tenía FIX25.
     */
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                abortMotionAnimations()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val targetScale = ViewerZoomPolicy.clampScale(
                    currentScale * detector.scaleFactor,
                    minScale
                )
                val appliedFactor = targetScale / currentScale

                currentScale = targetScale
                drawMatrix.postScale(
                    appliedFactor,
                    appliedFactor,
                    detector.focusX,
                    detector.focusY
                )
                fixTranslation()
                applyMatrix()
                notifyZoomChanged()
                onInteraction?.invoke()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Si el usuario terminó prácticamente en 100 %, centramos de forma suave.
                if (ViewerZoomPolicy.isAtFitScale(currentScale, minScale)) {
                    animateZoomTo(minScale, width / 2f, height / 2f)
                }
            }
        }
    )

    /**
     * Detector de gestos discretos: toque, doble toque y fling.
     * El paneo continuo se maneja en onTouchEvent para tener control total de límites.
     */
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                onSingleTap?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = if (ViewerZoomPolicy.isAtFitScale(currentScale, minScale)) {
                    ViewerZoomPolicy.doubleTapTargetScale(minScale)
                } else {
                    minScale
                }

                animateZoomTo(target, e.x, e.y)
                onInteraction?.invoke()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val pageSwipeDistance = 72f * density
                val pageSwipeVelocity = max(
                    520f * density,
                    viewConfiguration.scaledMinimumFlingVelocity * 4f
                )

                // Cuando estamos en ajuste completo, un fling horizontal claro cambia de página.
                if (ViewerZoomPolicy.shouldChangePage(
                        dx = dx,
                        dy = dy,
                        velocityX = velocityX,
                        currentScale = currentScale,
                        minScale = minScale,
                        minDistance = pageSwipeDistance,
                        minVelocity = pageSwipeVelocity
                    )
                ) {
                    if (dx < 0f) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                    onInteraction?.invoke()
                    return true
                }

                // Con zoom activo el mismo gesto pertenece a la página: aplicamos inercia.
                if (!ViewerZoomPolicy.isAtFitScale(currentScale, minScale)) {
                    startPanFling(velocityX, velocityY)
                    onInteraction?.invoke()
                    return true
                }

                return false
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = drawMatrix
        isClickable = true
        setBackgroundColor(0xFF101820.toInt())
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        abortMotionAnimations()
        super.setImageDrawable(drawable)
        post { fitPageToScreen() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Ambos detectores reciben el mismo flujo: Android está diseñado para combinarlos.
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortMotionAnimations()
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                dragging = true
                onInteraction?.invoke()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Evita que una inercia anterior compita con el nuevo gesto de dos dedos.
                scroller.abortAnimation()
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    // Mantiene actualizado el origen del drag para evitar saltos al soltar un dedo.
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (dragging && !ViewerZoomPolicy.isAtFitScale(currentScale, minScale)) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    drawMatrix.postTranslate(dx, dy)
                    fixTranslation()
                    applyMatrix()

                    lastTouchX = event.x
                    lastTouchY = event.y
                    onInteraction?.invoke()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitPageToScreen() }
    }

    /** Ajusta la página completa y centra el documento con una animación breve. */
    fun resetZoom(animated: Boolean = true) {
        if (drawable == null) return
        if (animated) {
            animateZoomTo(minScale, width / 2f, height / 2f)
        } else {
            fitPageToScreen()
        }
    }

    /** Relación de zoom visible: 1.0 = página ajustada, 2.0 = 200 %. */
    fun currentZoomRatio(): Float = ViewerZoomPolicy.zoomRatio(currentScale, minScale)

    private fun fitPageToScreen() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return

        abortMotionAnimations()

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val drawableW = d.intrinsicWidth.toFloat()
        val drawableH = d.intrinsicHeight.toFloat()

        // Encaja la hoja completa dejando un margen pequeño alrededor, estilo lector.
        val scaleX = viewW / drawableW
        val scaleY = viewH / drawableH
        minScale = min(scaleX, scaleY) * 0.96f
        currentScale = minScale

        val dx = (viewW - drawableW * minScale) / 2f
        val dy = (viewH - drawableH * minScale) / 2f

        drawMatrix.reset()
        drawMatrix.postScale(minScale, minScale)
        drawMatrix.postTranslate(dx, dy)
        applyMatrix()
        notifyZoomChanged()
    }

    /**
     * Anima el zoom para que el doble toque y el botón Ajustar no produzcan saltos.
     * El foco permanece bajo el dedo siempre que el contenido permita ese desplazamiento.
     */
    private fun animateZoomTo(targetScaleRaw: Float, focusX: Float, focusY: Float) {
        val targetScale = ViewerZoomPolicy.clampScale(targetScaleRaw, minScale)
        val startScale = currentScale

        if (kotlin.math.abs(startScale - targetScale) < 0.0001f) {
            fixTranslation()
            applyMatrix()
            return
        }

        zoomAnimator?.cancel()
        scroller.abortAnimation()

        var previousScale = startScale
        zoomAnimator = ValueAnimator.ofFloat(startScale, targetScale).apply {
            duration = ZOOM_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedScale = animator.animatedValue as Float
                val factor = animatedScale / previousScale
                previousScale = animatedScale
                currentScale = animatedScale

                drawMatrix.postScale(factor, factor, focusX, focusY)
                fixTranslation()
                applyMatrix()
                notifyZoomChanged()
            }
            start()
        }
    }

    /**
     * Inicia un desplazamiento con inercia dentro de los límites reales de la página.
     * OverScroller se encarga de desacelerar de forma natural según la convención Android.
     */
    private fun startPanFling(velocityX: Float, velocityY: Float) {
        val bounds = translationBounds() ?: return
        val current = currentTranslation()

        scroller.fling(
            current.first.toInt(),
            current.second.toInt(),
            velocityX.toInt(),
            velocityY.toInt(),
            bounds.minX,
            bounds.maxX,
            bounds.minY,
            bounds.maxY
        )
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        super.computeScroll()
        if (!scroller.computeScrollOffset()) return

        val current = currentTranslation()
        val dx = scroller.currX - current.first
        val dy = scroller.currY - current.second

        drawMatrix.postTranslate(dx, dy)
        fixTranslation()
        applyMatrix()
        postInvalidateOnAnimation()
    }

    /** Mantiene la hoja dentro de la pantalla sin dejar zonas vacías innecesarias. */
    private fun fixTranslation() {
        val rect = currentDrawableRect() ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val dx = if (rect.width() <= viewW) {
            (viewW - rect.width()) / 2f - rect.left
        } else {
            when {
                rect.left > 0f -> -rect.left
                rect.right < viewW -> viewW - rect.right
                else -> 0f
            }
        }

        val dy = if (rect.height() <= viewH) {
            (viewH - rect.height()) / 2f - rect.top
        } else {
            when {
                rect.top > 0f -> -rect.top
                rect.bottom < viewH -> viewH - rect.bottom
                else -> 0f
            }
        }

        drawMatrix.postTranslate(dx, dy)
    }

    private fun currentDrawableRect(): RectF? {
        val d = drawable ?: return null
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)
        return rect
    }

    /** Devuelve la traslación actual de la matriz en coordenadas de pantalla. */
    private fun currentTranslation(): Pair<Float, Float> {
        drawMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MTRANS_X] to matrixValues[Matrix.MTRANS_Y]
    }

    /** Límites absolutos de traslación usados por OverScroller durante un fling. */
    private fun translationBounds(): PanBounds? {
        val d = drawable ?: return null
        val contentW = d.intrinsicWidth * currentScale
        val contentH = d.intrinsicHeight * currentScale
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val minX: Int
        val maxX: Int
        if (contentW <= viewW) {
            val centered = ((viewW - contentW) / 2f).toInt()
            minX = centered
            maxX = centered
        } else {
            minX = (viewW - contentW).toInt()
            maxX = 0
        }

        val minY: Int
        val maxY: Int
        if (contentH <= viewH) {
            val centered = ((viewH - contentH) / 2f).toInt()
            minY = centered
            maxY = centered
        } else {
            minY = (viewH - contentH).toInt()
            maxY = 0
        }

        return PanBounds(minX, maxX, minY, maxY)
    }

    private fun applyMatrix() {
        imageMatrix = drawMatrix
    }

    private fun notifyZoomChanged() {
        onZoomChanged?.invoke(currentZoomRatio())
    }

    private fun abortMotionAnimations() {
        zoomAnimator?.cancel()
        zoomAnimator = null
        if (!scroller.isFinished) scroller.abortAnimation()
    }

    override fun onDetachedFromWindow() {
        abortMotionAnimations()
        super.onDetachedFromWindow()
    }

    private data class PanBounds(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )

    companion object {
        private const val ZOOM_ANIMATION_MS = 220L
    }
}
