package com.frank.visordocumentoscifrado.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.frank.visordocumentoscifrado.config.SecurityConfig
import com.frank.visordocumentoscifrado.documents.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Visor PDF seguro y fluido.
 *
 * Diseño UX:
 * - Pantalla limpia tipo lector digital.
 * - Un toque: muestra/oculta controles flotantes.
 * - Doble toque: alterna zoom rápido / ajustar pantalla.
 * - Pellizcar: zoom libre.
 * - Arrastrar: mover página cuando está ampliada.
 * - Swipe horizontal: cambiar página cuando no está ampliada.
 * - Controles translúcidos tipo overlay para no tapar la lectura.
 *
 * Seguridad:
 * - El PDF se descifra solamente dentro de cache interno de la app.
 * - No hay botón de compartir/exportar.
 * - Al salir se elimina el archivo temporal.
 * - Respeta FLAG_SECURE para bloquear capturas cuando está activado.
 */
class PdfActivity : AppCompatActivity() {
    companion object {
        var selectedFile: String = ""
        var selectedTitle: String = ""
        private const val TAG = "VisorPDF"

        // Límite razonable para evitar OutOfMemory en teléfonos de gama media.
        private const val MAX_RENDER_WIDTH = 2600
        private const val MAX_RENDER_HEIGHT = 3900
        private const val CONTROLS_AUTO_HIDE_MS = 3800L
    }

    private var renderer: PdfRenderer? = null
    private var pageIndex = 0

    private lateinit var image: ZoomImageView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var searchPanel: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var pageText: TextView
    private lateinit var searchBox: EditText
    private lateinit var leftPageButton: TextView
    private lateinit var rightPageButton: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var cacheFile: File? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var lastSearch = ""

    private val autoHideRunnable = Runnable { setControlsVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SecurityConfig.BLOCK_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        PDFBoxResourceLoader.init(applicationContext)
        buildReaderUi()
        openPdfSafely()
    }

