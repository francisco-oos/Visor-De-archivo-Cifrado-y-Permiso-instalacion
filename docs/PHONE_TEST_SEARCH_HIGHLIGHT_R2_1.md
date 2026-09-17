# PHONE TEST — Search Highlight R2.1

Rama: `feature/visor-hardening-r1`  
Versión esperada: `1.3.1-debug`

## Objetivo

Validar en un teléfono real que la búsqueda no sólo encuentre una página, sino que resalte correctamente la palabra/frase encontrada sin romper los gestos de Viewer R2.

## 1. Actualizar rama

Desde el repositorio del Visor:

```powershell
git fetch origin
git switch feature/visor-hardening-r1
git pull
```

## 2. Ejecutar pruebas locales

```powershell
gradlew.bat testDebugUnitTest
```

Si Android Studio solicita sincronizar Gradle, permitir la sincronización antes de compilar.

## 3. Compilar e instalar

Compilar la variante `debug` e instalarla en el teléfono.

Verificar en la información de la aplicación que la versión sea:

```text
1.3.1-debug
```

## 4. Prueba A — palabra única

1. Abrir un manual que contenga texto seleccionable.
2. Tocar `Buscar`.
3. Escribir una palabra visible y conocida, por ejemplo una palabra del título o un encabezado.
4. Tocar `Ir` o la acción Buscar del teclado.

Esperado:

- aparece contador `1 de N · pág. X`;
- la página correcta se abre;
- la palabra queda marcada con una banda translúcida;
- si existen otras coincidencias en esa misma página también deben verse marcadas;
- la coincidencia activa debe verse más enfatizada.

## 5. Prueba B — varias coincidencias en la misma página

Buscar una palabra frecuente que aparezca dos o más veces en una página.

Tocar varias veces `›`.

Esperado:

- el contador avanza por ocurrencias, no sólo por páginas;
- cuando dos resultados están en la misma página, el contador cambia aunque la página permanezca igual;
- la coincidencia activa cambia de énfasis;
- no debe saltar a una página distinta hasta que corresponda al siguiente resultado real.

Después usar `‹` y comprobar navegación inversa con wrap al final/inicio.

## 6. Prueba C — frase

Buscar una frase de 2 a 4 palabras que exista exactamente en el documento.

Esperado:

- se resalta el bloque completo de la frase;
- si la frase cruza un salto de línea, puede mostrarse como dos rectángulos, uno por renglón;
- el resaltado no debe desplazarse a otra línea o palabra cercana.

## 7. Prueba D — zoom

Con una coincidencia activa:

1. hacer pinch para ampliar;
2. arrastrar la página;
3. reducir nuevamente;
4. usar doble toque.

Esperado:

- el resaltado escala junto con la página;
- permanece colocado sobre el texto correspondiente;
- no modifica la respuesta del pinch/pan.

Nota: en R2.1 el resaltado está dibujado dentro del bitmap renderizado, por lo que sigue naturalmente las transformaciones del `ZoomImageView`.

## 8. Prueba E — cerrar búsqueda

Con marcas visibles, tocar `×` del buscador.

Esperado:

- desaparece el panel;
- desaparecen los resaltados;
- el documento continúa abierto en la página actual;
- los gestos siguen funcionando.

## 9. Prueba F — consulta nueva

1. Buscar `palabra A`.
2. Sin cerrar el documento, cambiar a `palabra B`.

Esperado:

- las marcas anteriores desaparecen;
- sólo aparecen las coincidencias de la consulta nueva;
- una búsqueda anterior que todavía estuviera trabajando no debe reemplazar el resultado nuevo.

## 10. Prueba G — sin resultados

Buscar una cadena inexistente, por ejemplo:

```text
xyz987textoimposible
```

Esperado:

- `Sin coincidencias`;
- no quedan marcas de una búsqueda previa;
- la app no se congela.

## 11. Prueba H — PDF escaneado

Si tienes un manual que sea imagen escaneada sin texto seleccionable, buscar una palabra que se vea en la imagen.

Esperado actual:

- puede indicar `Sin coincidencias`;
- no debe cerrarse ni bloquearse;
- este comportamiento es correcto hasta incorporar OCR.

## 12. Regresión Viewer R2

Repetir brevemente:

- pinch in/out;
- doble toque;
- pan con zoom;
- inercia;
- swipe de página al 100 %;
- avanzar varias páginas rápido;
- botón `Ajustar`;
- abrir/cerrar Buscar.

No debe haber pérdida de fluidez respecto a `1.3.0-debug`.

## 13. Seguridad

Intentar una captura de pantalla mientras se visualiza el manual.

Esperado:

- `FLAG_SECURE` mantiene el mismo comportamiento que R1/R2;
- la incorporación del resaltado no habilita compartir/exportar.

## Qué reportar si algo falla

Anotar:

- nombre del manual;
- página visible;
- palabra/frase buscada;
- si el resaltado apareció arriba/abajo/izquierda/derecha del texto;
- si el PDF está rotado;
- si la página tiene columnas/tablas;
- si el problema ocurre sólo con una frase o también con una palabra;
- modelo de teléfono y versión Android;
- captura/foto del resultado si `FLAG_SECURE` permite documentarlo desde otro dispositivo.

Con esos datos se puede ajustar la transformación geométrica sin adivinar.
