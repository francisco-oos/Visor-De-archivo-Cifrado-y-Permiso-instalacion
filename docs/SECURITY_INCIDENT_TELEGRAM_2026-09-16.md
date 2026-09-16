# Registro de seguridad — credencial Telegram expuesta

Fecha: 2026-09-16  
Estado: Contenido / mitigado por revocación

## Hallazgo

La auditoría R1 detectó que el commit base del repositorio público contenía una credencial de bot Telegram directamente en `TelegramConfig.kt`.

No se reproduce el valor en esta documentación.

## Acción realizada

El propietario confirmó la revocación del token en BotFather el 2026-09-16.

La rama `feature/visor-hardening-r1` elimina la credencial del código y sólo permite valores Telegram desde configuración local para builds debug. La variante release recibe credenciales vacías por diseño.

## Persistencia histórica

Eliminar un secreto del HEAD no lo elimina de commits Git anteriores. Dado que la credencial fue revocada, ya no debe considerarse utilizable. Una reescritura del historial podría hacerse más adelante por higiene, pero no es necesaria para recuperar la confidencialidad de un token ya invalidado y sería una operación destructiva para clones existentes.

## Prevención

- `visor-secrets.properties` ignorado por Git.
- `tools/check_no_secrets.py`.
- `tools/VERIFICAR_SECRETOS.bat`.
- Telegram directo limitado a debug.
- No se añade CI facturable; el chequeo se ejecuta localmente antes de publicar/mergear.
