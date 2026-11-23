*non est reditus*

# ROADMAP ASD

Funzioni decise il 2026-11-07 (elenco di proposte con pro e contro, scelte del titolare). Da fare un
pezzo alla volta, nell'ordine delle fasi. Valgono per tutte e tre le versioni (minimal, tube, full)
salvo dove indicato. Regole fisse: ASD non cancella né rinomina mai i file dell'utente; la minimal
resta senza rete; ogni testo nuovo dell'app va in `values/strings.xml` e poi tradotto (vedi
TRADUZIONI.md), compresi i testi della minimal e della tube generati da `minimal_strings.py`.

Stato: `[ ]` da fare, `[~]` in corso, `[x]` fatto (con versione).

## Fase 1 — piccole, subito utili (fatta nella 0.7.0, 2026-11-07)

- [x] 0.7.0 **Titoli puliti** (proposta 1). Solo nel nome mostrato, mai sul file (`SongNames`).
  Via i prefissi di catalogazione (`001 - `, `01. `, `1) `, `(03) `, `CD1-03 `, `1-03 `, `07_`),
  le code dei download (`(Official Video)`, `[Official Music Video HD]`, `(Lyric Video)`,
  `(Video ufficiale)`, `(Audio)`, `[HD]`…) e " - Topic" dagli artisti; i trattini bassi diventano
  spazi se nel nome non ce ne sono. Il numero rosso a sinistra resta. Niente interruttore.
  - **Regola sui numeri, più larga di quella decisa**: si toglie solo un numero di traccia (una o
    due cifre, o con lo zero davanti). Restano quindi gli anni 1950–2050 come deciso, ma anche
    gli altri numeri di 3–4 cifre: "883 - Gli anni" con la regola 1950–2050 avrebbe perso il nome
    del gruppo. Restano anche "99 Luftballons" (numero senza separatore e senza zero), "2-4-6-8
    Motorway", "1.5 Grad". Non fatti: i prefissi di lato vinile (`A1 `), troppo ambigui.
- [x] 0.7.0 **Artista e titolo dal nome file** (proposta 3). Con `A - B` (dopo la pulizia):
  con un artista nei tag vince il tag (il titolo è l'altra parte); altrimenti si contano le volte
  in cui A e B compaiono nella libreria (come artista dei tag o come parte di un altro nome file):
  l'artista è quella che torna di più; a pari merito "Artista - Titolo". Si divide al primo
  ` - ` (anche – e —). Nel database ora restano i nomi grezzi; i brani divisi dalle versioni fino
  alla 0.6 ("$artista - $titolo" = nome file) si riconoscono e si rifanno senza riscansione.
- [x] 0.7.0 **Cartella aperta e posizione nella lista** ricordate (proposta 10), anche per ogni
  cartella visitata nella sessione: risalendo si ritrova il punto.
- [x] 0.7.0 **Ultimo ascolto** (proposta 11): coda in `files/last_queue.txt`, pezzo, punto,
  shuffle e repeat nelle preferenze, salvati a ogni cambio pezzo/pausa/salto, ogni 15 s mentre
  suona e all'uscita. Alla riapertura il player si rimette lì in pausa, senza preparare (niente
  notifica finché non si preme play); i pezzi spariti si saltano.
- [x] 0.7.0 **Timer di spegnimento** (proposta 17): icona luna in alto nel player, 15/30/60 min o
  "a fine pezzo" (`pauseAtEndOfMediaItems`); sfuma negli ultimi 10 s, poi pausa. Vive solo in
  memoria: chiudendo l'app del tutto si perde.

## Fase 2 — player più ricco (fatta nella 0.7.0, 2026-11-08)

- [x] 0.7.0 **Copertina** (proposta 5) al posto del vinile nel player, letta solo per il pezzo che
  suona (`TrackDetailsReader`, `MetadataRetriever` di Media3, ridotta a ~1024 px). In notifica e
  schermata di blocco la mette Media3 dai metadati del file. Senza copertina resta il vinile.
