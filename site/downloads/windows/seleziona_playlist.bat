@echo off
rem Doppio clic qui: avvia seleziona_playlist.ps1 senza dover cambiare i permessi di PowerShell.
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0seleziona_playlist.ps1" %*
echo.
pause
