package app.k56.player.dsp

import android.content.Context
import java.io.File

/**
 * Live DSP parameters. Audio threads read these directly, so every field is a
 * volatile primitive or an immutable snapshot; the UI only ever swaps values.
 */
class DspState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("k56_dsp", Context.MODE_PRIVATE)

    @Volatile var enabled: Boolean = prefs.getBoolean(KEY_ENABLED, false)

    @Volatile var preampDb: Float = prefs.getFloat(KEY_PREAMP, 0f)

    /** Ten bands, dB, indexed like [BANDS]. */
    val bandGains: FloatArray = FloatArray(BANDS.size).also { gains ->
        val stored = prefs.getString(KEY_BANDS, null)
        if (stored != null) {
            stored.split(',').take(BANDS.size).forEachIndexed { index, value ->
                gains[index] = value.toFloatOrNull() ?: 0f
            }
        }
    }

    @Volatile var limiterEnabled: Boolean = prefs.getBoolean(KEY_LIMITER, true)

    /** Bumped by the UI whenever bands/preamp change so the engine rebuilds. */
    @Volatile var eqVersion: Int = 0

    private var currentIr: ImpulseResponse? = null
    private var currentIrName: String? = prefs.getString(KEY_IR, null)
    @Volatile var irVersion: Int = 0
        private set

    val irName: String? get() = currentIrName
    val ir: ImpulseResponse? get() = currentIr

    fun applyEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun applyPreamp(value: Float) {
        preampDb = value
        prefs.edit().putFloat(KEY_PREAMP, value).apply()
        eqVersion++
    }

    fun setBand(index: Int, gainDb: Float) {
        if (index !in bandGains.indices) return
        bandGains[index] = gainDb
        prefs.edit().putString(KEY_BANDS, bandGains.joinToString(",")).apply()
        eqVersion++
    }

    fun setLimiter(value: Boolean) {
        limiterEnabled = value
        prefs.edit().putBoolean(KEY_LIMITER, value).apply()
    }

    /** Loads an impulse response from an imported file, or clears it. */
    fun setIr(file: File?) {
        currentIr = if (file == null) null else runCatching {
            val decoded = WavReader.read(file)
            if (decoded.frames < 8) null else decoded
        }.getOrNull()
        currentIrName = if (currentIr == null) null else file?.name
        prefs.edit().putString(KEY_IR, currentIrName).apply()
        irVersion++
    }

    fun restoreIr(directory: File) {
        val name = currentIrName ?: return
        val file = File(directory, name)
        if (file.exists()) setIr(file) else clearIr()
    }

    fun clearIr() {
        currentIr = null
        currentIrName = null
        prefs.edit().remove(KEY_IR).apply()
        irVersion++
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PREAMP = "preamp"
        private const val KEY_BANDS = "bands"
        private const val KEY_LIMITER = "limiter"
        private const val KEY_IR = "ir"

        /** Center frequencies, Hz. */
        val BANDS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val Q = 1.4f
    }
}
