package com.frank.visordocumentoscifrado.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ImageView especializado para leer documentos.
 *
 * Se mantiene separado de PdfActivity para que el visor sea fácil de mantener:
 * - PdfActivity decide qué página renderizar.
 * - ZoomImageView solo maneja gestos y navegación visual.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private val drawMatrix = Matrix()
    private val lastTouch = PointF()

    private var minScale = 1f
    private var currentScale = 1f
    private var dragMode = false

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onSingleTap: (() -> Unit)? = null
    var onInteraction: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val targetScale = (currentScale * factor).coerceIn(minScale, MAX_SCALE)
                val appliedFactor = targetScale / currentScale

                currentScale = targetScale
                drawMatrix.postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = drawMatrix
                onInteraction?.invoke()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > minScale + 0.15f) {
                    resetZoom()
                } else {
                    quickZoom(e.x, e.y)
                }
                onInteraction?.invoke()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null || currentScale > minScale + 0.08f) return false

                val dx = e2.x - e1.x
                val dy = e2.y - e1.y

                if (abs(dx) > SWIPE_DISTANCE && abs(dx) > abs(dy) && abs(velocityX) > SWIPE_VELOCITY) {
                    if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
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
        setBackgroundColor(0xFF101820.toInt())
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { fitPageToScreen() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouch.set(event.x, event.y)
                dragMode = true
                onInteraction?.invoke()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragMode && currentScale > minScale + 0.02f) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y

                    drawMatrix.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = drawMatrix

                    lastTouch.set(event.x, event.y)
                    onInteraction?.invoke()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragMode = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitPageToScreen() }
    }

    /**
     * Ajusta la página completa al centro de pantalla.
     * Se usa al abrir página y al tocar "Ajustar".
     */
    fun resetZoom() {
        fitPageToScreen()
    }

    private fun fitPageToScreen() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val drawableW = d.intrinsicWidth.toFloat()
        val drawableH = d.intrinsicHeight.toFloat()

        // Ajuste tipo lector: encaja página completa, dejando margen visual.
        val scaleX = viewW / drawableW
        val scaleY = viewH / drawableH
        minScale = min(scaleX, scaleY) * 0.96f
        currentScale = minScale

        val dx = (viewW - drawableW * minScale) / 2f
        val dy = (viewH - drawableH * minScale) / 2f

        drawMatrix.reset()
        drawMatrix.postScale(minScale, minScale)
        drawMatrix.postTranslate(dx, dy)

        imageMatrix = drawMatrix
    }

    private fun quickZoom(x: Float, y: Float) {
        val targetScale = max(minScale * 2.25f, 2.0f).coerceAtMost(MAX_SCALE)
        val factor = targetScale / currentScale
        currentScale = targetScale
        drawMatrix.postScale(factor, factor, x, y)
        fixTranslation()
        imageMatrix = drawMatrix
    }

    /**
     * Evita que la página se pierda fuera de la pantalla al arrastrar o hacer zoom.
     */
    private fun fixTranslation() {
        val rect = currentDrawableRect() ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var dx = 0f
        var dy = 0f

        dx = if (rect.width() <= viewW) {
            (viewW - rect.width()) / 2f - rect.left
        } else {
            when {
                rect.left > 0 -> -rect.left
                rect.right < viewW -> viewW - rect.right
                else -> 0f
            }
        }

        dy = if (rect.height() <= viewH) {
            (viewH - rect.height()) / 2f - rect.top
        } else {
            when {
                rect.top > 0 -> -rect.top
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

    @Suppress("unused")
    private fun currentMatrixScale(): Float {
        drawMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    companion object {
        private const val MAX_SCALE = 6.0f
        private const val SWIPE_DISTANCE = 140
        private const val SWIPE_VELOCITY = 250
    }
}
