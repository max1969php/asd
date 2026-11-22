*non est reditus*

# ASD — Anarchic Streaming Device

Lettore musicale Android per la **tua** musica, zero abbonamenti. Niente account ASD, niente
pubblicità, niente tracciamento.

Tre versioni dallo stesso codice (flavor Gradle):

| | **minimal** (`com.asd.player.minimal`, "ASD") | **tube** (`com.asd.player.tube`, "ASD Tubo") | **full** (`com.asd.player`, "ASD+") |
|---|---|---|---|
| Fonti | cartella del telefono | cartella del telefono | cartella del telefono + Google Drive |
| TUBO (yt-dlp) | no | sì: salva solo nel telefono | sì: salva nel telefono **e** su Drive |
| Rete | **nessuna** (niente permesso INTERNET) | solo YouTube | Drive e YouTube |
| Peso | ~3 MB | ~45 MB (universale) | ~45 MB (universale) |
| Dove | sito (`asd.apk`) e Play Store | sito (`asd-tubo.apk`) | solo per uso personale |

La full usa il client OAuth Android del progetto Google del titolare (pacchetto + SHA-1 della
chiave debug) e non va su Play Store: scaricare da YouTube è contro le sue regole.

- **Sito** (istruzioni, guida a yt-dlp, script per PC): https://asd-eight-rust.vercel.app
- **Scarica l'APK**: [ultima Release](https://github.com/max1969php/asd/releases/latest), `asd.apk`
  (minimal) o `asd-tubo.apk` (tube).

In più c'è **ASD Desktop**, il lettore per PC con Ubuntu (`desktop/`, pacchetto `asd-desktop.deb`):
stessa logica dell'app grazie al modulo condiviso `shared/`, vedi [ASD Desktop](#asd-desktop-ubuntu).

Il ragionamento architetturale di partenza è in [`a.md`](a.md).

## Cosa fa

- **Roba**: la cartella scelta come cartelle: senza sottocartelle si vedono solo i file audio,
  altrimenti prima le sottocartelle (toccandone una ci si entra, con la stessa regola, a ogni
  profondità) e poi i file di quel livello; `..`, il percorso in alto o Indietro per risalire.
  Nella full, con brani sia del telefono sia di Drive, alla radice le due fonti sono due cartelle.
  "Tutto" e "Caos" suonano tutto quello che c'è dalla cartella aperta in giù. Ricerca su titolo,
  artista, album e nome file, filtri per formato e artista: con ricerca o filtri attivi niente
  cartelle, i risultati di tutto l'albero sotto la cartella aperta. Ordinamento per titolo,
  artista, album, durata o data, crescente o decrescente. Tieni premuto un brano per metterlo in
  un mixtape: un mixtape può avere brani di cartelle diverse e non cambia quando ci si sposta tra
  le cartelle. Cambiando cartella in SETUP i brani della vecchia escono dai mixtape e i mixtape
  rimasti vuoti spariscono. Alla riapertura ROBA torna nella cartella e nel punto della lista
  dov'eri, e riscansiona da sola la cartella (solo file nuovi, cambiati o spariti; nella full anche
  Drive, se c'è rete e l'ultima scansione ha più di un'ora).
- **Viste** CARTELLE / ARTISTI / ALBUM in ROBA (`BrowseFolder`): artisti → loro album → pezzi
  (i pezzi senza album accanto agli album), album → pezzi; dentro un album l'ordine delle tracce
  dai tag (disco, poi traccia). **DOPPIONI**: filtro che mostra a gruppi i pezzi uguali (stesso
  artista e titolo, o durata ±1 s e dimensione ±2%) con il loro percorso; ASD non cancella, si
  tolgono dal telefono.
- **Nomi puliti** (`SongNames`, solo quello che si vede: i file non si toccano): via i numeri di
  catalogazione in testa (`001 - `, `01. `, `1) `, `(03) `, `CD1-03 `) e le code dei download
  (`(Official Video)`, `[HD]`, `(Lyric Video)`, `(Video ufficiale)`…), e " - Topic" dagli artisti.
  Si toglie solo un numero di traccia (una o due cifre, o con lo zero davanti): "1979",
  "2000 miglia", "883", "99 Luftballons" restano. Senza tag artista e titolo vengono dal nome
  file: con `A - B` l'artista è la parte che torna in altri brani (come artista dei tag o in altri
  nomi file); se nessuna delle due torna vale "Artista - Titolo". Nel database restano i nomi grezzi
  (tag o nome file): la pulizia si rifà a ogni lettura della libreria.
