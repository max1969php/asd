# ASD — Anarchic Streaming Device

Lettore musicale Android **solo locale**: una cartella, i tuoi file, zero abbonamenti.
Niente account, niente cloud, niente pubblicità, e nemmeno il permesso `INTERNET`.

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

Formati accettati (whitelist): MP3, WAV, FLAC, M4A, AAC, OGG, OPUS.

## Stack

| Parte | Scelta |
|---|---|
| Linguaggio | Kotlin 2.0, minSdk 26, target/compile 34 |
| UI | Jetpack Compose + Material 3 (tema "fanzine" personalizzato) |
| Riproduzione | **AndroidX Media3 ExoPlayer** + `MediaSessionService` (open source, Apache 2.0) |
| Catalogo e playlist | Room (SQLite) |
| Accesso ai file | Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) con permesso persistente |
| DI | Manuale (`AppContainer`) |

## Struttura

```
app/src/main/java/com/asd/player/
├── data/
│   ├── database/     Room: entità, SongDao, PlaylistDao
│   ├── scanner/      AudioScanner (visita SAF), MetadataReader (tag)
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

## Fuori dall'MVP

Esportazione M3U, ripristino della coda dopo il riavvio, copertine degli album, FFmpeg per codec esotici.
