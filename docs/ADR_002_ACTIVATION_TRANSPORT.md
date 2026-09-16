# ADR-002 — Transporte de activación desacoplado

Estado: Aceptado en R1  
Fecha: 2026-09-16

## Contexto

Telegram nació como puente temporal para evitar depender de dominio/VPS/API durante el prototipo. Sin embargo, `MainActivity` conocía directamente clases Telegram y una migración a servidor podía terminar mezclando UI, red y licenciamiento.

## Decisión

Introducir el contrato `ActivationTransport` y un único selector `ActivationTransportProvider`.

```text
UI
│
├── envía evento
│
└── consulta estado
        ↓
ActivationTransport
        ├── TelegramActivationTransport   (debug temporal)
        ├── DisabledActivationTransport   (estado seguro por defecto)
        └── ApiActivationTransport        (futuro)
```

`ActivationStatusClient` sigue siendo responsable de recibir una aprobación y entregar el texto de licencia a `LicenseManager`, pero ya no conoce el protocolo concreto de Telegram.

## Reglas

1. La UI no debe construir URLs de Telegram/API.
2. La UI no debe leer tokens.
3. La implementación productiva futura se seleccionará detrás del provider.
4. Si no existe transporte configurado, la aplicación debe fallar de forma cerrada: no obtiene una licencia por defecto.
5. Telegram directo sólo puede configurarse en build debug.

## Consecuencias

- La futura integración con Server Oficina/API no requiere reescribir la pantalla principal.
- Podemos probar el visor debug sin habilitar ningún canal externo.
- La ausencia de backend se muestra explícitamente en release en vez de esconder un fallback inseguro.

## Próximo paso

Implementar `ApiActivationTransport` cuando se defina el frontend/API de licenciamiento. La API debe devolver estados equivalentes (`PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`) y una licencia V2 firmada cuando corresponda.