- **Mixtape**: le playlist. Puoi crearle, rinominarle, eliminarle (i file audio restano dove sono),
  riordinare i brani, toglierli e riprodurre tutto in ordine o in modalità "Caos" (shuffle).
- **Player**: mini-player sempre visibile e schermata completa con seek, shuffle e repeat.
  La riproduzione continua in background, con i controlli nella notifica, nella lock screen
  e sulle cuffie. Alla riapertura riparte dall'ultimo ascolto (stessa coda, pezzo e punto, in
  pausa: `PlaybackMemory`, la coda in un file dell'app). Timer di spegnimento (icona luna in alto
  nel player): 15, 30, 60 minuti o a fine pezzo; negli ultimi 10 secondi il volume sfuma, poi pausa.
  Copertina incorporata nel file al posto del vinile (anche in notifica e schermata di blocco, via
  Media3); **INFO** (album, anno, genere, traccia, formato, bitrate, frequenza, dimensione, percorso,
  loudness e correzione) e **TESTO** (solo quello scritto nel file: ID3 USLT, Vorbis LYRICS, M4A
  ©lyr; altrimenti "Mancano testi nel file"), letti con il `MetadataRetriever` di Media3
  (`TrackDetailsReader`). Riproduzione gapless (ExoPlayer, con i dati di encoder delay del file).
- **Silenzio a inizio e fine** (SETUP, acceso di default): l'analisi in sottofondo che misura il
  volume trova anche dove comincia e finisce il suono (soglia −54 dBFS); i silenzi di almeno 0,4 s
  all'inizio e 0,7 s alla fine si saltano con una `ClippingConfiguration`, con un margine; le
  pause dentro il pezzo restano (`SilenceTrim`).
- **Equalizzatore** (SETUP): l'equalizzatore e i bassi di Android sulla sessione audio di ASD,
  preset del telefono o bande a mano; con bande alzate il player abbassa del guadagno aggiunto,
  per non distorcere (`AudioEffects`). Permesso `MODIFY_AUDIO_SETTINGS`, nessuna rete.
