@echo off
setlocal
set "PS1=%TEMP%\compresor_maximo_%RANDOM%.ps1"
> "%PS1%" (
  echo Add-Type -AssemblyName System.Windows.Forms
  echo Add-Type -AssemblyName System.Drawing
  echo function Pick-Target {
  echo   $msg = [System.Windows.Forms.MessageBox]::Show('Quieres comprimir una CARPETA? Pulsa No para elegir un archivo.','Compresor maximo','YesNoCancel','Question')
  echo   if ($msg -eq 'Cancel') { return $null }
  echo   if ($msg -eq 'Yes') {
  echo     $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
  echo     $dlg.Description = 'Selecciona la carpeta a comprimir'
  echo     if ($dlg.ShowDialog() -eq 'OK') { return $dlg.SelectedPath }
  echo   } else {
  echo     $dlg = New-Object System.Windows.Forms.OpenFileDialog
  echo     $dlg.Title = 'Selecciona el archivo a comprimir'
  echo     $dlg.Filter = 'Todos los archivos (*.*)|*.*'
  echo     if ($dlg.ShowDialog() -eq 'OK') { return $dlg.FileName }
  echo   }
  echo   return $null
  echo }
  echo $target = Pick-Target
  echo if (-not $target) { exit }
  echo $save = New-Object System.Windows.Forms.SaveFileDialog
  echo $save.Title = 'Guardar comprimido como'
  echo $save.Filter = '7z ultra si existe 7-Zip (*.7z)|*.7z|ZIP compatible Windows (*.zip)|*.zip'
  echo $save.FileName = ((Split-Path $target -Leaf) + '_LIMPIO.7z')
  echo if ($save.ShowDialog() -ne 'OK') { exit }
  echo $out = $save.FileName
  echo $seven = @('C:\Program Files\7-Zip\7z.exe','C:\Program Files (x86)\7-Zip\7z.exe') ^| Where-Object { Test-Path $_ } ^| Select-Object -First 1
  echo $exclude = @('-xr!.gradle','-xr!build','-xr!app\build','-xr!caches','-xr!daemon','-xr!native','-xr!kotlin-profile','-xr!.tmp','-xr!wrapper','-xr!notifications')
  echo try {
  echo   if ($seven) {
  echo     $args = @('a','-t7z','-mx=9','-m0=lzma2','-ms=on','-mmt=on',$out,$target) + $exclude
  echo     Start-Process -FilePath $seven -ArgumentList $args -Wait -NoNewWindow
  echo   } else {
  echo     if (-not $out.ToLower().EndsWith('.zip')) { $out = [System.IO.Path]::ChangeExtension($out, '.zip') }
  echo     $temp = Join-Path $env:TEMP ('zip_limpio_' + [guid]::NewGuid().ToString())
  echo     New-Item -ItemType Directory -Path $temp ^| Out-Null
  echo     if ((Get-Item $target).PSIsContainer) {
  echo       $root = Split-Path $target -Parent
  echo       $name = Split-Path $target -Leaf
  echo       robocopy $target (Join-Path $temp $name) /E /XD .gradle build caches daemon native kotlin-profile .tmp wrapper notifications /XF *.apk *.aab ^| Out-Null
  echo       Compress-Archive -Path (Join-Path $temp $name) -DestinationPath $out -CompressionLevel Optimal -Force
  echo     } else {
  echo       Compress-Archive -Path $target -DestinationPath $out -CompressionLevel Optimal -Force
  echo     }
  echo     Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
  echo   }
  echo   [System.Windows.Forms.MessageBox]::Show(('Compresion terminada:' + [Environment]::NewLine + $out),'Listo','OK','Information')
  echo } catch {
  echo   [System.Windows.Forms.MessageBox]::Show(('Error al comprimir:' + [Environment]::NewLine + $_.Exception.Message),'Error','OK','Error')
  echo }
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%"
del "%PS1%" >nul 2>nul
