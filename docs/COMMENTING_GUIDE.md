# Guía breve de comentarios del proyecto

Objetivo: que cualquier cambio futuro pueda entenderse al entrar al código sin depender de recordar esta conversación.

## Regla

Los comentarios deben explicar **por qué** existe una decisión, no repetir literalmente lo que hace cada línea.

Ejemplos útiles:

- por qué un render debe ir en un worker único;
- por qué un swipe sólo cambia de página al 100 %;
- por qué Telegram está limitado a debug;
- qué deuda queda para VSDOC3;
- qué parte puede sustituirse sin romper contratos.

## Clases principales del visor

- `PdfActivity`: coordina sesión visual, render, búsqueda y controles.
- `ZoomImageView`: gestos y movimiento visual; no conoce documentos ni permisos.
- `ViewerZoomPolicy`: reglas matemáticas testeables de zoom/swipe.
- `PdfSearchEngine`: búsqueda textual PDFBox fuera de UI.
- `DocumentRepository`: catálogo + descifrado compatible VSDOC1/VSDOC2.
- `DocumentAccessGuard`: autorización inmediatamente antes de descifrar.

## Convención

- Comentarios de arquitectura y seguridad: español profesional.
- Nombres de APIs/clases: conservar nombre técnico original.
- Evitar comentarios redundantes como `// incrementa i`.
- Toda decisión temporal debe indicar la etapa que la sustituirá.
