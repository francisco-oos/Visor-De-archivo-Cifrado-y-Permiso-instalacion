@echo off
cd /d "%~dp0\.."
echo Limpiando carpetas pesadas generadas por Android Studio / Gradle...
for %%D in (.gradle build app\build caches daemon native kotlin-profile .tmp wrapper notifications) do (
  if exist "%%D" (
    echo Eliminando %%D
    rmdir /s /q "%%D"
  )
)
echo.
echo Limpieza terminada. Estas carpetas no son necesarias para enviar el proyecto.
pause
