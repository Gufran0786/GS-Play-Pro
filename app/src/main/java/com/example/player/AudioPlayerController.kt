package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class AudioPlayerController(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _currentTrack = MutableStateFlow<MediaItem?>(null)
    val currentTrack: StateFlow<MediaItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _visualizerAmplitudes = MutableStateFlow<List<Float>>(List(16) { 0.2f })
    val visualizerAmplitudes: StateFlow<List<Float>> = _visualizerAmplitudes.asStateFlow()

    fun playTrack(item: MediaItem, newQueue: List<MediaItem> = emptyList()) {
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (_queue.value.none { it.id == item.id }) {
            _queue.value = listOf(item) + _queue.value
        }

        _currentTrack.value = item
        stopAndRelease()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (item.uri.startsWith("content://") || item.uri.startsWith("file://")) {
                    setDataSource(context, Uri.parse(item.uri))
                } else {
                    setDataSource(item.uri)
                }
                isLooping = _isLooping.value

                setOnPreparedListener { mp ->
                    mp.start()
                    _isPlaying.value = true
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(item.durationMs)
                    applyPlaybackSpeed(_playbackSpeed.value)
                    startProgressTracker()
                }

                setOnCompletionListener {
                    if (!_isLooping.value) {
                        next()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "Error what=$what extra=$extra")
                    _isPlaying.value = false
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to start playback: ${e.message}")
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs
    }

    fun next() {
        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val nextIndex = if (_isShuffle.value) {
            Random.nextInt(currentQueue.size)
        } else {
            (currentIndex + 1) % currentQueue.size
        }
        playTrack(currentQueue[nextIndex])
    }

    fun previous() {
        val current = _currentTrack.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val prevIndex = if (currentIndex - 1 < 0) currentQueue.size - 1 else currentIndex - 1
        playTrack(currentQueue[prevIndex])
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applyPlaybackSpeed(speed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val params = it.playbackParams
                        params.speed = speed
                        it.playbackParams = params
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Speed set error: ${e.message}")
            }
        }
    }

    fun toggleLoop() {
        val newLoop = !_isLooping.value
        _isLooping.value = newLoop
        mediaPlayer?.isLooping = newLoop
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            _currentPositionMs.value = mp.currentPosition.toLong()
                            if (mp.duration > 0) {
                                _durationMs.value = mp.duration.toLong()
                            }
                            // Simulate dynamic audio wave amplitudes
                            _visualizerAmplitudes.value = List(16) {
                                (0.15f + Random.nextFloat() * 0.85f)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore state exception during transition
                    }
                }
                delay(200)
            }
        }
    }

    fun stopAndRelease() {
        progressJob?.cancel()
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                // ignore
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
    }
}
