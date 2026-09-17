# Prueba física — Viewer R2

Versión esperada: `1.3.0-debug`  
Rama: `feature/visor-hardening-r1`

## Antes de instalar

1. Actualiza la rama:

```bat
git fetch origin
git switch feature/visor-hardening-r1
git pull
```

2. Conserva tu `visor-secrets.properties` local de la prueba R1.
3. Mantén `TELEGRAM_DIRECT_ENABLED=false`.
4. Ejecuta, si quieres validar secretos antes de compilar:

```bat
tools\VERIFICAR_SECRETOS.bat
```

5. Sincroniza Gradle y ejecuta los tests locales:

```bat
gradlew.bat testDebugUnitTest
```

6. Compila/instala `debug`.

## Prueba gestual mínima

Usa un PDF real de varias páginas y preferentemente con texto buscable.

### A. Ajuste inicial

- Abrir el manual.
- Confirmar que la hoja completa aparece centrada.
- La cabecera debe indicar aproximadamente `100 %`.

Esperado: no hay salto de escala al abrir.

### B. Pellizcar para ampliar

- Coloca dos dedos sobre una zona de texto.
- Sepáralos lentamente.
- Júntalos lentamente.

Esperado:

- el contenido permanece bajo el foco de los dedos;
- el zoom cambia continuamente, sin saltos;
- puede regresar a 100 %;
- no cambia de página durante el pinch.

### C. Doble toque

- En 100 %, haz doble toque sobre un párrafo.
- Repite el doble toque.

Esperado:

- primer doble toque: zoom animado alrededor del punto tocado, ~235 %;
- segundo doble toque: vuelve suavemente al ajuste completo;
- no debe saltar a un zoom exagerado.

### D. Pan y límites

- Amplía la página.
- Arrastra en las cuatro direcciones.

Esperado:

- la página acompaña el dedo;
- no aparecen grandes espacios vacíos fuera de los bordes;
- estando ampliado, un arrastre horizontal NO cambia de página.

### E. Inercia

- Con zoom activo, arrastra y suelta rápidamente.

Esperado:

- el movimiento continúa brevemente y desacelera;
- se detiene en los límites de la hoja;
- un toque nuevo detiene inmediatamente la inercia.

### F. Cambio de página

- Pulsa `Ajustar` para volver a 100 %.
- Haz un swipe horizontal claro hacia la izquierda.
- Haz otro hacia la derecha.

Esperado:

- izquierda = siguiente página;
- derecha = página anterior;
- un movimiento predominantemente vertical no cambia de página;
- estando ampliado, el mismo gesto mueve la página en vez de navegar.

### G. Cambio rápido de páginas

- En 100 %, avanza varias páginas rápidamente.

Esperado:

- la interfaz no queda congelada;
- no debe aparecer finalmente una página antigua por terminar tarde su render;
- el contador coincide con la página visible.

## Prueba de búsqueda

### H. Abrir búsqueda

- Toca una vez para mostrar controles si están ocultos.
- Pulsa `Buscar`.

Esperado:

- se abre el teclado;
- el panel de búsqueda permanece visible;
- los controles no desaparecen automáticamente mientras escribes.

### I. Primera búsqueda

- Escribe una palabra que sabes que aparece en varias páginas.
- Pulsa `Ir` o la lupa/acción Buscar del teclado.

Esperado:

- aparece indicador de trabajo sin congelar el zoom/interfaz;
- al terminar muestra `1 de N · pág. X`;
- salta a la primera coincidencia desde la página actual.

### J. Anterior / siguiente

- Usa `‹` y `›` dentro del panel.

Esperado:

- recorre las páginas coincidentes;
- al llegar al final vuelve al inicio;
- no repite el escaneo completo en cada toque.

### K. PDF escaneado

Si tienes un PDF que sea sólo imagen, busca una palabra visible en la foto.

Esperado actual:

- puede indicar `Sin coincidencias`;
- esto NO es un error del visor: R2 no incorpora OCR todavía.

## Seguridad/regresión

Confirmar también:

- sigue bloqueada la captura de pantalla;
- los manuales permitidos siguen abriendo;
- no apareció botón compartir/exportar;
- cerrar el visor regresa correctamente a la biblioteca;
- la app R1/productiva no se sobrescribe porque debug mantiene `.debug`.

## Qué reportar

Si algo no se siente natural, anota:

- gesto exacto;
- nivel aproximado de zoom;
- página;
- orientación/dirección de movimiento;
- si ocurrió una vez o siempre;
- modelo del teléfono;
- video de pantalla sólo si `FLAG_SECURE` lo permite en el build que estés probando (no desactivar seguridad para obtenerlo).

Con esa evidencia ajustamos los umbrales sin cambiar la arquitectura.
