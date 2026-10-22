# ASD — Anarchic Streaming Device

Lettore musicale Android per la **tua** musica: una cartella del telefono e/o una cartella del
tuo Google Drive, zero abbonamenti. Niente account ASD, niente pubblicità, niente tracciamento.
La rete viene usata solo per Google Drive, e solo se lo colleghi.

Il ragionamento architetturale di partenza è in [`a.md`](a.md).

## Cosa fa

- **Roba**: la lista dei brani della cartella scelta (anche nelle sottocartelle), con ricerca
  su titolo, artista, album e nome file, filtri per formato e artista e ordinamento
  per titolo, artista, album, durata o data, crescente o decrescente. Tieni premuto un brano
  per metterlo in un mixtape.
- **Mixtape**: le playlist. Puoi crearle, rinominarle, eliminarle (i file audio restano dove sono),
  riordinare i brani, toglierli e riprodurre tutto in ordine o in modalità "Caos" (shuffle).
- **Player**: mini-player sempre visibile e schermata completa con seek, shuffle e repeat.
  La riproduzione continua in background, con i controlli nella notifica, nella lock screen
  e sulle cuffie.
- **Niente cancellazioni**: l'app non elimina mai file, né dal telefono né da Drive. Chi vuole
  togliere un pezzo lo fa dal telefono (file manager) o da Drive; alla riscansione sparisce
  anche dalla lista.
- **Tubo**: incolli un link YouTube e premi invio. Del link si tiene solo la parte prima di `&list`
  (un brano, mai la playlist) e si scarica solo l'audio con **yt-dlp**. Una select **SALVA IN** sceglie
  la destinazione: cartella del telefono o Google Drive (la cartella scelta in SETUP). Il brano compare subito in ROBA.
- **Volume uniforme**: niente salti di volume tra un brano e l'altro. Ogni brano viene misurato
  una volta secondo **EBU R128 / ITU-R BS.1770-4** (lo standard di ReplayGain 2.0 e della
  normalizzazione di Spotify e YouTube) e suonato a **-18 LUFS**, il riferimento ReplayGain.
  La misura gira in sottofondo un brano alla volta (prima quello in riproduzione) e resta salvata;
  i file di Drive si misurano solo su rete non a consumo. Il guadagno passa dal volume del
  player, che può solo abbassare: i brani più bassi di -18 LUFS restano com'erano (niente clipping,
  niente limiter). Si spegne in SETUP.
- **Setup**: per cambiare cartella, riscansionare e scegliere l'ordinamento predefinito.
- **Due fonti**: la cartella del telefono (SAF) e una cartella di **Google Drive**. La libreria le
  unisce; il filtro TELEFONO / DRIVE compare quando le usi entrambe, e i brani di Drive hanno il simbolo ☁.
- **Dropbox** è un'estensione che non fa parte dell'app base: vive nel branch `dropbox`.

### Google Drive

- Si collega un account Google **presente sul telefono** tramite il selettore di sistema di Android.
- In SETUP c'è una **select** con gli account collegati: lì scegli il predefinito ogni volta che vuoi.
  L'ultima voce della select collega un altro account del telefono.
- Ogni account ricorda la sua cartella. Quando cambi account predefinito, i brani Drive in libreria
  diventano quelli del nuovo account (e spariscono dai mixtape quelli del vecchio).
- I file restano su Drive: si leggono i tag e si suonano **in streaming** con lo stesso Media3 ExoPlayer,
  aggiungendo il token OAuth al volo (`DriveStreamResolver`). Scope richiesto: `drive` (vedi sotto).

### Tubo

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
│   ├── drive/        DriveAuth (OAuth), DriveClient (REST), DriveSourceScanner, DriveStreamResolver
│   ├── loudness/     LoudnessMeter (BS.1770), LoudnessAnalyzer (MediaCodec), LoudnessQueue, VolumeLeveling
│   ├── tube/         TubeLink, TubeTrack, TubeDownloader (yt-dlp), TubeImporter (coda → Drive)
│   ├── repository/   MusicLibraryRepository, PlaylistRepository
│   └── settings/     SettingsStore (cartella, ordinamento, ultima scansione)
├── domain/           Song, Playlist, LibraryQuery, SearchSongs
├── playback/         PlaybackService (MediaSession), PlaybackController, CloudStreamResolver
├── ui/               theme, components, library, playlists, player, tube, cloud, settings
├── AppContainer.kt
├── MainActivity.kt
└── MusicApplication.kt
```

La UI non vede mai ExoPlayer: passa da `PlaybackController`, che parla al servizio tramite
`MediaController`. La riscansione è incrementale: i metadati vengono riletti solo per i file
nuovi o cambiati (confronto su dimensione e data di modifica). I file spariti vengono tolti
dal catalogo e, in cascata, dai mixtape che li contenevano.

## Build

Apri il progetto con Android Studio, oppure da terminale:

```bash
./gradlew testDebugUnitTest   # test unitari (ricerca/filtri, whitelist formati)
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Servono JDK 17 e l'Android SDK con la piattaforma 34 (`ANDROID_HOME` oppure `local.properties`).
L'APK include solo le librerie native ARM (`arm64-v8a`, `armeabi-v7a`), quelle dei telefoni veri.
Per un emulatore x86_64 aggiungi `x86_64` ad `abiFilters` in `app/build.gradle.kts` (+15 MB).

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

Esportazione M3U, ripristino della coda dopo il riavvio, copertine degli album, FFmpeg per codec
esotici, cache offline dei brani di Drive.
