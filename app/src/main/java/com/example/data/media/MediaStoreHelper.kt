package com.example.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreHelper(private val context: Context) {

    suspend fun loadPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = it.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: "Photo_$id"
                    val dateAdded = it.getLong(dateColumn) * 1000
                    val size = it.getLong(sizeColumn)
                    val width = if (widthColumn >= 0) it.getInt(widthColumn) else 0
                    val height = if (heightColumn >= 0) it.getInt(heightColumn) else 0
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    photos.add(
                        MediaItem(
                            id = id,
                            title = name,
                            subtitle = if (width > 0 && height > 0) "${width}x${height}" else "Gallery Photo",
                            uri = contentUri.toString(),
                            type = MediaType.PHOTO,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            resolution = if (width > 0) "${width}x${height}" else "HD Photo"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (photos.isEmpty()) {
            photos.addAll(getDemoPhotos())
        }
        photos
    }

    suspend fun loadVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthColumn = it.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = it.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: "Video_$id"
                    val duration = it.getLong(durationColumn)
                    val size = it.getLong(sizeColumn)
                    val dateAdded = it.getLong(dateColumn) * 1000
                    val width = if (widthColumn >= 0) it.getInt(widthColumn) else 0
                    val height = if (heightColumn >= 0) it.getInt(heightColumn) else 0
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    videos.add(
                        MediaItem(
                            id = id,
                            title = name,
                            subtitle = if (width > 0 && height > 0) "${width}p HD" else "Video Clip",
                            uri = contentUri.toString(),
                            type = MediaType.VIDEO,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            resolution = if (width > 0) "${width}x${height}" else "1080p"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (videos.isEmpty()) {
            videos.addAll(getDemoVideos())
        }
        videos
    }

    suspend fun loadMusic(): List<MediaItem> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Track_$id"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val album = it.getString(albumColumn) ?: "Unknown Album"
                    val duration = it.getLong(durationColumn)
                    val size = it.getLong(sizeColumn)
                    val albumId = it.getLong(albumIdColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    songs.add(
                        MediaItem(
                            id = id,
                            title = title,
                            subtitle = artist,
                            artist = artist,
                            album = album,
                            uri = contentUri.toString(),
                            type = MediaType.MUSIC,
                            durationMs = duration,
                            sizeBytes = size,
                            thumbnailUri = artworkUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (songs.isEmpty()) {
            songs.addAll(getDemoMusic())
        }
        songs
    }

    private fun getDemoPhotos(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = 1001,
                title = "Neon Cyber City Skyline",
                subtitle = "3840x2160 • 4K Wallpaper",
                uri = "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1200",
                type = MediaType.PHOTO,
                resolution = "3840x2160",
                isDemo = true
            ),
            MediaItem(
                id = 1002,
                title = "Cosmic Aurora Borealis",
                subtitle = "4000x2600 • Ultra HD",
                uri = "https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=1200",
                type = MediaType.PHOTO,
                resolution = "4000x2600",
                isDemo = true
            ),
            MediaItem(
                id = 1003,
                title = "Retro Synthwave Sunset",
                subtitle = "2560x1440 • Vibrant Art",
                uri = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200",
                type = MediaType.PHOTO,
                resolution = "2560x1440",
                isDemo = true
            ),
            MediaItem(
                id = 1004,
                title = "Futuristic Sports Supercar",
                subtitle = "3840x2160 • Speed",
                uri = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=1200",
                type = MediaType.PHOTO,
                resolution = "3840x2160",
                isDemo = true
            ),
            MediaItem(
                id = 1005,
                title = "Emerald Mountain Lake",
                subtitle = "3000x2000 • Nature",
                uri = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1200",
                type = MediaType.PHOTO,
                resolution = "3000x2000",
                isDemo = true
            ),
            MediaItem(
                id = 1006,
                title = "Cyberpunk Samurai Glow",
                subtitle = "1920x1080 • Concept Art",
                uri = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
                type = MediaType.PHOTO,
                resolution = "1920x1080",
                isDemo = true
            )
        )
    }

    private fun getDemoVideos(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = 2001,
                title = "Big Buck Bunny Cinema Reel",
                subtitle = "1080p 60fps • 4K Open Movie",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                thumbnailUri = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=600",
                type = MediaType.VIDEO,
                durationMs = 596000,
                resolution = "1920x1080",
                isDemo = true
            ),
            MediaItem(
                id = 2002,
                title = "Elephants Dream Sci-Fi Clip",
                subtitle = "1080p • 3D Animation Showcase",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                thumbnailUri = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                type = MediaType.VIDEO,
                durationMs = 653000,
                resolution = "1920x1080",
                isDemo = true
            ),
            MediaItem(
                id = 2003,
                title = "For Bigger Blazes 4K HDR",
                subtitle = "Chromecast Ultra Demo Film",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                thumbnailUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                type = MediaType.VIDEO,
                durationMs = 15000,
                resolution = "3840x2160",
                isDemo = true
            ),
            MediaItem(
                id = 2004,
                title = "Tears of Steel Futuristic Sci-Fi",
                subtitle = "VFX & CGI Action Scene",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                thumbnailUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                type = MediaType.VIDEO,
                durationMs = 734000,
                resolution = "1920x1080",
                isDemo = true
            )
        )
    }

    private fun getDemoMusic(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = 3001,
                title = "Synthwave Midnight Pulse",
                subtitle = "Neon Dreams • Electronic Synth",
                artist = "Neon Dreams",
                album = "Cyber Horizon 2025",
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                thumbnailUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600",
                type = MediaType.MUSIC,
                durationMs = 372000,
                isDemo = true
            ),
            MediaItem(
                id = 3002,
                title = "Lo-Fi Beats & Rainy Nights",
                subtitle = "Chillhop Collective",
                artist = "Chillhop Collective",
                album = "Coffee & Rain",
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                thumbnailUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                type = MediaType.MUSIC,
                durationMs = 423000,
                isDemo = true
            ),
            MediaItem(
                id = 3003,
                title = "Acoustic Sunset Serenade",
                subtitle = "Acoustic Melody",
                artist = "Acoustic Melody",
                album = "Golden Hour Sessions",
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                thumbnailUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                type = MediaType.MUSIC,
                durationMs = 345000,
                isDemo = true
            ),
            MediaItem(
                id = 3004,
                title = "Electro Bass Euphoria",
                subtitle = "DJ Hyperion",
                artist = "DJ Hyperion",
                album = "Bassline Voltage",
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                thumbnailUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600",
                type = MediaType.MUSIC,
                durationMs = 502000,
                isDemo = true
            )
        )
    }
}
