package app.k56.player.dsp

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A decoded impulse response. ViPER4Android kernels are plain WAV files with an
 * `.irs` name; they may be 16-bit, 24-bit or 32-bit float and are often stereo.
 */
data class ImpulseResponse(
    val sampleRate: Int,
    val channels: Int,
    /** Interleaved samples in [-1, 1]. */
    val samples: FloatArray,
    val name: String,
) {
    val frames: Int get() = if (channels > 0) samples.size / channels else 0
}

object WavReader {

    fun read(file: File): ImpulseResponse = file.inputStream().use { read(it, file.nameWithoutExtension) }

    fun read(input: InputStream, name: String): ImpulseResponse {
        val bytes = input.readBytes()
        require(bytes.size >= 44) { "Datei zu klein für WAV" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) { "Kein RIFF-Header" }
        require(bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()) { "Kein WAVE-Header" }

        var offset = 12
        var format = 0
        var channels = 1
        var sampleRate = 44100
        var bits = 16
        var dataStart = -1
        var dataLength = 0

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = leInt(bytes, offset + 4)
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    format = leShort(bytes, body).toInt()
                    channels = leShort(bytes, body + 2).toInt().coerceAtLeast(1)
                    sampleRate = leInt(bytes, body + 4)
                    bits = leShort(bytes, body + 14).toInt()
                    // WAVE_FORMAT_EXTENSIBLE: the real code sits in the GUID tail.
                    if (format == 0xFFFE && size >= 40) {
                        format = leShort(bytes, body + 24).toInt()
                    }
                }
                "data" -> {
                    dataStart = body
                    dataLength = size
                }
            }
            offset = body + size + (size and 1)
        }
        require(dataStart >= 0) { "Kein data-Chunk" }
        require(channels in 1..2) { "Nur mono/stereo unterstützt ($channels Kanäle)" }

        val bytesPerSample = when {
            format == 3 && bits == 32 -> 4
            format == 1 && bits == 16 -> 2
            format == 1 && bits == 24 -> 3
            format == 1 && bits == 32 -> 4
            else -> throw IllegalArgumentException("Format $format mit $bits Bit nicht unterstützt")
        }
        val usable = minOf(dataLength, bytes.size - dataStart)
        val count = usable / bytesPerSample
        val out = FloatArray(count)
        var p = dataStart
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            out[i] = when (bytesPerSample) {
                2 -> buffer.getShort(p) / 32768f
                3 -> {
                    val b0 = bytes[p].toInt() and 0xFF
                    val b1 = bytes[p + 1].toInt() and 0xFF
                    val b2 = bytes[p + 2].toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                }
                else -> buffer.getFloat(p)
            }
            p += bytesPerSample
        }
        return ImpulseResponse(sampleRate, channels, out, name)
    }

    private fun leShort(bytes: ByteArray, at: Int): Short {
        val lo = bytes[at].toInt() and 0xFF
        val hi = bytes[at + 1].toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    private fun leInt(bytes: ByteArray, at: Int): Int {
        val b0 = bytes[at].toInt() and 0xFF
        val b1 = bytes[at + 1].toInt() and 0xFF
        val b2 = bytes[at + 2].toInt() and 0xFF
        val b3 = bytes[at + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    /** Cheap sanity check without decoding the whole file. */
    fun looksLikeWav(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 44) return false
            val head = ByteArray(12)
            raf.readFully(head)
            String(head, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(head, 8, 4, Charsets.US_ASCII) == "WAVE"
        }
    }.getOrDefault(false)
}
