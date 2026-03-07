package com.memorystream.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import com.memorystream.audio.AudioPlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FullPlayerUiState(
    val chunk: CloudApi.ChunkSummary? = null,
    val utterances: List<CloudApi.UtteranceResult> = emptyList(),
    val speakers: Map<String, CloudApi.SpeakerResult> = emptyMap(),
    val currentUtteranceIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

@HiltViewModel
class FullPlayerViewModel @Inject constructor(
    private val cloudApi: CloudApi,
    val playbackManager: AudioPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullPlayerUiState())
    val uiState: StateFlow<FullPlayerUiState> = _uiState.asStateFlow()

    private var allChunks: List<CloudApi.ChunkSummary> = emptyList()
    private var currentChunkIndex = -1

    init {
        viewModelScope.launch {
            allChunks = cloudApi.listChunks(limit = 100)
                .sortedBy { it.start_timestamp }

            playbackManager.playbackState.collect { state ->
                val chunkId = state.chunkId
                if (chunkId != null && _uiState.value.chunk?.id != chunkId) {
                    loadChunkData(chunkId)
                }
                updateCurrentUtterance(state.currentPositionMs)
            }
        }

        playbackManager.setOnChunkComplete {
            playNext()
        }
    }

    private suspend fun loadChunkData(chunkId: String) {
        val chunk = allChunks.firstOrNull { it.id == chunkId } ?: return
        val utterances = cloudApi.getUtterances(chunkId)
        val speakers = cloudApi.getSpeakers().associateBy { it.id }

        currentChunkIndex = allChunks.indexOfFirst { it.id == chunkId }

        _uiState.value = _uiState.value.copy(
            chunk = chunk,
            utterances = utterances,
            speakers = speakers,
            hasNext = currentChunkIndex >= 0 && currentChunkIndex < allChunks.size - 1,
            hasPrevious = currentChunkIndex > 0
        )
    }

    private fun updateCurrentUtterance(positionMs: Int) {
        val utterances = _uiState.value.utterances
        val chunk = _uiState.value.chunk ?: return
        if (utterances.isEmpty()) return

        val absoluteMs = chunk.start_timestamp + positionMs
        var bestIndex = 0
        for (i in utterances.indices) {
            if (utterances[i].timestamp <= absoluteMs) {
                bestIndex = i
            } else break
        }

        if (bestIndex != _uiState.value.currentUtteranceIndex) {
            _uiState.value = _uiState.value.copy(currentUtteranceIndex = bestIndex)
        }
    }

    fun playNext() {
        if (currentChunkIndex < 0 || currentChunkIndex >= allChunks.size - 1) return
        val next = allChunks[currentChunkIndex + 1]
        viewModelScope.launch {
            val audioResp = cloudApi.getAudioUrl(next.id)
            if (audioResp != null) {
                playbackManager.playFromUrl(
                    url = audioResp.audio_url,
                    chunkStartTimestamp = next.start_timestamp,
                    chunkId = next.id,
                    placeName = next.place_name
                )
            }
        }
    }

    fun playPrevious() {
        if (currentChunkIndex <= 0) return
        val prev = allChunks[currentChunkIndex - 1]
        viewModelScope.launch {
            val audioResp = cloudApi.getAudioUrl(prev.id)
            if (audioResp != null) {
                playbackManager.playFromUrl(
                    url = audioResp.audio_url,
                    chunkStartTimestamp = prev.start_timestamp,
                    chunkId = prev.id,
                    placeName = prev.place_name
                )
            }
        }
    }

    fun setSpeed(speed: Float) {
        playbackManager.setSpeed(speed)
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.setOnChunkComplete(null)
    }
}
