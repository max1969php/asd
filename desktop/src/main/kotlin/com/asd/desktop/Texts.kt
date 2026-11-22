package com.asd.desktop

/**
 * I testi di ASD Desktop, in italiano. Dove esiste, il testo è lo stesso della chiave indicata in
 * app/src/main/res/values/strings.xml: le altre lingue arriveranno riusando quei file (fase 4).
 */
object Texts {
    const val APP = "ASD Desktop"
    const val TAB_LIBRARY = "Roba" // tab_library
    const val TAB_SETTINGS = "Setup" // tab_settings
    const val PLAY_ALL = "Tutto" // play_all
    const val CHAOS = "Caos" // shuffle_button
    const val SEARCH_HINT = "titolo, artista, album, file…" // search_hint
    const val SORT_TITLE = "Titolo" // sort_title
    const val SORT_ARTIST = "Artista" // sort_artist
    const val SORT_ALBUM = "Album" // sort_album
    const val SORT_DURATION = "Durata" // sort_duration
    const val SORT_RECENT = "Recenti" // sort_recent
    const val VIEW_FOLDERS = "Cartelle" // view_folders
    const val VIEW_ARTISTS = "Artisti" // view_artists
    const val VIEW_ALBUMS = "Album" // view_albums
    const val UNKNOWN_ARTIST = "Anonimo" // unknown_artist
    const val LIBRARY_EMPTY = "CARTELLA VUOTA." // library_empty
    const val LIBRARY_EMPTY_HINT = "Buttaci dentro qualche MP3, WAV, FLAC o M4A e premi Riscansiona in SETUP."
    const val CHANGE_FOLDER = "Cambia cartella" // change_folder
    const val CHOOSE_FOLDER = "Scegli la cartella della musica"
    const val NO_FOLDER = "Nessuna cartella scelta."
    const val NO_FOLDER_HINT = "Scegli la cartella con la tua musica: ASD la legge e basta, non scrive e non cancella niente."
    const val RESCAN = "Riscansiona" // rescan
    const val FOLDER = "Cartella" // folder
    const val SCAN_LISTING = "FRUGANDO NELLA CARTELLA…"
    const val NOTHING_PLAYING = "NIENTE IN CODA"
    const val SITE = "Sito"
    const val ON = "Acceso" // on
    const val OFF = "Spento" // off
    const val VOLUME_TITLE = "Volume uniforme" // volume_title
    const val VOLUME_HINT =
        "Ogni pezzo viene misurato una volta (EBU R128) e suonato a -14 LUFS, lo stesso livello di Spotify e YouTube: " +
            "niente più salti di volume tra un MP3 e l'altro. I pezzi forti vengono abbassati e quelli deboli alzati, " +
            "con un limitatore che non fa distorcere." // volume_hint (senza la frase su Drive)
    const val SILENCE_TITLE = "Silenzio a inizio e fine" // silence_title
    const val SILENCE_HINT =
        "Salta i secondi muti all'inizio e alla fine dei pezzi; le pause dentro il pezzo restano. " +
            "Si misura in sottofondo insieme al volume." // silence_hint, senza "vale dal prossimo pezzo" (qui vale subito)
    const val ALL_MEASURED = "tutti i pezzi misurati" // volume_all_measured
    const val VERSION = "Versione"
    const val MISSING_AAC =
        "Per gli M4A/AAC (anche quelli del TUBO) serve il decoder di GStreamer: sudo apt install gstreamer1.0-libav"

    fun scanIndexing(done: Int, total: Int) = "INDICIZZO $done/$total…" // scan_indexing
    fun scanSummary(added: Int, changed: Int, removed: Int) = "+$added nuovi · ~$changed cambiati · -$removed spariti" // scan_summary
    fun scanError(message: String) = "ERRORE: $message"
    fun folderMissing(folder: String) = "La cartella $folder non c'è più. Scegline un'altra in SETUP."
    fun measuring(n: Int) = "misuro il volume: ne mancano $n" // volume_measuring
    fun songs(n: Int) = if (n == 1) "1 pezzo" else "$n pezzi"
}
