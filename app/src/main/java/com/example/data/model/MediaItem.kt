package com.example.data.model

import android.net.Uri

enum class MediaType {
    PHOTO,
    VIDEO,
    MUSIC,
    STREAM_URL,
    PRIVATE_NOTE
}

data class MediaItem(
    val id: Long = 0,
    val title: String,
    val subtitle: String = "",
    val uri: String,
    val path: String = "",
    val type: MediaType,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val resolution: String = "",
    val thumbnailUri: String? = null,
    val isFavorite: Boolean = false,
    val isDemo: Boolean = false
) {
    fun getFormattedDuration(): String {
        if (durationMs <= 0) return "--:--"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun getFormattedSize(): String {
        if (sizeBytes <= 0) return ""
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
