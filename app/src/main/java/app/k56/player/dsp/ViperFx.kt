package app.k56.player.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tanh

/**
 * The ViPER-style effect blocks: dynamic bass, clarity/exciter, compressor,
 * AGC, tube stage and stereo tools. Each block is stateful and cheap; none of
 * them allocates per sample.
 */

/** One-pole low pass, used as a band splitter. */
class OnePoleLowpass(sampleRate: Int, freq: Float) {
    private var a = coefficient(sampleRate, freq)
    private var z = 0f

    fun setFrequency(sampleRate: Int, freq: Float) {
        a = coefficient(sampleRate, freq)
    }

    fun process(input: Float): Float {
        z += a * (input - z)
        return z
    }

    fun reset() { z = 0f }

    private fun coefficient(sr: Int, freq: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * freq / sr)).toFloat().coerceIn(0.0001f, 0.9999f)
}

/**
 * Viper-Bass / Dynamic Bass: a low shelf whose amount shrinks as the bass gets
 * loud, so quiet material gains body without the kick drum booming.
 */
class DynamicBass(private val sampleRate: Int) {

    private val lowpass = OnePoleLowpass(sampleRate, 60f)
    private var envelope = 0f
    private var appliedFreq = -1f

    fun process(input: Float, gainDb: Float, freq: Float): Float {
        if (gainDb <= 0.01f) {
            lowpass.process(input)
            return input
        }
        if (freq != appliedFreq) {
            lowpass.setFrequency(sampleRate, freq)
            appliedFreq = freq
        }
        val low = lowpass.process(input)
        val level = abs(low)
        envelope = if (level > envelope) {
            envelope + (level - envelope) * 0.35f
        } else {
            envelope + (level - envelope) * 0.0015f
        }
        // Full boost on silence, ~30 % at 0.5 FS.
        val duck = (1f - (envelope * 1.4f)).coerceIn(0.3f, 1f)
        val gain = 10f.pow((gainDb * duck) / 20f)
        return input + low * (gain - 1f)
    }

    fun reset() {
        lowpass.reset()
        envelope = 0f
    }
}

/**
 * Viper-Clarity: a presence lift around 3 kHz plus a little air, with a gentle
 * dynamic component so already-bright material is not pushed further.
 */
class Clarity(private val sampleRate: Int) {

    private val presence = Biquad()
    private val air = Biquad()
    private var envelope = 0f
    private var applied = -1f

    fun process(input: Float, amount: Float): Float {
        if (amount <= 0.01f) return input
        if (amount != applied) {
            applied = amount
            presence.peaking(sampleRate, 3000f, 0.9f, amount * 0.55f)
            air.highShelf(sampleRate, 9000f, amount * 0.22f)
        }
        val level = abs(input)
        envelope += (level - envelope) * (if (level > envelope) 0.2f else 0.004f)
        val damping = (1f - envelope * 0.8f).coerceIn(0.35f, 1f)
        val shaped = presence.process(input) * damping + air.process(input) * damping
        return input + (shaped - input) * 0.9f
    }

    fun reset() {
        presence.reset()
        air.reset()
        envelope = 0f
    }
}

/**
 * Spectrum Extension: synthesises harmonics from the top band, which restores
 * the "air" lost to low-bitrate transcodes.
 */
class Exciter(private val sampleRate: Int) {

    private val splitter = OnePoleLowpass(sampleRate, 4500f)
    private var appliedSplit = -1f

    fun process(input: Float, amount: Float, splitHz: Float): Float {
        if (amount <= 0.01f) return input
        if (splitHz != appliedSplit) {
            splitter.setFrequency(sampleRate, splitHz)
            appliedSplit = splitHz
        }
        val low = splitter.process(input)
        val high = input - low
        val driven = tanh(high * (1f + amount * 0.6f))
        return input + driven * (amount * 0.18f)
    }

    fun reset() { splitter.reset() }
}

/** Peak compressor with soft knee, used for FET-style control. */
class Compressor(private val sampleRate: Int) {

    private var envelope = 0f

    fun process(
        input: Float,
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float,
        releaseMs: Float,
    ): Float {
        val level = abs(input)
        val attack = coefficient(attackMs)
        val release = coefficient(releaseMs)
        envelope += (level - envelope) * (if (level > envelope) attack else release)
        val db = 20f * kotlin.math.log10(envelope.coerceAtLeast(1e-6f))
        val knee = 6f
        val over = db - thresholdDb
        val reductionDb = when {
            over <= -knee / 2f -> 0f
            over >= knee / 2f -> over * (1f - 1f / ratio)
            else -> {
                val x = over + knee / 2f
                (x * x / (2f * knee)) * (1f - 1f / ratio)
            }
        }
        val gain = 10f.pow(-reductionDb / 20f)
        // Automatic makeup: half the theoretical reduction.
        val makeup = 10f.pow((reductionDb * 0.5f) / 20f)
        return input * gain * makeup
    }

