package app.k56.player.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Signal path: preamp -> 10-band EQ -> convolution -> limiter.
 * Runs on ExoPlayer's audio thread; it never allocates per sample block beyond
 * the scratch arrays it owns, and it degrades to a passthrough when disabled.
 */
@UnstableApi
class DspProcessor(private val state: DspState) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channels = 2
    private var floatOutput = false

    private var bandFilters: Array<Array<Biquad>> = emptyArray()
    private var appliedEqVersion = -1
    private var appliedIrVersion = -1
    private var convolvers: Array<Convolver?> = emptyArray()
    private var preampGain = 1f
    private var limiterGain = FloatArray(2) { 1f }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount.coerceIn(1, 2)
        floatOutput = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        appliedEqVersion = -1
        appliedIrVersion = -1
        limiterGain = FloatArray(channels) { 1f }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (floatOutput) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = inputBuffer.remaining() / frameBytes
        if (frames == 0) return
        val total = frames * channels

        val output = replaceOutputBuffer(frames * frameBytes)
        if (!state.enabled) {
            // Fast path: keep the bytes, skip every filter.
            val copy = ByteArray(inputBuffer.remaining())
            inputBuffer.get(copy)
            output.put(copy)
            output.flip()
            return
        }

        syncParameters()

        val interleaved = FloatArray(total)
        for (i in 0 until total) {
            interleaved[i] = if (floatOutput) inputBuffer.float else inputBuffer.short / 32768f
        }

        val channelBuffer = FloatArray(frames)
        for (ch in 0 until channels) {
            for (f in 0 until frames) channelBuffer[f] = interleaved[f * channels + ch]

            val filters = bandFilters.getOrNull(ch)
            for (f in 0 until frames) {
                var sample = channelBuffer[f] * preampGain
                if (filters != null) {
                    for (band in filters) sample = band.process(sample)
                }
                channelBuffer[f] = sample
            }

            convolvers.getOrNull(ch)?.process(channelBuffer, frames)

            if (state.limiterEnabled) {
                var gain = limiterGain[ch]
                for (f in 0 until frames) {
                    val sample = channelBuffer[f]
                    val peak = abs(sample)
                    val target = if (peak > LIMIT_THRESHOLD) LIMIT_THRESHOLD / peak else 1f
                    gain = if (target < gain) {
                        gain + (target - gain) * ATTACK
                    } else {
                        gain + (target - gain) * RELEASE
                    }
                    channelBuffer[f] = (sample * gain).coerceIn(-1f, 1f)
                }
                limiterGain[ch] = gain
            }

            for (f in 0 until frames) interleaved[f * channels + ch] = channelBuffer[f]
        }

        for (i in 0 until total) {
            if (floatOutput) output.putFloat(interleaved[i]) else {
                output.putShort((interleaved[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }

    override fun onReset() {
        bandFilters.forEach { filters -> filters.forEach { it.reset() } }
        limiterGain = FloatArray(channels) { 1f }
        appliedIrVersion = -1
    }

    private fun syncParameters() {
        val eqVersion = state.eqVersion
        if (eqVersion != appliedEqVersion) {
            appliedEqVersion = eqVersion
            preampGain = Math.pow(10.0, state.preampDb / 20.0).toFloat()
            bandFilters = Array(channels) { ch ->
                Array(DspState.BANDS.size) { band ->
                    Biquad().apply {
                        peaking(
                            sampleRate = sampleRate,
                            freq = DspState.BANDS[band].toFloat(),
                            q = DspState.Q,
                            gainDb = state.bandGains.getOrElse(band) { 0f },
                        )
                    }
                }
            }
        }

        val irVersion = state.irVersion
        if (irVersion != appliedIrVersion) {
            appliedIrVersion = irVersion
            val ir = state.ir
            convolvers = if (ir == null || ir.frames < 8) {
                arrayOfNulls(channels)
            } else {
                val left = resample(ir, 0)
                val right = if (ir.channels > 1) resample(ir, 1) else left
                Array(channels) { ch -> Convolver(if (ch == 0) left else right) }
            }
        }
    }

    /** Linear resampling to the device rate; kernels are already short. */
    private fun resample(ir: ImpulseResponse, channel: Int): FloatArray {
        val sourceFrames = ir.frames
        val mono = FloatArray(sourceFrames) { f -> ir.samples[f * ir.channels + channel] }
        if (ir.sampleRate == sampleRate) return mono
        val ratio = sampleRate.toDouble() / ir.sampleRate
        val targetFrames = (sourceFrames * ratio).toInt().coerceAtLeast(8)
        val out = FloatArray(targetFrames)
        for (i in out.indices) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, sourceFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(sourceFrames - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = mono[i0] * (1 - frac) + mono[i1] * frac
        }
        return out
    }

    private companion object {
        const val LIMIT_THRESHOLD = 0.95f
        const val ATTACK = 0.35f
        const val RELEASE = 0.002f
    }
}
