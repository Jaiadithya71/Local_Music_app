package com.contentagent.vibestream.data.model

import java.io.Serializable

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Long = 0L,
    val durationText: String = "",
    val thumbnailUrl: String = "",
    var streamUrl: String? = null,
    var localFilePath: String? = null,
    var isDownloaded: Boolean = false,
    val kind: String = "song", // "song" (YouTube Music) or "video" (YouTube-wide)
    var isDownloading: Boolean = false,
    var downloadProgress: Float = 0f
) : Serializable {
    /**
     * Returns the playable URI: local file URI if downloaded, otherwise the resolved stream URL.
     */
    val playableUri: String?
        get() = if (isDownloaded && !localFilePath.isNullOrEmpty()) "file://$localFilePath" else streamUrl
}
