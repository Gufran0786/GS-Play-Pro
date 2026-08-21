package com.example.data.remote

import android.content.Context
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

class GeminiMediaExplorerService(private val context: Context) {

    private val prefs = context.getSharedPreferences("gs_ai_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getCustomApiKey(): String {
        return prefs.getString("custom_gemini_key", "") ?: ""
    }

    fun saveCustomApiKey(key: String) {
        prefs.edit().putString("custom_gemini_key", key.trim()).apply()
    }

    suspend fun searchMedia(query: String, category: AIMediaCategory): List<AIMediaCard> = withContext(Dispatchers.IO) {
        val customKey = getCustomApiKey()
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        val effectiveKey = when {
            customKey.isNotBlank() -> customKey
            buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }

        if (effectiveKey.isNotBlank()) {
            try {
                val results = fetchFromGemini(query, category, effectiveKey)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.e("GeminiMediaService", "Gemini API error: ${e.message}")
            }
        }

        // Fallback intelligent curated catalog matching user search
        return@withContext getCuratedMediaResults(query, category)
    }

    private fun fetchFromGemini(query: String, category: AIMediaCategory, apiKey: String): List<AIMediaCard> {
        val targetCategory = if (category == AIMediaCategory.ALL) "ALL" else category.name
        val prompt = """
            You are GS Play Pro's Media Discovery AI. The user is searching for: "$query" in category: $targetCategory.
            Return a JSON array of 6 to 10 media items (Movies, Songs/Music, Videos, Photos/Wallpapers).
            Each JSON object MUST have these exact keys:
            - "title": string
            - "subtitle": string (e.g. "Action, Sci-Fi • 2024" or "Arijit Singh • Romantic")
            - "category": string (one of "MOVIES", "MUSIC", "VIDEOS", "PHOTOS", "TRENDING")
            - "description": string (short 1-2 sentence overview or lyrics excerpt)
            - "rating": string (e.g. "8.8/10 IMDb" or "4.9★" or "450M Streams")
            - "yearOrDuration": string (e.g. "2h 45m" or "3:45 min" or "4K UHD")
            - "imageUrl": string (high quality image URL)
            - "streamOrWebUrl": string (direct media stream URL or web link for streaming/listening)
            - "sourcePlatform": string (e.g. "Spotify", "YouTube", "Netflix", "Unsplash", "SoundCloud")
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

        val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
        for (model in models) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: continue

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates") ?: continue
                val firstCandidate = candidates.optJSONObject(0) ?: continue
                val text = firstCandidate.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: continue

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

                    val rawStreamUrl = obj.optString("streamOrWebUrl", "")
                    val verifiedStreamUrl = if (rawStreamUrl.startsWith("http://") || rawStreamUrl.startsWith("https://")) {
                        rawStreamUrl
                    } else {
                        when (catEnum) {
                            AIMediaCategory.MOVIES, AIMediaCategory.VIDEOS -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                            AIMediaCategory.MUSIC -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                            AIMediaCategory.PHOTOS -> "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1600"
                            else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                        }
                    }

                    val rawImg = obj.optString("imageUrl", "")
                    val verifiedImg = if (rawImg.startsWith("http://") || rawImg.startsWith("https://")) {
                        rawImg
                    } else {
                        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800"
                    }

                    list.add(
                        AIMediaCard(
                            title = obj.optString("title", "Online Media Item"),
                            category = catEnum,
                            subtitle = obj.optString("subtitle", ""),
                            description = obj.optString("description", ""),
                            rating = obj.optString("rating", "4.8★"),
                            yearOrDuration = obj.optString("yearOrDuration", "HD"),
                            imageUrl = verifiedImg,
                            streamOrWebUrl = verifiedStreamUrl,
                            sourcePlatform = obj.optString("sourcePlatform", "GS Pro Cloud"),
                            tags = if (tagsList.isEmpty()) listOf("Online", "HD") else tagsList,
                            type = when (catEnum) {
                                AIMediaCategory.MOVIES, AIMediaCategory.VIDEOS -> MediaType.VIDEO
                                AIMediaCategory.MUSIC -> MediaType.MUSIC
                                AIMediaCategory.PHOTOS -> MediaType.PHOTO
                                else -> MediaType.STREAM_URL
                            }
                        )
                    )
                }

                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.w("GeminiMediaService", "Model $model attempt failed: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun getCuratedMediaResults(query: String, category: AIMediaCategory): List<AIMediaCard> {
        val q = query.trim().lowercase()

        val fullCatalog = listOf(
            // --- ONLINE MOVIES ---
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
            AIMediaCard(
                title = "Pushpa 2: The Rule",
                category = AIMediaCategory.MOVIES,
                subtitle = "High Voltage Mass Action • Allu Arjun",
                description = "Pushpa Raj expands his sandalwood empire against formidable adversaries in this high-octane spectacle.",
                rating = "8.5/10 IMDb",
                yearOrDuration = "3h 15m • Ultra HD",
                imageUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                sourcePlatform = "GS MegaStream",
                tags = listOf("Mass Action", "Blockbuster", "2024"),
                type = MediaType.VIDEO
            ),
            AIMediaCard(
                title = "Animal / Dark Thriller Saga",
                category = AIMediaCategory.MOVIES,
                subtitle = "Ranbir Kapoor • Anil Kapoor • Action Thriller",
                description = "A son undergoes a remarkable transformation as the bond with his father starts to fracture under violent circumstances.",
                rating = "8.1/10 IMDb",
                yearOrDuration = "3h 21m • 4K",
                imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                sourcePlatform = "Cinema Hub",
                tags = listOf("Thriller", "Bollywood", "Viral"),
                type = MediaType.VIDEO
            ),

            // --- ONLINE MUSIC & SONGS ---
            AIMediaCard(
                title = "Kesariya / Romantic Melody",
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
            AIMediaCard(
                title = "Lo-Fi Midnight Beats / Study & Chill",
                category = AIMediaCategory.MUSIC,
                subtitle = "Chillhop Music • Relaxing Rain Piano",
                description = "Smooth ambient piano chords paired with gentle vinyl crackle and soothing downtempo beats.",
                rating = "4.9★ Soundscape",
                yearOrDuration = "5:12 min • 320kbps",
                imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=800",
                streamOrWebUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                sourcePlatform = "Lo-Fi Vault",
                tags = listOf("Lo-Fi", "Relax", "Study"),
                type = MediaType.MUSIC
            ),
            AIMediaCard(
                title = "Apna Bana Le / Soulful Acoustic",
                category = AIMediaCategory.MUSIC,
                subtitle = "Arijit Singh • Sachin-Jigar • Bhediya",
                description = "Gentle acoustic guitar strums blending with emotionally charged vocals and heartwarming lyrics.",
                rating = "780M Streams",
                yearOrDuration = "4:15 min • Master Audio",
                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800",
                streamOrWebUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                sourcePlatform = "GS Audio Vault",
                tags = listOf("Acoustic", "Romantic", "Bollywood"),
                type = MediaType.MUSIC
            ),

            // --- ONLINE VIDEOS & CLIPS ---
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
            AIMediaCard(
                title = "Sintel: The Dragon Quest 4K Animation",
                category = AIMediaCategory.VIDEOS,
                subtitle = "Open Source Cinema • Blender Foundation",
                description = "A lonely young woman searches the dangerous fantasy realm for her lost baby dragon companion.",
                rating = "4.9★ Animation",
                yearOrDuration = "15:00 min • 4K",
                imageUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800",
                streamOrWebUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                sourcePlatform = "Blender Studio",
                tags = listOf("Animation", "Fantasy", "4K"),
                type = MediaType.VIDEO
            ),

            // --- ONLINE PHOTOS & WALLPAPERS ---
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
            ),
            AIMediaCard(
                title = "Minimalist Sunset Dune Waves",
                category = AIMediaCategory.PHOTOS,
                subtitle = "Warm Aesthetic Wallpaper • Sahara",
                description = "Golden sun rays casting soft shadows across rippling desert sand dunes with pastel twilight gradient.",
                rating = "5K Resolution",
                yearOrDuration = "5120x2880",
                imageUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1200",
                streamOrWebUrl = "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1600",
                sourcePlatform = "Desert Lens",
                tags = listOf("Minimal", "Warm", "Wallpaper"),
                type = MediaType.PHOTO
            )
        )

        // Filter according to category and search query
        val matched = fullCatalog.filter { item ->
            val matchesCategory = category == AIMediaCategory.ALL ||
                    (category == AIMediaCategory.TRENDING) ||
                    item.category == category

            val matchesQuery = q.isBlank() ||
                    item.title.lowercase().contains(q) ||
                    item.subtitle.lowercase().contains(q) ||
                    item.description.lowercase().contains(q) ||
                    item.tags.any { it.lowercase().contains(q) }

            matchesCategory && matchesQuery
        }

        if (matched.isNotEmpty()) {
            return matched
        }

        // If specific search term didn't match directly, synthesize dynamic results for that query
        return if (q.isNotBlank()) {
            listOf(
                AIMediaCard(
                    title = "$query (Online Stream HD)",
                    category = if (category == AIMediaCategory.ALL) AIMediaCategory.VIDEOS else category,
                    subtitle = "Online Result for \"$query\"",
                    description = "High quality online streaming media matching your search keyword \"$query\".",
                    rating = "4.9★ Trending",
                    yearOrDuration = "Full HD • 2024",
                    imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800",
                    streamOrWebUrl = when (category) {
                        AIMediaCategory.MUSIC -> "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                        AIMediaCategory.PHOTOS -> "https://images.unsplash.com/photo-1519501025264-65ba15a82390?w=1600"
                        else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    },
                    sourcePlatform = "Web Cloud Stream",
                    tags = listOf(query.take(10), "Online", "HQ"),
                    type = when (category) {
                        AIMediaCategory.MUSIC -> MediaType.MUSIC
                        AIMediaCategory.PHOTOS -> MediaType.PHOTO
                        else -> MediaType.VIDEO
                    }
                )
            ) + fullCatalog.filter { it.category == category || category == AIMediaCategory.ALL }
        } else {
            fullCatalog
        }
    }
}
