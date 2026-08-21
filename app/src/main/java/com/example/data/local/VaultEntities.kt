package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.MediaType

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val contentOrUri: String,
    val secretNotes: String = "",
    val mediaType: String = MediaType.PRIVATE_NOTE.name,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val streamUrl: String,
    val mediaType: String,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false,
    val thumbnail: String? = null
)
