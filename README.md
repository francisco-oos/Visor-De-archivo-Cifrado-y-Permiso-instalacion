# Visor Seguro de Manuales — Hardening R1

Aplicación Android para consultar manuales cifrados por departamento y herramienta Python para preparar documentos VSDOC2.

## Estado de esta rama

Rama de trabajo: `feature/visor-hardening-r1`

R1 corrige primero seguridad/configuración y crea contratos internos antes de modificar el motor PDF. La modernización de memoria, tiles y VSDOC3 queda para R2/R3 después de una prueba física en teléfono.

## Seguridad R1

- `release` **nunca** puede saltarse licencia.
- El build `debug` se instala con `applicationIdSuffix .debug`, separado de producción.
- La versión visible proviene de `BuildConfig`; ya no hay versión duplicada en `AppConfig`.
- No hay token de Telegram ni clave VSDOC real versionados en el código actual.
- `visor-secrets.properties` es local y está ignorado por Git.
- Telegram directo sólo puede habilitarse en `debug`; `release` recibe campos vacíos.
- `VISOR_LICENSE_V2` verifica ECDSA P-256/SHA-256 con clave pública.
- La licencia V1/HMAC queda sólo como compatibilidad de desarrollo en `debug`.
- `FLAG_SECURE` continúa activo.
- `DocumentAccessGuard` mantiene la autorización justo antes de descifrar.

## VSDOC actual

Se conserva compatibilidad:

- VSDOC1: lectura legado.
- VSDOC2: AES-256-GCM + metadata autenticada.

La herramienta `tools/document_encryptor.py` sigue generando VSDOC2, pero ahora guarda la clave local en `visor-secrets.properties` en vez de escribirla dentro de `DocumentKeyConfig.kt`.

> Nota: en R1 la clave VSDOC1/VSDOC2 todavía termina dentro de la APK al compilar. Esto elimina la exposición en Git, pero no la extracción desde la APK. VSDOC3 resolverá ese límite con un modelo de content keys/Keystore.

## Preparación local

1. Copia `visor-secrets.properties.example` como `visor-secrets.properties`.
2. Para manuales, ejecuta `tools/ABRIR_ENCRIPTADOR.bat`; el encriptador generará/actualizará la clave local.
3. Sincroniza Gradle y compila la variante `debug` para las pruebas del visor.
4. Antes de publicar, ejecuta `tools/VERIFICAR_SECRETOS.bat`.

## Departamentos oficiales

- ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC

El acceso global se representa con `access_mode = ALL`; no existe carpeta GENERAL.

## Activación

La UI ya no conoce Telegram. Consume un contrato `ActivationTransport`:

```text
MainActivity
    ↓
ActivationStatusClient / ActivationTransportProvider
    ↓
ActivationTransport
    ├── TelegramActivationTransport (debug temporal)
    └── ApiActivationTransport (siguiente etapa)
```

Esto permite conectar la futura API sin reescribir la pantalla principal.

## Pruebas R1

Se añadieron pruebas JVM para:

- firma ECDSA V2 y manipulación de payload;
- compatibilidad HMAC debug;
- reglas AREA/ALL;
- área vacía sin privilegio global;
- normalización del catálogo;
- Base64/SHA-256/comparación segura.

No se añadió GitHub Actions para evitar consumo facturable. Las verificaciones se ejecutan localmente.

## Documentación

- `docs/PRE_CHANGE_BASELINE_R1.md`
- `docs/ADR_001_SECURITY_BUILD_BOUNDARIES.md`
- `docs/ADR_002_ACTIVATION_TRANSPORT.md`
- `docs/ROADMAP_VSDOC3_VIEWER.md`
- `docs/POST_CHANGE_REPORT_R1.md`

## Próxima etapa

Después de validar R1 en teléfono:

1. descifrado VSDOC2 por stream hacia almacenamiento privado;
2. sesión PDF asíncrona + búsqueda fuera de UI;
3. `RenderScheduler`, caché por presupuesto y render visible;
4. VSDOC3 con Streaming AEAD/lectura seekable;
5. `ProxyFileDescriptor` y eliminación del PDF completo en claro;
6. Vault desacoplado del APK;
7. integración posterior como Biblioteca dentro de Formatos HSE.
