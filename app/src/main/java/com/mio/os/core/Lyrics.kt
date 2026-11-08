package com.mio.os.core

/** One synced lyric line: milliseconds from track start plus the text. */
data class LrcLine(val ms: Long, val text: String)

/**
 * Minimal LRC parser for sidecar `.lrc` files sitting next to local audio
 * (same file name, `.lrc` extension). Untimed or malformed lines are skipped.
 */
object Lyrics {

    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?]""")

    fun parse(lrc: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        lrc.lineSequence().forEach { line ->
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    2 -> (frac.toLongOrNull() ?: 0L) * 10
                    3 -> frac.toLongOrNull() ?: 0L
                    else -> 0L
                }
                out += LrcLine(min * 60_000 + sec * 1_000 + ms, text)
            }
        }
        return out.sortedBy { it.ms }
    }

    fun lrcNameFor(audioName: String): String {
        val base = audioName.substringBeforeLast('.', audioName)
        return "$base.lrc"
    }
}
