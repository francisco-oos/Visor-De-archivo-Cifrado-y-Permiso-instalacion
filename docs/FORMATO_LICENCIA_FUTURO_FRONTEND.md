# Formato recomendado para licencia.key

El frontend/API de licencias deberá generar un archivo de texto JSON llamado `licencia.key`.

Formato estable recomendado:

```json
{
  "schema": "VISOR_LICENSE_V1",
  "payload_b64": "BASE64_DEL_JSON_PAYLOAD_UTF8",
  "signature": "HMAC_SHA256_BASE64(payload_b64, APP_VERIFY_SECRET)"
}
```

`APP_VERIFY_SECRET` debe coincidir con:

- `tools/app_constants.py`
- `LicenseManager.kt`

Payload antes de codificar en Base64:

```json
{
  "employee_name": "Juan Pérez",
  "employee_id": "739",
  "position": "Observador",
  "area": "TOPOGRAFIA",
  "project": "ALACTE",
  "device_hash": "...",
  "install_id": "...",
  "expires_at": "2026-12-31",
  "issued_at": "2026-06-21T19:00:00",
  "access_mode": "AREA",
  "allowed_areas": ["TOPOGRAFIA", "OPERACIONES"]
}
```

Para acceso total:

```json
{
  "access_mode": "ALL",
  "allowed_areas": ["ALL"]
}
```

## Importante

La licencia NO contiene la clave de documentos.

La clave se genera al cifrar manuales y queda embebida en:

```text
app/src/main/java/.../config/DocumentKeyConfig.kt
```

Eso evita que cada empleado tenga una clave distinta y evita errores de BAD_DECRYPT por licencia equivocada.
