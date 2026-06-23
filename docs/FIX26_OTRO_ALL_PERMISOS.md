# FIX26 - OTRO como permiso global y permisos limpios

## Cambio realizado

En la app se separó el significado de **OTRO** según el contexto:

- En el **formulario de solicitud**, `Otro` sigue existiendo para que un usuario pueda escribir un área no contemplada.
- En **permisos/licencia**, `OTRO` se interpreta como `ALL`, es decir, acceso a todos los departamentos.
- En el catálogo de permisos no se crean carpetas ni departamentos llamados `OTRO`.

## Regla recomendada para el futuro frontend

Para aprobar acceso a todos los documentos, usar preferentemente:

```json
{
  "access_mode": "ALL",
  "allowed_areas": ["ALL"]
}
```

Por compatibilidad, si llega:

```json
{
  "area": "OTRO"
}
```

o

```json
{
  "allowed_areas": ["OTRO"]
}
```

la APK lo tratará como acceso global.

## Comportamiento esperado en DEBUG

El modo DEBUG no requiere licencia y siempre muestra todos los documentos cifrados del `index.json`.

## Mantenimiento

Los departamentos reales siguen siendo únicamente:

- ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC
