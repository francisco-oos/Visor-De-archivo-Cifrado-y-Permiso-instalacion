# CIERRE — Visor R1 + R2 + R2.1

Fecha: 2026-09-17  
Rama autoritativa: `main`  
Versión: `1.3.1` / `1.3.1-debug`

## Estado de cierre

La línea R1 → R2 → R2.1 se considera cerrada como base estable del visor.

La validación física en teléfono confirmó el comportamiento esperado del visor R2.1, incluyendo fluidez general, gestos de lectura, búsqueda y resaltado de coincidencias.

## Capacidades consolidadas

- hardening de debug/release;
- secretos fuera de Git;
- licencia V2 asimétrica;
- compatibilidad VSDOC1/VSDOC2;
- pinch-to-zoom relativo;
- doble toque animado;
- pan con límites e inercia;
- swipe de página sólo en escala ajustada;
- render fuera del hilo UI;
- búsqueda fuera del hilo UI;
- navegación por ocurrencias individuales;
- resaltado geométrico normalizado;
- comentarios de arquitectura y mantenimiento en español;
- documentación PRE/POST y protocolos de prueba.

## Ramas

`main` contiene la versión autoritativa.

La rama histórica `feature/visor-hardening-r1` no contiene trabajo exclusivo pendiente. Puede conservarse temporalmente como referencia del proceso o eliminarse posteriormente; no debe usarse como nueva base de desarrollo.

## Integración

El proyecto combinado `Formatos_HSE-Manuales` consume este visor desde `main` y fija el submódulo al commit R2.1 validado.

## Deuda deliberada

Queda fuera de este cierre y debe tratarse como una etapa futura independiente:

- streaming de VSDOC2;
- eliminación del PDF temporal completo;
- render por tiles/viewport;
- caché gobernada por presupuesto de RAM;
- VSDOC3 con Streaming AEAD y acceso seekable;
- OCR para PDFs escaneados.

Estas tareas no son defectos abiertos de R2.1; son evolución arquitectónica futura.
