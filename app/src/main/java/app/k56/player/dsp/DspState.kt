package app.k56.player.dsp

import android.content.Context
import org.json.JSONObject
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

    // --- Viper-style block -------------------------------------------------

    /** Viper-Clarity, 0..14 like the original slider. */
    @Volatile var clarity: Float = prefs.getFloat(KEY_CLARITY, 0f)

    /** Dynamic bass amount, dB. */
    @Volatile var bassGainDb: Float = prefs.getFloat(KEY_BASS_GAIN, 0f)

    /** Dynamic bass corner frequency, Hz. */
    @Volatile var bassFreq: Float = prefs.getFloat(KEY_BASS_FREQ, 60f)

    /** Spectrum Extension, 0..12 (restores air after transcoding). */
    @Volatile var spectrumExtension: Float = prefs.getFloat(KEY_SPECTRUM, 0f)

    /** Field surround width, percent (100 = untouched). */
    @Volatile var stereoWidth: Float = prefs.getFloat(KEY_WIDTH, 100f)

    /** Differential surround (Haas), milliseconds. */
    @Volatile var haasMs: Float = prefs.getFloat(KEY_HAAS, 0f)

    /** Reverberation wet mix, 0..100. */
    @Volatile var reverbWet: Float = prefs.getFloat(KEY_REVERB_WET, 0f)

    /** Reverberation room size, 0..100. */
    @Volatile var reverbRoom: Float = prefs.getFloat(KEY_REVERB_ROOM, 50f)

    @Volatile var compressorEnabled: Boolean = prefs.getBoolean(KEY_COMP_ENABLED, false)
    @Volatile var compressorThresholdDb: Float = prefs.getFloat(KEY_COMP_THRESHOLD, -18f)
    @Volatile var compressorRatio: Float = prefs.getFloat(KEY_COMP_RATIO, 2.5f)

    @Volatile var agcEnabled: Boolean = prefs.getBoolean(KEY_AGC, false)

    /** Tube / Analog-X drive in dB. */
    @Volatile var tubeDrive: Float = prefs.getFloat(KEY_TUBE_DRIVE, 0f)
    @Volatile var tubeMix: Float = prefs.getFloat(KEY_TUBE_MIX, 0f)

    /** Bumped whenever a filter-affecting parameter changes. */
    @Volatile var eqVersion: Int = 0

    private var currentDdc: DdcProfile? = null
    private var currentDdcName: String? = prefs.getString(KEY_DDC, null)
    @Volatile var ddcVersion: Int = 0
        private set

    private var currentIr: ImpulseResponse? = null
    private var currentIrName: String? = prefs.getString(KEY_IR, null)
    @Volatile var irVersion: Int = 0
        private set

    val irName: String? get() = currentIrName
    val ddcName: String? get() = currentDdcName
    val ddc: DdcProfile? get() = currentDdc
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

    fun applyClarity(value: Float) {
        clarity = value
        prefs.edit().putFloat(KEY_CLARITY, value).apply()
        eqVersion++
    }

    fun setBass(gainDb: Float, freq: Float = bassFreq) {
        bassGainDb = gainDb
        bassFreq = freq
        prefs.edit().putFloat(KEY_BASS_GAIN, gainDb).putFloat(KEY_BASS_FREQ, freq).apply()
        eqVersion++
    }

    fun applySpectrumExtension(value: Float) {
        spectrumExtension = value
        prefs.edit().putFloat(KEY_SPECTRUM, value).apply()
        eqVersion++
    }

    fun applyStereoWidth(value: Float) {
        stereoWidth = value
        prefs.edit().putFloat(KEY_WIDTH, value).apply()
        eqVersion++
    }

    fun setHaas(value: Float) {
        haasMs = value
        prefs.edit().putFloat(KEY_HAAS, value).apply()
        eqVersion++
    }

    fun setReverb(wet: Float, room: Float = reverbRoom) {
        reverbWet = wet
        reverbRoom = room
        prefs.edit().putFloat(KEY_REVERB_WET, wet).putFloat(KEY_REVERB_ROOM, room).apply()
        eqVersion++
    }

    fun setCompressor(enabled: Boolean, thresholdDb: Float = compressorThresholdDb, ratio: Float = compressorRatio) {
        compressorEnabled = enabled
        compressorThresholdDb = thresholdDb
        compressorRatio = ratio
        prefs.edit()
            .putBoolean(KEY_COMP_ENABLED, enabled)
            .putFloat(KEY_COMP_THRESHOLD, thresholdDb)
            .putFloat(KEY_COMP_RATIO, ratio)
            .apply()
        eqVersion++
    }

    fun setAgc(enabled: Boolean) {
        agcEnabled = enabled
        prefs.edit().putBoolean(KEY_AGC, enabled).apply()
        eqVersion++
    }

    fun setTube(drive: Float, mix: Float = tubeMix) {
        tubeDrive = drive
        tubeMix = mix
        prefs.edit().putFloat(KEY_TUBE_DRIVE, drive).putFloat(KEY_TUBE_MIX, mix).apply()
        eqVersion++
    }

    /** Selects a DDC correction curve, or clears it with null. */
    fun setDdc(file: File?) {
        currentDdc = if (file == null) null else DdcProfile.parse(file)
        currentDdcName = if (currentDdc == null) null else file?.name
        prefs.edit().putString(KEY_DDC, currentDdcName).apply()
        ddcVersion++
    }

    fun restoreDdc(directory: File) {
        val name = currentDdcName ?: return
        val file = File(directory, name)
        if (file.exists()) setDdc(file) else clearDdc()
    }

    fun clearDdc() {
        currentDdc = null
        currentDdcName = null
        prefs.edit().remove(KEY_DDC).apply()
        ddcVersion++
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

    /** The DDC/IR selection (file names only), for settings backup. */
    fun exportSelection(): JSONObject = JSONObject().apply {
        put("ddc", currentDdcName ?: JSONObject.NULL)
        put("ir", currentIrName ?: JSONObject.NULL)
    }

    /** Restores the DDC/IR selection from a backup; profiles load on next start. */
    fun importSelection(selection: JSONObject) {
        val ddc = selection.optStringOrNull("ddc")
        val ir = selection.optStringOrNull("ir")
        if (ddc != null) {
            currentDdcName = ddc
            currentDdc = null
            prefs.edit().putString(KEY_DDC, ddc).apply()
            ddcVersion++
        } else {
            clearDdc()
        }
        if (ir != null) {
            currentIrName = ir
            currentIr = null
            prefs.edit().putString(KEY_IR, ir).apply()
            irVersion++
        } else {
            clearIr()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PREAMP = "preamp"
        private const val KEY_BANDS = "bands"
        private const val KEY_LIMITER = "limiter"
        private const val KEY_IR = "ir"
        private const val KEY_DDC = "ddc"
        private const val KEY_CLARITY = "clarity"
        private const val KEY_BASS_GAIN = "bass_gain"
        private const val KEY_BASS_FREQ = "bass_freq"
        private const val KEY_SPECTRUM = "spectrum_extension"
        private const val KEY_WIDTH = "stereo_width"
        private const val KEY_HAAS = "haas_ms"
        private const val KEY_REVERB_WET = "reverb_wet"
        private const val KEY_REVERB_ROOM = "reverb_room"
        private const val KEY_COMP_ENABLED = "comp_enabled"
        private const val KEY_COMP_THRESHOLD = "comp_threshold"
        private const val KEY_COMP_RATIO = "comp_ratio"
        private const val KEY_AGC = "agc"
        private const val KEY_TUBE_DRIVE = "tube_drive"
        private const val KEY_TUBE_MIX = "tube_mix"

        /** Center frequencies, Hz. */
        val BANDS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val Q = 1.4f
    }
}
