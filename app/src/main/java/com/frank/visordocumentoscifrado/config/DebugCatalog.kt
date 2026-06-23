package com.frank.visordocumentoscifrado.config

/**
 * Datos de prueba y catálogos visibles en formulario de solicitud.
 * Mantener sincronizado con tools/app_constants.py.
 */
object DebugCatalog {
    const val NAME = "Francisco Alvarado"
    const val EMPLOYEE_ID = "0000"
    const val POSITION = "Administrador de nodos"
    const val AREA = AreaCatalog.ADQUISICION
    const val PHONE = ""
    const val PROJECT = "ALACTE"
    const val OBSERVATIONS = "Solicitud generada en modo prueba."

    val POSITIONS = listOf(
        "Obrero",
        "Cabo",
        "Checador",
        "Tirador",
        "Sobrestante",
        "Observador",
        "Conductor",
        "Op. Drone",
        "Técnico en mantenimiento",
        "Ingeniero Electrónico",
        "Jefe de Departamento",
        "Oficina",
        "Otro"
    )

    val AREAS = AreaCatalog.REQUEST_AREAS
}
