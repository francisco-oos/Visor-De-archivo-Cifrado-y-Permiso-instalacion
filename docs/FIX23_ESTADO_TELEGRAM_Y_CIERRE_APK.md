# FIX23 — Estado por Telegram, catálogo limpio y visor más profesional

> **Documento histórico.** Telegram fue el puente temporal de esta etapa. Desde Hardening R1 las credenciales ya no se versionan, Telegram directo sólo puede habilitarse en `debug` y la UI consume el contrato neutral `ActivationTransport`.

## Cambios que introdujo FIX23

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
- Para permitir acceso a todo se usa `access_mode = ALL`.
- La app incorporó consulta de estado usando `device_hash` + `install_id`.
- Los campos de captura obtuvieron límites y validación básica.
- El visor PDF recibió controles flotantes y marca visual.

## Protocolo histórico Telegram

FIX23 utilizaba `VISOR_STATUS_V1:<base64_del_json>` con estados `PENDING`, `APPROVED`, `REJECTED` y `EXPIRED`.

Ese protocolo puede seguir sirviendo para una prueba debug temporal, pero ya no es el contrato arquitectónico de la UI. `ActivationTransportStatus` es ahora el modelo interno y la futura API productiva deberá mapear sus respuestas a esos estados.

## Evolución de licencia

La licencia productiva actual del diseño es `VISOR_LICENSE_V2` firmada mediante ECDSA P-256/SHA-256. Consulta `FORMATO_LICENCIA_FUTURO_FRONTEND.md`.
