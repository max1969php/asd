@echo off
rem Doppio clic qui: avvia scarica_url.ps1 senza dover cambiare i permessi di PowerShell.
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scarica_url.ps1" %*
echo.
pause
