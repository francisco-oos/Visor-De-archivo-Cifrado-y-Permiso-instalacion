# FIX22 — Flujo oculto de solicitud y estado

> **Documento histórico.** Describe la etapa previa a Hardening R1. Desde R1 la UI usa `ActivationTransport` y Telegram directo sólo puede existir en `debug`; consulta `ARQUITECTURA_LIMPIA_APP_CORE.md` y `ADR_002_ACTIVATION_TRANSPORT.md` para el estado actual.

## Objetivo original

Limpiar la APK para que el usuario no vea ni manipule JSON, `.req` ni detalles de Telegram.

La APK quedó entonces con este alcance:

1. Al instalarse por primera vez enviaba `INSTALL_EVENT` al bot.
2. El usuario rellenaba sus datos y presionaba **Solicitar acceso**.
3. La APK enviaba `ACCESS_REQUEST` al bot por el canal configurado.
4. La APK quedaba en estado **En revisión**.
5. En próximas aperturas consultaba estado mediante `ActivationStatusClient`.

## Lo que se eliminó de la experiencia del usuario

- Botón visible para enviar JSON.
- Compartir archivo `.req` manualmente.
- Mención de Telegram en la pantalla de solicitud.
- Carga manual visible de `licencia.key`.

## Lo que se conservó internamente

- `LicenseManager`.
- `ActivationStatusClient`.
- `DocumentRepository` y `DocumentAccessGuard`.

## Evolución R1

`MainActivity` ya no llama a `TelegramRequestSender` directamente. El transporte está detrás de `ActivationTransportProvider` y la futura API podrá reemplazar el puente temporal sin modificar la UI.

La regla de seguridad sigue siendo: una APK `release` no muestra/abre documentos sin licencia válida; el bypass existe únicamente en la variante `debug` separada.
