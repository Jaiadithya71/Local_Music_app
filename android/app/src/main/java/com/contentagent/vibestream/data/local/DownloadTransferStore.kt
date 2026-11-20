package com.contentagent.vibestream.data.local

import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session transfer state, separate from verified offline files. Never saves partial files as music. */
data class DownloadTransfer(
    val song: Song,
    val phase: String = "Queued",
    val bytes: Long = 0,
    val totalBytes: Long = -1,
    val etaSeconds: Long? = null
) {
    val percent: Int? get() = if (totalBytes > 0) ((bytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else null
    val status: String get() = when (phase) {
        "Downloading" -> "${percent?.let { "$it%" } ?: "Size unknown"} · ${etaSeconds?.let { if (it >= 60) "About ${it / 60}m ${it % 60}s left" else "About ${it}s left" } ?: "Estimating time left..."}"
        "Verifying" -> "100% · Verifying audio..."
        else -> phase
    }
}

class DownloadTransferStore {
    private val mutable = MutableStateFlow<Map<String, DownloadTransfer>>(emptyMap())
    val transfers = mutable.asStateFlow()
    fun queued(song: Song) { mutable.update { it + (song.id to DownloadTransfer(song)) } }
    fun phase(song: Song, phase: String) { mutable.update { it + (song.id to (it[song.id] ?: DownloadTransfer(song)).copy(phase = phase, etaSeconds = null)) } }
    fun progress(song: Song, bytes: Long, total: Long, elapsedMillis: Long) {
        val eta = if (total > 0 && bytes > 0 && elapsedMillis >= 1000) {
            kotlin.math.ceil((total - bytes).coerceAtLeast(0).toDouble() / bytes * elapsedMillis / 1000).toLong()
        } else null
        mutable.update { it + (song.id to DownloadTransfer(song, "Downloading", bytes, total, eta)) }
    }
    fun finished(id: String) { mutable.update { it - id } }
}
