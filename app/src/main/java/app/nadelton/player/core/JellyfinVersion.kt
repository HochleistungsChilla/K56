package app.nadelton.player.core

/**
 * Jellyfin reports versions like "10.10.7", "10.11.0" or "10.10.7.0".
 * Comparison is numeric per component so "10.10.10" > "10.10.7".
 */
object JellyfinVersion {

    fun parse(version: String): List<Int> {
        val core = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (core.isBlank()) return emptyList()
        return core.split('.').map { part ->
            // "7b" and similar suffixes still compare by their leading number.
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    /** Negative when [a] < [b], positive when [a] > [b], zero when equal. */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    fun isSupported(version: String, minimum: String): Boolean =
        parse(version).isNotEmpty() && compare(version, minimum) >= 0
}
