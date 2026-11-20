package com.contentagent.vibestream.data.local

import android.content.Context
import android.os.Environment
import android.media.MediaExtractor
import android.media.MediaFormat
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
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
    private val offlineStorage: OfflineStorage,
    private val resolveAudio: suspend (String) -> String? = { extractor.resolveAudioStream(it) }
) {

    private val client = OkHttpClient()
    private val transfers = DownloadTransferStore()
    val downloadTransfers = transfers.transfers

    // Map of videoId -> progress float (0.0f to 1.0f)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val downloadingSet = ConcurrentHashMap.newKeySet<String>()

    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors = _downloadErrors.asStateFlow()
    private val queue = TrackDownloadQueue(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        isSaved = { offlineStorage.isDownloaded(it) || downloadingSet.contains(it) },
        onQueued = { song ->
            transfers.queued(song)
            _downloadErrors.update { it - song.id }
            updateProgress(song.id, 0f)
        },
        transfer = { song -> downloadTrack(song) }
    )

    fun enqueueTracks(songs: List<Song>) = queue.enqueue(songs)

    fun isDownloading(videoId: String): Boolean = _downloadProgressMap.value.containsKey(videoId)

    suspend fun downloadTrack(song: Song, onComplete: ((Boolean, String?) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (offlineStorage.isDownloaded(song.id)) {
            removeProgress(song.id)
            transfers.finished(song.id)
            onComplete?.invoke(true, offlineStorage.getLocalSong(song.id)?.localFilePath)
            return@withContext
        }

        if (!downloadingSet.add(song.id)) return@withContext
        _downloadErrors.update { it - song.id }
        transfers.phase(song, "Resolving audio...")
        updateProgress(song.id, 0.05f)

        var completedFile: File? = null
        try {
            // 1. Resolve direct audio stream URL if not already known
            val streamUrl = song.streamUrl ?: resolveAudio(song.id)
            if (streamUrl.isNullOrEmpty()) {
                downloadingSet.remove(song.id)
                removeProgress(song.id)
                _downloadErrors.update { it + (song.id to "Could not resolve audio. Tap to retry.") }
                transfers.phase(song, "Could not resolve audio. Tap to retry.")
                onComplete?.invoke(false, "Could not resolve audio stream URL")
                return@withContext
            }

            transfers.phase(song, "Connecting...")
            updateProgress(song.id, 0.15f)

            // 2. Download to a temporary file. The MIME type, not an assumed codec,
            // chooses the final extension. An interrupted transfer never enters the library.
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            if (!musicDir.exists()) musicDir.mkdirs()
            val safeId = song.id.replace("[^a-zA-Z0-9_-]".toRegex(), "")
            val safeTitle = song.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(40)
            val tempFile = File.createTempFile("${safeId}_", ".part", musicDir)
            var targetFile: File? = null
            try {
                val request = Request.Builder().url(streamUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("Download failed with HTTP ${response.code}")
                    val mediaType = response.body?.contentType()?.toString()?.lowercase() ?: ""
                    val extension = when {
                        "audio/mp4" in mediaType || "audio/x-m4a" in mediaType -> "m4a"
                        "audio/webm" in mediaType || "video/webm" in mediaType -> "webm"
                        "audio/ogg" in mediaType -> "ogg"
                        "audio/mpeg" in mediaType -> "mp3"
                        else -> throw IllegalStateException("Unknown audio format: $mediaType")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val expected = body.contentLength()
                    var downloaded = 0L
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    var lastUpdate = startedAt
                    transfers.progress(song, 0, expected, 0)
                    FileOutputStream(tempFile).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 250 || downloaded == expected) {
                                    transfers.progress(song, downloaded, expected, now - startedAt)
                                    if (expected > 0) updateProgress(song.id,
                                        (0.15f + downloaded.toFloat() / expected * 0.85f).coerceIn(0.15f, 1f))
                                    lastUpdate = now
                                }
                            }
                        }
                        output.fd.sync()
                    }
                    if (downloaded == 0L || (expected >= 0 && downloaded != expected))
                        throw IllegalStateException("Incomplete download: $downloaded of $expected bytes")
                    // A nonempty HTTP body can still be an error page or corrupt container.
                    // Let Android parse the saved bytes and require an audio track.
                    transfers.phase(song, "Verifying")
                    val inspector = MediaExtractor()
                    try {
                        inspector.setDataSource(tempFile.absolutePath)
                        val hasAudio = (0 until inspector.trackCount).any { index ->
                            inspector.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                        }
                        if (!hasAudio) throw IllegalStateException("Downloaded file has no playable audio track")
                    } finally {
                        inspector.release()
                    }
                    val finalFile = File(musicDir, "${safeId}_$safeTitle.$extension")
                    if (!tempFile.renameTo(finalFile)) throw IllegalStateException("Could not save finished audio")
                    targetFile = finalFile
                    completedFile = finalFile
                }
            } finally {
                tempFile.delete()
            }

            // 4. Save metadata only after the verified file has been renamed.
            val downloadedSong = song.copy(
                streamUrl = streamUrl,
                localFilePath = targetFile!!.absolutePath,
                isDownloaded = true,
                isDownloading = false,
                downloadProgress = 1f
            )
            offlineStorage.saveSong(downloadedSong)

            downloadingSet.remove(song.id)
            removeProgress(song.id)
            transfers.finished(song.id)
            onComplete?.invoke(true, targetFile!!.absolutePath)

        } catch (e: Exception) {
            completedFile?.delete()
            if (e is CancellationException) {
                downloadingSet.remove(song.id)
                removeProgress(song.id)
                transfers.finished(song.id)
                throw e
            }
            _downloadErrors.update { it + (song.id to "Download failed. Tap to retry.") }
            transfers.phase(song, "Download failed. Tap to retry.")
            e.printStackTrace()
            downloadingSet.remove(song.id)
            removeProgress(song.id)
            onComplete?.invoke(false, e.localizedMessage)
        }
    }

    private fun updateProgress(videoId: String, progress: Float) {
        _downloadProgressMap.update { it + (videoId to progress) }
    }

    private fun removeProgress(videoId: String) {
        _downloadProgressMap.update { it - videoId }
    }
}
