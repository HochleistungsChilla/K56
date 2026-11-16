package app.k56.player.dsp

/**
 * Uniform partitioned overlap-save convolution. Output lags the input by one
 * block (21 ms at 48 kHz with 1024-sample blocks), which is inaudible for music
 * and far cheaper than a direct FIR for long impulse responses.
 */
class Convolver(impulse: FloatArray, private val blockSize: Int = 1024) {

    private val n = blockSize * 2
    private val fft = Fft(n)
    private val partitions = ((impulse.size + blockSize - 1) / blockSize).coerceAtLeast(1)

    private val hRe = Array(partitions) { FloatArray(n) }
    private val hIm = Array(partitions) { FloatArray(n) }
    private val xRe = Array(partitions) { FloatArray(n) }
    private val xIm = Array(partitions) { FloatArray(n) }

    private var pos = 0
    private val inBlock = FloatArray(blockSize)
    private var inPos = 0
    private var prevBlock = FloatArray(blockSize)

    private val accRe = FloatArray(n)
    private val accIm = FloatArray(n)
    private val outBlock = FloatArray(blockSize)
    private var outPos = blockSize

    init {
        for (p in 0 until partitions) {
            val start = p * blockSize
            for (i in 0 until blockSize) {
                val idx = start + i
                hRe[p][i] = if (idx < impulse.size) impulse[idx] else 0f
            }
            fft.forward(hRe[p], hIm[p])
        }
    }

    /** Processes [count] samples in place. */
    fun process(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            inBlock[inPos++] = samples[i]
            if (inPos == blockSize) {
                computeBlock()
                inPos = 0
            }
            if (outPos < blockSize) {
                samples[i] = outBlock[outPos++]
            } else {
                // Latency stage: this block has not been produced yet.
                samples[i] = 0f
            }
        }
    }

    private fun computeBlock() {
        System.arraycopy(prevBlock, 0, accRe, 0, blockSize)
        System.arraycopy(inBlock, 0, accRe, blockSize, blockSize)
        java.util.Arrays.fill(accIm, 0f)

        val curRe = FloatArray(n)
        val curIm = FloatArray(n)
        System.arraycopy(accRe, 0, curRe, 0, n)
        fft.forward(curRe, curIm)
        System.arraycopy(curRe, 0, xRe[pos], 0, n)
        System.arraycopy(curIm, 0, xIm[pos], 0, n)
        pos = (pos + 1) % partitions

        java.util.Arrays.fill(accRe, 0f)
        java.util.Arrays.fill(accIm, 0f)
        for (k in 0 until partitions) {
            val idx = (pos - 1 - k + 2 * partitions) % partitions
            val xr = xRe[idx]
            val xi = xIm[idx]
            val hr = hRe[k]
            val hi = hIm[k]
            for (i in 0 until n) {
                accRe[i] += xr[i] * hr[i] - xi[i] * hi[i]
                accIm[i] += xr[i] * hi[i] + xi[i] * hr[i]
            }
        }
        fft.inverse(accRe, accIm)
        System.arraycopy(accRe, blockSize, outBlock, 0, blockSize)
        outPos = 0
        System.arraycopy(inBlock, 0, prevBlock, 0, blockSize)
    }
}
