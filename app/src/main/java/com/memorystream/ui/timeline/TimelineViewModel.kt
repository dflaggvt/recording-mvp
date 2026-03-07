package com.memorystream.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import com.memorystream.audio.AudioPlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DayCard(
    val dayTimestamp: Long,
    val chunkCount: Int,
    val totalDurationMs: Long,
    val places: List<String>,
    val chunks: List<CloudApi.ChunkSummary> = emptyList()
)

data class DiscoveryMemory(
    val chunk: CloudApi.ChunkSummary,
    val daysAgo: Int,
    val label: String
)

data class TimelineUiState(
    val days: List<DayCard> = emptyList(),
    val discovery: DiscoveryMemory? = null,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val cloudApi: CloudApi,
    private val playbackManager: AudioPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private var currentOffset = 0

    init {
        loadInitialDays()
        loadDiscovery()
    }

    private fun loadInitialDays() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summaries = cloudApi.getDailySummaries(limit = 14, offset = 0)
                val dayCards = summaries.map { row ->
                    val dayStart = row.day_timestamp
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
                    val chunks = cloudApi.getChunksByRange(dayStart, dayEnd)
                    DayCard(
                        dayTimestamp = row.day_timestamp,
                        chunkCount = row.chunk_count,
                        totalDurationMs = row.total_duration_ms,
                        places = row.places?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                        chunks = chunks.sortedBy { it.start_timestamp }
                    )
                }
                currentOffset = summaries.size
                _uiState.value = _uiState.value.copy(
                    days = dayCards,
                    isLoading = false,
                    hasMore = summaries.size >= 14
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summaries = cloudApi.getDailySummaries(limit = 14, offset = currentOffset)
                val newCards = summaries.map { row ->
                    val dayStart = row.day_timestamp
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
                    val chunks = cloudApi.getChunksByRange(dayStart, dayEnd)
                    DayCard(
                        dayTimestamp = row.day_timestamp,
                        chunkCount = row.chunk_count,
                        totalDurationMs = row.total_duration_ms,
                        places = row.places?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                        chunks = chunks.sortedBy { it.start_timestamp }
                    )
                }
                currentOffset += summaries.size
                _uiState.value = _uiState.value.copy(
                    days = _uiState.value.days + newCards,
                    isLoading = false,
                    hasMore = summaries.size >= 14
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadDiscovery() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance()

                for (weeksBack in 1..52) {
                    cal.timeInMillis = now
                    cal.add(java.util.Calendar.WEEK_OF_YEAR, -weeksBack)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val dayStart = cal.timeInMillis
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1

                    val chunks = cloudApi.getChunksByRange(dayStart, dayEnd)
                        .filter { !it.transcript.isNullOrBlank() }

                    if (chunks.isNotEmpty()) {
                        val chunk = chunks.random()
                        val daysAgo = weeksBack * 7
                        val label = when {
                            weeksBack == 1 -> "One week ago"
                            weeksBack == 2 -> "Two weeks ago"
                            weeksBack == 4 -> "One month ago"
                            weeksBack < 52 -> "$weeksBack weeks ago"
                            else -> "One year ago"
                        }
                        _uiState.value = _uiState.value.copy(
                            discovery = DiscoveryMemory(chunk, daysAgo, label)
                        )
                        return@launch
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun playChunk(chunk: CloudApi.ChunkSummary) {
        viewModelScope.launch {
            val audioUrl = cloudApi.getAudioUrl(chunk.id)
            if (audioUrl != null) {
                playbackManager.playFromUrl(
                    url = audioUrl.audio_url,
                    chunkStartTimestamp = chunk.start_timestamp,
                    chunkId = chunk.id,
                    placeName = chunk.place_name
                )
            }
        }
    }

    fun refresh() {
        currentOffset = 0
        loadInitialDays()
        loadDiscovery()
    }
}
