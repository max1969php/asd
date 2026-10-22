# Versione Windows di scarica_url.sh: scarica l'audio di un elenco di link in MP3 a 128 kbps,
# nella cartella Musica accanto allo script. Si avvia con scarica_url.bat.
# Uso alternativo: scarica_url.bat elenco.txt (un link per riga).
param([string]$Elenco)

Write-Host ''
Write-Host '=========================================='
Write-Host '  SCARICATORE ELENCO URL - MP3 128 kbps'
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

if ($Elenco) {
    $righe = Get-Content -LiteralPath $Elenco -Encoding UTF8
} else {
    Write-Host "Incolla tutto l'elenco degli URL, uno per riga."
    Write-Host 'Quando hai finito, premi INVIO su una riga vuota.'
    Write-Host ''
    $righe = @()
    while ($true) {
        $riga = Read-Host
        if ([string]::IsNullOrWhiteSpace($riga)) { break }
        $righe += $riga
    }
}

$urls = @($righe | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($urls.Count -eq 0) {
    Write-Host 'Nessun URL inserito.'
    exit 0
}

Write-Host ''
Write-Host "URL da elaborare: $($urls.Count)"
Write-Host "Destinazione: $destinazione"
Write-Host ''

$riusciti = 0
$falliti = 0
$indice = 0

foreach ($url in $urls) {
    $indice++
    # Salta righe che non sembrano URL
    if ($url -notmatch '^https?://') {
        Write-Host "Riga ignorata (non è un URL): $url"
        continue
    }

    Write-Host ''
    Write-Host "[$indice/$($urls.Count)] $url"

    & $ytdlp @opzioniFfmpeg `
        --no-playlist `
        --ignore-errors `
        --continue `
        -f 'ba' `
        -x `
        --audio-format mp3 `
        --audio-quality 128K `
        -P $destinazione `
        -o '%(title)s [%(id)s].%(ext)s' `
        $url

    if ($LASTEXITCODE -eq 0) {
        $riusciti++
    } else {
        $falliti++
        Write-Host 'Download non riuscito; passo al prossimo.'
    }
}

Write-Host ''
Write-Host '=========================================='
Write-Host 'OPERAZIONE TERMINATA'
Write-Host '=========================================='
Write-Host "Download riusciti: $riusciti"
Write-Host "Download falliti:  $falliti"
Write-Host "Cartella: $destinazione"
