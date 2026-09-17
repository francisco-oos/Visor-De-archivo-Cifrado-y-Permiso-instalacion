# POST-CHANGE — Viewer R2 gestual

Fecha: 2026-09-16  
Rama: `feature/visor-hardening-r1`  
Versión: `1.3.0-debug`  
Estado: **READY_FOR_DEVICE_VALIDATION**

## Resultado

La capa de lectura PDF fue reorganizada para que el visor responda como un lector móvil moderno sin copiar implementaciones de Drive/AndroidX PDF.

R1 permanece funcional y esta R2 se monta encima de la misma rama solicitada por el propietario.

## Cambios implementados

### 1. Gestos

`ZoomImageView.kt` ahora:

- usa `ScaleGestureDetector` para pinch;
- expresa el zoom como ratio relativo a `fit-to-page`;
- limita el rango a 100–500 % relativo;
- usa 235 % relativo como zoom de doble toque;
- anima doble toque y Ajustar para evitar saltos;
- conserva el foco bajo el punto tocado/dedos;
- limita el paneo a los bordes reales de la hoja;
- incorpora inercia con `OverScroller`;
- detiene inercia/animación al iniciar un gesto nuevo;
- sólo interpreta un fling horizontal como cambio de página cuando la hoja está ajustada;
- usa umbrales dependientes de densidad y `ViewConfiguration`, no números de píxeles fijos;
- informa el ratio de zoom a la Activity.

El código quedó comentado en español explicando responsabilidades, decisiones y límites.

### 2. Política testeable

Se añadió `ViewerZoomPolicy.kt`, libre de dependencias Android, para concentrar:

- `zoomRatio`;
- clamp relativo;
- target de doble toque;
- detección de escala ajustada;
- criterio de cambio de página.

Esto evita esconder reglas de negocio de UX dentro de callbacks táctiles difíciles de probar.

### 3. Render fuera del hilo UI

`PdfActivity` usa ahora un executor de un solo worker para `PdfRenderer`.

Razones:

- PdfRenderer sólo debe tener una página abierta a la vez;
- el render es una operación costosa;
- un único worker evita carreras entre páginas.

`renderGeneration` identifica cada solicitud. Si el usuario avanza rápido y termina tarde un render anterior, ese bitmap se descarta en vez de sustituir la página más nueva.

### 4. Búsqueda

Se añadió `PdfSearchEngine.kt`.

La búsqueda:

- se ejecuta en `Dispatchers.IO`;
- no bloquea la UI;
- puede cancelarse al iniciar otra;
- escanea el documento una sola vez por consulta;
- conserva la lista de páginas coincidentes;
- `‹ / ›` navega por resultados sin reabrir/rebuscar todo el PDF;
- muestra contador `N de M · pág. X`;
- mantiene abierto el panel mientras el teclado está activo.

No se añadió OCR. Un PDF puramente escaneado puede seguir sin producir coincidencias.

### 5. UI del lector

- contador superior muestra página + porcentaje de zoom;
- botón `Ajustar` conserva una alternativa accesible al gesto;
- `Buscar` permanece como acción visible;
- toque simple muestra/oculta controles;
- si Buscar está abierto, un toque sobre el documento primero cierra búsqueda en lugar de dejar un panel flotante abandonado.

### 6. Versión

- `versionCode = 12`
- `versionName = 1.3.0`
- debug visible como `1.3.0-debug`

## Investigación aplicada

Referencias oficiales utilizadas:

- Android touch gestures: https://developer.android.com/develop/ui/views/touch-and-input/gestures
- Drag and scale: https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale
- PdfRenderer.Page: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- Android PDF reader APIs: https://developer.android.com/media/grow/pdf-viewer

Drive se utilizó únicamente como referencia de expectativas de UX. No se copió código ni arquitectura interna.

## Decisión explícita: gesto de zoom con una mano

No se implementó todavía `doble toque + arrastre vertical` para zoom. Aunque existe en visores como Drive, puede competir con scrolling/fling rápidos. Preferimos primero validar pinch + doble toque + pan + inercia con baja ambigüedad y añadir gestos secundarios únicamente si mejoran la prueba física.

## Verificaciones ejecutadas en esta sesión

### ViewerZoomPolicy

Se compiló la política pura con `kotlinc` y se ejecutó un harness para:

- límite máximo relativo;
- swipe válido al 100 %;
- swipe bloqueado con zoom.

Resultado:

```text
ViewerZoomPolicy verification: PASS
```

También se añadió `ViewerZoomPolicyTest.kt` para la suite Gradle local.

## Verificación pendiente en PC/teléfono

Este entorno no dispone de Android SDK para afirmar `assembleDebug`.

El gate real está descrito en `PHONE_TEST_VIEWER_R2.md` e incluye:

- Gradle Sync;
- `testDebugUnitTest`;
- compilación `1.3.0-debug`;
- pinch in/out;
- doble toque;
- pan + fling;
- swipe de página;
- avance rápido;
- búsqueda + anterior/siguiente;
- regresión de `FLAG_SECURE`.

## Deuda que continúa deliberadamente

No se ocultó ni se declaró resuelto lo que todavía no cambió:

- VSDOC2 continúa usando `readBytes()` completo antes del visor;
- sigue existiendo PDF temporal en cache privado;
- cada página todavía se renderiza como bitmap completo;
- el límite máximo de bitmap puede seguir siendo alto;
- no existe render de región/tiles al aumentar zoom;
- no existe OCR;
- no existe resaltado geométrico de texto encontrado.

La siguiente fase, después de validar R2 en teléfono, debe atacar memoria y resolución:

`SecureDocumentSession → streaming VSDOC2 → viewport → tile renderer → cache budget → VSDOC3`.
