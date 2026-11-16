package app.k56.player.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow

/**
 * Signal path: preamp -> 10-band EQ -> Viper blocks (clarity, exciter, dynamic
 * bass, tube, compressor, AGC) -> convolution -> reverb -> stereo -> limiter.
 *
 * Runs on ExoPlayer's audio thread and stays allocation-free per block apart
 * from the scratch arrays it owns.
 */
@UnstableApi
class DspProcessor(private val state: DspState) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channels = 2
    private var floatOutput = false

    private var bandFilters: Array<Array<Biquad>> = emptyArray()
    private var appliedEqVersion = -1
    private var appliedIrVersion = -1
    private var appliedDdcVersion = -1
    private var ddcStages: Array<DdcStage> = emptyArray()
    private var convolvers: Array<Convolver?> = emptyArray()

    private var preampGain = 1f
    private var limiterGain = FloatArray(2) { 1f }

    // Viper blocks, one instance per channel where state matters.
    private var clarityStages: Array<Clarity> = emptyArray()
    private var exciters: Array<Exciter> = emptyArray()
    private var bassStages: Array<DynamicBass> = emptyArray()
    private var tubeStages: Array<TubeStage> = emptyArray()
    private var compressors: Array<Compressor> = emptyArray()
    private var agcs: Array<Agc> = emptyArray()
    private var reverbs: Array<Reverb> = emptyArray()
    private var stereoStage: StereoStage? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount.coerceIn(1, 2)
        floatOutput = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        appliedEqVersion = -1
        appliedIrVersion = -1
        limiterGain = FloatArray(channels) { 1f }

        clarityStages = Array(channels) { Clarity(sampleRate) }
        exciters = Array(channels) { Exciter(sampleRate) }
        bassStages = Array(channels) { DynamicBass(sampleRate) }
        tubeStages = Array(channels) { TubeStage() }
        compressors = Array(channels) { Compressor(sampleRate) }
        agcs = Array(channels) { Agc(sampleRate) }
        reverbs = Array(channels) { channel -> Reverb(sampleRate, channel) }
        appliedDdcVersion = -1
        stereoStage = StereoStage(sampleRate)
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
            processChannel(ch, channelBuffer, frames)
            for (f in 0 until frames) interleaved[f * channels + ch] = channelBuffer[f]
        }

        // Stereo tools work on both channels at once.
        if (channels == 2) {
            val stereo = stereoStage
            val width = state.stereoWidth
            val haas = state.haasMs
            if (stereo != null && (width != 100f || haas > 0.01f)) {
                for (f in 0 until frames) {
                    val (l, r) = stereo.process(
                        interleaved[f * 2],
                        interleaved[f * 2 + 1],
                        width,
                        haas,
                    )
                    interleaved[f * 2] = l
                    interleaved[f * 2 + 1] = r
                }
            }
        }

        for (i in 0 until total) {
            val clamped = interleaved[i].coerceIn(-1f, 1f)
            if (floatOutput) output.putFloat(clamped) else {
                output.putShort((clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }

    private fun processChannel(ch: Int, buffer: FloatArray, frames: Int) {
        val filters = bandFilters.getOrNull(ch)
        val clarity = clarityStages.getOrNull(ch)
        val exciter = exciters.getOrNull(ch)
        val bass = bassStages.getOrNull(ch)
        val tube = tubeStages.getOrNull(ch)
        val compressor = compressors.getOrNull(ch)
        val agc = agcs.getOrNull(ch)
        val reverb = reverbs.getOrNull(ch)
        val convolver = convolvers.getOrNull(ch)

        val clarityAmount = state.clarity
        val spectrum = state.spectrumExtension
        val bassGain = state.bassGainDb
        val bassFreq = state.bassFreq
        val tubeDrive = state.tubeDrive
        val tubeMix = state.tubeMix / 100f
        val compOn = state.compressorEnabled
        val agcOn = state.agcEnabled
        val reverbWet = state.reverbWet / 100f
        val reverbRoom = state.reverbRoom

        // Headphone correction first: the DDC curve assumes a raw signal.
        ddcStages.getOrNull(ch)?.process(buffer, frames)

        for (f in 0 until frames) {
            var sample = buffer[f] * preampGain
            if (filters != null) {
                for (band in filters) sample = band.process(sample)
            }
            clarity?.let { sample = it.process(sample, clarityAmount) }
            exciter?.let { sample = it.process(sample, spectrum, 4500f) }
            bass?.let { sample = it.process(sample, bassGain, bassFreq) }
            tube?.let { sample = it.process(sample, tubeDrive, tubeMix) }
            if (compOn && compressor != null) {
                sample = compressor.process(
                    sample,
                    state.compressorThresholdDb,
                    state.compressorRatio,
                    4f,
                    120f,
                )
            }
            if (agcOn && agc != null) sample = agc.process(sample)
            buffer[f] = sample
        }

        convolver?.process(buffer, frames)
        if (reverbWet > 0.01f && reverb != null) {
            for (f in 0 until frames) buffer[f] = reverb.process(buffer[f], reverbWet, reverbRoom)
        }

        // Limiter last: everything above gets caught here.
        if (state.limiterEnabled) {
            var gain = limiterGain[ch]
            for (f in 0 until frames) {
                val sample = buffer[f]
                val peak = abs(sample)
                val target = if (peak > LIMIT_THRESHOLD) LIMIT_THRESHOLD / peak else 1f
                gain = if (target < gain) {
                    gain + (target - gain) * ATTACK
                } else {
                    gain + (target - gain) * RELEASE
                }
                buffer[f] = sample * gain
            }
            limiterGain[ch] = gain
        }
    }

    override fun onReset() {
        bandFilters.forEach { filters -> filters.forEach { it.reset() } }
        clarityStages.forEach { it.reset() }
        exciters.forEach { it.reset() }
        bassStages.forEach { it.reset() }
        tubeStages.forEach { it.reset() }
        compressors.forEach { it.reset() }
        agcs.forEach { it.reset() }
        reverbs.forEach { it.reset() }
        ddcStages.forEach { it.reset() }
        stereoStage?.reset()
        limiterGain = FloatArray(channels) { 1f }
        appliedIrVersion = -1
    }

    private fun syncParameters() {
        val eqVersion = state.eqVersion
        if (eqVersion != appliedEqVersion) {
            appliedEqVersion = eqVersion
            preampGain = 10f.pow(state.preampDb / 20f)
            bandFilters = Array(channels) {
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

        val ddcVersion = state.ddcVersion
        if (ddcVersion != appliedDdcVersion) {
            appliedDdcVersion = ddcVersion
            ddcStages = Array(channels) { DdcStage(state.ddc, sampleRate) }
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
