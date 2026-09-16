# Arquitectura limpia — Visor Seguro App Core (R1)

## Responsabilidades actuales

El repositorio mantiene dos productos técnicos:

1. **APK Visor Seguro**
   - catálogo y control de acceso por área;
   - lectura VSDOC1/VSDOC2;
   - visor PDF FIX25;
   - identidad de instalación;
   - verificación local de licencia;
   - contrato neutral de activación.

2. **Encriptador de manuales**
   - crea carpetas oficiales;
   - administra PDFs fuente;
   - cifra VSDOC2;
   - genera `index.json`;
   - exporta temporalmente a `app/src/main/assets/manuales`;
   - guarda la clave local en `visor-secrets.properties` fuera de Git.

## Capas R1

```text
ui/
  MainActivity
  PdfActivity
  ZoomImageView

activation/
  ActivationTransport
  ActivationTransportProvider
  TelegramActivationTransport (debug temporal)

license/
  ActivationStatusClient
  LicenseManager
  LicenseSignatureVerifier
  LicenseAccessPolicy
  LicenseModels

documents/
  DocumentRepository
  DocumentAccessGuard

security/
  CryptoUtils
  DeviceIdentity
  RootDetector

config/
  AppConfig
  AreaCatalog
  SecurityConfig
  DocumentKeyConfig (fachada BuildConfig V1/V2)
  LicenseSecurityConfig
  TelegramConfig (debug)
```

## Frontera debug/release

`BuildConfig` es la única fuente de verdad para versión y bypass.

- debug: package `.debug`, puede habilitar bypass para probar el lector;
- release: bypass fijo en `false`, Telegram directo desactivado y sin token/chat ID.

## Licencia

Productivo:

```text
VISOR_LICENSE_V2
payload_b64
     ↓ firma ECDSA P-256 / SHA-256
private key: servidor/admin
public key: APK
```

La licencia V1/HMAC sólo queda disponible en debug como compatibilidad temporal.

## Seguridad por área

La UI filtra documentos, pero `DocumentAccessGuard` vuelve a comprobar autorización antes del descifrado. VSDOC2 incluye metadata interna autenticada mediante AES-GCM/AAD para detectar manipulación entre `index.json` y el documento.

`ALL` representa acceso global. No existe carpeta GENERAL.

## Activación

La UI no conoce Telegram:

```text
MainActivity
     ↓
ActivationTransportProvider
     ↓
ActivationTransport
```

Telegram es sólo una implementación debug. La futura API sustituirá esa implementación, no la pantalla.

## Secretos

No se versionan secretos nuevos. `visor-secrets.properties` está en `.gitignore`.

La clave VSDOC1/VSDOC2 todavía termina en la APK para conservar compatibilidad; se considera deuda transitoria y está explícitamente programada para desaparecer con VSDOC3.

## Motor PDF

FIX25 se conserva sin reescritura en R1. El roadmap detallado está en `ROADMAP_VSDOC3_VIEWER.md`.

La siguiente etapa introducirá sesión, streaming VSDOC2, trabajos asíncronos, caché ponderada, viewport/render scheduler y posteriormente VSDOC3 seekable.

## Vigencias separadas

- `AppConfig.APP_EXPIRES_AT`: vigencia de la versión/producto.
- `license.expires_at`: vigencia del permiso del empleado.

Actualizar la APK no debe renovar automáticamente la licencia.
