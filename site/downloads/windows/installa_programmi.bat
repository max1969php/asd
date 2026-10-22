@echo off
rem Installa yt-dlp, FFmpeg e Deno con winget (incluso in Windows 10 e 11).
chcp 65001 >nul
echo.
echo ==========================================
echo   Installo yt-dlp, FFmpeg e Deno
echo ==========================================
echo.
where winget >nul 2>nul
if errorlevel 1 (
    echo winget non trovato. Installa "Programma di installazione app" dal Microsoft Store,
    echo oppure scarica yt-dlp.exe e ffmpeg.exe a mano: istruzioni sul sito.
    pause
    exit /b 1
)
winget install --id yt-dlp.yt-dlp -e --accept-source-agreements --accept-package-agreements
winget install --id Gyan.FFmpeg -e --accept-source-agreements --accept-package-agreements
winget install --id DenoLand.Deno -e --accept-source-agreements --accept-package-agreements
echo.
echo Fatto. Chiudi questa finestra e avvia scarica_url.bat o seleziona_playlist.bat.
pause
