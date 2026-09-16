# POST-CHANGE REPORT R1 — Hardening y contratos

Fecha: 2026-09-16  
Rama: `feature/visor-hardening-r1`  
Base: `main@9becbf22a66895d22b9f5b1f13a446b6d73890f4`  
Estado: **READY_FOR_DEVICE_VALIDATION**

## Resultado

R1 completa el hardening/configuración acordado antes de modificar el motor PDF. `main` permanece intacta; todos los cambios viven en la rama de trabajo.

## Cambios cerrados

### Build

- Gradle queda como única fuente de versión (`1.2.0`; debug visible como `1.2.0-debug`).
- `AppConfig.DEBUG_MODE` deriva de `BuildConfig.DEBUG && ALLOW_LICENSE_BYPASS`.
- `release` fija `ALLOW_LICENSE_BYPASS=false`.
- debug usa package `.debug` independiente.

### Secretos

- token/chat Telegram eliminados del HEAD de la rama.
- token histórico revocado por el propietario y registrado en `SECURITY_INCIDENT_TELEGRAM_2026-09-16.md`.
- clave VSDOC1/VSDOC2 eliminada del código versionado.
- `visor-secrets.properties` ignorado por Git y plantilla `.example` añadida.
- encriptador migra/genera la clave local en ese archivo.
- escáner local gratuito de secretos añadido; no se creó GitHub Actions.

### Licencias

- `VISOR_LICENSE_V2` con ECDSA P-256/SHA-256.
- release sólo acepta V2.
- V1/HMAC y formato histórico sólo pueden habilitarse en debug con secreto local.
- permisos por área separados en `LicenseAccessPolicy`.
- área vacía sin `allowed_areas` ya no deriva accidentalmente en privilegio global.
- validación de licencia desacoplada de la clave VSDOC.

### Activación

- nuevo contrato `ActivationTransport`.
- `MainActivity` deja de importar Telegram.
- Telegram queda como `TelegramActivationTransport` debug temporal.
- ausencia de backend usa transporte deshabilitado/fail-closed.

### Superficie Android

- `FileProvider` heredado no usado eliminado.
- queries WhatsApp heredadas eliminadas.
- `file_paths.xml` eliminado.
- `INTERNET` se conserva para activación debug/API futura.
- `FLAG_SECURE` se conserva sin cambios.

### Repositorio

- eliminado `android/FakeDependency.jar` vacío/no referenciado.
- `.gitignore` endurecido para secretos/keystores y corregido para no impedir versionar `gradle/wrapper` en el futuro.
- documentación histórica FIX22/FIX23 marcada como histórica.
- arquitectura/roadmap/documentación de licencia actualizados.

## Verificaciones ejecutadas por esta sesión

### Kotlin puro

Se compiló y ejecutó con `kotlinc` un harness de:

- `CryptoUtils`;
- `LicenseSignatureVerifier`;
- `LicenseAccessPolicy`;
- `AreaCatalog`;
- `LicenseData`.

Resultado:

```text
R1 pure Kotlin verification: PASS
```

Incluyó:

- SHA-256 conocido;
- normalización de área;
- área vacía no global;
- `ALL` global;
- generación ECDSA P-256;
- firma válida aceptada;
- payload manipulado rechazado.

### Contratos críticos con stubs

Se compiló con `kotlinc` un harness tipado para:

- `LicenseManager`;
- configuraciones BuildConfig;
- `ActivationTransport`;
- `TelegramActivationTransport`;
- `ActivationTransportProvider`;
- `ActivationStatusClient`.

Resultado:

```text
Critical Kotlin type/syntax check: PASS
Activation contract type/syntax check: PASS
```

### Python

Se ejecutó `py_compile` sobre las versiones R1 de:

- `tools/document_encryptor.py`;
- `tools/check_no_secrets.py`.

Resultado:

```text
R1 Python syntax verification: PASS
```

## Verificación no ejecutable en este entorno

No se afirma una compilación Android completa. El runtime de esta sesión no dispone de Android SDK/`android.jar` y su shell no tiene resolución de red para descargar dependencias/Gradle. No se activó GitHub Actions porque el propietario solicitó evitar cualquier posibilidad de consumo facturable.

Por tanto quedan como gate físico/local:

- Gradle Sync real;
- `testDebugUnitTest` con JUnit;
- `assembleDebug`;
- instalación y apertura en teléfono;
- prueba de manual cifrado real;
- gestos FIX25;
- búsqueda;
- `FLAG_SECURE`;
- logs/crashes.

El procedimiento exacto está en `PHONE_TEST_R1.md`.

## Riesgos/deuda deliberadamente no tocados

- `DocumentRepository.decrypt()` aún usa `readBytes()` completo.
- VSDOC2 todavía genera plaintext temporal completo.
- el bitmap máximo del visor puede rondar 38.7 MiB.
- búsqueda PDFBox aún pertenece a `PdfActivity`.
- la clave VSDOC1/VSDOC2, aunque ya no está en Git, sigue terminando dentro de la APK al compilar.
- root/emulador sigue siendo una señal simple, no una garantía.
- el commit histórico del token revocado permanece en la historia Git.
- el commit base no incluía `gradle/wrapper`; una instalación local existente puede conservarlo, pero debe resolverse antes de depender del wrapper en clones limpios.

Estos puntos están programados en `ROADMAP_VSDOC3_VIEWER.md` y no se mezclaron en R1 para poder detectar regresiones con claridad.

## Gate para abrir R2

R2A sólo comienza después de una prueba física R1 exitosa o de corregir cualquier error encontrado en esa prueba.

R2A previsto:

1. `SecureDocumentSession`;
2. descifrado VSDOC2 por stream hacia almacenamiento privado temporal;
3. limpieza defensiva de plaintext;
4. operaciones pesadas fuera del hilo UI;
5. medición PRE/POST de RAM y tiempo a primera página.
