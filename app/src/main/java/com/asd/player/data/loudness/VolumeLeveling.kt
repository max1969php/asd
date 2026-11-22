package com.asd.player.data.loudness

import kotlin.math.pow

/**
 * Volume uniforme: ogni brano viene portato a -14 LUFS, lo stesso livello di Spotify (normale),
 * YouTube e Amazon Music. Così ASD suona forte quanto loro allo stesso volume del telefono.
 *
 * Il guadagno può essere negativo (pezzi forti, quasi tutti i download da YouTube) o positivo
 * (pezzi deboli, registrazioni vecchie), al massimo [MAX_BOOST_DB]: si abbassa con il volume del
 * player, si alza con il LoudnessEnhancer di Android, che ha un limitatore e non fa distorcere.
 */
object VolumeLeveling {
    const val TARGET_LUFS = -14.0

    /** Oltre questo un pezzo molto debole resta un po' più basso: il limitatore schiaccerebbe troppo. */
    const val MAX_BOOST_DB = 12.0

    /** Guadagno in dB per un brano. Senza misura usa [fallbackLufs] (la mediana della libreria). */
    fun gainDb(lufs: Double?, fallbackLufs: Double?): Double {
        val loudness = lufs ?: fallbackLufs ?: return 0.0
        return (TARGET_LUFS - loudness).coerceAtMost(MAX_BOOST_DB)
    }

    /** dB → fattore lineare. */
    fun linear(db: Double): Float = 10.0.pow(db / 20).toFloat()

    fun median(values: Collection<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
