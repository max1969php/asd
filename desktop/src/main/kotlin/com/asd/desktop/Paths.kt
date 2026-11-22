package com.asd.desktop

import java.io.File

/** Dove ASD Desktop tiene i suoi file, secondo le regole XDG di Linux. */
object Paths {
    private val home = File(System.getProperty("user.home"))

    private fun xdg(variable: String, fallback: String): File =
        System.getenv(variable)?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, fallback)

    /** Impostazioni: ~/.config/asd-desktop */
    val config: File = File(xdg("XDG_CONFIG_HOME", ".config"), "asd-desktop").apply { mkdirs() }

    /** Catalogo e ultimo ascolto: ~/.local/share/asd-desktop */
    val data: File = File(xdg("XDG_DATA_HOME", ".local/share"), "asd-desktop").apply { mkdirs() }

    /** Scrive [text] in [file] passando da un file temporaneo: un'uscita a metà non lascia file rotti. */
    fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
