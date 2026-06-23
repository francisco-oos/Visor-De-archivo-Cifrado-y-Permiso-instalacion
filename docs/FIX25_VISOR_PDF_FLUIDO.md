# FIX25 - Mejora de experiencia del visor PDF

Esta versión se enfoca en que la lectura se sienta más como una app moderna de documentos digitales.

## Cambios principales

- Visor a pantalla limpia.
- Controles flotantes translúcidos.
- Un toque sobre la hoja: muestra/oculta controles.
- Doble toque: zoom rápido / volver a ajustar.
- Pellizcar: ampliar o reducir.
- Arrastrar: mover la página cuando está ampliada.
- Deslizar horizontalmente: cambiar página cuando la página no está ampliada.
- Botones laterales flotantes para página anterior/siguiente.
- Barra inferior flotante para cerrar, buscar y ajustar.
- Marca de agua más sutil para mantener aspecto profesional.
- Código comentado y separado:
  - `PdfActivity.kt`: controla apertura, búsqueda, render y seguridad.
  - `ZoomImageView.kt`: controla gestos, zoom, arrastre y navegación.

## Nota de seguridad

El PDF sigue sin exportarse ni compartirse. Se descifra en cache interno y se elimina al cerrar el visor.
