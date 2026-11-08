package com.asd.player.domain.model

enum class SortField { TITLE, ARTIST, ALBUM, DURATION, RECENT }

data class SortOrder(
    val field: SortField = SortField.TITLE,
    val ascending: Boolean = true,
)

/** Query testuale e filtri sono indipendenti: si combinano tutti in AND. */
data class LibraryQuery(
    val text: String = "",
    val formats: Set<String> = emptySet(),
    val sources: Set<SongSource> = emptySet(),
    val artist: String? = null,
    /** Solo i pezzi che sembrano doppioni, a gruppi. */
    val duplicates: Boolean = false,
    val sort: SortOrder = SortOrder(),
) {
    val filtering: Boolean
        get() = text.isNotBlank() || formats.isNotEmpty() || sources.isNotEmpty() || artist != null || duplicates
}
