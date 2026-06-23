@echo off
chcp 65001 >nul
title Ver log Android - Visor Documentos Cifrado
color 0A

echo ======================================================
echo  LOGCAT - Visor Documentos Cifrado
echo ======================================================
echo.
echo Conecta el telefono con depuracion USB activa.
echo Cuando la app se cierre al abrir un PDF, revisa aqui el error.
echo.

echo Limpiando log anterior...
adb logcat -c

echo.
echo Abre la app en el telefono y reproduce el error.
echo Presiona CTRL+C para detener.
echo.
adb logcat | findstr /i "VisorPDF AndroidRuntime FATAL EXCEPTION com.frank.visordocumentoscifrado"

pause
