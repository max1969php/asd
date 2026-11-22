package com.asd.player.playback

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.asd.player.data.settings.EqSettings
import com.asd.player.data.settings.SettingsStore
import kotlin.math.roundToInt

/** Una banda dell'equalizzatore del telefono: frequenza centrale in Hz. */
data class EqBand(val index: Int, val centerHz: Int)

/**
 * Equalizzatore e bassi di Android sulla sessione audio di ASD (non su tutto il telefono).
 * La sessione la crea l'app all'avvio e il player la usa: così le bande si conoscono anche
 * prima che suoni qualcosa. Alcuni telefoni non hanno l'equalizzatore: allora [available] è false.
 */
class AudioEffects(context: Context, private val settings: SettingsStore) {

    val sessionId: Int = context.getSystemService(AudioManager::class.java).generateAudioSessionId()

    private val equalizer: Equalizer? = runCatching { Equalizer(0, sessionId) }.getOrNull()
    private val bassBoost: BassBoost? = runCatching { BassBoost(0, sessionId) }.getOrNull()?.takeIf { it.strengthSupported }

    /** Alza i pezzi deboli del volume uniforme: guadagno con limitatore, niente distorsione. */
    private val enhancer: LoudnessEnhancer? = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

    val available: Boolean get() = equalizer != null
    val bassAvailable: Boolean get() = bassBoost != null

    /** Minimo e massimo di una banda, in millibel (es. −1500..1500). */
    val levelRange: IntRange = equalizer?.bandLevelRange?.let { it[0].toInt()..it[1].toInt() } ?: 0..0

    val bands: List<EqBand> = equalizer?.let { eq ->
        (0 until eq.numberOfBands).map { EqBand(it, eq.getCenterFreq(it.toShort()) / 1000) }
    }.orEmpty()

    /** Nomi dei preset del telefono (di solito in inglese: Normal, Rock, Jazz...). */
    val presets: List<String> = equalizer?.let { eq ->
        (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    }.orEmpty()

    /** Livelli delle bande di un preset del telefono, per mostrarli negli slider. */
    fun presetLevels(preset: Int): List<Int> = runCatching {
        val eq = equalizer ?: return emptyList()
        eq.usePreset(preset.toShort())
        bands.map { eq.getBandLevel(it.index.toShort()).toInt() }
    }.getOrDefault(emptyList())

    fun apply(eq: EqSettings) {
        runCatching {
            equalizer?.let { e ->
                if (eq.enabled) {
                    eq.levels.forEachIndexed { i, level ->
                        if (i < bands.size) e.setBandLevel(i.toShort(), level.coerceIn(levelRange).toShort())
                    }
                }
                e.enabled = eq.enabled
            }
            bassBoost?.let { b ->
                if (eq.enabled) b.setStrength(eq.bass.coerceIn(0, 1000).toShort())
                b.enabled = eq.enabled && eq.bass > 0
            }
        }
    }

    /**
     * Le bande alzate (e i bassi) aggiungono guadagno: con il volume uniforme e i pezzi già forti
     * distorcerebbero. Il player abbassa del guadagno massimo aggiunto: dB, 0 o negativo.
     */
    fun headroomDb(eq: EqSettings): Double {
        if (!eq.enabled || equalizer == null) return 0.0
        val maxBoostDb = (eq.levels.take(bands.size).maxOrNull() ?: 0).coerceAtLeast(0) / 100.0 +
            (if (bassBoost != null) eq.bass.coerceIn(0, 1000) * BASS_DB_PER_UNIT else 0.0)
        return -maxBoostDb
    }

    /**
     * Guadagno positivo del volume uniforme ([db] > 0), che il volume del player (max 1) non può
     * dare. 0 o meno: spento. Se il telefono non ha il LoudnessEnhancer i pezzi deboli restano come sono.
     */
    fun setBoost(db: Double) {
        val e = enhancer ?: return
        runCatching {
            val mB = (db * 100).roundToInt()
            if (mB > 0) {
                e.setTargetGain(mB)
                e.enabled = true
            } else {
                e.enabled = false
            }
        }
    }

    init {
        apply(settings.eq.value)
    }

    private companion object {
        /** BassBoost non dichiara il suo guadagno: circa 10 dB al massimo sui telefoni comuni. */
        const val BASS_DB_PER_UNIT = 10.0 / 1000
    }
}
