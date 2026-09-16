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
- Crear/actualizar `visor-secrets.properties` con la clave local VSDOC1/VSDOC2.

Desde R1 el encriptador **ya no escribe una clave real dentro de `DocumentKeyConfig.kt`**.
`visor-secrets.properties` está ignorado por Git y Gradle lo consume localmente.

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

El formato actual de creación sigue siendo `VSDOC2` con AES-256-GCM para conservar compatibilidad. La siguiente etapa introducirá VSDOC3 con streaming y acceso seekable; no se mezcla esa migración con este hardening R1.

La licencia productiva no se genera en este repositorio. La APK R1 verifica `VISOR_LICENSE_V2` mediante ECDSA P-256/SHA-256 usando únicamente una clave pública. La compatibilidad HMAC V1 existe sólo en compilaciones debug.

## Verificar secretos antes de publicar

Ejecuta:

```bat
VERIFICAR_SECRETOS.bat
```

O directamente:

```bash
python tools/check_no_secrets.py
```

Este control es local y no usa GitHub Actions, para no consumir cuota facturable.