- [x] 0.7.0 **Pannello INFO** (proposta 7): album, anno, genere, traccia (n/tot), formato, bitrate
  (dal file o da dimensione/durata), frequenza e canali, dimensione, percorso, LUFS misurati,
  correzione del volume uniforme in dB.
- [x] 0.7.0 **TESTO** (proposta 8): ID3 USLT (decodificato a mano: è un frame binario per
  ExoPlayer), Vorbis LYRICS/UNSYNCEDLYRICS, M4A ©lyr. Altrimenti "Mancano testi nel file".

## Fase 3 — suono (fatta nella 0.7.0, 2026-11-08)

- [x] 0.7.0 **Gapless**: niente codice, lo fa ExoPlayer tra gli elementi della coda (encoder
  delay/padding LAME e iTunSMPB). Da verificare a orecchio sul telefono con un album live.
- [x] 0.7.0 **Silenzio a inizio e fine** saltato (proposta 16): l'analisi del volume
  (`LoudnessAnalyzer`) trova primo e ultimo campione sopra −54 dBFS; `SilenceTrim` salta silenzi
  di almeno 0,4 s all'inizio (margine 150 ms) e 0,7 s alla fine (margine 400 ms), con
  `ClippingConfiguration`. Interruttore in SETUP, acceso di default; l'analisi gira se è acceso
  il volume uniforme o il salto del silenzio. DB v6 (`soundStartMs`, `soundEndMs`, `analysis`):
  tutti i pezzi già misurati vengono rianalizzati una volta in sottofondo.
- [x] 0.7.0 **Equalizzatore e bassi** (proposta 18): `AudioEffects` sulla sessione audio di ASD
  (generata dall'app all'avvio, così le bande si vedono anche senza musica). Preset del telefono
  (nomi del telefono, di solito in inglese), bande a mano, bassi. Il player abbassa del massimo
  guadagno aggiunto (bande + ~10 dB per i bassi al massimo) per non distorcere con il volume
  uniforme. Se il telefono non ha l'equalizzatore la sezione lo dice.

## Fase 4 — libreria (fatta nella 0.7.0, 2026-11-08)

