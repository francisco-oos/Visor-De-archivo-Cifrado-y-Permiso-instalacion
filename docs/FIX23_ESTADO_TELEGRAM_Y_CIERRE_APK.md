# FIX23 - Estado por Telegram, catálogo limpio y visor más profesional

## Cambios principales

- Se eliminó `GENERAL` como departamento porque no existe en el catálogo operativo.
- El encriptador crea únicamente carpetas oficiales:
  - ADQUISICION
  - PERFORACION
  - TOPOGRAFIA
  - GESTORIA
  - LOGISTICA
  - OPERACIONES
  - INMUEBLES
  - QC
- Para permitir acceso a todo no se usa carpeta `GENERAL`; se usa `access_mode = ALL` en la futura licencia.
- La app consulta estado al abrirse si ya envió solicitud.
- La consulta se hace por Telegram como canal temporal usando `device_hash` + `install_id`.
- Los campos de captura tienen límites de caracteres y validación básica.
- El visor PDF tiene controles flotantes translúcidos y marca visual transparente.

## Protocolo temporal de respuesta por Telegram

El futuro frontend puede enviar al bot un mensaje con este formato:

```text
VISOR_STATUS_V1:<base64_del_json>
```

JSON esperado:

```json
{
  "schema": "VISOR_STATUS_V1",
  "device_hash": "HASH_DEL_TELEFONO",
  "install_id": "INSTALL_ID_DEL_TELEFONO",
  "status": "APPROVED",
  "message": "Solicitud aprobada",
  "license_text": "CONTENIDO_COMPLETO_DE_LICENCIA.KEY"
}
```

Estados válidos:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `EXPIRED`

Si `status=APPROVED`, debe incluir `license_text`; la app lo valida y lo guarda localmente.

## Nota importante

Telegram sigue siendo un puente temporal. Cuando exista servidor/API, solo debe reemplazarse la lógica interna de:

```text
ActivationStatusClient.kt
```

La pantalla principal, el cifrado de documentos y la validación local quedan igual.
