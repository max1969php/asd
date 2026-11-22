package com.asd.player.domain.usecase

import com.asd.player.domain.model.Song

/**
 * Nomi mostrati puliti: via i numeri di catalogazione ("001 - ", "01. ", "CD1-03 ") e le code dei
 * download ("(Official Video)", "[HD]"...). Solo quello che si vede: i file restano come sono.
 *
 * Senza tag il nome del file fa da tag: "Artista - Titolo" o "Titolo - Artista". Quale parte sia
 * l'artista lo dice il resto della libreria: un artista torna in più brani, un titolo di solito no.
 */
object SongNames {

    fun resolve(songs: List<Song>): List<Song> {
        val parsed = songs.map { song -> song.fromFileName()?.let { split(cleanTitle(it)) } }
        // Quante volte compare ogni nome: come artista dei tag o come parte di un nome file.
        val seen = HashMap<String, Int>()
        songs.forEachIndexed { i, song ->
            if (parsed[i] == null) song.artist?.let { seen.merge(key(it), 1, Int::plus) }
        }
        parsed.forEach { parts -> parts?.toList()?.forEach { seen.merge(key(it), 1, Int::plus) } }

        return songs.mapIndexed { i, song ->
            val parts = parsed[i]
            when {
                song.fromFileName() == null -> song.copy(title = cleanTitle(song.title), artist = song.artist?.let(::cleanArtist))
                parts == null -> song.copy(
                    title = cleanTitle(song.fileName.base()),
                    artist = song.tagArtist()?.let(::cleanArtist),
                )
                else -> {
                    val (a, b) = parts
                    val tagArtist = song.tagArtist()
                    val artistIsB = when {
                        tagArtist != null -> key(b) == key(tagArtist)
                        else -> (seen[key(b)] ?: 0) > (seen[key(a)] ?: 0)
                    }
                    song.copy(
                        artist = cleanArtist(tagArtist ?: if (artistIsB) b else a),
                        title = cleanTitle(if (artistIsB) a else b),
                    )
                }
            }
        }
    }

    /** Il titolo mostrato è quello del nome file? Allora il nome file è da interpretare. */
    private fun Song.fromFileName(): String? {
        val base = fileName.base()
        // Le versioni fino alla 0.6 salvavano già "Artista - Titolo" diviso: si rifà da capo.
        return base.takeIf { title == it || (artist != null && "$artist - $title" == it) }
    }

    /** L'artista dei tag, non quello ricavato dal nome file da una versione precedente. */
    private fun Song.tagArtist(): String? =
        artist?.takeIf { "$it - $title" != fileName.base() }?.takeIf { it.isNotBlank() }

    private fun split(base: String): Pair<String, String>? {
        val parts = base.split(SEPARATOR, limit = 2).map { it.trim() }
        return parts.takeIf { it.size == 2 && it.all(String::isNotEmpty) }?.let { it[0] to it[1] }
    }

    /** Pulizia comune: trattini bassi come spazi, numeri di catalogazione in testa. */
    fun clean(name: String): String {
        var s = name.trim()
        if (' ' !in s && '_' in s) s = s.replace('_', ' ')
        while (true) {
            val next = stripPrefix(s)
            if (next == s || next.isBlank()) break
            s = next
        }
        return s.trim()
    }

    /** Titolo pulito anche delle code dei download. */
    fun cleanTitle(title: String): String {
        var s = clean(title)
        while (true) {
            val next = s.replace(JUNK_TAIL, "").trim().removeSuffix("-").trim()
            if (next == s || next.isBlank()) break
            s = next
        }
        return s
    }

    /** I canali automatici di YouTube si chiamano "Artista - Topic". */
    private fun cleanArtist(artist: String) = artist.removeSuffix(" - Topic").trim()

    private fun stripPrefix(s: String): String {
        DISC_TRACK.find(s)?.let { return s.substring(it.range.last + 1) }
        BRACKETED_NUMBER.find(s)?.let { m -> if (isTrack(m.groupValues[1])) return s.substring(m.range.last + 1) }
        NUMBER_SEPARATOR.find(s)?.let { m -> if (isTrack(m.groupValues[1])) return s.substring(m.range.last + 1) }
        // Solo spazio dopo il numero: si toglie solo se ha lo zero davanti ("01 Song", non "99 Luftballons").
        PADDED_NUMBER.find(s)?.let { return s.substring(it.range.last + 1) }
        return s
    }

    /**
     * Numero di traccia: una o due cifre, o con lo zero davanti ("7", "12", "001"). Gli altri si
     * tengono: gli anni tra 1950 e 2050 ("1979", "2000 miglia") ma anche i nomi come "883".
     */
    private fun isTrack(number: String) = number.startsWith("0") || number.length <= 2

    private fun String.base() = substringBeforeLast('.')

    private fun key(name: String) = name.trim().lowercase()

    private val SEPARATOR = Regex("\\s+[-–—]\\s+")
    /** "CD1-03 ", "1-03 ": disco e traccia (la traccia ha sempre due cifre, così "2-4-6-8" resta). */
    private val DISC_TRACK = Regex("^(?:(?:cd|disc|disco)\\s*\\d{1,2}|\\d)\\s*[-.]\\s*\\d{2,3}(?:\\s*[-–—.)_]\\s*|\\s+)(?=\\D)", RegexOption.IGNORE_CASE)
    /** "(01) ", "[3] - ". */
    private val BRACKETED_NUMBER = Regex("^[\\[(](\\d{1,4})[\\])]\\s*[-–—.]?\\s*")
    /** "001 - ", "01. ", "1) ", "07_": il numero non deve continuare ("1.5" resta). */
    private val NUMBER_SEPARATOR = Regex("^(\\d{1,4})\\s*[-–—.)_]\\s*(?=\\D)")
    private val PADDED_NUMBER = Regex("^0\\d{0,3}\\s+(?=\\D)")
    /** Code dei download: "(Official Music Video)", "[Lyric Video HD]", "(Audio)", "(Video ufficiale)", "[HD]". */
    private val JUNK_TAIL = Regex(
        "\\s*[\\[(]\\s*(?:(?:official\\s+)?(?:music\\s+|lyrics?\\s+)?(?:video|audio|visuali[sz]er|videoclip)" +
            "(?:\\s+ufficiale)?(?:\\s+(?:hd|hq|4k))?|official|lyrics?|testo|hd|hq|4k)\\s*[\\])]\\s*$",
        RegexOption.IGNORE_CASE,
    )
}
