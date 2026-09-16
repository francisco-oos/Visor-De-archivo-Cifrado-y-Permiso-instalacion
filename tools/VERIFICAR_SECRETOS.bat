@echo off
setlocal
cd /d "%~dp0"
python check_no_secrets.py
if errorlevel 1 (
  echo.
  echo Se detectaron posibles secretos. No publiques ni mezcles cambios hasta corregirlos.
  pause
  exit /b 1
)
echo.
echo Verificacion terminada sin hallazgos.
pause
