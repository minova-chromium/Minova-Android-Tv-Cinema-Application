package com.minova.cinema

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import com.minova.cinema.data.local.ArtworkCachePreferences
import com.minova.cinema.data.local.OFFLINE_ARTWORK_MAX_BYTES
import com.minova.cinema.data.local.TRANSIENT_ARTWORK_MAX_BYTES
import com.minova.cinema.data.local.offlineArtworkDirectory

class MinovaCinemaApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader {
        val keepOffline = ArtworkCachePreferences(context).isEnabled()
        val directory = if (keepOffline) {
            offlineArtworkDirectory(context)
        } else {
            context.cacheDir.resolve("transient_artwork")
        }
        val maxBytes = if (keepOffline) OFFLINE_ARTWORK_MAX_BYTES else TRANSIENT_ARTWORK_MAX_BYTES
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(maxBytes)
                    .build()
            }
            .build()
    }
}
