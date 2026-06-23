package com.frank.visordocumentoscifrado.config

/**
 * Catálogo oficial de áreas/departamentos del ecosistema HSE.
 *
 * Importante para mantenimiento:
 * - Estos IDs son los únicos nombres válidos de carpetas para el encriptador.
 * - index.json debe guardar area_id usando estos mismos IDs.
 * - El futuro frontend de licencias debe guardar allowed_areas con estos mismos IDs.
 * - No existe departamento GENERAL.
 * - OTRO solo existe en el formulario de solicitud cuando el área real no está en catálogo.
 * - En permisos/licencia, OTRO se interpreta como ALL para evitar bloqueos en pruebas o aprobaciones globales.
 * - Para permisos formales usa access_mode = ALL o allowed_areas = ["ALL"].
 */
object AreaCatalog {
    const val ADQUISICION = "ADQUISICION"
    const val PERFORACION = "PERFORACION"
    const val TOPOGRAFIA = "TOPOGRAFIA"
    const val GESTORIA = "GESTORIA"
    const val LOGISTICA = "LOGISTICA"
    const val OPERACIONES = "OPERACIONES"
    const val INMUEBLES = "INMUEBLES"
    const val QC = "QC"
    const val OTRO = "OTRO"
    const val ALL = "ALL"

    val AREA_IDS = listOf(
        ADQUISICION,
        PERFORACION,
        TOPOGRAFIA,
        GESTORIA,
        LOGISTICA,
        OPERACIONES,
        INMUEBLES,
        QC
    )

    val REQUEST_AREAS = AREA_IDS + OTRO

    private val displayNames = mapOf(
        ADQUISICION to "Adquisición",
        PERFORACION to "Perforación",
        TOPOGRAFIA to "Topografía",
        GESTORIA to "Gestoría",
        LOGISTICA to "Logística",
        OPERACIONES to "Operaciones",
        INMUEBLES to "Inmuebles",
        QC to "QC",
        OTRO to "Otro / Todos",
        ALL to "Todos los departamentos"
    )

    fun displayName(areaId: String): String = displayNames[normalize(areaId)] ?: areaId

    /** Normaliza nombres humanos/carpetas para evitar errores por acentos o espacios. */
    fun normalize(value: String): String {
        val clean = value.trim().uppercase()
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ü", "U")
            .replace(" ", "_")
            .replace("-", "_")
            .replace("(", "")
            .replace(")", "")
        return when {
            clean == "ADQUISICION_REGISTRO" || clean == "ADQUISICION" -> ADQUISICION
            clean == "PERFORACION" -> PERFORACION
            clean == "TOPOGRAFIA" -> TOPOGRAFIA
            clean == "GESTORIA" -> GESTORIA
            clean == "LOGISTICA" -> LOGISTICA
            clean == "JEFATURA" || clean == "OPERACIONES" -> OPERACIONES
            clean == "INMUEBLES" -> INMUEBLES
            clean == "QC" -> QC
            clean == "TODOS" || clean == "TODAS" || clean == "TODO" || clean == "ALL" || clean == "OTRO" -> ALL
            else -> clean.ifBlank { OTRO }
        }
    }
}
