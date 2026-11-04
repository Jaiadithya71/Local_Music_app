package com.contentagent.vibestream

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache

/** Artwork is disposable cache, separate from explicit offline downloads. */
class VibeStreamApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork_v1")).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(false)
        .build()
}
