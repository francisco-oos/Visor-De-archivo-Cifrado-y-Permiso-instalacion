package com.frank.visordocumentoscifrado.ui

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Motor de búsqueda textual separado de PdfActivity.
 *
 * Motivo de esta separación:
 * - PdfActivity se ocupa de la experiencia de lectura.
 * - Esta clase se ocupa únicamente de recorrer el texto del PDF.
 * - La llamada se ejecuta desde un Dispatcher de trabajo; nunca debe invocarse
 *   directamente en el hilo principal porque PDFBox puede tardar en manuales grandes.
 *
 * Las páginas se devuelven con índice base 0 para coincidir con PdfRenderer.
 */
object PdfSearchEngine {
    fun findMatchingPages(
        file: File,
        queryRaw: String,
        shouldContinue: () -> Boolean = { true }
    ): List<Int> {
        val query = queryRaw.trim()
        if (query.isBlank()) return emptyList()

        val matches = mutableListOf<Int>()

        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()

            for (pageIndex in 0 until document.numberOfPages) {
                // Permite cancelar una búsqueda anterior cuando el usuario inicia otra.
                if (!shouldContinue()) break

                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val pageText = stripper.getText(document)

                if (pageText.contains(query, ignoreCase = true)) {
                    matches += pageIndex
                }
            }
        }

        return matches
    }
}
