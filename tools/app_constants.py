"""
Constantes compartidas del ecosistema Visor Seguro.

Este archivo debe mantenerse sincronizado con:
- app/src/main/java/.../config/AppConfig.kt
- app/src/main/java/.../config/AreaCatalog.kt

La herramienta solo cifra manuales y los coloca en la APK.
La emisión de licencias vive fuera de este repositorio. Desde R1 las licencias
productivas se verifican con firma asimétrica VISOR_LICENSE_V2; no existe un
secreto HMAC productivo compartido dentro de la APK.
"""
APP_DISPLAY_NAME = "Visor Seguro de Manuales"
COMPANY_NAME = "Empresa"
PROJECT_NAME = "ALACTE"
APP_EXPIRES_AT = "2026-12-31"

MANUALS_ASSET_DIR = "manuales"
SOURCE_MANUALS_DIR = "MANUALES_PARA_ENCRIPTAR"

# Catálogo oficial: carpetas válidas y permisos por área.
CATALOG_AREAS = [
    "ADQUISICION",
    "PERFORACION",
    "TOPOGRAFIA",
    "GESTORIA",
    "LOGISTICA",
    "OPERACIONES",
    "INMUEBLES",
    "QC",
]
DISPLAY_AREAS = {
    "ADQUISICION": "Adquisición",
    "PERFORACION": "Perforación",
    "TOPOGRAFIA": "Topografía",
    "GESTORIA": "Gestoría",
    "LOGISTICA": "Logística",
    "OPERACIONES": "Operaciones",
    "INMUEBLES": "Inmuebles",
    "QC": "QC",
}
