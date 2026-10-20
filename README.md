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
- **Setup**: per cambiare cartella, riscansionare e scegliere l'ordinamento predefinito.
- **Due fonti**: la cartella del telefono (SAF) e una cartella di **Google Drive**. La libreria le
  unisce; il filtro TELEFONO / DRIVE compare quando le usi entrambe, e i brani di Drive hanno il simbolo ☁.

### Google Drive

- Si collega un account Google **presente sul telefono** tramite il selettore di sistema di Android.
- In SETUP c'è una **select** con gli account collegati: lì scegli il predefinito ogni volta che vuoi.
  L'ultima voce della select collega un altro account del telefono.
- Ogni account ricorda la sua cartella. Quando cambi account predefinito, i brani Drive in libreria
  diventano quelli del nuovo account (e spariscono dai mixtape quelli del vecchio).
- I file restano su Drive: si leggono i tag e si suonano **in streaming** con lo stesso Media3 ExoPlayer,
  aggiungendo il token OAuth al volo (`DriveStreamResolver`). Scope richiesto: `drive.readonly`.

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
| DI | Manuale (`AppContainer`) |

## Struttura

```
app/src/main/java/com/asd/player/
├── data/
│   ├── database/     Room: entità, SongDao, PlaylistDao
│   ├── scanner/      SourceScanner, AudioScanner (visita SAF), MetadataReader (tag)
│   ├── drive/        DriveAuth (OAuth), DriveClient (REST), DriveSourceScanner, DriveStreamResolver
│   ├── repository/   MusicLibraryRepository, PlaylistRepository
│   └── settings/     SettingsStore (cartella, ordinamento, ultima scansione)
├── domain/           Song, Playlist, LibraryQuery, SearchSongs
├── playback/         PlaybackService (MediaSession), PlaybackController (facciata per la UI)
├── ui/               theme, components, library, playlists, player, settings
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

## Configurare Google Drive (una volta sola)

Su Android non c'è una API key da copiare nel codice: Google riconosce l'app dal **package** e
dallo **SHA-1** del certificato che firma l'APK. Senza questa configurazione, collegare un account
restituisce un errore.

1. Vai su [console.cloud.google.com](https://console.cloud.google.com) e crea un progetto (es. `asd`).
2. In **API e servizi → Libreria**, abilita la **Google Drive API**.
3. In **Schermata consenso OAuth**:
   - tipo **Esterno**, stato **Test**;
   - aggiungi lo scope `.../auth/drive.readonly`;
   - tra gli **utenti di test** aggiungi gli account Google che userai (massimo 100).
4. In **Credenziali → Crea credenziali → ID client OAuth → Android**:
   - package: `com.asd.player`;
   - SHA-1: quello della chiave che firma l'APK. Si ottiene con
     `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey`.
   - Crea **un client per ogni chiave**: la chiave debug di WSL e quella di Android Studio su Windows
     sono diverse, e lo sarà anche quella di release.

`drive.readonly` è uno scope "restricted": in stato Test funziona per gli utenti di test, mostrando
l'avviso "app non verificata". Per distribuire l'app a chiunque serve la verifica di Google.

## Fuori dall'MVP

Esportazione M3U, ripristino della coda dopo il riavvio, copertine degli album, FFmpeg per codec
esotici, cache offline dei brani di Drive.
