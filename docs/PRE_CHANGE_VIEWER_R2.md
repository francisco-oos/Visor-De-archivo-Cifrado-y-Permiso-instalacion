# PRE-CHANGE — Viewer R2 gestual

Fecha: 2026-09-16  
Rama: `feature/visor-hardening-r1`  
Baseline validado en teléfono: R1 / `1.2.0-debug`

## Motivo

La prueba física confirmó que seguridad, licencia debug, catálogo y apertura son fluidos. El defecto perceptible está concentrado en la experiencia del lector PDF: zoom, reducción, desplazamiento y búsqueda no se sienten tan naturales como un lector maduro.

## Estado anterior (FIX25/R1)

`ZoomImageView` ya contenía pinch, doble toque y swipe, pero presentaba estas limitaciones:

- `MAX_SCALE` era una escala absoluta, no relativa al ajuste real de cada PDF.
- el doble toque podía saltar a `2.0f` absoluto aunque la hoja estuviera ajustada a una escala mucho menor;
- no existía inercia al desplazar una página ampliada;
- el swipe de página dependía de umbrales fijos en píxeles;
- no se mostraba al usuario el porcentaje de zoom;
- la búsqueda cargaba y recorría PDFBox dentro del hilo de interfaz;
- cada "Siguiente" de búsqueda volvía a recorrer páginas;
- el render de PdfRenderer se ejecutaba desde la UI.

## Referencias estudiadas

No se copia código de Google Drive ni de AndroidX PDF. Se estudian patrones de interacción y APIs públicas.

### Android Gestures

Android recomienda combinar `GestureDetector` para gestos comunes con `ScaleGestureDetector` para escalado, y usar `Scroller`/`OverScroller` para animar flings.

- https://developer.android.com/develop/ui/views/touch-and-input/gestures
- https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale

### PdfRenderer

La documentación de Android indica que renderizar una página es una operación larga y las APIs modernas la marcan como trabajo de worker. También documenta `destClip + Matrix` como base para render por regiones/tiles al hacer zoom.

- https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- https://developer.android.com/media/grow/pdf-viewer

### Drive como referencia de UX, no de implementación

Se toma como referencia la expectativa del usuario: pinch continuo, paneo natural, doble toque y búsqueda accesible. No se replica el gesto de doble-toque + arrastre vertical para zoom con una mano en esta etapa, porque puede entrar en conflicto con desplazamientos rápidos y se prioriza precisión sobre cantidad de gestos.

## Objetivo R2

1. Escala expresada como ratio respecto de `fit-to-page`.
2. Pinch focal bajo los dedos.
3. Doble toque animado `100 % ↔ 235 %` relativo.
4. Pan limitado a la hoja.
5. Fling/inercia con `OverScroller` cuando hay zoom.
6. Swipe horizontal de página únicamente al 100 % y con umbrales adaptados a densidad/velocidad Android.
7. Controles no intrusivos y porcentaje visible.
8. Búsqueda fuera del hilo UI, con lista de páginas coincidentes y navegación anterior/siguiente sin reescanear.
9. Render de PdfRenderer en worker serializado para evitar bloquear gestos.
10. Mantener intactas autorización, cifrado y `FLAG_SECURE`.

## Fuera de alcance deliberado

R2 gestual NO cambia todavía:

- `DocumentRepository.decrypt()` / `readBytes()` completo;
- formato VSDOC2;
- clave VSDOC2 embebida en APK de prueba;
- render por tiles/regiones;
- resaltado gráfico de cada palabra encontrada;
- OCR de PDFs escaneados;
- VSDOC3/Streaming AEAD/ProxyFileDescriptor.

Esos puntos siguen en `ROADMAP_VSDOC3_VIEWER.md` y se abordarán después de validar esta experiencia en teléfono.
