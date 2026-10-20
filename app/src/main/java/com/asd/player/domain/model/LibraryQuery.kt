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
    val artist: String? = null,
    val sort: SortOrder = SortOrder(),
)
