package com.example.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.example.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DownloadRecord(
    val id: Long,
    val title: String,
    val url: String,
    val mediaType: MediaType,
    val filename: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Downloading..."
)

class MediaDownloadManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    private val _downloadHistory = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val downloadHistory: StateFlow<List<DownloadRecord>> = _downloadHistory.asStateFlow()

    fun downloadMedia(
        title: String,
        url: String,
        mediaType: MediaType
    ): Long {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            Toast.makeText(context, "Cannot download: Invalid online URL", Toast.LENGTH_SHORT).show()
            return -1L
        }

        val sanitizedTitle = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(40).ifEmpty { "download" }
        val extension = when (mediaType) {
            MediaType.PHOTO -> if (cleanUrl.contains(".png")) "png" else "jpg"
            MediaType.MUSIC -> "mp3"
            MediaType.VIDEO -> "mp4"
            else -> {
                val urlExt = MimeTypeMap.getFileExtensionFromUrl(cleanUrl)
                if (urlExt.isNotBlank()) urlExt else "mp4"
            }
        }

        val filename = "GSPlayPro_${sanitizedTitle}_${System.currentTimeMillis()}.$extension"

        val destinationDir = when (mediaType) {
            MediaType.PHOTO -> Environment.DIRECTORY_PICTURES
            MediaType.MUSIC -> Environment.DIRECTORY_MUSIC
            MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val mimeType = when (mediaType) {
            MediaType.PHOTO -> if (extension == "png") "image/png" else "image/jpeg"
            MediaType.MUSIC -> "audio/mpeg"
            MediaType.VIDEO -> "video/mp4"
            else -> "*/*"
        }

        return try {
            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                setTitle(title.ifEmpty { "GS Play Pro Media" })
                setDescription("Downloading $filename for offline use")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(destinationDir, filename)
                setMimeType(mimeType)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager?.enqueue(request) ?: -1L
            if (downloadId != -1L) {
                val record = DownloadRecord(
                    id = downloadId,
                    title = title,
                    url = cleanUrl,
                    mediaType = mediaType,
                    filename = filename,
                    status = "Downloading"
                )
                _downloadHistory.value = listOf(record) + _downloadHistory.value
                Toast.makeText(context, "📥 Download started: $filename", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to start download", Toast.LENGTH_SHORT).show()
            }
            downloadId
        } catch (e: Exception) {
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            -1L
        }
    }
}