    fun reset() { envelope = 0f }

    private fun coefficient(ms: Float): Float {
        if (ms <= 0.01f) return 1f
        return 1f - exp(-1.0 / (sampleRate * ms / 1000.0)).toFloat()
    }
}

/** Slow automatic gain control, keeps quiet tracks audible without pumping. */
class Agc(private val sampleRate: Int) {

    private var envelope = 0.1f
    private var gain = 1f

    fun process(input: Float): Float {
        val level = abs(input)
        envelope += (level - envelope) * (if (level > envelope) 0.05f else 0.0008f)
        val target = (TARGET / envelope.coerceAtLeast(1e-4f)).coerceIn(0.25f, 4f)
        gain += (target - gain) * 0.0005f
        return input * gain
    }

    fun reset() {
        envelope = 0.1f
        gain = 1f
    }

    private companion object { const val TARGET = 0.18f }
}

/** Tube / Analog-X style asymmetric saturation. */
class TubeStage {

    private var bias = 0.08f

    fun process(input: Float, drive: Float, mix: Float): Float {
        if (drive <= 0.01f || mix <= 0.01f) return input
        val gain = 1f + drive * 0.5f
        val driven = input * gain + bias
        val shaped = if (driven >= 0f) {
            tanh(driven * 1.4f) - tanh(bias * 1.4f)
        } else {
            tanh(driven * 1.1f) - tanh(bias * 1.1f)
        }
        val wet = shaped / gain.coerceAtLeast(1f)
        return input * (1f - mix) + wet * mix
    }

    fun reset() { bias = 0.08f }
}

/** Field surround (width) plus Differential Surround (Haas delay). */
class StereoStage(private val sampleRate: Int) {

    private val maxDelay = (sampleRate * 0.02f).toInt().coerceAtLeast(64)
    private val delay = FloatArray(maxDelay)
    private var write = 0
    private var appliedDelay = -1

    fun process(left: Float, right: Float, widthPercent: Float, haasMs: Float): Pair<Float, Float> {
        var l = left
        var r = right
        if (widthPercent != 100f) {
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f * (widthPercent / 100f)
            l = mid + side
            r = mid - side
        }
        if (haasMs > 0.01f) {
            val samples = (haasMs * sampleRate / 1000f).toInt().coerceIn(1, maxDelay - 1)
            if (samples != appliedDelay) appliedDelay = samples
            delay[write] = r
            val read = (write - samples + maxDelay) % maxDelay
            // Mix the delayed copy into the opposite channel for width.
            r = r * 0.7f + delay[read] * 0.5f
            l = l * 0.9f + delay[read] * 0.1f
            write = (write + 1) % maxDelay
        }
        return l to r
    }

    fun reset() {
        delay.fill(0f)
        write = 0
    }
}

/** Small Schroeder reverb: four combs and two allpasses per channel. */
class Reverb(sampleRate: Int, private val channel: Int) {

    private val combDelays = intArrayOf(1116, 1188, 1277, 1356)
        .map { it * sampleRate / 44100 }
        .toIntArray()
    private val allpassDelays = intArrayOf(556, 441).map { it * sampleRate / 44100 }.toIntArray()

    private val combs = Array(combDelays.size) { FloatArray(combDelays[it].coerceAtLeast(8)) }
    private val combIdx = IntArray(combDelays.size)
    private val combFilter = FloatArray(combDelays.size)

    private val allpass = Array(allpassDelays.size) { FloatArray(allpassDelays[it].coerceAtLeast(8)) }
    private val allpassIdx = IntArray(allpassDelays.size)

    fun process(input: Float, wetAmount: Float, room: Float): Float {
        if (wetAmount <= 0.01f) return input
        val feedback = 0.72f + room * 0.0024f // up to ~0.96
        var sum = 0f
        for (i in combs.indices) {
            val buffer = combs[i]
            val idx = combIdx[i]
            val delayed = buffer[idx]
            combFilter[i] = delayed * 0.98f + combFilter[i] * 0.02f
            buffer[idx] = input + combFilter[i] * feedback.coerceAtMost(0.96f)
            combIdx[i] = (idx + 1) % buffer.size
            sum += delayed
        }
        var wet = sum / combs.size.coerceAtLeast(1)
        for (i in allpass.indices) {
            val buffer = allpass[i]
            val idx = allpassIdx[i]
            val delayed = buffer[idx]
            val out = -wet + delayed
            buffer[idx] = wet + delayed * 0.5f
            allpassIdx[i] = (idx + 1) % buffer.size
            wet = out
        }
        return input * (1f - wetAmount) + wet * wetAmount
    }

    fun reset() {
        combs.forEach { it.fill(0f) }
        combIdx.fill(0)
        combFilter.fill(0f)
        allpass.forEach { it.fill(0f) }
        allpassIdx.fill(0)
    }
}
