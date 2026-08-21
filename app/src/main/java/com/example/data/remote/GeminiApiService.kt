package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AIMediaCard
import com.example.data.model.AIMediaCategory
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiMediaExplorerService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun searchMedia(query: String, category: AIMediaCategory): List<AIMediaCard> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val results = fetchFromGemini(query, category, apiKey)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.e("GeminiMediaService", "API call error: ${e.message}")
            }
        }

        // Fallback intelligent curated catalog based on query and category
        return@withContext getCuratedMediaResults(query, category)
    }

    private fun fetchFromGemini(query: String, category: AIMediaCategory, apiKey: String): List<AIMediaCard> {
        val prompt = """
            You are GS Play Pro's Media Discovery AI. The user is searching for: "$query" in category: ${category.name}.
            Return a JSON array of 5 to 8 media results (Movies, Songs, Videos, Photos/Wallpapers).
            Each JSON object MUST have these exact keys:
            - "title": string
            - "subtitle": string (e.g. "Action, Sci-Fi • 2024" or "Arijit Singh • Romantic")
            - "category": string (one of "MOVIES", "MUSIC", "VIDEOS", "PHOTOS", "TRENDING")
            - "description": string (short 1-2 sentence overview or lyrics excerpt)
            - "rating": string (e.g. "8.8/10 IMDb" or "4.9★" or "450M Views")
            - "yearOrDuration": string (e.g. "2h 45m" or "3:45 min" or "4K UHD")
            - "imageUrl": string (a high quality direct Unsplash or TMDB placeholder image URL)
            - "streamOrWebUrl": string (direct media preview URL or web link for streaming/trailer/download)
            - "sourcePlatform": string (e.g. "Netflix", "Spotify", "YouTube", "Unsplash", "Amazon Prime")
            - "tags": array of 2-3 string tags
            
            ONLY return valid raw JSON array with NO markdown ticks.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return emptyList()

        val responseJson = JSONObject(responseBody)
        val candidates = responseJson.optJSONArray("candidates") ?: return emptyList()
        val firstCandidate = candidates.optJSONObject(0) ?: return emptyList()
        val text = firstCandidate.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text") ?: return emptyList()

        val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsedArray = JSONArray(cleanJson)
        val list = mutableListOf<AIMediaCard>()

        for (i in 0 until parsedArray.length()) {
            val obj = parsedArray.getJSONObject(i)
            val catStr = obj.optString("category", category.name).uppercase()
            val catEnum = try {
                AIMediaCategory.valueOf(catStr)
            } catch (e: Exception) {
                category
            }

            val tagsList = mutableListOf<String>()
            val tagsArr = obj.optJSONArray("tags")
            if (tagsArr != null) {
                for (j in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.getString(j))
                }
            }

            list.add(
                AIMediaCard(
                    title = obj.optString("title", "Untitled Media"),
                    category = catEnum,
                    subtitle = obj.optString("subtitle", ""),
                    description = obj.optString("description", ""),
                    rating = obj.optString("rating", "4.8★"),
                    yearOrDuration = obj.optString("yearOrDuration", "HD"),
                    imageUrl = obj.optString("imageUrl", "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800"),
                    streamOrWebUrl = obj.optString("streamOrWebUrl", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                    sourcePlatform = obj.optString("sourcePlatform", "Web Stream"),
                    tags = if (tagsList.isEmpty()) listOf("Popular", "HD") else tagsList,
                    type = when (catEnum) {
                        AIMediaCategory.MOVIES, AIMediaCategory.VIDEOS -> MediaType.VIDEO
                        AIMediaCategory.MUSIC -> MediaType.MUSIC
                        AIMediaCategory.PHOTOS -> MediaType.PHOTO
                        else -> MediaType.STREAM_URL
                    }
                )
            )
        }
        return list
    }

    private fun getCuratedMediaResults(query: String, category: AIMediaCategory): List<AIMediaCard> {
        val allCurated = listOf(
            // Movies
            AIMediaCard(
                title = "Interstellar: Beyond Space & Time",
                category = AIMediaCategory.MOVIES,
                subtitle = "Sci-Fi, Adventure • Christopher Nolan",
                description = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
                rating = "8.7/10 IMDb",
                yearOrDuration = "2h 49m • 4K",
                imageUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                sourcePlatform = "GS Cinema Hub",
                tags = listOf("Blockbuster", "Sci-Fi", "Oscar Winner"),
                type = MediaType.VIDEO
            ),
            AIMediaCard(
                title = "Kalki 2898 AD / Epic Sci-Fi Saga",
                category = AIMediaCategory.MOVIES,
                subtitle = "Mythology & Futuristic Cyberpunk • 2024",
                description = "In a post-apocalyptic dystopian world, the battle between light and dark forces reaches an epic climax.",
                rating = "8.2/10 IMDb",
                yearOrDuration = "3h 01m • IMAX 4K",
                imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                sourcePlatform = "Streaming Plus",
                tags = listOf("Action", "VFX", "Trending"),
                type = MediaType.VIDEO
            ),
            AIMediaCard(
                title = "Dune: Part Two",
                category = AIMediaCategory.MOVIES,
                subtitle = "Desert Epic • Denis Villeneuve",
                description = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                rating = "8.9/10 IMDb",
                yearOrDuration = "2h 46m • Dolby Vision",
                imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                sourcePlatform = "Cinema Max",
                tags = listOf("Action", "Sci-Fi", "Masterpiece"),
                type = MediaType.VIDEO
            ),

            // Music / Songs
            AIMediaCard(
                title = "Kesariya / Romantic Melodies",
                category = AIMediaCategory.MUSIC,
                subtitle = "Arijit Singh • Pritam • Bollywood Hits",
                description = "Soul-touching romantic melody with soaring orchestral string arrangements and mesmerizing vocals.",
                rating = "950M Streams",
                yearOrDuration = "4:28 min • 320kbps",
                imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800",
                streamOrWebUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                sourcePlatform = "GS Audio Vault",
                tags = listOf("Romantic", "Bollywood", "Arijit Singh"),
                type = MediaType.MUSIC
            ),
            AIMediaCard(
                title = "Starboy & After Hours / Synthwave Pop",
                category = AIMediaCategory.MUSIC,
                subtitle = "The Weeknd • Daft Punk • Retro Neon",
                description = "Pumping electro-pop beats fused with dark 80s synthesizer textures and electrifying melodies.",
                rating = "2.1B Streams",
                yearOrDuration = "3:50 min • Hi-Res Audio",
                imageUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800",
                streamOrWebUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                sourcePlatform = "Spotify / GS Stream",
                tags = listOf("Synthwave", "Billboard #1", "Dance"),
                type = MediaType.MUSIC
            ),
            AIMediaCard(
                title = "Illuminati / Modern Hip-Hop Beat",
                category = AIMediaCategory.MUSIC,
                subtitle = "Sushin Shyam • Dabzee • Viral Track",
                description = "High octane club rhythm with infectious hook and pulsing 808 bass lines.",
                rating = "180M Views",
                yearOrDuration = "3:10 min • Studio Quality",
                imageUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800",
                streamOrWebUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                sourcePlatform = "Music Pro",
                tags = listOf("Party", "Viral", "Bass Boost"),
                type = MediaType.MUSIC
            ),

            // Videos
            AIMediaCard(
                title = "4K Drone Reel: Swiss Alps & Norwegian Fjords",
                category = AIMediaCategory.VIDEOS,
                subtitle = "Ultra HD 60fps • Scenic Nature Cinema",
                description = "Breathtaking aerial cinematography across snow-capped peaks, crystal glaciers, and misty waterfalls.",
                rating = "4K HDR • 60 FPS",
                yearOrDuration = "12:40 min",
                imageUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                sourcePlatform = "YouTube 4K",
                tags = listOf("Nature", "4K HDR", "Drone"),
                type = MediaType.VIDEO
            ),
            AIMediaCard(
                title = "Future of Artificial Intelligence & Robotics 2026",
                category = AIMediaCategory.VIDEOS,
                subtitle = "Tech Documentary • Deep Dive",
                description = "Exploring quantum computing, humanoid robotics, and the new era of generative artificial intelligence.",
                rating = "1.8M Views • 99% Likes",
                yearOrDuration = "18:25 min",
                imageUrl = "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                sourcePlatform = "TechStream",
                tags = listOf("Tech", "AI", "Futuristic"),
                type = MediaType.VIDEO
            ),

            // Photos / Wallpapers
            AIMediaCard(
                title = "Neon Cyberpunk Tokyo Rain Alley",
                category = AIMediaCategory.PHOTOS,
                subtitle = "8K Masterpiece Wallpaper • OLED Black",
                description = "Glowing holographic billboards reflecting on wet asphalt with neon magenta and cyan rain ambiance.",
                rating = "8K UHD • 12MB",
                yearOrDuration = "7680x4320",
                imageUrl = "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1200",
                streamOrWebUrl = "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1600",
                sourcePlatform = "Unsplash Pro",
                tags = listOf("Cyberpunk", "OLED Wallpaper", "8K"),
                type = MediaType.PHOTO
            ),
            AIMediaCard(
                title = "Aurora Borealis Galaxy Reflection",
                category = AIMediaCategory.PHOTOS,
                subtitle = "Night Sky Astrophotography • Iceland",
                description = "Vibrant emerald northern lights dancing over frozen volcanic lagoons under sparkling stars.",
                rating = "Ultra HD • 4K",
                yearOrDuration = "3840x2160",
                imageUrl = "https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=1200",
                streamOrWebUrl = "https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=1600",
                sourcePlatform = "AstroGallery",
                tags = listOf("Aurora", "Nature", "Wallpaper"),
                type = MediaType.PHOTO
            )
        )

        val q = query.trim().lowercase()
        return allCurated.filter { item ->
            val matchCategory = category == AIMediaCategory.ALL ||
                    (category == AIMediaCategory.TRENDING) ||
                    item.category == category

            val matchQuery = q.isBlank() ||
                    item.title.lowercase().contains(q) ||
                    item.subtitle.lowercase().contains(q) ||
                    item.tags.any { it.lowercase().contains(q) } ||
                    item.description.lowercase().contains(q)

            matchCategory && matchQuery
        }.ifEmpty {
            allCurated
        }
    }
}
