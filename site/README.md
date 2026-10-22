# Sito di ASD

Pagina statica con le istruzioni di ASD, la guida a yt-dlp e gli script per scaricare solo
l'audio su Linux e Windows. Niente build: HTML, CSS e un po' di JavaScript.

## Deploy su Vercel

1. *Add New → Project* e importa il repo `asd`.
2. **Root Directory: `site`**. Framework Preset: *Other*. Build Command e Output Directory vuoti.
3. Deploy. `vercel.json` serve gli script come file da scaricare (`Content-Disposition: attachment`).

Il bottone "Scarica l'APK" punta a
`https://github.com/max1969php/asd/releases/latest/download/asd-arm64.apk` (e `asd-armv7.apk`):
a ogni versione crea una Release su GitHub e allega i due APK con questi nomi esatti.

Prova in locale: `python3 -m http.server -d site 8000` e apri http://localhost:8000.

## Lingue

- I testi stanno in `i18n/<codice>.json`; `it.json` è il riferimento con tutte le chiavi.
  Nell'HTML ci sono solo le chiavi (`data-i18n`); i comandi nei riquadri non si traducono.
- I valori possono contenere `<strong>`, `<em>`, `<code>`, `<kbd>` e `<a>`: chi traduce li lascia
  come sono.
- Per pubblicare una lingua: crea `i18n/<codice>.json` (es. `en.json`, `zh-CN.json`) e in `i18n.js`
  metti `ready: true` alla sua riga in `LANGUAGES`. Le chiavi mancanti restano in italiano.
- La select compare da sola quando c'è più di una lingua pronta. Arabo e farsi girano già da
  destra a sinistra.

## Script

- `downloads/linux/`: gli script bash originali (`utility-yt-bash/`, senza la riga vuota prima
  dello shebang, così partono con `./`).
- `downloads/windows/`: le stesse cose in PowerShell (`.ps1`) con un `.bat` da cliccare due volte,
  più `installa_programmi.bat` (winget: yt-dlp, FFmpeg, Deno). Se `yt-dlp.exe` e `ffmpeg.exe` sono
  nella cartella dello script, vengono usati quelli. I `.ps1` sono UTF-8 **con BOM** e righe CRLF:
  senza BOM Windows PowerShell 5.1 rovina le lettere accentate.
