# PRE-CHANGE BASELINE R1 — Visor Seguro

Fecha de auditoría: 2026-09-16  
Commit base: `9becbf22a66895d22b9f5b1f13a446b6d73890f4`  
Rama de trabajo: `feature/visor-hardening-r1`

## Objetivo

Congelar la situación real del visor antes de iniciar la modernización del motor PDF/VSDOC3. R1 corrige primero fronteras de seguridad, configuración, contratos internos y pruebas puras. No reescribe todavía el motor PDF.

## Línea base encontrada

### Build y versiones

- `app/build.gradle`: `versionName 1.0.0`, `versionCode 1`.
- `AppConfig.kt`: `VERSION_NAME = 1.1.0`, `VERSION_CODE = 10`.
- Existían dos fuentes de verdad diferentes para la misma versión.
- `AppConfig.DEBUG_MODE = true` estaba codificado en fuente y `DocumentAccessGuard` lo usaba para saltarse la licencia.
- La rama base no contiene `gradle/wrapper/`; `.gitignore` incluía `wrapper/`, lo que podía impedir versionarlo correctamente.

### Activación y secretos

- Telegram era el transporte temporal para `INSTALL_EVENT`, `ACCESS_REQUEST` y consulta de estado.
- `TelegramConfig.kt` contenía directamente token y chat ID.
- El token detectado en la auditoría fue revocado por el propietario el 2026-09-16.
- El repositorio es público, por lo que eliminar el valor del HEAD no borra el commit histórico; la revocación es la mitigación efectiva del secreto ya expuesto.
- `MainActivity` conocía directamente `TelegramRequestSender`.

### Licencias

- `VISOR_LICENSE_V1` verificaba HMAC-SHA256 con un secreto incluido dentro de la APK.
- Un esquema simétrico de ese tipo permite que quien extraiga el secreto de la APK pueda producir firmas nuevas.
- La validación de licencia estaba además acoplada a la presencia/huella de la clave de documentos.
- No existían pruebas automatizadas de firma ni de autorización por área.

### Documentos cifrados

- La herramienta Python genera VSDOC2 con AES-256-GCM y metadata autenticada como AAD.
- `DocumentKeyConfig.kt` contenía la clave maestra VSDOC1/VSDOC2 en Base64 dentro del repositorio/APK.
- `DocumentRepository.decrypt()` usa `readBytes()` sobre el `.bin` completo y devuelve un segundo `ByteArray` completo con el PDF en claro.
- `PdfActivity` vuelve a escribir ese `ByteArray` a `cacheDir` para abrir `PdfRenderer`.
- Este flujo puede mantener simultáneamente ciphertext + plaintext + bitmap de página en memoria.

### Visor PDF

- FIX25 ya separó correctamente gestos (`ZoomImageView`) de apertura/render (`PdfActivity`).
- Se conservan pellizco, doble toque, arrastre, swipe y `FLAG_SECURE`.
- Cada página puede renderizarse hasta `2600 x 3900` en `ARGB_8888`, aproximadamente 38.7 MiB para un bitmap máximo.
- No existe todavía un `MemoryGovernor`, caché acotada por presupuesto ni render por tiles/región visible.
- La búsqueda usa PDFBox y recorre páginas desde `PdfActivity`; debe migrarse fuera del hilo/UI en R2.

### Pruebas

- No había `app/src/test` ni suite de regresión para seguridad/licencias/permisos.
- No había control local automatizado para detectar secretos antes de un commit.
- No se añadirá GitHub Actions en R1 para evitar consumo facturable; las verificaciones se ejecutarán localmente.

## Compatibilidad que R1 no debe romper

- Lectura VSDOC1 existente.
- Lectura VSDOC2 existente.
- Metadata autenticada VSDOC2 y control de área antes de descifrar.
- Catálogo oficial de departamentos.
- Semántica histórica `ALL` / `OTRO` documentada por el proyecto.
- `FLAG_SECURE`.
- Experiencia FIX25 del lector.
- Cifrado de manuales mediante la herramienta Python.

## Alcance R1

1. Hacer imposible el bypass de licencia en `release`.
2. Unificar versión/modo debug bajo `BuildConfig`.
3. Retirar secretos reales del código versionado.
4. Mantener VSDOC1/VSDOC2, pero sacar su clave de Git hacia configuración local ignorada.
5. Introducir `VISOR_LICENSE_V2` con firma asimétrica ECDSA P-256/SHA-256.
6. Permitir V1/HMAC sólo en debug como puente de migración.
7. Separar la UI del transporte de activación mediante un contrato.
8. Añadir pruebas JVM de criptografía y permisos.
9. Añadir escaneo local gratuito de secretos.
10. Documentar R2/VSDOC3 antes de tocar el motor PDF.

## Fuera de alcance deliberado de R1

- Streaming de VSDOC2.
- Tink Streaming AEAD.
- `ProxyFileDescriptor`.
- VSDOC3 productivo.
- Vault externo a la APK.
- Render por tiles/viewport.
- Nueva búsqueda PDF asíncrona.
- AndroidX PDF como backend moderno.
- Android Keystore para content keys.

Esos puntos quedan en R2/R3 después de validar R1 físicamente en un teléfono.
