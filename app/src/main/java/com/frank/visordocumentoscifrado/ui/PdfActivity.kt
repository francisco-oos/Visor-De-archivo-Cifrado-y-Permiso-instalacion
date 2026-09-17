package com.frank.visordocumentoscifrado.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.frank.visordocumentoscifrado.config.SecurityConfig
import com.frank.visordocumentoscifrado.documents.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Visor PDF seguro y gestual.
 *
 * R2/R2.1 separa cuatro responsabilidades:
 * - interacción: ZoomImageView maneja pinch, doble toque, pan, inercia y swipe;
 * - render: PdfRenderer trabaja en un único worker para no congelar la pantalla;
 * - búsqueda: PdfSearchEngine recorre texto mediante PDFBox en Dispatchers.IO;
 * - resaltado: la geometría normalizada de las coincidencias se pinta sobre el bitmap.
 *
 * El resaltado NO modifica el PDF. Se dibuja únicamente en memoria sobre la imagen que
 * ya se muestra en pantalla, por lo que desaparece al cerrar búsqueda o cambiar consulta.
 *
 * Seguridad conservada:
 * - el PDF temporal vive sólo en cache interno;
 * - no existe exportar/compartir;
 * - FLAG_SECURE continúa bloqueando capturas donde Android lo respeta;
 * - el archivo temporal se elimina al cerrar.
 *
 * Nota de arquitectura:
 * VSDOC2 todavía se descifra completo antes de llegar aquí. Esa deuda se mantiene
 * deliberadamente separada para R2A/VSDOC3, donde se migrará a streaming/tiles.
 */
class PdfActivity : AppCompatActivity() {
    companion object {
        var selectedFile: String = ""
        var selectedTitle: String = ""
        private const val TAG = "VisorPDF"

        // Límite heredado mientras llega el render por tiles de la siguiente etapa.
        private const val MAX_RENDER_WIDTH = 2600
        private const val MAX_RENDER_HEIGHT = 3900
        private const val CONTROLS_AUTO_HIDE_MS = 3800L
    }

    private var renderer: PdfRenderer? = null
    private var pageIndex = 0
    private var lastPageCount = 0
    private var lastZoomRatio = 1f

    private lateinit var image: ZoomImageView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var searchPanel: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var pageText: TextView
    private lateinit var searchBox: EditText
    private lateinit var searchStatus: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var leftPageButton: TextView
    private lateinit var rightPageButton: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideHandler = Handler(Looper.getMainLooper())
    private val renderExecutor = Executors.newSingleThreadExecutor()
    private val renderGeneration = AtomicInteger(0)
    private val rendererLock = Any()
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controlsVisible = true
    private var cacheFile: File? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var searchJob: Job? = null
    private var searchQuery = ""
    private var searchOccurrences: List<PdfSearchOccurrence> = emptyList()
    private var searchCursor = -1

    private val autoHideRunnable = Runnable { setControlsVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SecurityConfig.BLOCK_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        PDFBoxResourceLoader.init(applicationContext)
        pageIndex = savedInstanceState?.getInt("page_index", 0) ?: 0
        buildReaderUi()
        openPdfSafely()
    }

