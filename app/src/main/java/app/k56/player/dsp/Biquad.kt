package app.k56.player.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Direct-form biquad used for the parametric bands and the shelves. */
class Biquad {

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    /** Peaking band. */
    fun peaking(sampleRate: Int, freq: Float, q: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw = cos(w0)
        val a0 = 1 + alpha / a
        b0 = ((1 + alpha * a) / a0).toFloat()
        b1 = (-2 * cosw / a0).toFloat()
        b2 = ((1 - alpha * a) / a0).toFloat()
        a1 = (-2 * cosw / a0).toFloat()
        a2 = ((1 - alpha / a) / a0).toFloat()
    }

    /** Low shelf, used as the bass control. */
    fun lowShelf(sampleRate: Int, freq: Float, gainDb: Float, slope: Float = 0.9f) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / 2.0 * Math.sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        val twoSqrtAalpha = 2 * Math.sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosw + twoSqrtAalpha
        b0 = (a * ((a + 1) - (a - 1) * cosw + twoSqrtAalpha) / a0).toFloat()
        b1 = (2 * a * ((a - 1) - (a + 1) * cosw) / a0).toFloat()
        b2 = (a * ((a + 1) - (a - 1) * cosw - twoSqrtAalpha) / a0).toFloat()
        a1 = (-2 * ((a - 1) + (a + 1) * cosw) / a0).toFloat()
        a2 = (((a + 1) + (a - 1) * cosw - twoSqrtAalpha) / a0).toFloat()
    }

    /** High shelf, used as the treble control. */
    fun highShelf(sampleRate: Int, freq: Float, gainDb: Float, slope: Float = 0.9f) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / 2.0 * Math.sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        val twoSqrtAalpha = 2 * Math.sqrt(a) * alpha
        val a0 = (a + 1) - (a - 1) * cosw + twoSqrtAalpha
        b0 = (a * ((a + 1) + (a - 1) * cosw + twoSqrtAalpha) / a0).toFloat()
        b1 = (-2 * a * ((a - 1) + (a + 1) * cosw) / a0).toFloat()
        b2 = (a * ((a + 1) + (a - 1) * cosw - twoSqrtAalpha) / a0).toFloat()
        a1 = (2 * ((a - 1) - (a + 1) * cosw) / a0).toFloat()
        a2 = (((a + 1) - (a - 1) * cosw - twoSqrtAalpha) / a0).toFloat()
    }

    /** Direct coefficient assignment (used by the ViPER DDC cascade). */
    fun setCoefficients(nb0: Float, nb1: Float, nb2: Float, na1: Float, na2: Float) {
        b0 = nb0; b1 = nb1; b2 = nb2
        a1 = na1; a2 = na2
    }

    fun process(input: Float): Float {
        val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input
        y2 = y1; y1 = out
        return out
    }
}
