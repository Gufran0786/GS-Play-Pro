package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.StreamHistoryEntity
import com.example.data.local.VaultItemEntity
import com.example.data.local.VaultRepository
import com.example.data.local.VaultSecurityManager
import com.example.data.media.MediaStoreHelper
import com.example.data.model.AIMediaCard
import com.example.data.model.AIMediaCategory
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.remote.GeminiMediaExplorerService
import com.example.player.AudioPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    PHOTOS,
    VIDEOS,
    MUSIC,
    STREAM_URL,
    AI_EXPLORER,
    VAULT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaHelper = MediaStoreHelper(application)
    private val db = AppDatabase.getDatabase(application)
    private val vaultRepo = VaultRepository(db.vaultDao(), db.streamHistoryDao())
    val securityManager = VaultSecurityManager(application)
    private val aiService = GeminiMediaExplorerService()
    val audioPlayer = AudioPlayerController(application)

    // Current Tab
    private val _currentTab = MutableStateFlow(AppTab.PHOTOS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Media Collections
    private val _photos = MutableStateFlow<List<MediaItem>>(emptyList())
    val photos: StateFlow<List<MediaItem>> = _photos.asStateFlow()

    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()

    private val _music = MutableStateFlow<List<MediaItem>>(emptyList())
    val music: StateFlow<List<MediaItem>> = _music.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    // Active Fullscreen Player / Viewers
    private val _activePhoto = MutableStateFlow<MediaItem?>(null)
    val activePhoto: StateFlow<MediaItem?> = _activePhoto.asStateFlow()

    private val _activeVideo = MutableStateFlow<MediaItem?>(null)
    val activeVideo: StateFlow<MediaItem?> = _activeVideo.asStateFlow()

    private val _showNowPlayingSheet = MutableStateFlow(false)
    val showNowPlayingSheet: StateFlow<Boolean> = _showNowPlayingSheet.asStateFlow()

    // Stream URL Player
    private val _streamUrlInput = MutableStateFlow("")
    val streamUrlInput: StateFlow<String> = _streamUrlInput.asStateFlow()

    private val _selectedStreamType = MutableStateFlow(MediaType.STREAM_URL)
    val selectedStreamType: StateFlow<MediaType> = _selectedStreamType.asStateFlow()

    val streamHistory: StateFlow<List<StreamHistoryEntity>> = vaultRepo.allStreamHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Media Explorer
    private val _aiCategory = MutableStateFlow(AIMediaCategory.ALL)
    val aiCategory: StateFlow<AIMediaCategory> = _aiCategory.asStateFlow()

    private val _aiSearchQuery = MutableStateFlow("")
    val aiSearchQuery: StateFlow<String> = _aiSearchQuery.asStateFlow()

    private val _aiResults = MutableStateFlow<List<AIMediaCard>>(emptyList())
    val aiResults: StateFlow<List<AIMediaCard>> = _aiResults.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Private Vault State
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    val vaultItems: StateFlow<List<VaultItemEntity>> = vaultRepo.allVaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadAllMedia()
        performAiSearch("", AIMediaCategory.ALL)
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _isLoadingMedia.value = true
            _photos.value = mediaHelper.loadPhotos()
            _videos.value = mediaHelper.loadVideos()
            _music.value = mediaHelper.loadMusic()
            _isLoadingMedia.value = false
        }
    }

    fun openPhoto(item: MediaItem) {
        _activePhoto.value = item
    }

    fun closePhoto() {
        _activePhoto.value = null
    }

    fun openVideo(item: MediaItem) {
        _activeVideo.value = item
    }

    fun closeVideo() {
        _activeVideo.value = null
    }

    fun openNowPlaying(show: Boolean) {
        _showNowPlayingSheet.value = show
    }

    fun playAudioItem(item: MediaItem, queue: List<MediaItem> = _music.value) {
        audioPlayer.playTrack(item, queue)
    }

    // Stream URL Player
    fun updateStreamUrlInput(url: String) {
        _streamUrlInput.value = url
    }

    fun setStreamType(type: MediaType) {
        _selectedStreamType.value = type
    }

    fun launchStream(customUrl: String? = null) {
        val url = (customUrl ?: _streamUrlInput.value).trim()
        if (url.isBlank()) {
            Toast.makeText(getApplication(), "Please enter or paste a valid URL", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanLower = url.lowercase()
        val detectedType = when {
            _selectedStreamType.value == MediaType.PHOTO ||
                    cleanLower.endsWith(".jpg") || cleanLower.endsWith(".png") ||
                    cleanLower.endsWith(".jpeg") || cleanLower.endsWith(".webp") ||
                    cleanLower.contains("unsplash.com") -> MediaType.PHOTO

            _selectedStreamType.value == MediaType.MUSIC ||
                    cleanLower.endsWith(".mp3") || cleanLower.endsWith(".aac") ||
                    cleanLower.endsWith(".ogg") || cleanLower.endsWith(".wav") ||
                    cleanLower.contains("soundhelix.com") -> MediaType.MUSIC

            else -> MediaType.VIDEO
        }

        viewModelScope.launch {
            vaultRepo.recordStreamPlay(
                title = url.substringAfterLast("/").take(30).ifEmpty { "Online Stream" },
                url = url,
                type = detectedType.name
            )
        }

        when (detectedType) {
            MediaType.PHOTO -> {
                val photoItem = MediaItem(
                    title = "Web Photo Stream",
                    subtitle = url,
                    uri = url,
                    type = MediaType.PHOTO
                )
                openPhoto(photoItem)
            }
            MediaType.MUSIC -> {
                val audioItem = MediaItem(
                    title = "Online Audio Stream",
                    subtitle = url,
                    uri = url,
                    artist = "Direct Stream",
                    type = MediaType.MUSIC
                )
                playAudioItem(audioItem, listOf(audioItem))
                openNowPlaying(true)
            }
            else -> {
                val videoItem = MediaItem(
                    title = "Direct Video Stream",
                    subtitle = url,
                    uri = url,
                    type = MediaType.VIDEO
                )
                openVideo(videoItem)
            }
        }
    }

    fun toggleStreamBookmark(id: Long, current: Boolean) {
        viewModelScope.launch {
            vaultRepo.toggleBookmarkStream(id, current)
        }
    }

    fun clearStreamHistory() {
        viewModelScope.launch {
            vaultRepo.clearStreamHistory()
        }
    }

    // AI Media Search
    fun updateAiQuery(q: String) {
        _aiSearchQuery.value = q
    }

    fun setAiCategory(cat: AIMediaCategory) {
        _aiCategory.value = cat
        performAiSearch(_aiSearchQuery.value, cat)
    }

    fun performAiSearch(query: String = _aiSearchQuery.value, category: AIMediaCategory = _aiCategory.value) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResults.value = aiService.searchMedia(query, category)
            _isAiLoading.value = false
        }
    }

    // Private Vault
    fun unlockVault(pin: String): Boolean {
        val success = securityManager.verifyPin(pin)
        if (success) {
            _isVaultUnlocked.value = true
        }
        return success
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun setupVaultPin(pin: String, question: String, answer: String): Boolean {
        val ok = securityManager.setupPin(pin, question, answer)
        if (ok) {
            _isVaultUnlocked.value = true
        }
        return ok
    }

    fun addVaultItem(
        title: String,
        content: String,
        notes: String = "",
        type: MediaType = MediaType.PRIVATE_NOTE,
        category: String = "Private"
    ) {
        viewModelScope.launch {
            val entity = VaultItemEntity(
                title = title,
                contentOrUri = content,
                secretNotes = notes,
                mediaType = type.name,
                category = category
            )
            vaultRepo.addVaultItem(entity)
            Toast.makeText(getApplication(), "Item locked safely in Private Vault", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteVaultItem(item: VaultItemEntity) {
        viewModelScope.launch {
            vaultRepo.deleteVaultItem(item)
            Toast.makeText(getApplication(), "Removed from Vault", Toast.LENGTH_SHORT).show()
        }
    }

    fun addMediaToVault(item: MediaItem) {
        addVaultItem(
            title = item.title,
            content = item.uri,
            notes = "Moved from ${item.type.name} Gallery. Resolution/Info: ${item.resolution} ${item.subtitle}",
            type = item.type,
            category = "Imported Media"
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAndRelease()
    }
}
