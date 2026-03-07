package com.memorystream.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val currentFilePath: String? = null,
    val chunkStartTimestamp: Long = 0,
    val speed: Float = 1.0f,
    val chunkId: String? = null,
    val placeName: String? = null
)

@Singleton
class AudioPlaybackManager @Inject constructor() {

    companion object {
        private const val TAG = "AudioPlaybackManager"
        private const val POSITION_UPDATE_INTERVAL_MS = 200L
    }

    private var mediaPlayer: MediaPlayer? = null
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentSpeed = 1.0f
    private var onChunkComplete: (() -> Unit)? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(
        filePath: String,
        chunkStartTimestamp: Long,
        seekMs: Int = 0,
        chunkId: String? = null,
        placeName: String? = null
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file not found: $filePath")
            return
        }

        val currentPath = _playbackState.value.currentFilePath
        if (currentPath == filePath && mediaPlayer != null) {
            mediaPlayer?.let { mp ->
                mp.seekTo(seekMs)
                if (!mp.isPlaying) mp.start()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    currentPositionMs = seekMs
                )
                startPositionUpdates()
            }
            return
        }

        stop()

        var newPlayer: MediaPlayer? = null
        try {
            newPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                if (seekMs > 0) seekTo(seekMs)
                start()
            }

            mediaPlayer = newPlayer
            applySpeedAndSetup(newPlayer, filePath, chunkStartTimestamp, seekMs, chunkId, placeName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            releasePlayer(newPlayer)
            mediaPlayer = null
            _playbackState.value = PlaybackState()
        }
    }

    fun playFromUrl(
        url: String,
        chunkStartTimestamp: Long,
        seekMs: Int = 0,
        chunkId: String? = null,
        placeName: String? = null
    ) {
        val currentPath = _playbackState.value.currentFilePath
        if (currentPath == url && mediaPlayer != null) {
            mediaPlayer?.let { mp ->
                mp.seekTo(seekMs)
                if (!mp.isPlaying) mp.start()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    currentPositionMs = seekMs
                )
                startPositionUpdates()
            }
            return
        }

        stop()

        var newPlayer: MediaPlayer? = null
        try {
            newPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepare()
                if (seekMs > 0) seekTo(seekMs)
                start()
            }

            mediaPlayer = newPlayer
            applySpeedAndSetup(newPlayer, url, chunkStartTimestamp, seekMs, chunkId, placeName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio from URL", e)
            releasePlayer(newPlayer)
            mediaPlayer = null
            _playbackState.value = PlaybackState()
        }
    }

    private fun applySpeedAndSetup(
        player: MediaPlayer,
        source: String,
        chunkStartTimestamp: Long,
        seekMs: Int,
        chunkId: String?,
        placeName: String?
    ) {
        if (currentSpeed != 1.0f) {
            try {
                player.playbackParams = player.playbackParams.setSpeed(currentSpeed)
            } catch (_: Exception) { }
        }

        _playbackState.value = PlaybackState(
            isPlaying = true,
            currentPositionMs = seekMs,
            durationMs = player.duration,
            currentFilePath = source,
            chunkStartTimestamp = chunkStartTimestamp,
            speed = currentSpeed,
            chunkId = chunkId,
            placeName = placeName
        )

        player.setOnCompletionListener {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                currentPositionMs = _playbackState.value.durationMs
            )
            stopPositionUpdates()
            onChunkComplete?.invoke()
        }

        startPositionUpdates()
        Log.i(TAG, "Playing $source from ${seekMs}ms")
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    currentPositionMs = mp.currentPosition
                )
                stopPositionUpdates()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying) {
                mp.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
                startPositionUpdates()
            }
        }
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) pause() else resume()
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun stop() {
        stopPositionUpdates()
        val mp = mediaPlayer
        mediaPlayer = null
        releasePlayer(mp)
        _playbackState.value = PlaybackState()
    }

    private fun releasePlayer(mp: MediaPlayer?) {
        if (mp == null) return
        try { mp.stop() } catch (_: Exception) {}
        try { mp.reset() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            _playbackState.value = _playbackState.value.copy(
                                currentPositionMs = mp.currentPosition
                            )
                        }
                    } catch (_: IllegalStateException) { }
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        mediaPlayer?.let { mp ->
            try {
                mp.playbackParams = mp.playbackParams.setSpeed(speed)
                _playbackState.value = _playbackState.value.copy(speed = speed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set speed $speed", e)
            }
        }
    }

    fun setOnChunkComplete(callback: (() -> Unit)?) {
        onChunkComplete = callback
    }
}
