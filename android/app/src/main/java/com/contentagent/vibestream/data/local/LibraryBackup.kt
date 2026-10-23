package com.contentagent.vibestream.data.local

import android.content.Context
import android.net.Uri
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-file JSON export/import of the whole library (liked, playlists, recents)
 * so playlists survive reinstalls and can move between devices.
 */
class LibraryBackup(private val context: Context, private val library: LibraryStore) {
    private val gson = Gson()

    data class Backup(
        val version: Int = 1,
        val liked: List<Song>? = null,
        val recent: List<Song>? = null,
        val playlists: List<Playlist>? = null
    )

    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = Backup(
                liked = library.liked.value,
                recent = library.recent.value,
                playlists = library.playlists.value
            )
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext false
            out.use { it.write(gson.toJson(data).toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) { false }
    }

    suspend fun importFrom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext false
            val text = input.use { String(it.readBytes(), Charsets.UTF_8) }
            val data = gson.fromJson(text, Backup::class.java) ?: return@withContext false
            library.mergeBackup(
                data.liked ?: emptyList(),
                data.recent ?: emptyList(),
                data.playlists ?: emptyList()
            )
            true
        } catch (e: Exception) { false }
    }
}
