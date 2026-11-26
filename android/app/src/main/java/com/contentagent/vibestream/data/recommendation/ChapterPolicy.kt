package com.contentagent.vibestream.data.recommendation

data class Chapter(val title: String, val startSec: Long)

/** Song boundaries inside one long recording, from YouTube chapters or timestamp lines in the description. */
object ChapterPolicy {
    private const val TIME = "(?:\\d{1,2}:)?\\d{1,2}:\\d{2}"
    private val lead = Regex("^\\W{0,3}(?:\\d{1,3}[.)]\\s*)?[\\[(]?($TIME)[\\])]?\\s*[-\u2013\u2014:|.]*\\s*(.+?)\\s*$")
    private val trail = Regex("^(.+?)\\s*[-\u2013\u2014:|]\\s*[\\[(]?($TIME)[\\])]?\\s*$")

    fun parseTime(t: String): Long {
        val p = t.split(":").map { it.toLongOrNull() ?: return -1L }
        return when (p.size) {
            2 -> p[0] * 60 + p[1]
            3 -> p[0] * 3600 + p[1] * 60 + p[2]
            else -> -1L
        }
    }

    private fun clean(s: String) = s.trim().trim('-', '\u2013', '\u2014', ':', '|', '.', ' ').take(80)

    fun fromDescription(desc: String, durationSec: Long): List<Chapter> {
        val found = ArrayList<Chapter>()
        for (raw in desc.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = lead.find(line)
            val (t, title) = when {
                m != null -> m.groupValues[1] to m.groupValues[2]
                else -> trail.find(line)?.let { it.groupValues[2] to it.groupValues[1] } ?: continue
            }
            val sec = parseTime(t)
            val name = clean(title)
            if (sec < 0 || name.isEmpty()) continue
            if (found.isNotEmpty() && sec <= found.last().startSec) continue
            if (durationSec > 0 && sec >= durationSec) continue
            found.add(Chapter(name, sec))
        }
        if (found.size < 3) return emptyList()
        return if (found.first().startSec > 15) listOf(Chapter("Start", 0)) + found else found
    }

    fun indexAt(chapters: List<Chapter>, posMs: Long): Int {
        var idx = -1
        for ((i, c) in chapters.withIndex()) if (c.startSec * 1000L <= posMs) idx = i
        return idx
    }

    /** Start of the next song in the recording, or null when there is none (normal Next applies). */
    fun nextStartMs(chapters: List<Chapter>, posMs: Long): Long? =
        chapters.firstOrNull { it.startSec * 1000L > posMs + 500L }?.startSec?.times(1000L)

    /** More than 3 s into a song: restart it. Otherwise the previous song. Null: normal Previous applies. */
    fun prevStartMs(chapters: List<Chapter>, posMs: Long): Long? {
        val i = indexAt(chapters, posMs)
        if (i < 0) return null
        val cur = chapters[i].startSec * 1000L
        if (posMs - cur > 3000L) return cur
        return if (i > 0) chapters[i - 1].startSec * 1000L else null
    }
}
