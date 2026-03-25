package com.memorystream.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.ApiConfig
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import com.memorystream.audio.AudioPlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.util.Calendar
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
    val hasMore: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val cloudApi: CloudApi,
    private val playbackManager: AudioPlaybackManager
) : ViewModel() {

    companion object {
        private const val TAG = "TimelineViewModel"
    }

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private var currentOffset = 0

    init {
        loadInitialDays()
        loadDiscovery()
    }

    private fun loadInitialDays() {
        viewModelScope.launch {
            if (!ApiConfig.isCloudConfigured()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Cloud API URL is not configured. Set CLOUD_RUN_URL in build config."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val summaries = cloudApi.getDailySummaries(limit = 14, offset = 0)
                Log.d(TAG, "Got ${summaries.size} daily summaries")
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
                    days = fillMissingDays(dayCards),
                    isLoading = false,
                    hasMore = summaries.size >= 14
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load timeline", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load timeline: ${e.message}"
                )
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
                    days = fillMissingDays(_uiState.value.days + newCards),
                    isLoading = false,
                    hasMore = summaries.size >= 14
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load more", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load more: ${e.message}"
                )
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

    /**
     * Given a list of day cards, fills in any missing calendar days between them
     * with empty DayCard entries, and also fills from today down to the earliest card.
     */
    private fun fillMissingDays(cards: List<DayCard>): List<DayCard> {
        if (cards.isEmpty()) return cards

        fun startOfDay(millis: Long): Long {
            val c = Calendar.getInstance()
            c.timeInMillis = millis
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        // Build a map of existing days keyed by start-of-day
        val existing = cards.associateBy { startOfDay(it.dayTimestamp) }

        // Range: from the earliest card's day up to today
        val todayStart = startOfDay(System.currentTimeMillis())
        val earliestStart = cards.minOf { startOfDay(it.dayTimestamp) }

        val result = mutableListOf<DayCard>()
        // Use Calendar to step back one day at a time (handles DST correctly)
        val cal = Calendar.getInstance()
        cal.timeInMillis = todayStart
        var safety = 0
        while (cal.timeInMillis >= earliestStart && safety < 400) {
            val cursor = cal.timeInMillis
            val card = existing[cursor]
            if (card != null) {
                result.add(card)
            } else {
                result.add(DayCard(dayTimestamp = cursor, chunkCount = 0, totalDurationMs = 0, places = emptyList()))
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            safety++
        }
        return result
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
