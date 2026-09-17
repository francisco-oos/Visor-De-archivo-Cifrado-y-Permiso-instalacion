package com.frank.visordocumentoscifrado.ui

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Rectángulo normalizado de una coincidencia textual.
 *
 * Las coordenadas se expresan de 0.0 a 1.0 respecto al ancho/alto de la página.
 * Esto desacopla la búsqueda de la resolución concreta usada por PdfRenderer: una
 * coincidencia sirve igual si la página se renderiza a 1080 px, 2200 px o por tiles.
 */
data class PdfSearchRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** Una coincidencia individual; una frase puede ocupar varios rectángulos/líneas. */
data class PdfSearchOccurrence(
    val pageIndex: Int,
    val rects: List<PdfSearchRect>
)

/**
 * Motor de búsqueda textual separado de PdfActivity.
 *
 * Además de localizar la página, R2.1 conserva la geometría del texto para que el
 * visor pueda resaltar la palabra/frase como un lector moderno sin modificar el PDF.
 *
 * Importante:
 * - trabaja sobre PDFs que realmente contienen texto;
 * - un PDF escaneado como imagen necesita OCR, que sigue fuera de alcance;
 * - debe invocarse desde un dispatcher/worker, nunca desde el hilo principal.
 */
object PdfSearchEngine {

    fun findOccurrences(
        file: File,
        queryRaw: String,
        shouldContinue: () -> Boolean = { true }
    ): List<PdfSearchOccurrence> {
        val query = normalizeQuery(queryRaw)
        if (query.isBlank()) return emptyList()

        val matches = mutableListOf<PdfSearchOccurrence>()

        PDDocument.load(file).use { document ->
            val collector = PositionCollector()

            for (pageIndex in 0 until document.numberOfPages) {
                if (!shouldContinue()) break

                val positions = collector.collect(document, pageIndex + 1)
                val indexedPage = buildIndexedPage(positions)
                if (indexedPage.text.isBlank()) continue

                findInPage(indexedPage, query, pageIndex).forEach { matches += it }
            }
        }

        return matches
    }

    /**
     * PDFBox entrega TextPosition por fragmentos/glyphs. Construimos una cadena lógica
     * y, en paralelo, un mapa carácter → TextPosition. Así podemos buscar con indexOf
     * y después recuperar exactamente qué glyphs corresponden a cada coincidencia.
     *
     * El texto se normaliza carácter por carácter a minúsculas. Evitamos aplicar
     * lowercase() sobre la cadena completa porque algunos caracteres Unicode pueden
     * cambiar de longitud y romper la correspondencia 1:1 con charMap.
     */
    private fun buildIndexedPage(positions: List<TextPosition>): IndexedPage {
        val text = StringBuilder()
        val charMap = mutableListOf<TextPosition?>()
        var previous: TextPosition? = null

        fun appendSpace() {
            if (text.isNotEmpty() && text.last() != ' ') {
                text.append(' ')
                charMap += null
            }
        }

        positions.forEach { position ->
            val unicode = position.unicode.orEmpty()
            if (unicode.isEmpty()) return@forEach

            previous?.let { prior ->
                val maxHeight = max(prior.height, position.height).coerceAtLeast(1f)
                val sameLine = abs(position.y - prior.y) <= maxHeight * 0.65f
                val gap = position.x - (prior.x + prior.width)
                val expectedSpace = max(
                    max(prior.widthOfSpace, position.widthOfSpace) * 0.35f,
                    maxHeight * 0.12f
                )

                // Un salto de línea se normaliza a espacio para que una frase pueda
                // encontrarse aunque el PDF la haya partido por ajuste de renglón.
                if (!sameLine || gap > expectedSpace) appendSpace()
            }

            unicode.forEach { char ->
                if (char.isWhitespace()) {
                    appendSpace()
                } else {
                    text.append(char.lowercaseChar())
                    charMap += position
                }
            }

            previous = position
        }

        return IndexedPage(text.toString(), charMap)
    }

    private fun findInPage(
        indexedPage: IndexedPage,
        normalizedQuery: String,
        pageIndex: Int
    ): List<PdfSearchOccurrence> {
        val haystack = indexedPage.text
        val needle = normalizedQuery
        val result = mutableListOf<PdfSearchOccurrence>()
        var fromIndex = 0

        while (fromIndex <= haystack.length - needle.length) {
            val start = haystack.indexOf(needle, startIndex = fromIndex)
            if (start < 0) break

            val endExclusive = start + needle.length
            val selected = mutableListOf<TextPosition>()

            for (index in start until endExclusive.coerceAtMost(indexedPage.charMap.size)) {
                val position = indexedPage.charMap[index] ?: continue
                if (selected.isEmpty() || selected.last() !== position) selected += position
            }

            val rects = positionsToRects(selected)
            if (rects.isNotEmpty()) {
                result += PdfSearchOccurrence(pageIndex = pageIndex, rects = rects)
            }

            // +1 permite localizar ocurrencias superpuestas cuando el texto lo admite.
            fromIndex = start + 1
        }

        return result
    }

