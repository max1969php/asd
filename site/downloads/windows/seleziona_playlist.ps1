# Versione Windows di seleziona_playlist.sh: mostra i brani di una playlist YouTube, chiede quali
# scaricare e li salva in MP3 a 128 kbps nella cartella Musica accanto allo script.
# Si avvia con seleziona_playlist.bat.

Write-Host ''
Write-Host '=========================================='
Write-Host '  SELETTORE PLAYLIST YOUTUBE'
Write-Host '  Formato MP3 - 128 kbps'
Write-Host '=========================================='
Write-Host ''

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$cartella = $PSScriptRoot

# Prima il programma nella cartella dello script (versione senza installazione), poi quello installato.
function Trova($nome) {
    $locale = Join-Path $cartella "$nome.exe"
    if (Test-Path $locale) { return $locale }
    $comando = Get-Command $nome -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    return $null
}

$ytdlp = Trova 'yt-dlp'
$ffmpeg = Trova 'ffmpeg'
foreach ($p in @(@('yt-dlp', $ytdlp), @('ffmpeg', $ffmpeg))) {
    if (-not $p[1]) {
        Write-Host "ERRORE: $($p[0]) non è installato."
        Write-Host "Avvia installa_programmi.bat, oppure metti $($p[0]).exe in questa cartella."
        Write-Host "Se l'hai appena installato, chiudi e riapri la finestra."
        exit 1
    }
}
# ffmpeg.exe accanto allo script: yt-dlp va avvisato di dove sta.
$opzioniFfmpeg = @()
if ($ffmpeg -eq (Join-Path $cartella 'ffmpeg.exe')) { $opzioniFfmpeg = @('--ffmpeg-location', $cartella) }

$destinazione = Join-Path $cartella 'Musica'
New-Item -ItemType Directory -Force -Path $destinazione | Out-Null

$url = (Read-Host 'Incolla il link della playlist YouTube').Trim()
if (-not $url) {
    Write-Host 'Non hai inserito un link.'
    exit 1
}

Write-Host ''
Write-Host "Recupero l'elenco dei brani..."
Write-Host ''

# Legge la playlist senza scaricare l'audio. L'elenco passa da un file UTF-8 e non dall'output:
# Windows PowerShell leggerebbe i titoli con la codifica sbagliata (accenti rovinati).
$fileElenco = Join-Path $env:TEMP "asd-playlist-$PID.txt"
Remove-Item -LiteralPath $fileElenco -ErrorAction SilentlyContinue
& $ytdlp --flat-playlist --ignore-errors --print-to-file '%(playlist_index)s|%(title)s' $fileElenco $url | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'Attenzione: impossibile recuperare la playlist.' }
$elenco = @()
if (Test-Path -LiteralPath $fileElenco) {
    $elenco = Get-Content -LiteralPath $fileElenco -Encoding UTF8
    Remove-Item -LiteralPath $fileElenco
}

$titoli = [ordered]@{}
foreach ($riga in @($elenco)) {
    $parti = "$riga".Split('|', 2)
    if ($parti.Count -eq 2 -and $parti[0] -match '^[0-9]+$') { $titoli[$parti[0]] = $parti[1] }
}

if ($titoli.Count -eq 0) {
    Write-Host 'Nessun brano trovato. Controlla il link.'
    exit 1
}

Write-Host "Brani trovati: $($titoli.Count)"
Write-Host '------------------------------------------'
foreach ($numero in $titoli.Keys) {
    Write-Host ('{0,3}. {1}' -f $numero, $titoli[$numero])
}
Write-Host '------------------------------------------'
Write-Host ''
Write-Host 'Esempi di selezione:'
Write-Host '  1,3,7-10  = brani 1, 3 e dal 7 al 10'
Write-Host '  tutti     = intera playlist'
Write-Host ''

$scelta = (Read-Host 'Quali brani vuoi scaricare?').Trim()
if ($scelta -match '^(tutti|tutto|all)$') {
    $selezione = ($titoli.Keys | ForEach-Object { $_ }) -join ','
} else {
    $selezione = $scelta -replace '\s', ''
}

# Convalida numeri e intervalli
if ($selezione -notmatch '^[0-9]+([,-][0-9]+)*$') {
    Write-Host 'Selezione non valida.'
    exit 1
}

Write-Host ''
Write-Host 'Scarico i brani selezionati in MP3 a 128 kbps...'
Write-Host "Destinazione: $destinazione"
Write-Host ''

& $ytdlp @opzioniFfmpeg `
    --yes-playlist `
    --playlist-items $selezione `
    --ignore-errors `
    --continue `
    -f 'ba' `
    -x `
    --audio-format mp3 `
    --audio-quality 128K `
    -P $destinazione `
    -o '%(playlist_index)03d - %(title)s.%(ext)s' `
    $url

Write-Host ''
if ($LASTEXITCODE -eq 0) {
    Write-Host 'Download completato.'
} else {
    Write-Host 'Download terminato con alcuni errori.'
}
Write-Host "File salvati in: $destinazione"
