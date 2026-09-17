# POST-CHANGE — Search Highlight R2.1

Fecha: 2026-09-16  
Rama: `feature/visor-hardening-r1`  
Versión: `1.3.1-debug`  
Estado: **READY_FOR_DEVICE_VALIDATION**

## Objetivo

Extender la búsqueda de Viewer R2 para que el usuario no sólo llegue a la página donde existe una palabra/frase, sino que pueda ver **exactamente dónde aparece** dentro de la hoja, de forma comparable a lectores móviles modernos.

No se copió código de Google Drive. Se reconstruyó el comportamiento usando `PDFBox`, geometría normalizada y el pipeline de render existente.

## Cambios implementados

### 1. La búsqueda ahora trabaja con ocurrencias, no sólo páginas

Antes:

```text
consulta -> [página 3, página 9, página 12]
```

Ahora:

```text
consulta
  -> ocurrencia 1 -> página 3 -> rectángulo(s)
  -> ocurrencia 2 -> página 3 -> rectángulo(s)
  -> ocurrencia 3 -> página 9 -> rectángulo(s)
```

Una misma página puede contener varias coincidencias y los botones `‹ / ›` recorren cada una individualmente.

### 2. Coordenadas normalizadas

`PdfSearchEngine` convierte cada zona encontrada a coordenadas 0..1 respecto del tamaño de página:

```text
left / pageWidth
right / pageWidth
top / pageHeight
bottom / pageHeight
```

La búsqueda no queda acoplada a una resolución concreta de bitmap. El mismo resultado podrá reutilizarse cuando migremos a render por tiles/regiones.

### 3. Orden visual de PDFBox

La geometría se captura en `PDFTextStripper.writeString(...)`, después de que PDFBox haya aplicado su ordenación visual y tratamiento de glyphs superpuestos.

Esto es importante porque un PDF es un formato gráfico: el orden interno de sus operadores no tiene por qué coincidir con el orden humano de lectura.

### 4. Relación carácter -> TextPosition

Por cada página se construye simultáneamente:

- texto lógico normalizado;
- mapa de cada carácter hacia su `TextPosition` original.

Cuando `indexOf()` encuentra una consulta, el motor recupera los glyphs exactos y calcula su geometría.

La normalización se hace carácter por carácter para mantener la relación 1:1 entre texto e índices, incluso al convertir a minúsculas.

### 5. Frases y saltos de línea

El motor infiere espacios entre `TextPosition` cuando detecta:

- cambio de línea;
- separación horizontal compatible con espacio.

Así una frase puede encontrarse aunque el PDF la almacene en varios fragmentos internos o la haya partido por ajuste de renglón.

### 6. Resaltado

`PdfActivity` pinta las zonas de búsqueda después de `PdfRenderer` y antes de la marca de agua:

- coincidencias de la página: resaltado translúcido;
- coincidencia activa: mayor énfasis + borde;
- al cambiar `‹ / ›`, cambia la ocurrencia activa;
- al cerrar búsqueda, las marcas desaparecen;
- el PDF original nunca se modifica.

### 7. Navegación

El panel indica:

```text
3 de 14 · pág. 8
```

Es decir, el contador representa ocurrencias reales, no sólo páginas que contienen texto.

### 8. Versión

- `versionCode = 13`
- `versionName = 1.3.1`
- debug visible como `1.3.1-debug`

## Comentarios de mantenimiento

Se conservaron comentarios en español dentro de:

- `PdfSearchEngine.kt`;
- `PdfActivity.kt`.

Los comentarios explican:

- por qué se normalizan coordenadas;
- por qué se usa `writeString()` y no `processTextPosition()`;
- por qué existe el mapa carácter -> glyph;
- cómo se agrupan glyphs por línea;
- qué parte depende todavía de PDFBox.

## Límites conocidos

### PDF escaneado

Si la página es sólo una imagen y no tiene capa de texto, PDFBox no puede localizar palabras. OCR sigue siendo una capacidad futura opcional.

### PDFs con estructura textual irregular

PDF permite dibujar texto en órdenes arbitrarios. `setSortByPosition(true)` y la inferencia de espacios mejoran la mayoría de documentos, pero PDFs especialmente complejos pueden requerir ajustes posteriores.

### Render actual

R2.1 todavía pinta el resaltado sobre el bitmap renderizado. Al cambiar la coincidencia activa se vuelve a renderizar la página. Es correcto funcionalmente; si la prueba física muestra latencia perceptible, la siguiente optimización será mover las marcas a un overlay independiente que siga la matriz de zoom sin volver a renderizar el PDF.

### PDFBox Android

El proyecto usa `com.tom-roush:pdfbox-android:2.0.27.0`, que es la última publicación disponible de ese port. Seguimos utilizándolo únicamente para extracción/búsqueda en esta etapa; el roadmap mantiene la intención de depender cada vez más de APIs PDF nativas modernas cuando la compatibilidad del dispositivo lo permita.

## Investigación utilizada

- Apache PDFBox `PDFTextStripper`: `writeString(String, List<TextPosition>)` expone las posiciones asociadas al texto ya preparado para salida.
- Apache PDFBox `TextPosition`: `x`, `y`, `width`, `height`, `pageWidth` y `pageHeight` permiten reconstruir geometría de pantalla.
- PDFBox recomienda `setSortByPosition(true)` cuando el orden del contenido no coincide con el orden de lectura.

No se añadió GitHub Actions ni CI de pago.

## Gate físico

Antes de llevar este visor a `Formatos_HSE-Manuales` deben validarse en teléfono:

1. búsqueda de una palabra simple;
2. múltiples ocurrencias en la misma página;
3. navegación `‹ / ›`;
4. frase de varias palabras;
5. palabra cerca del inicio/final de una línea;
6. zoom sobre una coincidencia;
7. cierre del panel y eliminación de marcas;
8. PDF escaneado sin texto;
9. regresión de pinch, pan, swipe y `FLAG_SECURE`.

El procedimiento está en `docs/PHONE_TEST_SEARCH_HIGHLIGHT_R2_1.md`.
