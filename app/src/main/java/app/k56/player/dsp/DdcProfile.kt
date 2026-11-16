package app.k56.player.dsp

import java.io.File
import kotlin.math.abs

/**
 * ViPER DDC headphone-correction profile.
 *
 * File format (reverse engineered from the shipped kernels):
 * one line per sample rate, `SR_44100:...` / `SR_48000:...`, holding 25 numbers
 * — five second-order sections of `b0,b1,b2,a1,a2`. The stored a1/a2 use the
 * opposite sign of the standard difference equation, so they are negated when
 * the sections are loaded.
 */
class DdcProfile(
    val name: String,
    /** sample rate -> flattened 25 coefficients */
    private val sections: Map<Int, FloatArray>,
) {

    val sampleRates: List<Int> get() = sections.keys.sorted()

    /** Applies the five sections to one channel in place. */
    fun process(buffer: FloatArray, count: Int, sampleRate: Int) {
        val coeffs = coefficientsFor(sampleRate) ?: return
        val filters = Array(SECTION_COUNT) { Biquad() }
        for (i in 0 until SECTION_COUNT) {
            val offset = i * 5
            filters[i].setCoefficients(
                coeffs[offset], coeffs[offset + 1], coeffs[offset + 2],
                -coeffs[offset + 3], -coeffs[offset + 4],
            )
        }
        for (f in 0 until count) {
            var sample = buffer[f]
            for (filter in filters) sample = filter.process(sample)
            buffer[f] = sample
        }
    }

    internal fun coefficientsFor(sampleRate: Int): FloatArray? {
        sections[sampleRate]?.let { return it }
        // Nearest available rate: the curves are smooth, so 44.1k works at 48k.
        val nearest = sections.keys.minByOrNull { abs(it - sampleRate) } ?: return null
        return sections[nearest]
    }

    companion object {
        const val SECTION_COUNT = 5

        fun parse(file: File): DdcProfile? = runCatching {
            parse(file.readText(), file.nameWithoutExtension)
        }.getOrNull()

        fun parse(text: String, name: String): DdcProfile? {
            val sections = LinkedHashMap<Int, FloatArray>()
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || !line.startsWith("SR_")) return@forEach
                val split = line.indexOf(':')
                if (split <= 3) return@forEach
                val rate = line.substring(3, split).trim().toIntOrNull() ?: return@forEach
                val values = line.substring(split + 1)
                    .split(',')
                    .mapNotNull { it.trim().toFloatOrNull() }
                    .toFloatArray()
                if (values.size >= SECTION_COUNT * 5) sections[rate] = values
            }
            return if (sections.isEmpty()) null else DdcProfile(name, sections)
        }
    }
}

/**
 * Stateful DDC cascade for one channel. Building the five biquads once and
 * reusing them keeps the audio thread free of allocations.
 */
class DdcStage(profile: DdcProfile?, sampleRate: Int) {

    private val filters = Array(DdcProfile.SECTION_COUNT) { Biquad() }
    private val active = profile?.coefficientsFor(sampleRate)?.let { coeffs ->
        for (i in 0 until DdcProfile.SECTION_COUNT) {
            val offset = i * 5
            filters[i].setCoefficients(
                coeffs[offset], coeffs[offset + 1], coeffs[offset + 2],
                -coeffs[offset + 3], -coeffs[offset + 4],
            )
        }
        true
    } ?: false

    fun process(buffer: FloatArray, count: Int) {
        if (!active) return
        for (f in 0 until count) {
            var sample = buffer[f]
            for (filter in filters) sample = filter.process(sample)
            buffer[f] = sample
        }
    }

    fun reset() = filters.forEach { it.reset() }
}
