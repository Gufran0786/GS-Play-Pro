package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.PhotoViewerDialog
import com.example.player.VideoPlayerDialog
import com.example.ui.components.GSPlayBottomBar
import com.example.ui.components.GSPlayTopBar
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.screens.AISearchScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.PrivateVaultScreen
import com.example.ui.screens.StreamLinkScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GSPlayProTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GSPlayProTheme {
                GSPlayProApp()
            }
        }
    }
}

@Composable
fun GSPlayProApp(
    viewModel: MainViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val music by viewModel.music.collectAsState()
    val isLoadingMedia by viewModel.isLoadingMedia.collectAsState()

    val activePhoto by viewModel.activePhoto.collectAsState()
    val activeVideo by viewModel.activeVideo.collectAsState()
    val showNowPlaying by viewModel.showNowPlayingSheet.collectAsState()

    val currentTrack by viewModel.audioPlayer.currentTrack.collectAsState()
    val isAudioPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val audioProgress by viewModel.audioPlayer.currentPositionMs.collectAsState()
    val audioDuration by viewModel.audioPlayer.durationMs.collectAsState()
    val playbackSpeed by viewModel.audioPlayer.playbackSpeed.collectAsState()
    val isLooping by viewModel.audioPlayer.isLooping.collectAsState()
    val isShuffle by viewModel.audioPlayer.isShuffle.collectAsState()
    val amplitudes by viewModel.audioPlayer.visualizerAmplitudes.collectAsState()

    val streamUrlInput by viewModel.streamUrlInput.collectAsState()
    val selectedStreamType by viewModel.selectedStreamType.collectAsState()
    val streamHistory by viewModel.streamHistory.collectAsState()

    val aiQuery by viewModel.aiSearchQuery.collectAsState()
    val aiCategory by viewModel.aiCategory.collectAsState()
    val aiResults by viewModel.aiResults.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val vaultItems by viewModel.vaultItems.collectAsState()

    Scaffold(
        topBar = {
            GSPlayTopBar(
                currentTab = currentTab,
                isVaultUnlocked = isVaultUnlocked,
                onRefresh = { viewModel.loadAllMedia() },
                onOpenVault = { viewModel.selectTab(AppTab.VAULT) },
                onLockVault = { viewModel.lockVault() }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mini Player docked right above bottom navigation bar
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    MiniPlayerBar(
                        currentTrack = currentTrack,
                        isPlaying = isAudioPlaying,
                        progressMs = audioProgress,
                        durationMs = audioDuration,
                        onTogglePlayPause = { viewModel.audioPlayer.togglePlayPause() },
                        onNext = { viewModel.audioPlayer.next() },
                        onClick = { viewModel.openNowPlaying(true) }
                    )
                }

                GSPlayBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        },
        containerColor = DarkSurface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.PHOTOS -> {
                    PhotosScreen(
                        photos = photos,
                        isLoading = isLoadingMedia,
                        onPhotoClick = { viewModel.openPhoto(it) },
                        onAddToVault = { viewModel.addMediaToVault(it) },
                        onRefresh = { viewModel.loadAllMedia() }
                    )
                }
                AppTab.VIDEOS -> {
                    VideosScreen(
                        videos = videos,
                        isLoading = isLoadingMedia,
                        onVideoClick = { viewModel.openVideo(it) },
                        onAddToVault = { viewModel.addMediaToVault(it) },
                        onRefresh = { viewModel.loadAllMedia() }
                    )
                }
                AppTab.MUSIC -> {
                    MusicScreen(
                        musicList = music,
                        currentTrack = currentTrack,
                        isPlaying = isAudioPlaying,
                        isLoading = isLoadingMedia,
                        onTrackClick = { viewModel.playAudioItem(it) },
                        onAddToVault = { viewModel.addMediaToVault(it) },
                        onRefresh = { viewModel.loadAllMedia() }
                    )
                }
                AppTab.STREAM_URL -> {
                    StreamLinkScreen(
                        urlInput = streamUrlInput,
                        selectedType = selectedStreamType,
                        historyList = streamHistory,
                        onUrlChange = { viewModel.updateStreamUrlInput(it) },
                        onTypeChange = { viewModel.setStreamType(it) },
                        onPlayStream = { viewModel.launchStream(it) },
                        onToggleBookmark = { id, cur -> viewModel.toggleStreamBookmark(id, cur) },
                        onClearHistory = { viewModel.clearStreamHistory() }
                    )
                }
                AppTab.AI_EXPLORER -> {
                    AISearchScreen(
                        searchQuery = aiQuery,
                        selectedCategory = aiCategory,
                        results = aiResults,
                        isLoading = isAiLoading,
                        onQueryChange = { viewModel.updateAiQuery(it) },
                        onCategoryChange = { viewModel.setAiCategory(it) },
                        onSearch = { viewModel.performAiSearch(it) },
                        onPlayMedia = { item ->
                            when (item.type) {
                                com.example.data.model.MediaType.PHOTO -> viewModel.openPhoto(item)
                                com.example.data.model.MediaType.MUSIC -> {
                                    viewModel.playAudioItem(item, listOf(item))
                                    viewModel.openNowPlaying(true)
                                }
                                else -> viewModel.openVideo(item)
                            }
                        },
                        onAddToVault = { viewModel.addMediaToVault(it) }
                    )
                }
                AppTab.VAULT -> {
                    PrivateVaultScreen(
                        isUnlocked = isVaultUnlocked,
                        vaultItems = vaultItems,
                        securityManager = viewModel.securityManager,
                        onUnlock = { viewModel.unlockVault(it) },
                        onLock = { viewModel.lockVault() },
                        onSetupPin = { pin, q, a -> viewModel.setupVaultPin(pin, q, a) },
                        onAddItem = { title, content, notes, type, cat ->
                            viewModel.addVaultItem(title, content, notes, type, cat)
                        },
                        onDeleteItem = { viewModel.deleteVaultItem(it) },
                        onPlayMediaItem = { item ->
                            when (item.type) {
                                com.example.data.model.MediaType.PHOTO -> viewModel.openPhoto(item)
                                com.example.data.model.MediaType.MUSIC -> {
                                    viewModel.playAudioItem(item, listOf(item))
                                    viewModel.openNowPlaying(true)
                                }
                                else -> viewModel.openVideo(item)
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal Video Player
    activeVideo?.let { video ->
        VideoPlayerDialog(
            item = video,
            onDismiss = { viewModel.closeVideo() }
        )
    }

    // Modal Photo Viewer with Zoom/Pan gestures
    activePhoto?.let { photo ->
        PhotoViewerDialog(
            item = photo,
            onDismiss = { viewModel.closePhoto() },
            onAddToVault = { viewModel.addMediaToVault(it) }
        )
    }

    // Modal Now Playing Bottom Sheet
    if (showNowPlaying && currentTrack != null) {
        NowPlayingSheet(
            track = currentTrack,
            isPlaying = isAudioPlaying,
            progressMs = audioProgress,
            durationMs = audioDuration,
            speed = playbackSpeed,
            isLooping = isLooping,
            isShuffle = isShuffle,
            amplitudes = amplitudes,
            onTogglePlayPause = { viewModel.audioPlayer.togglePlayPause() },
            onSeek = { viewModel.audioPlayer.seekTo(it) },
            onNext = { viewModel.audioPlayer.next() },
            onPrevious = { viewModel.audioPlayer.previous() },
            onSpeedChange = { viewModel.audioPlayer.setSpeed(it) },
            onToggleLoop = { viewModel.audioPlayer.toggleLoop() },
            onToggleShuffle = { viewModel.audioPlayer.toggleShuffle() },
            onAddToVault = { viewModel.addMediaToVault(it) },
            onDismiss = { viewModel.openNowPlaying(false) }
        )
    }
}
