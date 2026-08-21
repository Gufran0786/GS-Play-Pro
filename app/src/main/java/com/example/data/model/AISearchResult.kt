package com.example.data.model

enum class AIMediaCategory {
    ALL,
    MOVIES,
    MUSIC,
    VIDEOS,
    PHOTOS,
    TRENDING
}

data class AIMediaCard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val category: AIMediaCategory,
    val subtitle: String = "",
    val description: String = "",
    val rating: String = "",
    val yearOrDuration: String = "",
    val imageUrl: String = "",
    val streamOrWebUrl: String = "",
    val tags: List<String> = emptyList(),
    val sourcePlatform: String = "Web",
    val type: MediaType = MediaType.STREAM_URL
)
