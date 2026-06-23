# FIX22 — Flujo oculto de solicitud y estado

## Objetivo

Limpiar la APK para que el usuario no vea ni manipule JSON, `.req` ni detalles de Telegram.

La APK queda con este alcance:

1. Al instalarse por primera vez envía `INSTALL_EVENT` al bot.
2. El usuario rellena sus datos y presiona **Solicitar acceso**.
3. La APK envía `ACCESS_REQUEST` al bot por el canal configurado.
4. La APK queda en estado **En revisión**.
5. En próximas aperturas permite consultar estado mediante `ActivationStatusClient`.

## Lo que se eliminó de la experiencia del usuario

- Botón visible para enviar JSON.
- Compartir archivo `.req` manualmente.
- Mención de Telegram en la pantalla de solicitud.
- Carga manual visible de `licencia.key`.

## Lo que se conserva internamente

- `LicenseManager`: preparado para validar una licencia futura.
- `ActivationStatusClient`: punto único para conectar el futuro frontend/API.
- `TelegramRequestSender`: solo envía eventos de instalación/solicitud.
- `DocumentRepository` y `DocumentAccessGuard`: mantienen seguridad por área antes de descifrar.

## Eventos enviados al bot

### INSTALL_EVENT

Se envía automáticamente una sola vez por instalación.

Sirve para saber que alguien instaló la APK aunque no haya solicitado acceso.

### ACCESS_REQUEST

Se envía cuando el usuario presiona **Solicitar acceso**.

Sirve para el futuro frontend de licencias.

## Futuro frontend/API

Cuando exista el proyecto externo de licencias, se conectará en:

```text
app/src/main/java/com/frank/visordocumentoscifrado/license/ActivationStatusClient.kt
```

Ahí se consultará por:

- `device_hash`
- `install_id`
- `app_version`

Y la API responderá:

- `PENDING`
- `APPROVED` + licencia
- `REJECTED`
- `EXPIRED`

## Seguridad

La app no muestra documentos sin licencia salvo en `DEBUG_MODE=true`.
La UI oculta áreas, pero además `DocumentAccessGuard` vuelve a validar justo antes de descifrar.
