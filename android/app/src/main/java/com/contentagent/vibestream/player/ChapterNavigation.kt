package com.contentagent.vibestream.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.contentagent.vibestream.data.chapters.SongChapter
import com.contentagent.vibestream.data.chapters.SongChapters

/** Boundaries travel with each MediaItem so Bluetooth/session commands need no Activity. */
object ChapterNavigation {
    fun extras(chapters: List<SongChapter>): Bundle = Bundle().apply {
        putLongArray("song_starts", chapters.map { it.startMs }.toLongArray())
        putStringArrayList("song_titles", ArrayList(chapters.map { it.title }))
    }
    fun chapters(item: MediaItem?): List<SongChapter> {
        val b = item?.mediaMetadata?.extras ?: return emptyList()
        val starts = b.getLongArray("song_starts") ?: return emptyList()
        val titles = b.getStringArrayList("song_titles") ?: return emptyList()
        if (starts.size != titles.size) return emptyList()
        return SongChapters.validated(starts.indices.map { SongChapter(titles[it], starts[it]) })
    }
    fun next(item: MediaItem?, positionMs: Long) = SongChapters.next(chapters(item), positionMs)
    fun previous(item: MediaItem?, positionMs: Long) = SongChapters.previous(chapters(item), positionMs)
}
