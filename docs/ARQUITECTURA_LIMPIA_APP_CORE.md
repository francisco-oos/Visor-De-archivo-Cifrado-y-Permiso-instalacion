# Arquitectura limpia — VisorDocumentosCifrado App Core

## Enfoque actual

Este repositorio queda reducido a dos responsabilidades:

1. **APK Visor Seguro**
   - Contiene manuales cifrados en `assets/manuales`.
   - Envía aviso de instalación al bot.
   - Envía solicitud de acceso al bot.
   - Espera un archivo `licencia.key`.
   - Al recibir licencia válida, muestra documentos según áreas permitidas.

2. **Encriptador de manuales**
   - Crea carpetas por departamento.
   - Permite agregar, renombrar, mover y eliminar PDFs.
   - Cifra documentos como `VSDOC2`.
   - Genera `index.json`.
   - Coloca `.bin` e `index.json` en `app/src/main/assets/manuales`.
   - Actualiza `DocumentKeyConfig.kt`.

## Lo que se quitó de este repositorio

- Centro de licencias.
- Generador de licencias.
- Aprobación/rechazo/renovación.
- Lectura de solicitudes desde Telegram para aprobar.

Eso será otro proyecto: **frontend/API de licenciamiento**.

## Flujo

```text
PC:
tools/ABRIR_ENCRIPTADOR.bat
  -> crea MANUALES_PARA_ENCRIPTAR/
  -> cifra PDFs
  -> coloca assets en la APK
  -> actualiza DocumentKeyConfig.kt

Android:
instala APK
  -> avisa instalación al bot
  -> usuario presiona Enviar solicitud de acceso
  -> llega .req al bot/admin
  -> app queda esperando licencia.key

Futuro frontend:
recibe solicitud
  -> aprueba/rechaza/renueva
  -> genera licencia.key
  -> define áreas permitidas y caducidad
```

## Catálogo oficial de departamentos

- - ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC

Para ver todos los documentos se usará `access_mode = ALL` en la licencia futura; no existe carpeta GENERAL.

## Seguridad por área

La UI filtra documentos por licencia, pero además `DocumentAccessGuard.kt` valida justo antes de descifrar.

El `.bin` VSDOC2 incluye metadatos internos autenticados con AES-GCM:

- título
- archivo
- área
- sha256 del PDF original

Así, aunque alguien modifique `index.json`, la app vuelve a validar el área interna del documento.

## Vigencias separadas

- `AppConfig.APP_EXPIRES_AT`: caducidad de la APK/producto.
- `licencia.key / expires_at`: caducidad del permiso del empleado.

Actualizar APK encima no debe renovar licencia.
