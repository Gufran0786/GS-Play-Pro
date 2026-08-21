package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.MediaDownloadManager
import com.example.data.local.AppDatabase
import com.example.data.local.StreamHistoryEntity
import com.example.data.local.VaultItemEntity
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

    private val mediaStoreHelper = MediaStoreHelper(application)
    private val database = AppDatabase.getDatabase(application)
    private val vaultDao = database.vaultDao()
    private val historyDao = database.streamHistoryDao()

    val securityManager = VaultSecurityManager(application)
    val audioPlayer = AudioPlayerController(application)
    val downloadManager = MediaDownloadManager(application)
    private val aiService = GeminiMediaExplorerService(application)

    // Navigation & Tabs
    private val _currentTab = MutableStateFlow(AppTab.PHOTOS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Local Media States
    private val _photos = MutableStateFlow<List<MediaItem>>(emptyList())
    val photos: StateFlow<List<MediaItem>> = _photos.asStateFlow()

    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()

    private val _music = MutableStateFlow<List<MediaItem>>(emptyList())
    val music: StateFlow<List<MediaItem>> = _music.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    // Active Viewers & Players
    private val _activePhoto = MutableStateFlow<MediaItem?>(null)
    val activePhoto: StateFlow<MediaItem?> = _activePhoto.asStateFlow()

    private val _activeVideo = MutableStateFlow<MediaItem?>(null)
    val activeVideo: StateFlow<MediaItem?> = _activeVideo.asStateFlow()

    private val _showNowPlayingSheet = MutableStateFlow(false)
    val showNowPlayingSheet: StateFlow<Boolean> = _showNowPlayingSheet.asStateFlow()

    // Direct Stream State
    private val _streamUrlInput = MutableStateFlow("")
    val streamUrlInput: StateFlow<String> = _streamUrlInput.asStateFlow()

    private val _selectedStreamType = MutableStateFlow(MediaType.STREAM_URL)
    val selectedStreamType: StateFlow<MediaType> = _selectedStreamType.asStateFlow()

    val streamHistory: StateFlow<List<StreamHistoryEntity>> = historyDao.getAllStreamHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // AI Media Explorer State
    private val _aiSearchQuery = MutableStateFlow("")
    val aiSearchQuery: StateFlow<String> = _aiSearchQuery.asStateFlow()

    private val _aiCategory = MutableStateFlow(AIMediaCategory.ALL)
    val aiCategory: StateFlow<AIMediaCategory> = _aiCategory.asStateFlow()

    private val _aiResults = MutableStateFlow<List<AIMediaCard>>(emptyList())
    val aiResults: StateFlow<List<AIMediaCard>> = _aiResults.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _customApiKey = MutableStateFlow(aiService.getCustomApiKey())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Private Vault State
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    val vaultItems: StateFlow<List<VaultItemEntity>> = vaultDao.getAllVaultItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadAllMedia()
        performAiSearch("")
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _isLoadingMedia.value = true
            _photos.value = mediaStoreHelper.loadPhotos()
            _videos.value = mediaStoreHelper.loadVideos()
            _music.value = mediaStoreHelper.loadMusic()
            _isLoadingMedia.value = false
        }
    }

    // Media Viewer Openers
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

    fun playAudioItem(item: MediaItem, queue: List<MediaItem> = _music.value) {
        audioPlayer.playTrack(item, queue)
    }

    fun openNowPlaying(show: Boolean) {
        _showNowPlayingSheet.value = show
    }

    // Direct Stream Methods
    fun updateStreamUrlInput(url: String) {
        _streamUrlInput.value = url
    }

    fun setStreamType(type: MediaType) {
        _selectedStreamType.value = type
    }

    fun launchStream(explicitUrl: String? = null) {
        val targetUrl = (explicitUrl ?: _streamUrlInput.value).trim()
        if (targetUrl.isBlank()) return

        val detectedType = if (_selectedStreamType.value != MediaType.STREAM_URL) {
            _selectedStreamType.value
        } else {
            val lower = targetUrl.lowercase()
            when {
                lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".aac") || lower.endsWith(".flac") -> MediaType.MUSIC
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> MediaType.PHOTO
                else -> MediaType.VIDEO
            }
        }

        viewModelScope.launch {
            historyDao.insertOrUpdateStream(
                StreamHistoryEntity(
                    streamUrl = targetUrl,
                    title = targetUrl.substringAfterLast("/").take(30).ifEmpty { "Online Stream" },
                    mediaType = detectedType.name
                )
            )
        }

        val item = MediaItem(
            title = targetUrl.substringAfterLast("/").take(30).ifEmpty { "Online Media" },
            subtitle = targetUrl,
            uri = targetUrl,
            type = detectedType
        )

        when (detectedType) {
            MediaType.PHOTO -> openPhoto(item)
            MediaType.MUSIC -> {
                playAudioItem(item, listOf(item))
                openNowPlaying(true)
            }
            else -> openVideo(item)
        }
    }

    fun toggleStreamBookmark(id: Long, current: Boolean) {
        viewModelScope.launch {
            historyDao.setBookmark(id, !current)
        }
    }

    fun clearStreamHistory() {
        viewModelScope.launch {
            historyDao.clearHistory()
        }
    }

    // Downloads
    fun downloadMedia(item: MediaItem) {
        downloadManager.downloadMedia(
            title = item.title,
            url = item.uri,
            mediaType = item.type
        )
    }

    fun downloadUrl(url: String, type: MediaType = MediaType.VIDEO) {
        val clean = url.trim()
        val detected = if (type == MediaType.STREAM_URL) {
            val lower = clean.lowercase()
            when {
                lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".aac") -> MediaType.MUSIC
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> MediaType.PHOTO
                else -> MediaType.VIDEO
            }
        } else {
            type
        }
        val title = clean.substringAfterLast("/").substringBefore("?").ifEmpty { "Media_File" }
        downloadManager.downloadMedia(
            title = title,
            url = clean,
            mediaType = detected
        )
    }

    // AI Media Explorer
    fun updateAiQuery(query: String) {
        _aiSearchQuery.value = query
    }

    fun setAiCategory(category: AIMediaCategory) {
        _aiCategory.value = category
        performAiSearch(_aiSearchQuery.value)
    }

    fun performAiSearch(query: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResults.value = aiService.searchMedia(query, _aiCategory.value)
            _isAiLoading.value = false
        }
    }

    fun saveCustomApiKey(key: String) {
        aiService.saveCustomApiKey(key)
        _customApiKey.value = key
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
        val success = securityManager.setupPin(pin, question, answer)
        if (success) {
            _isVaultUnlocked.value = true
        }
        return success
    }

    fun addMediaToVault(item: MediaItem) {
        viewModelScope.launch {
            vaultDao.insertVaultItem(
                VaultItemEntity(
                    title = item.title,
                    contentOrUri = item.uri,
                    secretNotes = "Locked from ${item.type.name} library",
                    mediaType = item.type.name,
                    category = "MEDIA"
                )
            )
        }
    }

    fun addVaultItem(title: String, content: String, notes: String, type: MediaType, category: String) {
        viewModelScope.launch {
            vaultDao.insertVaultItem(
                VaultItemEntity(
                    title = title,
                    contentOrUri = content,
                    secretNotes = notes,
                    mediaType = type.name,
                    category = category
                )
            )
        }
    }

    fun deleteVaultItem(item: VaultItemEntity) {
        viewModelScope.launch {
            vaultDao.deleteVaultItem(item)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stopAndRelease()
    }
}
