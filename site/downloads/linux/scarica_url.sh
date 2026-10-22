#!/usr/bin/env bash

set -u

echo
echo "=========================================="
echo "  SCARICATORE ELENCO URL - MP3 128 kbps"
echo "=========================================="
echo
echo "Incolla tutto l'elenco degli URL, uno per riga."
echo "Quando hai finito, premi CTRL+D su una riga vuota."
echo

# Legge tutte le righe incollate fino a CTRL+D
URLS=()
while IFS= read -r riga || [[ -n "$riga" ]]; do
    riga="${riga%$'\r'}"
    [[ -z "${riga//[[:space:]]/}" ]] && continue
    URLS+=("$riga")
done

if [[ ${#URLS[@]} -eq 0 ]]; then
    echo "Nessun URL inserito."
    exit 0
fi

# Controlla i programmi necessari
for programma in yt-dlp ffmpeg; do
    if ! command -v "$programma" >/dev/null 2>&1; then
        echo "ERRORE: $programma non è installato."
        exit 1
    fi
done

CARTELLA_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DESTINAZIONE="$CARTELLA_SCRIPT/Musica"
mkdir -p "$DESTINAZIONE"

echo
echo "URL da elaborare: ${#URLS[@]}"
echo "Destinazione: $DESTINAZIONE"
echo

RIUSCITI=0
FALLITI=0
INDICE=0

for URL in "${URLS[@]}"; do
    ((INDICE+=1))

    # Accetta righe con URL preceduti da spazi
    URL="${URL#"${URL%%[![:space:]]*}"}"
    URL="${URL%"${URL##*[![:space:]]}"}"

    # Salta righe che non sembrano URL
    if [[ ! "$URL" =~ ^https?:// ]]; then
        echo "Riga ignorata (non è un URL): $URL"
        continue
    fi

    echo
    echo "[$INDICE/${#URLS[@]}] $URL"

    if yt-dlp \
        --no-playlist \
        --ignore-errors \
        --continue \
        -f "ba" \
        -x \
        --audio-format mp3 \
        --audio-quality 128K \
        -P "$DESTINAZIONE" \
        -o "%(title)s [%(id)s].%(ext)s" \
        "$URL"; then
        ((RIUSCITI+=1))
    else
        ((FALLITI+=1))
        echo "Download non riuscito; passo al prossimo."
    fi
done

echo
echo "=========================================="
echo "OPERAZIONE TERMINATA"
echo "=========================================="
echo "Download riusciti: $RIUSCITI"
echo "Download falliti:  $FALLITI"
echo "Cartella: $DESTINAZIONE"
