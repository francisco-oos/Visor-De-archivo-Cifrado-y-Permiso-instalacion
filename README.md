# Visor Documentos Cifrado - AppCore FIX23

Proyecto limpio de la APK y el encriptador de manuales.

## Alcance incluido

- Encriptador Python con interfaz.
- Carpetas por departamento oficial.
- Documentos cifrados anexados a `app/src/main/assets/manuales/`.
- APK que avisa instalación al bot.
- APK que envía solicitud de acceso de forma oculta.
- APK que consulta estado al abrirse usando Telegram como puente temporal.
- Licencia y frontend de aprobación quedan como proyecto independiente.

## Departamentos oficiales

- ADQUISICION
- PERFORACION
- TOPOGRAFIA
- GESTORIA
- LOGISTICA
- OPERACIONES
- INMUEBLES
- QC

No existe departamento GENERAL. Para acceso total se usará `access_mode = ALL` en la futura licencia.

## Flujo

1. Ejecuta `tools/ABRIR_ENCRIPTADOR.bat`.
2. Crea/valida carpetas.
3. Pega PDFs en su departamento.
4. Cifra y coloca en APK.
5. Compila la APK.
6. Al instalar, la app manda `INSTALL_EVENT` al bot.
7. El usuario presiona `Solicitar acceso`.
8. La app queda en revisión y consulta estado cuando se vuelve a abrir.

## Mantenimiento

- Configuración app: `AppConfig.kt`.
- Catálogo áreas: `AreaCatalog.kt` y `tools/app_constants.py`.
- Transporte Telegram: `TelegramRequestSender.kt`.
- Consulta de estado temporal: `TelegramStatusClient.kt`.
- Punto futuro para API: `ActivationStatusClient.kt`.