- **Lingue**: italiano di base più tutte le altre lingue dell'UE, norvegese, ucraino, cinese
  semplificato, hindi, arabo, farsi, swahili, wolof, hausa, yoruba, igbo, amarico, tigrino e somalo
  (38 in tutto). Si sceglie in SETUP → Lingua
  (o, da Android 13, nelle impostazioni di sistema dell'app). Una nuova lingua è un
  `res/values-<codice>/strings.xml` con le stesse chiavi di `res/values/strings.xml`, più il codice
  in `L10n.SUPPORTED` e in `res/xml/locales_config.xml`.
- **Link**: in SETUP (in cima) e nella schermata iniziale ci sono il sito, aperto nella lingua
  dell'app, e il repo GitHub.
- **Niente cancellazioni**: l'app non elimina mai file, né dal telefono né da Drive. Chi vuole
  togliere un pezzo lo fa dal telefono (file manager) o da Drive; alla riscansione sparisce
  anche dalla lista.
- **Tubo**: incolli un link YouTube e premi invio. Del link si tiene solo la parte prima di `&list`
  (un brano, mai la playlist) e si scarica solo l'audio con **yt-dlp**. Una select **SALVA IN** sceglie
  la destinazione: cartella del telefono o Google Drive (la cartella scelta in SETUP). Il brano compare subito in ROBA.
- **Volume uniforme**: niente salti di volume tra un brano e l'altro. Ogni brano viene misurato
  una volta secondo **EBU R128 / ITU-R BS.1770-4** e suonato a **-14 LUFS**, lo stesso livello di
  Spotify (normale), YouTube e Amazon Music: allo stesso volume del telefono ASD suona forte quanto loro.
  La misura gira in sottofondo un brano alla volta (prima quello in riproduzione) e resta salvata;
  finché un brano non è misurato vale la mediana della libreria. I file di Drive si misurano solo su
  rete non a consumo. I pezzi forti si abbassano con il volume del player, quelli deboli si alzano
  (al massimo +12 dB) con il `LoudnessEnhancer` di Android sulla sessione di ASD, che ha un
  limitatore: niente distorsione (`VolumeLeveling`, `AudioEffects.setBoost`). Sui telefoni senza
  LoudnessEnhancer i pezzi deboli restano come sono. Si spegne in SETUP.
- **Setup**: per cambiare cartella, riscansionare e scegliere l'ordinamento predefinito.
- **Due fonti**: la cartella del telefono (SAF) e una cartella di **Google Drive**. La libreria le
  unisce; il filtro TELEFONO / DRIVE compare quando le usi entrambe, e i brani di Drive hanno il simbolo ☁.
- **Dropbox** è un'estensione che non fa parte dell'app base: vive nel branch `dropbox`.

### Google Drive (solo full)

- Si collega un account Google **presente sul telefono** tramite il selettore di sistema di Android.
- In SETUP c'è una **select** con gli account collegati: lì scegli il predefinito ogni volta che vuoi.
  L'ultima voce della select collega un altro account del telefono.
- Ogni account ricorda la sua cartella. Quando cambi account predefinito, i brani Drive in libreria
  diventano quelli del nuovo account (e spariscono dai mixtape quelli del vecchio).
- I file restano su Drive: si leggono i tag e si suonano **in streaming** con lo stesso Media3 ExoPlayer,
  aggiungendo il token OAuth al volo (`DriveStreamResolver`). Scope richiesto: `drive` (vedi sotto).

### Tubo (tube e full)

- yt-dlp gira sul telefono grazie a [youtubedl-android](https://github.com/JunkFood02/youtubedl-android)
  (GPL-3.0), che impacchetta Python, yt-dlp e QuickJS (serve a YouTube). Niente server di mezzo.
- Si scarica la traccia audio già pronta (`bestaudio[ext=m4a]`, cioè AAC in M4A): nessuna
  riconversione e nessun FFmpeg, così l'APK resta più leggero. Il file si chiama
  "Artista - Titolo.m4a"; titolo e artista vengono dai tag di YouTube Music o dal titolo del video.
  Il file non ha tag: nelle riscansioni ASD ricava artista e titolo dal nome "Artista - Titolo".
- Per salvare nella cartella del telefono serve il permesso di scrittura: le cartelle scelte con
  le versioni precedenti hanno solo la lettura, va riscelta in SETUP una volta.
- YouTube cambia spesso: yt-dlp si aggiorna da solo al massimo una volta al giorno, e nella scheda
  c'è il bottone **Aggiorna yt-dlp**.
- La coda va avanti finché l'app è viva (un brano alla volta). Se Android chiude l'app, i download
  non finiti vanno rilanciati.
- **"Conferma di non essere un bot"**: YouTube a volte blocca le richieste anonime di una rete
  (succede anche a yt-dlp sul PC). Nella scheda, sezione YOUTUBE: **Accedi a YouTube** apre la pagina
  di accesso di Google dentro ASD e al tocco su FATTO ne salva i cookie per yt-dlp. Prima di ogni
  download i cookie vengono riletti, perché YouTube li fa ruotare. In alternativa
  **Importa cookies.txt** (formato Netscape, es. esportato dal PC con un'estensione del browser).
  Meglio usare un account secondario. **Esci** cancella i cookie.
- Scarica solo roba che hai il diritto di scaricare: i termini di YouTube lo vietano per il resto.

Formati accettati (whitelist): MP3, WAV, FLAC, M4A, AAC, OGG, OPUS.

## Stack

| Parte | Scelta |
|---|---|
| Linguaggio | Kotlin 2.0, minSdk 26, target/compile 34 |
| UI | Jetpack Compose + Material 3 (tema "fanzine" personalizzato) |
| Riproduzione | **AndroidX Media3 ExoPlayer** + `MediaSessionService` (open source, Apache 2.0) |
| Catalogo e playlist | Room (SQLite) |
| Accesso ai file | Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) con permesso persistente |
| Google Drive | Drive REST API v3 + Google Identity `AuthorizationClient` (play-services-auth) |
| YouTube | `youtubedl-android` 0.18.1 (yt-dlp + Python + QuickJS sul telefono) |
| DI | Manuale (`AppContainer`) |

## Struttura

```
app/src/main/java/com/asd/player/
├── data/
│   ├── database/     Room: entità, SongDao, PlaylistDao
│   ├── scanner/      SourceScanner, AudioScanner (visita SAF), MetadataReader (tag)
│   ├── drive/        DriveAuth (OAuth), DriveClient (REST), DriveSourceScanner
│   ├── loudness/     LoudnessMeter (BS.1770), LoudnessAnalyzer (MediaCodec), LoudnessQueue,
│   │                 VolumeLeveling (guadagno del volume uniforme), SilenceTrim
│   ├── tube/         TubeLink, TubeTrack, TubeCookies, TubeImporter (coda: YouTube → telefono/Drive,
│   │                 Drive → telefono); TubeDownloader sta in src/ytdlp (stub in src/minimal)
│   ├── repository/   MusicLibraryRepository, PlaylistRepository
│   └── settings/     SettingsStore (cartella, ordinamento, Drive, volume, silenzio, equalizzatore…)
├── domain/           Song, Playlist, LibraryQuery; usecase: SearchSongs, BrowseFolder, SongNames, Duplicates
├── playback/         PlaybackService (MediaSession), PlaybackController, CloudStreamResolver (Drive),
│                     MediaItems, PlaybackMemory (ultimo ascolto), SleepTimer, AudioEffects
│                     (equalizzatore, bassi, guadagno), TrackDetailsReader (copertina, info, testo)
├── ui/               theme, components, library, playlists, player, tube, cloud, settings
├── AppContainer.kt
├── L10n.kt           lingua dell'app (LocaleManager da Android 13, pref prima)
├── MainActivity.kt
└── MusicApplication.kt

shared/src/main/kotlin/com/asd/player/   logica senza Android, usata da app e desktop:
├── domain/           Song, Playlist, LibraryQuery; usecase: SearchSongs, BrowseFolder, SongNames, Duplicates
└── data/             loudness: LoudnessMeter, VolumeLeveling, SilenceTrim; tube: TubeLink, TubeTrack

desktop/              ASD Desktop (vedi "ASD Desktop" più sotto)
site/                 sito statico su Vercel (vedi "Sito" più sotto e site/README.md)
```

I pacchetti non cambiano spostandosi in `shared/` (`com.asd.player.domain…`), quindi gli import
dell'app sono gli stessi. `shared` è un modulo Kotlin JVM puro: niente classi Android, `org.json`
solo `compileOnly` (su Android c'è già nel sistema).

Nell'app la UI non vede mai ExoPlayer: passa da `PlaybackController`, che parla al servizio tramite
`MediaController`. La riscansione è incrementale: i metadati vengono riletti solo per i file
nuovi o cambiati (confronto su dimensione e data di modifica). I file spariti vengono tolti
dal catalogo e, in cascata, dai mixtape che li contenevano.

## Build

Apri il progetto con Android Studio, oppure da terminale:

```bash
./gradlew testFullDebugUnitTest testTubeDebugUnitTest testMinimalDebugUnitTest  # test unitari
./gradlew assembleFullDebug                     # app/build/outputs/apk/full/debug/
./gradlew assembleMinimalRelease                # app/build/outputs/apk/minimal/release/
./gradlew assembleTubeRelease                   # app/build/outputs/apk/tube/release/
./gradlew assembleFullRelease                   # app/build/outputs/apk/full/release/
./gradlew bundleMinimalRelease                             # AAB per Play Store
```

La release è minificata e firmata con la chiave debug (così l'impronta SHA-1 registrata per
Google Drive resta la stessa): si installa sopra le versioni precedenti.

Il codice comune sta in `src/main`. Le differenze:
- `src/ytdlp`: `TubeDownloader` vero, condiviso da full e tube (youtubedl-android è una dipendenza
  solo `fullImplementation`/`tubeImplementation`); queste due hanno R8 senza shrink né
  offuscamento (`proguard-ytdlp.pro`), altrimenti yt-dlp va in crash;
- `src/full`, `src/tube`: il permesso INTERNET e il nome ("ASD+", "ASD Tubo");
- `src/minimal`, `src/tube`: i testi senza Drive (intro, manifesto, volume, libreria vuota),
  generati dalle traduzioni esistenti più qualche frase nuova; la minimal ha anche uno stub di
  `TubeDownloader`;
- `BuildConfig.DRIVE` nasconde la sezione Drive di SETUP, il bottone Drive del primo avvio e le
  destinazioni Drive del TUBO (la tube salva solo nel telefono); `BuildConfig.TUBE` nasconde la
  scheda TUBO nella minimal.

Servono JDK 17 e l'Android SDK con la piattaforma 34 (`ANDROID_HOME` oppure `local.properties`);
sul PC di sviluppo stanno in `~/.local/share/asd-build-tools` (vedi `docs/DEPLOY.md`).
Full e tube hanno solo le librerie native ARM (`arm64-v8a`, `armeabi-v7a`), quelle dei telefoni veri:
un APK per CPU più uno universale con entrambe. L'universale è il download principale perché alcuni
telefoni con CPU a 64 bit hanno Android a 32 bit (es. Galaxy A13 SM-A137F) e rifiutano l'APK arm64
come "non compatibile". La build di debug tiene anche x86/x86_64, per gli emulatori.

## Rilasciare una versione

1. Alza `versionCode` e `versionName` in `app/build.gradle.kts`, poi
   `./gradlew assembleMinimalRelease`, poi `assembleTubeRelease` (uno alla volta: insieme possono
   finire la memoria).
2. Crea una Release su GitHub con tag `v<versionName>` e allega minimal e tube universali, con i
   nomi esatti `asd.apk` e `asd-tubo.apk` (la full resta privata):

   ```bash
   cp app/build/outputs/apk/minimal/release/app-minimal-universal-release.apk asd.apk
   cp app/build/outputs/apk/tube/release/app-tube-universal-release.apk asd-tubo.apk
   gh release create v0.5.0 --target main --title "ASD 0.5.0" --notes "..." asd.apk asd-tubo.apk
   ```

3. Allega anche ASD Desktop, con il nome esatto `asd-desktop.deb`:

   ```bash
   ./gradlew :desktop:packageAsdDeb
   cp desktop/build/deb/asd-desktop_*_amd64.deb asd-desktop.deb
   gh release upload v<versionName> asd-desktop.deb
   ```

Il sito punta a `releases/latest/download/asd.apk`, `.../asd-tubo.apk` e `.../asd-desktop.deb`:
prende sempre l'ultima Release, il sito non va toccato. **Ogni Release deve avere tutti e tre
gli asset**, altrimenti il bottone che manca dà errore 404.

Prima di rilasciare APK compilati su una macchina nuova, controlla la firma (vedi `docs/DEPLOY.md`):
con una chiave debug diversa non si installano sopra le versioni precedenti e Drive non va.

## ASD Desktop (Ubuntu)

Lettore per PC Linux, versione `0.1.0` (`desktopVersion` in `desktop/build.gradle.kts`). Kotlin con
Compose Desktop: tema e componenti (`ui/theme`, `ui/components`, tranne `AsdLinks`) sono compilati
direttamente dalla cartella dell'app, la logica viene da `shared/`.

- **Cosa fa (fase 2)**: SETUP per scegliere la cartella della musica (selettore di GNOME con
  zenity, altrimenti quello di Java) e riscansionare; ROBA per cartelle, artisti e album, con
  ricerca, ordinamento, Tutto e Caos, nomi puliti come nell'app; player in fondo alla finestra con
  coda, shuffle, repeat, avanzamento, riproduzione gapless; alla riapertura la stessa cartella, lo
  stesso punto della lista e l'ultimo ascolto in pausa. Riscansione incrementale a ogni avvio.
- **Non ancora**: volume uniforme, silenzio saltato, tasti multimediali/MPRIS (fase 3); mixtape,
  doppioni, info/copertina/testo, timer, altre lingue (fase 4); TUBO (fase 5). Vedi `docs/ROADMAP.md`.
- **Riproduzione**: GStreamer (`playbin`, via `gst1-java-core`), quello di sistema. Gli M4A/AAC
  (anche quelli del TUBO) vogliono `gstreamer1.0-libav`: il `.deb` lo dichiara tra le dipendenze,
  e se manca SETUP lo dice.
- **Tag**: jaudiotagger (MP3, FLAC, OGG, M4A, WAV); senza tag il titolo è il nome del file.
- **Dove salva** (regole XDG): impostazioni in `~/.config/asd-desktop/settings.json`, catalogo
  in `~/.local/share/asd-desktop/library.json`, ultimo ascolto in `playback.json` nella stessa
  cartella. Come l'app, non scrive e non cancella mai nella cartella della musica.
- **Testi**: per ora solo italiano, in `desktop/.../Texts.kt`, con accanto la chiave di
  `strings.xml` corrispondente (le lingue arriveranno riusando quei file).

```
desktop/
├── build.gradle.kts            versione, moduli Java, task packageAsdDeb
├── packaging/make-deb.sh       costruisce il .deb con dpkg-deb (dipendenze GStreamer, niente fakeroot)
└── src/main/kotlin/com/asd/desktop/
    ├── Main.kt                 finestra
    ├── Desktop.kt              collega impostazioni, catalogo, player, ultimo ascolto
    ├── Library.kt              scansione incrementale e catalogo JSON
    ├── TagReader.kt            tag con jaudiotagger
    ├── Player.kt               GStreamer playbin: coda, shuffle, repeat, gapless
    ├── PlaybackMemory.kt       ultimo ascolto
    ├── Settings.kt, Paths.kt   impostazioni e cartelle XDG
    ├── FolderChooser.kt        zenity o JFileChooser
    ├── Texts.kt                testi
    └── ui/                     App, LibraryScreen (ROBA), PlayerBar, SettingsScreen (SETUP)
```

```bash
./gradlew :shared:test :desktop:test      # test (PlayerTest suona due WAV di silenzio con GStreamer vero)
./gradlew :desktop:run                    # avvia senza installare
./gradlew :desktop:packageAsdDeb          # desktop/build/deb/asd-desktop_<versione>_amd64.deb
sudo apt install ./desktop/build/deb/asd-desktop_0.1.0_amd64.deb
```

Il pacchetto (~90 MB) contiene un Java 17 ridotto (jlink): non serve Java installato. ProGuard è
spento (`buildTypes.release.proguard`): GStreamer-Java e JNA vivono di riflessione. Si installa in
`/opt/asd-desktop`, con `asd-desktop` in `/usr/bin` e la voce nel menu delle applicazioni.

## Sito

`site/` è una pagina statica, senza build: istruzioni di ASD, guida a yt-dlp per scaricare solo
l'audio, script per Linux e Windows. È un progetto Vercel collegato a questo repo con
**Root Directory `site`**: ogni push su `main` aggiorna il sito (gli altri branch producono
anteprime). Testi, lingue e script sono spiegati in [`site/README.md`](site/README.md).

## Configurare Google Drive (una volta sola)

Su Android non c'è una API key da copiare nel codice: Google riconosce l'app dal **package** e
dallo **SHA-1** del certificato che firma l'APK. Senza questa configurazione, collegare un account
restituisce un errore.

La console chiama questa parte **Google Auth Platform** (ex "Schermata consenso OAuth").

1. Vai su [console.cloud.google.com](https://console.cloud.google.com) e crea un progetto (es. `asd`).
2. In **API e servizi → Libreria**, abilita la **Google Drive API**.
3. Apri **Google Auth Platform → Inizia** e compila i passaggi:
   - **Informazioni sull'app**: nome applicazione `ASD` (è quello che compare nella schermata di
     consenso) ed email di assistenza (la tua);
   - **Pubblico**: **Esterno**. L'app nasce in stato **Test**: non premere "Pubblica app";
   - **Dati di contatto**: la tua email;
   - accetta le norme e premi **Crea**.

   Logo, dominio e privacy policy si possono lasciare vuoti: in stato Test non servono.
4. Nel menu di Google Auth Platform:
   - **Pubblico → Utenti di test → + Add users**: aggiungi gli account Google che userai sul
     telefono (massimo 100). Gli altri account vengono rifiutati;
   - **Accesso ai dati → Aggiungi o rimuovi ambiti**: seleziona `.../auth/drive` (vedere, modificare,
     creare ed eliminare i file di Drive) e salva. Serve la scrittura per caricare i brani del TUBO
     (l'app non cancella mai niente: lo scope è largo solo perché Google non ne offre uno
     "scrivi ma non cancellare"):
     `drive.file` non permette di scrivere in una cartella creata da te fuori dall'app.
     Se non compare, la Drive API del punto 2 non è ancora abilitata.
5. **Client → + Crea client → Tipo di applicazione: Android**:
   - nome del pacchetto: `com.asd.player`;
   - impronta certificato SHA-1: quella della chiave che firma l'APK. Si ottiene con
     `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey`.
   - Crea **un client per ogni chiave**: la chiave debug di WSL e quella di Android Studio su Windows
     sono diverse, e lo sarà anche quella di release.

Non c'è nulla da copiare nel codice: nessun client ID, nessun file `google-services.json`.
Le modifiche in console possono richiedere qualche minuto prima di valere.

`drive` è uno scope "restricted": in stato Test funziona per gli utenti di test, mostrando
l'avviso "app non verificata". Per distribuire l'app a chiunque serve la verifica di Google.

## Fuori dall'MVP

FFmpeg per codec esotici, cache offline dei brani di Drive. Scartate (vedi `docs/ROADMAP.md`):
mixtape in `.m3u`, copertine nella lista, crossfade. Fatte nella 0.7.0: ripresa dell'ultimo
ascolto e copertina nel player.
