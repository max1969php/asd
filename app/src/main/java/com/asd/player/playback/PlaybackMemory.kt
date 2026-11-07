package com.asd.player.playback

import android.content.Context
import java.io.File

/** L'ultimo ascolto: coda, pezzo, punto, shuffle e repeat. */
data class SavedPlayback(
    val uris: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/**
 * Ricorda l'ultimo ascolto per ripartire da lì alla riapertura. La coda può essere una cartella
 * intera (migliaia di pezzi): sta in un file dell'app, il resto nelle preferenze.
 */
class PlaybackMemory(context: Context) {

    private val prefs = context.getSharedPreferences("asd_playback", Context.MODE_PRIVATE)
    private val queueFile = File(context.filesDir, "last_queue.txt")

    fun saveQueue(uris: List<String>) {
        runCatching { queueFile.writeText(uris.joinToString("\n")) }
    }

    fun savePosition(index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        prefs.edit()
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, positionMs)
            .putBoolean(KEY_SHUFFLE, shuffle)
            .putInt(KEY_REPEAT, repeatMode)
            .apply()
    }

    fun load(): SavedPlayback? {
        val uris = runCatching { queueFile.readLines() }.getOrNull()?.filter { it.isNotBlank() }
        if (uris.isNullOrEmpty()) return null
        return SavedPlayback(
            uris = uris,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
        )
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position_ms"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat_mode"
    }
}
