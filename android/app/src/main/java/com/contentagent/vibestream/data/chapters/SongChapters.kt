package com.contentagent.vibestream.data.chapters

import java.io.Serializable

data class SongChapter(val title: String, val startMs: Long) : Serializable

/** Publisher-supplied navigation points, never inferred equal-length song cuts. */
object SongChapters {
    fun validated(chapters: List<SongChapter>, durationMs: Long = 0): List<SongChapter> {
        val clean = chapters.filter { it.title.isNotBlank() && it.startMs >= 0 &&
            (durationMs <= 0 || it.startMs < durationMs) }
            .sortedBy { it.startMs }.distinctBy { it.startMs }
        // A lone timestamp is commonly a highlight/link, not a tracklist.
        if (clean.size < 2 || clean.zipWithNext().any { (a,b) -> b.startMs - a.startMs < 1000 }) return emptyList()
        return clean
    }

    private val stamp = Regex("(?<![\\d:])(?:\\d{1,3}:)?\\d{1,2}:[0-5]\\d(?![\\d:])")
    fun fromDescription(text: String, durationMs: Long = 0): List<SongChapter> {
        val points = text.lines().mapNotNull { line ->
            val matches = stamp.findAll(line).toList()
            if (matches.size != 1) return@mapNotNull null
            val m = matches.single()
            val parts = m.value.split(':').map { it.toLong() }
            if (parts.size == 3 && parts[1] >= 60) return@mapNotNull null
            val seconds = parts.fold(0L) { a, v -> a * 60 + v }
            val before = line.substring(0, m.range.first).trim().trim('-', '|', ' ', '[', '(', '►', '•')
            val after = line.substring(m.range.last + 1).trim().trim('-', '|', ' ', ']', ')', '►', '•')
            val title = if (after.isNotBlank()) after else before
            if (title.isBlank() || title.contains("http", true)) null else SongChapter(title, seconds * 1000)
        }
        // Reject scrambled lists rather than silently treating unrelated times as a sequence.
        if (points.zipWithNext().any { (a,b) -> b.startMs <= a.startMs }) return emptyList()
        return validated(points, durationMs)
    }
    fun next(chapters: List<SongChapter>, positionMs: Long): Long? =
        chapters.firstOrNull { it.startMs > positionMs + 250 }?.startMs

    fun previous(chapters: List<SongChapter>, positionMs: Long): Long? {
        if (chapters.size < 2) return null
        val current = chapters.indexOfLast { it.startMs <= positionMs + 250 }
        if (current < 0) return 0L
        return if (positionMs - chapters[current].startMs > 3000) chapters[current].startMs
        else chapters.getOrNull(current - 1)?.startMs ?: 0L
    }
}
