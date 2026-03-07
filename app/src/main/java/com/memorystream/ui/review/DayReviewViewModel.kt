package com.memorystream.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import com.memorystream.audio.AudioPlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DayReviewUiState(
    val narrative: String? = null,
    val isGeneratingNarrative: Boolean = false,
    val chunks: List<CloudApi.ChunkSummary> = emptyList(),
    val totalDurationMs: Long = 0,
    val placeCount: Int = 0,
    val commitments: List<CloudApi.InsightResult> = emptyList(),
    val inconsistencies: List<CloudApi.InsightResult> = emptyList(),
    val speakers: Map<String, CloudApi.SpeakerResult> = emptyMap(),
    val selectedChunk: CloudApi.ChunkSummary? = null,
    val selectedChunkUtterances: List<CloudApi.UtteranceResult> = emptyList(),
    val hasData: Boolean = false
)

@HiltViewModel
class DayReviewViewModel @Inject constructor(
    private val cloudApi: CloudApi,
    val playbackManager: AudioPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DayReviewUiState())
    val uiState: StateFlow<DayReviewUiState> = _uiState.asStateFlow()

    fun loadDay(timestamp: Long) {
        viewModelScope.launch {
            val (dayStart, dayEnd) = dayRange(timestamp)

            val chunks = cloudApi.getChunksByRange(dayStart, dayEnd)
            if (chunks.isEmpty()) {
                _uiState.value = DayReviewUiState(hasData = false)
                return@launch
            }

            val speakers = cloudApi.getSpeakers().associateBy { it.id }
            val places = chunks.mapNotNull { it.place_name }.distinct()
            val totalDuration = chunks.sumOf {
                maxOf(0L, it.end_timestamp - it.start_timestamp)
            }

            val commitments = try {
                cloudApi.getInsights(type = "commitment", start = dayStart, end = dayEnd)
            } catch (_: Exception) { emptyList() }

            val inconsistencies = try {
                cloudApi.getInsights(type = "inconsistency", start = dayStart, end = dayEnd)
            } catch (_: Exception) { emptyList() }

            _uiState.value = DayReviewUiState(
                chunks = chunks,
                totalDurationMs = totalDuration,
                placeCount = places.size,
                speakers = speakers,
                commitments = commitments,
                inconsistencies = inconsistencies,
                hasData = true,
                isGeneratingNarrative = true
            )

            val narrativeResp = cloudApi.generateDaySummary(dayStart)
            _uiState.value = _uiState.value.copy(
                narrative = narrativeResp?.narrative
                    ?: "Your day was recorded but a summary couldn't be generated right now.",
                isGeneratingNarrative = false
            )
        }
    }

    fun selectChunk(chunk: CloudApi.ChunkSummary) {
        viewModelScope.launch {
            val utterances = cloudApi.getUtterances(chunk.id)
            _uiState.value = _uiState.value.copy(
                selectedChunk = chunk,
                selectedChunkUtterances = utterances
            )
        }
    }

    fun clearSelection() {
        playbackManager.stop()
        _uiState.value = _uiState.value.copy(
            selectedChunk = null,
            selectedChunkUtterances = emptyList()
        )
    }

    fun completeCommitment(id: String) {
        viewModelScope.launch {
            cloudApi.dismissInsight(id)
            _uiState.value = _uiState.value.copy(
                commitments = _uiState.value.commitments.filter { it.id != id }
            )
        }
    }

    fun playChunkAudio(chunk: CloudApi.ChunkSummary) {
        viewModelScope.launch {
            val audioResp = cloudApi.getAudioUrl(chunk.id)
            if (audioResp != null) {
                playbackManager.playFromUrl(
                    url = audioResp.audio_url,
                    chunkStartTimestamp = chunk.start_timestamp,
                    chunkId = chunk.id,
                    placeName = chunk.place_name
                )
            }
        }
    }

    private fun dayRange(timestamp: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
        return dayStart to dayEnd
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.stop()
    }
}
