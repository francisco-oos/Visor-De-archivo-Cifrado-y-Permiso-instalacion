# Tools del Visor Seguro

## Encriptador de manuales

Ejecuta:

```bat
ABRIR_ENCRIPTADOR.bat
```

Funciones:

- Crear carpetas oficiales por departamento.
- Agregar PDFs.
- Renombrar PDFs.
- Mover PDFs entre departamentos.
- Eliminar PDFs.
- Cifrar y colocar archivos en `app/src/main/assets/manuales`.
- Actualizar `DocumentKeyConfig.kt`.

## Carpetas oficiales

- ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC

## Seguridad

El cifrado usa AES-256-GCM y formato `VSDOC2`.

La licencia no se genera en este repositorio. Ese flujo queda para el futuro frontend/API de licenciamiento.