- [x] 0.7.0 **Viste Artisti e Album** (proposta 19): CARTELLE / ARTISTI / ALBUM in ROBA, stessa
  navigazione delle cartelle. Artista → album → pezzi; i pezzi senza album stanno accanto agli
  album (in ALBUM alla radice). Nomi uguali a meno delle maiuscole uniti. Dentro un album ordine
  per disco e traccia (tag nuovi `trackNumber`, `discNumber`: `tagsVersion` 1, i tag si rileggono
  una volta conservando l'analisi audio). La vista scelta si ricorda.
- [x] 0.7.0 **Riscansione all'apertura** (proposta 20): la cartella del telefono a ogni apertura
  (solo i cambiamenti, la lista cambia una volta a fine scansione); Drive (full) se c'è rete e
  l'ultima scansione ha più di un'ora.
- [x] 0.7.0 **Doppioni** (proposta 21): filtro DOPPIONI in ROBA (`Duplicates`): stesso artista e
  titolo puliti (senza accenti e maiuscole), oppure durata ±1 s e dimensione ±2%. Mostrati a gruppi
  con il percorso al posto di artista/album. Nessuna cancellazione.

## Correzioni dopo la 0.7.0

- [x] **Volume uniforme a -14 LUFS, anche verso l'alto** (2026-11-22). Con -18 LUFS e solo
  attenuazione, una libreria da YouTube (master forti e molto diversi) suonava molto più bassa di
  Spotify (volume 9 contro 5) e i pezzi sotto -18 restavano bassi. Ora obiettivo -14 LUFS (Spotify
  normale, YouTube, Amazon), attenuazione con il volume del player e guadagno fino a +12 dB con
  `LoudnessEnhancer` (limitatore incluso). Le misure già salvate valgono: nessuna rianalisi.
  Da provare a orecchio sul telefono.
- [x] 0.8.0 **Android Auto e comandi vocali** (2026-11-22). In auto si vedevano solo pausa/riprendi
  e ±10 s, e "metti una canzone con ASD" non andava: ASD esponeva solo la sessione. Ora
  `MediaLibraryService` con libreria sfogliabile (ROBA, Artisti, Album, Mixtape, Tutto/Caos),
  ricerca, richieste vocali (`VoiceSearch`) e ripresa dell'ultimo ascolto (`onPlaybackResumption`),
  più `MEDIA_PLAY_FROM_SEARCH` per l'assistente sul telefono. Provato con test e build; da provare
  in macchina dal titolare.

## ASD Desktop per Ubuntu (deciso il 2026-11-22)

Nome "ASD Desktop", `.deb` sul sito insieme agli APK, con il TUBO (fase 5); equalizzatore dopo.
Kotlin + Compose Desktop nello stesso repo, GStreamer per suonare, logica comune in `shared/`.

- [x] desktop 0.1.0 **Fase 1 — modulo condiviso**: dominio, misura del volume, SilenceTrim, TubeLink
  in `shared/` (con i loro test), usato da app e desktop; app Android invariata.
- [x] desktop 0.1.0 **Fase 2 — desktop minimo**: cartella, ROBA (cartelle/artisti/album, ricerca,
  ordinamento, Tutto/Caos), player con coda, shuffle, repeat, gapless, ultimo ascolto, posizione
  nella lista ricordata, riscansione all'avvio, `.deb`.
- [x] desktop 0.2.0 **Fase 3 — suono** (2026-11-22): volume uniforme a −14 LUFS (decodifica
  GStreamer → `LoudnessMeter`; filtro `volume ! rglimiter` in playbin, guadagno cambiato all'evento
  di inizio pezzo, limitatore solo quando si alza), silenzio a inizio e fine saltato, MPRIS
  (controlli multimediali di GNOME, tasti). Interruttori in SETUP, accesi di default. Da provare a
  orecchio dal titolare.
- [x] desktop 0.3.0 **Fase 4 — il resto** (2026-11-22): mixtape, doppioni, filtro per formato,
  info/copertina/testo, timer, le 38 lingue (gli `strings.xml` dell'app nel jar, più le chiavi
  `desktop_*`). Il filtro per artista non serve: c'è la vista Artisti. Copertina solo dai tag
  (niente cover.jpg, scartato il 2026-11-07).
- [x] desktop 0.3.0 **Fase 5 — TUBO** (2026-11-22) con lo yt-dlp di sistema: m4a in
  `<cartella>/TUBO`, aggiornamento e cookie dal browser in SETUP. Provato con un finto yt-dlp;
  da provare con quello vero (qui non installato).
- [x] desktop 0.4.0 (2026-11-23): albero delle cartelle a sinistra in ROBA e barre di scorrimento
  su tutti gli elenchi. Solo desktop, chiesto dal titolare.
- [ ] Testi solo desktop del TUBO (`desktop_ytdlp_*`, `desktop_yt_*`, `desktop_tube_saves_to`,
  `desktop_missing_aac`) solo in italiano e inglese: le altre lingue li vedono in inglese.
- [ ] Dopo: equalizzatore (`equalizer-10bands`). Più leggero il `.deb`: le icone Material estese
  pesano 37 MB per otto icone usate.

## Scartate (2026-11-07)

2 interruttore "titoli puliti" · 4 copertine nella lista · 6 copertina dalla cartella ·
9 tag/copertina negli m4a del TUBO · 12 cartella intera nel mixtape · 13 coda "suona dopo" ·
14 lettera veloce A-Z · 15 ordinamento per traccia/nome file ·
crossfade (numero della proposta da ricontrollare: il 16 è il silenzio, fase 3) · 22 più ascoltati / recenti ·
loop di cartella/mixtape (c'è già repeat nel player) · 23 mixtape in `.m3u` (sembra social).
