# FIX21 - Cierre del área APK y encriptador

## Encriptador de manuales

La herramienta Python conserva el flujo limpio:

1. Abrir `tools/ABRIR_ENCRIPTADOR.bat`.
2. Presionar `Crear/validar carpetas`.
3. Pegar PDFs en `tools/MANUALES_PARA_ENCRIPTAR/<DEPARTAMENTO>/`.
4. Presionar `Cifrar todo y colocar en APK`.
5. Recompilar la APK.

El `index.json` queda generado con `area_id`, `category`, `title`, `file`, `sha256` y `format`. La app usa esos campos para mostrar los documentos por departamento y para aplicar permisos por área cuando reciba `licencia.key`.

## Catálogo de departamentos

Carpetas oficiales:

- ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC

Para ver todos los departamentos se usará `access_mode = ALL`; no existe carpeta GENERAL.

## Visor PDF

Se ajustó el visor para una experiencia más parecida a una app de lectura:

- Pellizcar para zoom.
- Doble toque para zoom rápido/ajustar.
- Deslizar izquierda/derecha para cambiar página cuando no está ampliado.
- Controles flotantes.
- Un toque sobre la hoja oculta/muestra controles.
- Búsqueda básica por palabra o frase.

## Licencias

La APK conserva el flujo modular:

- Envía aviso de instalación al bot.
- Envía solicitud de acceso al bot.
- Espera `licencia.key`.
- La generación/aprobación de licencias queda fuera de este repositorio, para un frontend/API futuro.

La vigencia de la app sigue separada en `AppConfig.APP_EXPIRES_AT`.
