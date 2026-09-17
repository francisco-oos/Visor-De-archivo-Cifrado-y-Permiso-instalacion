# Visor Seguro de Manuales — R1 + Viewer R2

Aplicación Android para consultar manuales cifrados por departamento y herramienta Python para preparar documentos VSDOC2.

## Estado de esta rama

Rama de trabajo: `feature/visor-hardening-r1`

- **R1 validado en teléfono:** hardening, configuración, licencias y activación.
- **Viewer R2 pendiente de validación física:** experiencia gestual, búsqueda asíncrona y render fuera del hilo UI.
- VSDOC3, streaming y render por tiles siguen como etapas posteriores para no mezclar regresiones.

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

## Viewer R2 — experiencia de lectura

La versión de prueba es `1.3.0-debug`.

Se reconstruyó la capa de interacción sin copiar el código de otros visores:

- pinch-to-zoom relativo a la escala real de ajuste de cada página;
- zoom centrado bajo los dedos;
- doble toque animado entre ajuste y zoom de lectura;
- pan limitado a los bordes del documento;
- inercia mediante `OverScroller` cuando existe zoom;
- swipe horizontal de página sólo cuando la hoja está ajustada, evitando confundirlo con paneo;
- porcentaje de zoom visible;
- render de `PdfRenderer` en un worker serializado;
- resultados de render obsoletos se descartan si el usuario avanza rápido;
- búsqueda PDFBox fuera del hilo principal;
- una búsqueda obtiene todas las páginas coincidentes una sola vez y permite navegar anterior/siguiente sin reescanear;
- el panel de búsqueda permanece visible mientras se usa el teclado.

`ViewerZoomPolicy` concentra las reglas matemáticas de zoom/swipe para mantenerlas testeables y separadas de Android.

## VSDOC actual

Se conserva compatibilidad:

- VSDOC1: lectura legado.
- VSDOC2: AES-256-GCM + metadata autenticada.

La herramienta `tools/document_encryptor.py` sigue generando VSDOC2, pero guarda la clave local en `visor-secrets.properties` en vez de escribirla dentro de `DocumentKeyConfig.kt`.

> La clave VSDOC1/VSDOC2 todavía termina dentro de la APK al compilar. Esto elimina la exposición en Git, pero no la extracción desde la APK. VSDOC3 resolverá ese límite mediante content keys/Keystore.

## Preparación local

1. Copia `visor-secrets.properties.example` como `visor-secrets.properties` si aún no existe.
2. Para manuales, ejecuta `tools/ABRIR_ENCRIPTADOR.bat`; el encriptador generará/actualizará la clave local.
3. Sincroniza Gradle y compila `debug`.
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

La UI no conoce Telegram. Consume `ActivationTransport`:

```text
MainActivity
    ↓
ActivationStatusClient / ActivationTransportProvider
    ↓
ActivationTransport
    ├── TelegramActivationTransport (debug temporal)
    └── ApiActivationTransport (siguiente etapa)
```

## Pruebas

R1 incluye pruebas JVM para firma ECDSA V2, reglas AREA/ALL, catálogo y utilidades criptográficas.

Viewer R2 añade `ViewerZoomPolicyTest` para comprobar:

- límites de zoom relativos;
- doble toque relativo a `fit-to-page`;
- cambio de página únicamente al 100 %;
- rechazo de un gesto predominantemente vertical como cambio de página.

No se añadió GitHub Actions para evitar consumo facturable. Las verificaciones se ejecutan localmente.

## Documentación

- `docs/PRE_CHANGE_BASELINE_R1.md`
- `docs/POST_CHANGE_REPORT_R1.md`
- `docs/PRE_CHANGE_VIEWER_R2.md`
- `docs/POST_CHANGE_VIEWER_R2.md`
- `docs/PHONE_TEST_VIEWER_R2.md`
- `docs/ADR_001_SECURITY_BUILD_BOUNDARIES.md`
- `docs/ADR_002_ACTIVATION_TRANSPORT.md`
- `docs/ROADMAP_VSDOC3_VIEWER.md`

## Siguiente etapa después de validar Viewer R2

1. `SecureDocumentSession`;
2. descifrado VSDOC2 por stream hacia almacenamiento privado;
3. render por regiones/tiles según viewport y nivel de zoom;
4. caché por presupuesto de memoria;
5. VSDOC3 con Streaming AEAD/lectura seekable;
6. `ProxyFileDescriptor` para evitar un PDF completo en claro;
7. Vault desacoplado del APK;
8. integración posterior como Biblioteca dentro de Formatos HSE.