    /**
     * Convierte glyphs consecutivos en uno o más rectángulos por línea.
     *
     * TextPosition.y representa la línea base en coordenadas ajustadas con origen arriba;
     * por eso extendemos el rectángulo principalmente hacia arriba usando la altura del
     * glyph y dejamos un pequeño margen inferior para descendentes (g, p, y, etc.).
     */
    private fun positionsToRects(positions: List<TextPosition>): List<PdfSearchRect> {
        if (positions.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableRect>()

        positions.forEach { position ->
            val pageWidth = position.pageWidth.coerceAtLeast(1f)
            val pageHeight = position.pageHeight.coerceAtLeast(1f)
            val height = position.height.coerceAtLeast(1f)
            val width = position.width.coerceAtLeast(1f)

            val glyph = MutableRect(
                left = position.x,
                top = position.y - height,
                right = position.x + width,
                bottom = position.y + height * 0.20f,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                referenceHeight = height
            )

            val current = groups.lastOrNull()
            if (current == null) {
                groups += glyph
                return@forEach
            }

            val sameLine = abs(glyph.top - current.top) <=
                max(glyph.referenceHeight, current.referenceHeight) * 0.75f
            val gap = glyph.left - current.right
            val mergeGap = max(position.widthOfSpace * 2.2f, glyph.referenceHeight * 0.9f)

            if (sameLine && gap <= mergeGap) {
                current.left = minOf(current.left, glyph.left)
                current.top = minOf(current.top, glyph.top)
                current.right = maxOf(current.right, glyph.right)
                current.bottom = maxOf(current.bottom, glyph.bottom)
                current.referenceHeight = max(current.referenceHeight, glyph.referenceHeight)
            } else {
                groups += glyph
            }
        }

        return groups.map { rect ->
            val padX = max(1.2f, rect.referenceHeight * 0.08f)
            val padY = max(1.0f, rect.referenceHeight * 0.10f)

            PdfSearchRect(
                left = ((rect.left - padX) / rect.pageWidth).coerceIn(0f, 1f),
                top = ((rect.top - padY) / rect.pageHeight).coerceIn(0f, 1f),
                right = ((rect.right + padX) / rect.pageWidth).coerceIn(0f, 1f),
                bottom = ((rect.bottom + padY) / rect.pageHeight).coerceIn(0f, 1f)
            )
        }.filter { it.right > it.left && it.bottom > it.top }
    }

    /**
     * Normaliza la consulta conservando una relación simple carácter-a-carácter con el
     * índice del texto de página: minúsculas y espacios consecutivos colapsados.
     */
    private fun normalizeQuery(raw: String): String {
        val result = StringBuilder()

        raw.trim().forEach { char ->
            if (char.isWhitespace()) {
                if (result.isNotEmpty() && result.last() != ' ') result.append(' ')
            } else {
                result.append(char.lowercaseChar())
            }
        }

        return result.toString()
    }

    /**
     * PDFTextStripper especializado para obtener las posiciones DESPUÉS de que PDFBox
     * haya aplicado su orden visual y la supresión de glyphs superpuestos.
     *
     * Capturarlas en processTextPosition() parecería más directo, pero ese callback ocurre
     * antes del ordenamiento de PDFTextStripper. writeString() recibe precisamente los
     * TextPosition que pertenecen al fragmento ya preparado para salida, por lo que es una
     * base más estable para relacionar texto visible y geometría.
     */
    private class PositionCollector : PDFTextStripper() {
        private val captured = mutableListOf<TextPosition>()

        init {
            setSortByPosition(true)
        }

        fun collect(document: PDDocument, pageNumber: Int): List<TextPosition> {
            captured.clear()
            startPage = pageNumber
            endPage = pageNumber
            getText(document)
            return captured.toList()
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            captured += textPositions
            super.writeString(text, textPositions)
        }
    }

    private data class IndexedPage(
        val text: String,
        val charMap: List<TextPosition?>
    )

    private data class MutableRect(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        val pageWidth: Float,
        val pageHeight: Float,
        var referenceHeight: Float
    )
}
