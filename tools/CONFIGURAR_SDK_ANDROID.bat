@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0\.."
set "DEFAULT_SDK=%LOCALAPPDATA%\Android\Sdk"

echo ===============================================
echo  Configurar Android SDK para este proyecto
echo ===============================================
echo.
echo Ruta detectada:
echo %DEFAULT_SDK%
echo.
if exist "%DEFAULT_SDK%\platforms" (
    set "SDK=%DEFAULT_SDK%"
) else (
    echo No encontre el SDK en la ruta default.
    set /p SDK=Escribe la ruta completa de tu Android SDK: 
)

if not exist "%SDK%\platforms" (
    echo.
    echo ERROR: Esa ruta no parece ser un Android SDK valido.
    echo Debe contener una carpeta llamada platforms.
    pause
    exit /b 1
)

set "SDK_ESC=%SDK:\=/%"
set "SDK_ESC=%SDK_ESC::=\:%"
> local.properties echo # Generado por tools\CONFIGURAR_SDK_ANDROID.bat
>> local.properties echo sdk.dir=%SDK_ESC%

echo.
echo local.properties generado correctamente:
type local.properties
echo.
echo Ahora en Android Studio ejecuta: File ^> Sync Project with Gradle Files
pause