    /**
     * Construye la pantalla sin XML para mantener el visor autocontenido.
     * Esto facilita moverlo a otro proyecto HSE más adelante.
     */
    private fun buildReaderUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF101820.toInt())
        }

        image = ZoomImageView(this).apply {
            setBackgroundColor(0xFF101820.toInt())
            onSwipeLeft = { nextPage() }
            onSwipeRight = { prevPage() }
            onSingleTap = { toggleControls() }
            onInteraction = { scheduleAutoHide() }
        }

        root.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBackground(0xD9111F2E.toInt(), dp(18).toFloat())
            elevation = dp(8).toFloat()
        }

        titleText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 2
            text = selectedTitle.ifBlank { "Documento" }
        }

        pageText = TextView(this).apply {
            setTextColor(0xFFC8D6E5.toInt())
            textSize = 12f
            text = "Cargando..."
        }

        topBar.addView(titleText)
        topBar.addView(pageText)

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { setMargins(dp(12), dp(14), dp(12), 0) }
        )

        leftPageButton = floatingCircle("‹") { prevPage() }
        rightPageButton = floatingCircle("›") { nextPage() }

        root.addView(
            leftPageButton,
            FrameLayout.LayoutParams(dp(48), dp(64), Gravity.START or Gravity.CENTER_VERTICAL)
                .apply { setMargins(dp(10), 0, 0, 0) }
        )
        root.addView(
            rightPageButton,
            FrameLayout.LayoutParams(dp(48), dp(64), Gravity.END or Gravity.CENTER_VERTICAL)
                .apply { setMargins(0, 0, dp(10), 0) }
        )

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(0xD9111F2E.toInt(), dp(18).toFloat())
            elevation = dp(8).toFloat()
        }

        bottomBar.addView(actionButton("Cerrar") { finish() })
        bottomBar.addView(actionButton("Buscar") { showSearchPanel() })
        bottomBar.addView(actionButton("Ajustar") { image.resetZoom() })

        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(dp(12), 0, dp(12), dp(18)) }
        )

        searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(0xEE0B2545.toInt(), dp(18).toFloat())
            elevation = dp(12).toFloat()
        }

        searchBox = EditText(this).apply {
            hint = "Buscar palabra o frase"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB3C8.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF86A8C7.toInt())
        }

        searchPanel.addView(searchBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchPanel.addView(actionButton("Ir") { searchInPdf(searchBox.text.toString(), false) })
        searchPanel.addView(actionButton("Sig.") { searchInPdf(searchBox.text.toString().ifBlank { lastSearch }, true) })
        searchPanel.addView(actionButton("×") { hideSearchPanel() })

        root.addView(
            searchPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(dp(12), 0, dp(12), dp(82)) }
        )

        setContentView(root)
        scheduleAutoHide()
    }

    private fun actionButton(textValue: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(0x334C8AB8, dp(14).toFloat())
            setOnClickListener {
                action()
                scheduleAutoHide()
            }
        }

    private fun floatingCircle(textValue: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(0x99111F2E.toInt(), dp(24).toFloat())
            elevation = dp(8).toFloat()
            setOnClickListener {
                action()
                scheduleAutoHide()
            }
        }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toggleControls() {
        setControlsVisible(!controlsVisible)
        if (controlsVisible) scheduleAutoHide()
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        val alphaTarget = if (visible) 1f else 0f
        val visibilityEnd = if (visible) View.VISIBLE else View.GONE
        listOf(topBar, bottomBar, leftPageButton, rightPageButton).forEach { v ->
            if (visible) v.visibility = View.VISIBLE
            v.animate().alpha(alphaTarget).setDuration(160).withEndAction {
                if (!visible) v.visibility = visibilityEnd
            }.start()
        }
        if (!visible) hideSearchPanel()
    }

    private fun scheduleAutoHide() {
        hideHandler.removeCallbacks(autoHideRunnable)
        hideHandler.postDelayed(autoHideRunnable, CONTROLS_AUTO_HIDE_MS)
    }

    private fun showSearchPanel() {
        setControlsVisible(true)
        searchPanel.visibility = View.VISIBLE
        searchPanel.alpha = 1f
        searchBox.requestFocus()
        hideHandler.removeCallbacks(autoHideRunnable)
    }

    private fun hideSearchPanel() {
        searchPanel.visibility = View.GONE
    }

    private fun nextPage() {
        renderer?.let {
            if (pageIndex < it.pageCount - 1) {
                pageIndex++
                renderSafely()
            } else {
                Toast.makeText(this, "Última página", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prevPage() {
        if (pageIndex > 0) {
            pageIndex--
            renderSafely()
        } else {
            Toast.makeText(this, "Primera página", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdfSafely() {
        try {
            if (selectedFile.isBlank()) throw IllegalStateException("No se seleccionó ningún documento.")

            val doc = DocumentRepository.listAllowed(this).firstOrNull { it.file == selectedFile }
                ?: throw IllegalStateException("No se encontró el documento o no está permitido para esta licencia: $selectedFile")

            titleText.text = doc.title
            pageText.text = "Abriendo documento..."

            val bytes = DocumentRepository.decrypt(this, doc)
            if (!bytes.take(5).toByteArray().contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D))) {
                throw IllegalStateException("El archivo descifrado no parece ser un PDF válido.")
            }

            cacheFile = File(cacheDir, "visor_pdf_${System.currentTimeMillis()}.pdf")
            cacheFile!!.writeBytes(bytes)

            parcelFileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(parcelFileDescriptor!!)

            if ((renderer?.pageCount ?: 0) <= 0) throw IllegalStateException("El PDF no tiene páginas legibles.")
            renderSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo PDF", e)
            showFatalError("No se pudo abrir el documento.", friendlyError(e))
        }
    }

    /**
     * Renderiza una página como bitmap con resolución suficiente para zoom moderado,
     * sin exceder límites de memoria.
     */
    private fun renderSafely() {
        try {
            val r = renderer ?: return
            val page = r.openPage(pageIndex)

            try {
                val screenWidth = resources.displayMetrics.widthPixels
                val renderWidth = (screenWidth * 2).coerceIn(screenWidth, MAX_RENDER_WIDTH)
                var scale = renderWidth.toFloat() / page.width.toFloat()
                var width = (page.width * scale).toInt().coerceAtLeast(1)
                var height = (page.height * scale).toInt().coerceAtLeast(1)

                if (height > MAX_RENDER_HEIGHT) {
                    scale = MAX_RENDER_HEIGHT.toFloat() / page.height.toFloat()
                    width = (page.width * scale).toInt().coerceAtLeast(1)
                    height = (page.height * scale).toInt().coerceAtLeast(1)
                }

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                drawProtectedWatermark(canvas, width, height)

                image.setImageBitmap(bmp)
                updatePageText(r.pageCount)
            } finally {
                page.close()
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Sin memoria renderizando PDF", oom)
            showFatalError("El PDF es demasiado pesado para este teléfono.", "Comprime el PDF o divide el manual en archivos más pequeños.")
        } catch (e: Exception) {
            Log.e(TAG, "Error renderizando PDF", e)
            showFatalError("No se pudo mostrar la página.", friendlyError(e))
        }
    }

    private fun updatePageText(totalPages: Int) {
        pageText.text = "Página ${pageIndex + 1} de $totalPages · pellizca para ampliar · doble toque para zoom"
    }

    /**
     * Marca de agua con baja opacidad.
     * No es la protección principal; solo refuerza visualmente que es consulta interna.
     */
    private fun drawProtectedWatermark(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1F071A2F
            textSize = (width / 18f).coerceIn(28f, 64f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.save()
        canvas.rotate(-28f, width / 2f, height / 2f)
        canvas.drawText("DOCUMENTO INTERNO", width / 2f, height / 2f, paint)
        canvas.restore()
    }

    private fun searchInPdf(queryRaw: String, fromNext: Boolean) {
        val query = queryRaw.trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Escribe una palabra o frase", Toast.LENGTH_SHORT).show()
            return
        }

        val file = cacheFile ?: return

        try {
            lastSearch = query
            pageText.text = "Buscando: $query..."

            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                val pageCount = doc.numberOfPages
                val start = if (fromNext) pageIndex + 1 else pageIndex
                val order = ((start until pageCount) + (0 until start)).distinct()

                for (p in order) {
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    val text = stripper.getText(doc)
                    if (text.contains(query, ignoreCase = true)) {
                        pageIndex = p
                        renderSafely()
                        Toast.makeText(this, "Encontrado en página ${p + 1}", Toast.LENGTH_LONG).show()
                        return
                    }
                }

                Toast.makeText(this, "No se encontró: $query", Toast.LENGTH_LONG).show()
                renderSafely()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando texto", e)
            AlertDialog.Builder(this)
                .setTitle("No se pudo buscar")
                .setMessage("Algunos PDFs escaneados son imágenes y no contienen texto buscable. Error: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
            renderSafely()
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("AEADBadTag", true) || msg.contains("BAD_DECRYPT", true) || msg.contains("BadPadding", true) || msg.contains("tag", true) ->
                "La APK fue compilada con una clave distinta a la usada para cifrar estos documentos. Vuelve a cifrar manuales con la herramienta y recompila la APK."
            msg.contains("Integridad", true) ->
                "Falló la verificación de integridad. El .bin o el index.json no corresponden al PDF cifrado."
            msg.contains("password", true) || msg.contains("encrypted", true) ->
                "El PDF original parece estar protegido con contraseña o cifrado. Quita esa protección antes de cifrarlo para la app."
            else -> msg
        }
    }

    private fun showFatalError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Regresar") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(autoHideRunnable)
        try { renderer?.close() } catch (_: Exception) {}
        try { parcelFileDescriptor?.close() } catch (_: Exception) {}
        cacheFile?.delete()
        super.onDestroy()
    }
}
