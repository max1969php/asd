package com.asd.player.data.tube

import android.content.Context
import java.io.File

/**
 * Versione minimal: niente yt-dlp. Stessa interfaccia di quello della full perché il resto del
 * codice compili uguale, ma il TUBO non compare nella UI e qui non si arriva mai.
 */
@Suppress("UNUSED_PARAMETER")
class TubeDownloader(context: Context, cookies: TubeCookies) {

    fun prepare(): Unit = unsupported()

    fun update(): String = unsupported()

    fun version(): String? = null

    fun download(url: String, jobId: Long, onProgress: (Float) -> Unit): Pair<File, TubeTrack> = unsupported()

    fun cleanup(jobId: Long) = Unit

    private fun unsupported(): Nothing = throw UnsupportedOperationException("TUBO: solo nella versione full")
}
