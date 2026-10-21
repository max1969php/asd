package com.asd.player.data.loudness

import kotlin.math.pow

/**
 * Normalizzazione stile ReplayGain 2.0: ogni brano viene portato a -18 LUFS.
 * Il guadagno passa dal volume del player, che può solo abbassare: i pezzi più bassi del
 * riferimento (rari, di solito registrazioni vecchie o classica) restano come sono, quindi niente
 * clipping e niente limiter. Il riferimento basso è lo stesso di ReplayGain: quasi tutta la musica
 * moderna sta sopra e viene abbassata, il volume del telefono fa il resto.
 */
object VolumeLeveling {
    const val TARGET_LUFS = -18.0

    /** Volume del player (0..1) per un brano. Senza misura usa [fallbackLufs] (la mediana della libreria). */
    fun volumeFor(lufs: Double?, fallbackLufs: Double?): Float {
        val loudness = lufs ?: fallbackLufs ?: return 1f
        val gainDb = (TARGET_LUFS - loudness).coerceAtMost(0.0)
        return 10.0.pow(gainDb / 20).toFloat()
    }

    fun median(values: Collection<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
