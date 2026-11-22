package com.asd.desktop

import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Selettore di cartella. Su Ubuntu usa quello di GNOME (zenity), che si vede e si comporta come
 * gli altri programmi; se zenity non c'è, quello di Java.
 */
object FolderChooser {

    /** Bloccante: va chiamato fuori dal thread della UI. Null = annullato. */
    fun choose(title: String, start: String?): String? = zenity(title, start) ?: swing(title, start)

    private fun zenity(title: String, start: String?): String? {
        val cmd = mutableListOf("zenity", "--file-selection", "--directory", "--title=$title")
        if (start != null) cmd += "--filename=${start.trimEnd('/')}/"
        val process = runCatching { ProcessBuilder(cmd).redirectErrorStream(false).start() }.getOrNull() ?: return null
        val out = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(10, TimeUnit.MINUTES)
        // 1 = annullato, altro = zenity c'è ma qualcosa è andato storto: in entrambi i casi niente cartella.
        return out.takeIf { process.exitValue() == 0 && it.isNotEmpty() && File(it).isDirectory }
            ?: if (process.exitValue() == 1) CANCELLED else null
    }

    private fun swing(title: String, start: String?): String? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(start).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
    }

    /** zenity annullato: non aprire anche il selettore di Java. */
    private const val CANCELLED = "\u0000"

    fun isCancelled(result: String?) = result == null || result == CANCELLED
}
