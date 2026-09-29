package com.contentagent.vibestream.data.local

import android.content.Context
import android.os.Environment
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * On-Device Music Download Pipeline.
 * Streams and writes the raw audio file directly to Android internal/external storage.
 */
class DownloadManager(
    private val context: Context,
    private val extractor: YouTubeExtractor,
    private val offlineStorage: OfflineStorage
) {

    private val client = OkHttpClient()

    // Map of videoId -> progress float (0.0f to 1.0f)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()

    fun isDownloading(videoId: String): Boolean = downloadingSet.contains(videoId)

    suspend fun downloadTrack(song: Song, onComplete: ((Boolean, String?) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (downloadingSet.contains(song.id)) return@withContext
        if (offlineStorage.isDownloaded(song.id)) {
            onComplete?.invoke(true, offlineStorage.getLocalSong(song.id)?.localFilePath)
            return@withContext
        }

        downloadingSet.add(song.id)
        updateProgress(song.id, 0.05f)

        try {
            // 1. Resolve direct audio stream URL if not already known
            val streamUrl = song.streamUrl ?: extractor.resolveAudioStream(song.id)
            if (streamUrl.isNullOrEmpty()) {
                downloadingSet.remove(song.id)
                removeProgress(song.id)
                onComplete?.invoke(false, "Could not resolve audio stream URL")
                return@withContext
            }

            updateProgress(song.id, 0.15f)

            // 2. Prepare target music file on device
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            if (!musicDir.exists()) musicDir.mkdirs()

            // Clean sanitized file name and ID to prevent any path traversal
            val safeId = song.id.replace("[^a-zA-Z0-9_-]".toRegex(), "")
            val safeTitle = song.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(40)
            val targetFile = File(musicDir, "${safeId}_$safeTitle.m4a")

            // 3. Stream bytes to file
            val request = Request.Builder().url(streamUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    downloadingSet.remove(song.id)
                    removeProgress(song.id)
                    onComplete?.invoke(false, "Download failed with HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = 0.15f + (downloadedBytes.toFloat() / totalBytes.toFloat()) * 0.85f
                        updateProgress(song.id, progress.coerceIn(0.15f, 1f))
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            // 4. Save metadata to local offline storage
            val downloadedSong = song.copy(
                streamUrl = streamUrl,
                localFilePath = targetFile.absolutePath,
                isDownloaded = true,
                isDownloading = false,
                downloadProgress = 1f
            )
            offlineStorage.saveSong(downloadedSong)

            downloadingSet.remove(song.id)
            removeProgress(song.id)
            onComplete?.invoke(true, targetFile.absolutePath)

        } catch (e: Exception) {
            e.printStackTrace()
            downloadingSet.remove(song.id)
            removeProgress(song.id)
            onComplete?.invoke(false, e.localizedMessage)
        }
    }

    private fun updateProgress(videoId: String, progress: Float) {
        val current = _downloadProgressMap.value.toMutableMap()
        current[videoId] = progress
        _downloadProgressMap.value = current
    }

    private fun removeProgress(videoId: String) {
        val current = _downloadProgressMap.value.toMutableMap()
        current.remove(videoId)
        _downloadProgressMap.value = current
    }
}