    /**
     * Construye la pantalla del lector. Se mantiene programática para que el componente
     * siga siendo autocontenido y pueda integrarse posteriormente como Biblioteca HSE.
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
            onZoomChanged = { ratio ->
                lastZoomRatio = ratio
                if (lastPageCount > 0) updatePageText(lastPageCount)
            }
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

        // Barra mínima: las funciones importantes siguen disponibles sin depender de gestos.
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

        buildSearchPanel(root)
        setContentView(root)
        scheduleAutoHide()
    }

    /**
     * Panel de búsqueda inspirado en lectores modernos: entrada persistente, anterior/
     * siguiente y contador de coincidencias. La búsqueda real ocurre fuera del hilo UI.
     */
    private fun buildSearchPanel(root: FrameLayout) {
        searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(0xEE0B2545.toInt(), dp(18).toFloat())
            elevation = dp(12).toFloat()
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        searchBox = EditText(this).apply {
            hint = "Buscar palabra o frase"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB3C8.toInt())
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF86A8C7.toInt())
            setOnEditorActionListener { _, actionId, event ->
                val pressedEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event?.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_SEARCH || pressedEnter) {
                    executeSearch(searchBox.text.toString())
                    true
                } else {
                    false
                }
            }
        }

        searchRow.addView(
            searchBox,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        searchRow.addView(actionButton("Ir") { executeSearch(searchBox.text.toString()) })
        searchRow.addView(actionButton("×") { hideSearchPanel(clearResults = true) })

        val resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previous = actionButton("‹") { moveSearchCursor(-1) }
        val next = actionButton("›") { moveSearchCursor(1) }

        searchStatus = TextView(this).apply {
            text = "Escribe una palabra o frase"
            setTextColor(0xFFD7E5F3.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
        }

        searchProgress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        resultRow.addView(previous)
        resultRow.addView(
            searchStatus,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        resultRow.addView(
            searchProgress,
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(6), 0, dp(6), 0) }
        )
        resultRow.addView(next)

        searchPanel.addView(searchRow)
        searchPanel.addView(resultRow)

        root.addView(
            searchPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(dp(12), 0, dp(12), dp(82)) }
        )
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
                // Si Buscar está abierto no iniciamos el auto-ocultado detrás del teclado.
                if (!::searchPanel.isInitialized || searchPanel.visibility != View.VISIBLE) {
                    scheduleAutoHide()
                }
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
        if (::searchPanel.isInitialized && searchPanel.visibility == View.VISIBLE) {
            hideSearchPanel(clearResults = true)
            scheduleAutoHide()
            return
        }

        setControlsVisible(!controlsVisible)
        if (controlsVisible) scheduleAutoHide()
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        val alphaTarget = if (visible) 1f else 0f
        val visibilityEnd = if (visible) View.VISIBLE else View.GONE

        listOf(topBar, bottomBar, leftPageButton, rightPageButton).forEach { view ->
            if (visible) view.visibility = View.VISIBLE
            view.animate().alpha(alphaTarget).setDuration(160).withEndAction {
                if (!visible) view.visibility = visibilityEnd
            }.start()
        }

        if (!visible && ::searchPanel.isInitialized && searchPanel.visibility == View.VISIBLE) {
            hideSearchPanel(clearResults = true)
        }
    }

    private fun scheduleAutoHide() {
        hideHandler.removeCallbacks(autoHideRunnable)
        if (::searchPanel.isInitialized && searchPanel.visibility == View.VISIBLE) return
        hideHandler.postDelayed(autoHideRunnable, CONTROLS_AUTO_HIDE_MS)
    }

    private fun showSearchPanel() {
        setControlsVisible(true)
        searchPanel.visibility = View.VISIBLE
        searchPanel.alpha = 1f
        hideHandler.removeCallbacks(autoHideRunnable)
        searchBox.requestFocus()

        searchBox.post {
            val keyboard = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Cierra el buscador. Si clearResults=true también elimina las marcas del documento.
     * De esta forma el resaltado existe sólo mientras la sesión de búsqueda está activa.
     */
    private fun hideSearchPanel(clearResults: Boolean) {
        searchJob?.cancel()
        searchProgress.visibility = View.GONE
        searchPanel.visibility = View.GONE
        searchBox.clearFocus()

        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(searchBox.windowToken, 0)

        if (clearResults) clearSearchResultsAndRefresh()
    }

    private fun clearSearchResultsAndRefresh() {
        val hadHighlights = searchOccurrences.isNotEmpty()
        searchQuery = ""
        searchOccurrences = emptyList()
        searchCursor = -1
        searchStatus.text = "Escribe una palabra o frase"

        if (hadHighlights && lastPageCount > 0 && !isFinishing && !isDestroyed) {
            renderSafely()
        }
    }

    private fun nextPage() {
        if (lastPageCount <= 0) return
        if (pageIndex < lastPageCount - 1) {
            pageIndex++
            renderSafely()
        } else {
            Toast.makeText(this, "Última página", Toast.LENGTH_SHORT).show()
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
                ?: throw IllegalStateException(
                    "No se encontró el documento o no está permitido para esta licencia: $selectedFile"
                )

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
            lastPageCount = renderer?.pageCount ?: 0

            if (lastPageCount <= 0) throw IllegalStateException("El PDF no tiene páginas legibles.")
            pageIndex = pageIndex.coerceIn(0, lastPageCount - 1)
            renderSafely()
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo PDF", e)
            showFatalError("No se pudo abrir el documento.", friendlyError(e))
        }
    }

    /**
     * Renderiza la página en un worker único.
     *
     * PdfRenderer permite sólo una página abierta a la vez; por eso no usamos un pool de
     * múltiples threads. renderGeneration descarta resultados antiguos cuando el usuario
     * pasa varias páginas rápidamente y evita que aparezca una página atrasada.
     */
    private fun renderSafely() {
        val targetPage = pageIndex
        val generation = renderGeneration.incrementAndGet()

        // Snapshot inmutable para que una búsqueda nueva no cambie los datos a mitad del render.
        val occurrenceSnapshot = searchOccurrences
        val activeCursorSnapshot = searchCursor

        pageText.text = "Página ${targetPage + 1} de $lastPageCount · cargando..."

        renderExecutor.execute {
            try {
                val rendered = synchronized(rendererLock) {
                    val activeRenderer = renderer ?: return@synchronized null
                    if (targetPage !in 0 until activeRenderer.pageCount) return@synchronized null

                    val page = activeRenderer.openPage(targetPage)
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

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        drawSearchHighlights(
                            canvas = canvas,
                            width = width,
                            height = height,
                            page = targetPage,
                            occurrences = occurrenceSnapshot,
                            activeCursor = activeCursorSnapshot
                        )
                        drawProtectedWatermark(canvas, width, height)

                        RenderedPage(bitmap, activeRenderer.pageCount)
                    } finally {
                        page.close()
                    }
                } ?: return@execute

                mainHandler.post {
                    if (isFinishing || isDestroyed || generation != renderGeneration.get()) {
                        rendered.bitmap.recycle()
                        return@post
                    }

                    lastPageCount = rendered.pageCount
                    lastZoomRatio = 1f
                    image.setImageBitmap(rendered.bitmap)
                    updatePageText(lastPageCount)
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "Sin memoria renderizando PDF", oom)
                mainHandler.post {
                    showFatalError(
                        "El PDF es demasiado pesado para este teléfono.",
                        "El render por regiones/tiles está previsto para la siguiente etapa del visor."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error renderizando PDF", e)
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        showFatalError("No se pudo mostrar la página.", friendlyError(e))
                    }
                }
            }
        }
    }

    private fun updatePageText(totalPages: Int) {
        val zoomPercent = (lastZoomRatio * 100f).toInt().coerceAtLeast(100)
        pageText.text = "Página ${pageIndex + 1} de $totalPages · $zoomPercent %"
    }

    /**
     * Dibuja todas las coincidencias de la página y enfatiza la coincidencia activa.
     *
     * Los rectángulos llegan normalizados (0..1), por lo que basta multiplicarlos por
     * el bitmap actual. Esto seguirá funcionando cuando el tamaño de render cambie.
     */
    private fun drawSearchHighlights(
        canvas: Canvas,
        width: Int,
        height: Int,
        page: Int,
        occurrences: List<PdfSearchOccurrence>,
        activeCursor: Int
    ) {
        if (occurrences.isEmpty()) return

        val normalFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFD54F
            style = Paint.Style.FILL
        }
        val activeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFB300.toInt()
            style = Paint.Style.FILL
        }
        val activeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xD9F57C00.toInt()
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, width / 700f)
        }
        val corner = maxOf(3f, width / 360f)

        occurrences.forEachIndexed { globalIndex, occurrence ->
            if (occurrence.pageIndex != page) return@forEachIndexed
            val active = globalIndex == activeCursor

            occurrence.rects.forEach { normalized ->
                val rect = RectF(
                    normalized.left * width,
                    normalized.top * height,
                    normalized.right * width,
                    normalized.bottom * height
                )
                canvas.drawRoundRect(rect, corner, corner, if (active) activeFill else normalFill)
                if (active) canvas.drawRoundRect(rect, corner, corner, activeStroke)
            }
        }
    }

    /**
     * Marca de agua visual de baja opacidad.
     * No sustituye al control criptográfico ni a FLAG_SECURE.
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

    /**
     * Ejecuta una nueva búsqueda completa fuera del hilo UI y conserva CADA ocurrencia,
     * no sólo la página. Esto permite navegar y resaltar palabra/frase por palabra/frase.
     */
    private fun executeSearch(queryRaw: String) {
        val query = queryRaw.trim()
        if (query.isBlank()) {
            searchStatus.text = "Escribe una palabra o frase"
            return
        }

        if (query.equals(searchQuery, ignoreCase = true) && searchOccurrences.isNotEmpty()) {
            moveSearchCursor(1)
            return
        }

        val file = cacheFile ?: return
        searchJob?.cancel()

        val hadPreviousHighlights = searchOccurrences.isNotEmpty()
        searchQuery = query
        searchOccurrences = emptyList()
        searchCursor = -1
        searchProgress.visibility = View.VISIBLE
        searchStatus.text = "Buscando…"
        if (hadPreviousHighlights) renderSafely()

        searchJob = readerScope.launch {
            try {
                val occurrences = withContext(Dispatchers.IO) {
                    val workerJob = coroutineContext[Job]
                    PdfSearchEngine.findOccurrences(file, query) {
                        workerJob?.isActive != false
                    }
                }

                if (!isActive) return@launch
                searchProgress.visibility = View.GONE
                searchOccurrences = occurrences

                if (occurrences.isEmpty()) {
                    searchStatus.text = "Sin coincidencias"
                    Toast.makeText(
                        this@PdfActivity,
                        "No se encontró “$query”. Si el PDF es escaneado puede no contener texto.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Empieza por la primera coincidencia desde la página actual; luego hace wrap.
                searchCursor = occurrences.indexOfFirst { it.pageIndex >= pageIndex }
                    .let { if (it >= 0) it else 0 }
                navigateToCurrentSearchMatch()
            } catch (e: Exception) {
                if (!isActive) return@launch
                Log.e(TAG, "Error buscando texto", e)
                searchProgress.visibility = View.GONE
                searchStatus.text = "No se pudo buscar"
                AlertDialog.Builder(this@PdfActivity)
                    .setTitle("No se pudo buscar")
                    .setMessage(
                        "Algunos PDFs escaneados son imágenes y no contienen texto buscable. " +
                            "Detalle: ${e.message}"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun moveSearchCursor(direction: Int) {
        if (searchOccurrences.isEmpty()) {
            executeSearch(searchBox.text.toString())
            return
        }

        val size = searchOccurrences.size
        searchCursor = (searchCursor + direction + size) % size
        navigateToCurrentSearchMatch()
    }

    private fun navigateToCurrentSearchMatch() {
        if (searchCursor !in searchOccurrences.indices) return

        val occurrence = searchOccurrences[searchCursor]
        pageIndex = occurrence.pageIndex
        searchStatus.text =
            "${searchCursor + 1} de ${searchOccurrences.size} · pág. ${occurrence.pageIndex + 1}"
        renderSafely()
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("AEADBadTag", true) ||
                msg.contains("BAD_DECRYPT", true) ||
                msg.contains("BadPadding", true) ||
                msg.contains("tag", true) ->
                "La APK fue compilada con una clave distinta a la usada para cifrar estos documentos. Vuelve a cifrar manuales con la herramienta y recompila la APK."

            msg.contains("Integridad", true) ->
                "Falló la verificación de integridad. El .bin o el index.json no corresponden al PDF cifrado."

            msg.contains("password", true) || msg.contains("encrypted", true) ->
                "El PDF original parece estar protegido con contraseña o cifrado. Quita esa protección antes de cifrarlo para la app."

            else -> msg
        }
    }

    private fun showFatalError(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Regresar") { _, _ -> finish() }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page_index", pageIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(autoHideRunnable)
        searchJob?.cancel()
        readerScope.cancel()
        renderGeneration.incrementAndGet()
        renderExecutor.shutdownNow()

        synchronized(rendererLock) {
            try { renderer?.close() } catch (_: Exception) {}
            renderer = null
            try { parcelFileDescriptor?.close() } catch (_: Exception) {}
            parcelFileDescriptor = null
        }

        cacheFile?.delete()
        super.onDestroy()
    }

    private data class RenderedPage(
        val bitmap: Bitmap,
        val pageCount: Int
    )
}
