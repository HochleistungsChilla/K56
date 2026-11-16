package app.k56.player.dsp

/** Minimal radix-2 FFT; sizes are always powers of two here. */
class Fft(val size: Int) {

    private val levels: Int
    private val cosTable: FloatArray
    private val sinTable: FloatArray
    private val reverse: IntArray

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        levels = Integer.numberOfTrailingZeros(size)
        cosTable = FloatArray(size / 2) { i -> Math.cos(2.0 * Math.PI * i / size).toFloat() }
        sinTable = FloatArray(size / 2) { i -> Math.sin(2.0 * Math.PI * i / size).toFloat() }
        reverse = IntArray(size) { i -> Integer.reverse(i) ushr (32 - levels) }
    }

    /** In-place forward transform. */
    fun forward(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = reverse[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var span = 2
        while (span <= size) {
            val half = span / 2
            val step = size / span
            var start = 0
            while (start < size) {
                var k = 0
                for (i in start until start + half) {
                    val j = i + half
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val tr = re[j] * wr - im[j] * wi
                    val ti = re[j] * wi + im[j] * wr
                    re[j] = re[i] - tr
                    im[j] = im[i] - ti
                    re[i] += tr
                    im[i] += ti
                    k += step
                }
                start += span
            }
            span = span shl 1
        }
    }

    /** In-place inverse transform, scaled by 1/size. */
    fun inverse(re: FloatArray, im: FloatArray) {
        for (i in im.indices) im[i] = -im[i]
        forward(re, im)
        val scale = 1f / size
        for (i in re.indices) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }
}
