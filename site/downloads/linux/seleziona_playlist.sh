#!/usr/bin/env bash

set -o pipefail

echo
echo "=========================================="
echo "  SELETTORE PLAYLIST YOUTUBE"
echo "  Formato MP3 - 128 kbps"
echo "=========================================="
echo

# Verifica programmi necessari
for programma in yt-dlp ffmpeg; do
    if ! command -v "$programma" >/dev/null 2>&1; then
        echo "ERRORE: $programma non è installato."
        echo "Installa i programmi e riprova."
        exit 1
    fi
done

read -r -p "Incolla il link della playlist YouTube: " URL

if [[ -z "$URL" ]]; then
    echo "Non hai inserito un link."
    exit 1
fi

echo
echo "Recupero l'elenco dei brani..."
echo

# Legge la playlist senza scaricare l'audio
if ! ELENCO=$(yt-dlp \
    --flat-playlist \
    --ignore-errors \
    --print '%(playlist_index)s|%(title)s' \
    "$URL"); then
    echo "Attenzione: impossibile recuperare la playlist."
fi

if [[ -z "$ELENCO" ]]; then
    echo "Nessun brano trovato. Controlla il link."
    exit 1
fi

declare -A TITOLI
declare -a NUMERI

while IFS='|' read -r NUMERO TITOLO; do
    [[ "$NUMERO" =~ ^[0-9]+$ ]] || continue
    TITOLI["$NUMERO"]="$TITOLO"
    NUMERI+=("$NUMERO")
done <<< "$ELENCO"

if [[ ${#NUMERI[@]} -eq 0 ]]; then
    echo "Non sono riuscito a leggere i brani."
    exit 1
fi

echo "Brani trovati: ${#NUMERI[@]}"
echo "------------------------------------------"

for NUMERO in "${NUMERI[@]}"; do
    printf "%3s. %s\n" "$NUMERO" "${TITOLI[$NUMERO]}"
done

echo "------------------------------------------"
echo
echo "Esempi di selezione:"
echo "  1,3,7-10  = brani 1, 3 e dal 7 al 10"
echo "  tutti     = intera playlist"
echo

read -r -p "Quali brani vuoi scaricare? " SCELTA

if [[ "$SCELTA" =~ ^(tutti|tutto|all)$ ]]; then
    SELEZIONE=$(IFS=,; echo "${NUMERI[*]}")
else
    SELEZIONE="$SCELTA"
fi

# Convalida numeri e intervalli
if [[ ! "$SELEZIONE" =~ ^[0-9]+([,-][0-9]+)*$ ]]; then
    echo "Selezione non valida."
    exit 1
fi

# Directory Musica accanto allo script
CARTELLA_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DESTINAZIONE="$CARTELLA_SCRIPT/Musica"
mkdir -p "$DESTINAZIONE"

echo
echo "Scarico i brani selezionati in MP3 a 128 kbps..."
echo "Destinazione: $DESTINAZIONE"
echo

yt-dlp \
    --yes-playlist \
    --playlist-items "$SELEZIONE" \
    --ignore-errors \
    --continue \
    -f "ba" \
    -x \
    --audio-format mp3 \
    --audio-quality 128K \
    -P "$DESTINAZIONE" \
    -o '%(playlist_index)03d - %(title)s.%(ext)s' \
    "$URL"

RISULTATO=$?

echo
if [[ $RISULTATO -eq 0 ]]; then
    echo "Download completato."
else
    echo "Download terminato con alcuni errori."
fi

echo "File salvati in: $DESTINAZIONE"
