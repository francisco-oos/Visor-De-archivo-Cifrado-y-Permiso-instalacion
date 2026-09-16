# ADR-001 — Fronteras de seguridad entre debug y release

Estado: Aceptado en R1  
Fecha: 2026-09-16

## Contexto

La línea base permitía activar `DEBUG_MODE=true` desde una constante versionada y contenía credenciales de Telegram/clave VSDOC dentro del código fuente. Además, la licencia usaba un secreto HMAC compartido con la APK.

## Decisión

### BuildConfig como fuente de verdad

- Gradle define versión y campos de seguridad.
- `AppConfig.DEBUG_MODE` sólo es verdadero cuando `BuildConfig.DEBUG && ALLOW_LICENSE_BYPASS`.
- `release` fija `ALLOW_LICENSE_BYPASS=false`.
- `debug` utiliza `applicationIdSuffix .debug` para instalarse como aplicación separada y evitar confundirla con producción.

### Secretos locales

Los valores locales viven en `visor-secrets.properties` o variables de entorno:

- `DOCUMENT_KEY_B64`
- `DOCUMENT_KEY_SHA256`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ADMIN_CHAT_ID`
- `LEGACY_LICENSE_HMAC_SECRET`

El archivo real está ignorado por Git.

Telegram sólo se inyecta en `debug`; los campos correspondientes de `release` son vacíos por diseño.

### Licencias asimétricas

`VISOR_LICENSE_V2` usa ECDSA P-256/SHA-256. El emisor conserva la clave privada y la APK sólo recibe la pública (`LICENSE_VERIFY_PUBLIC_KEY_B64`).

V1/HMAC no se considera seguridad productiva. Sólo se acepta en `debug` como puente de compatibilidad.

### Clave de documentos VSDOC1/VSDOC2

R1 elimina la clave real de Git, pero no afirma resolver todavía la extracción desde APK. La clave local sigue inyectándose en `BuildConfig` para no romper VSDOC1/VSDOC2. VSDOC3 debe retirar esta dependencia mediante un esquema de content keys protegido por instalación/Keystore.

## Consecuencias

Positivas:

- Una release no puede quedar accidentalmente en modo libre por editar una constante.
- El repositorio deja de ser el almacén de tokens/claves de documentos.
- Extraer la APK ya no permite fabricar licencias V2 porque sólo contiene clave pública.
- Debug y release pueden coexistir en el mismo teléfono.

Pendientes:

- Eliminar la content key global del APK en VSDOC3.
- Conectar un backend real de activación.
- Evaluar atestación/integridad de instalación como señal adicional, sin convertirla en la única barrera.

## Alternativas descartadas

- **Ofuscar el token/clave dentro del APK:** retrasa análisis, no elimina el secreto.
- **Mantener HMAC en release:** quien extrae el secreto puede firmar nuevas licencias.
- **Guardar secretos en `local.properties` pero inyectarlos igualmente en release:** evita Git, pero no evita extracción de APK. Sólo se conserva esa técnica temporalmente para la clave VSDOC1/VSDOC2 hasta VSDOC3.
