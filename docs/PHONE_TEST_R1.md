# Prueba física R1 — Android

Objetivo: validar Hardening R1 antes de comenzar R2 (streaming/render/memoria).

Rama:

```text
feature/visor-hardening-r1
```

Versión esperada:

```text
1.2.0-debug
```

El debug usa `applicationId = com.frank.visordocumentoscifrado.debug`, por lo que puede convivir con la instalación anterior sin sustituirla.

## 1. Sincronizar rama

Desde el clon local:

```bash
git fetch origin
git switch feature/visor-hardening-r1
git pull
```

No mezclar todavía con `main`.

## 2. Secretos locales / clave VSDOC2

No copies ninguna clave a archivos `.kt`.

Ejecuta:

```bat
tools\ABRIR_ENCRIPTADOR.bat
```

Si existe `tools/CLAVE_DOCUMENTOS_NO_ENVIAR.txt` de la versión anterior, el encriptador la migra a `visor-secrets.properties`. Si no existe clave, crea una nueva y cifra los PDFs con ella.

Confirma que en la raíz exista localmente:

```text
visor-secrets.properties
```

pero que:

```bash
git status
```

**no lo muestre como archivo versionable**.

Telegram directo debe permanecer apagado para esta prueba:

```text
TELEGRAM_DIRECT_ENABLED=false
```

No necesitas un token de bot para probar el visor.

## 3. Control de secretos

Ejecuta:

```bat
tools\VERIFICAR_SECRETOS.bat
```

Resultado esperado:

```text
OK: no se detectaron patrones de secretos en archivos versionables.
```

## 4. Sincronizar Gradle

Abrir el proyecto raíz en Android Studio y ejecutar **Sync Project with Gradle Files**.

Si tu clon conserva `gradle/wrapper`, úsalo normalmente. La auditoría detectó que el commit base no versionaba esa carpeta; no borrar tu wrapper local si ya existe.

## 5. Pruebas JVM

Desde Android Studio: ejecutar tests de `app/src/test`.

Si el wrapper está operativo:

```bash
./gradlew testDebugUnitTest
```

En Windows:

```bat
gradlew.bat testDebugUnitTest
```

Deben pasar:

- `CryptoUtilsTest`;
- `LicenseSignatureVerifierTest`;
- `LicenseAccessPolicyTest`;
- `AreaCatalogTest`.

## 6. Compilar debug

Selecciona variante `debug` y genera/instala la APK.

Esperado:

- nombre de versión: `1.2.0-debug`;
- aparece aviso `DEBUG activo: acceso temporal sin licencia`;
- en **Ver información DEBUG** el transporte debe mostrar `disabled` si Telegram permanece apagado;
- todos los manuales de prueba son visibles por el bypass exclusivo de debug.

## 7. Regresión funcional del visor FIX25

Abrir al menos un PDF y comprobar:

- abre sin `BAD_DECRYPT`;
- título correcto;
- contador de páginas correcto;
- siguiente/anterior;
- swipe horizontal;
- pellizco zoom;
- arrastre estando ampliado;
- doble toque zoom/ajustar;
- botón Ajustar;
- búsqueda en PDF con texto;
- cerrar y volver a abrir otro documento.

## 8. Seguridad visible

Comprobar:

- captura de pantalla bloqueada por `FLAG_SECURE`;
- no existe botón compartir/exportar;
- marca de agua presente;
- la app debug y la app anterior pueden coexistir por package distinto.

## 9. Prueba de release conceptual

No es necesario distribuir `release` todavía. Al revisar/compilar release debe cumplirse:

- `ALLOW_LICENSE_BYPASS=false` fijo en Gradle;
- `TELEGRAM_DIRECT_ENABLED=false` fijo;
- `TELEGRAM_BOT_TOKEN=""`;
- `TELEGRAM_ADMIN_CHAT_ID=""`;
- `LEGACY_LICENSE_HMAC_SECRET=""`;
- sólo `VISOR_LICENSE_V2` puede ser productiva.

Hasta conectar la API/clave pública real, una release sin licencia válida debe quedar cerrada, no conceder acceso.

## 10. Qué reportar de la prueba

Anota o envía:

- modelo de teléfono y Android;
- si Gradle Sync compila sin errores;
- resultado de tests;
- apertura del primer manual;
- PDF/tamaño/páginas usados;
- cualquier lag de swipe/zoom;
- búsqueda;
- screenshot;
- mensajes `BAD_DECRYPT`, OOM o cierres.

Con esa evidencia se abre R2A: streaming VSDOC2 + `SecureDocumentSession`.
