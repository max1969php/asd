package com.asd.player.data.loudness

/**
 * Da dove a dove suonare un brano saltando il silenzio ai lati. Solo silenzi lunghi abbastanza
 * da sentirsi, e con un po' di margine per non tagliare attacchi e code morbide. Le pause dentro
 * il pezzo non si toccano (per questo non si usa lo skipSilence di ExoPlayer).
 */
object SilenceTrim {
    /** Silenzio iniziale più corto di così: si lascia. */
    const val MIN_LEAD_MS = 400L
    const val MIN_TAIL_MS = 700L
    /** Margine prima del primo suono e dopo l'ultimo. */
    const val LEAD_MARGIN_MS = 150L
    const val TAIL_MARGIN_MS = 400L

    /** (inizio, fine) in ms; null = niente da saltare da quel lato. */
    fun bounds(firstSoundMs: Long?, lastSoundMs: Long?, totalMs: Long): Pair<Long?, Long?> {
        if (firstSoundMs == null || lastSoundMs == null) return null to null
        val start = (firstSoundMs - LEAD_MARGIN_MS).takeIf { firstSoundMs >= MIN_LEAD_MS }
        val end = (lastSoundMs + TAIL_MARGIN_MS).takeIf { totalMs - lastSoundMs >= MIN_TAIL_MS }
        return start to end
    }
}
