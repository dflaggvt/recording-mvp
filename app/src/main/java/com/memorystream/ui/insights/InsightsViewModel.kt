package com.memorystream.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val dailySummary: String? = null,
    val topBooks: List<String> = emptyList(),
    val topPeople: List<String> = emptyList(),
    val moodData: List<Float> = emptyList()
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val cloudApi: CloudApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        loadInsights()
        loadPeople()
        loadMoodData()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            try {
                val insights = cloudApi.getInsights(limit = 20)
                val summary = insights.firstOrNull { it.type == "daily_summary" }?.body
                val books = insights
                    .filter { it.type == "book_mention" }
                    .map { it.title }
                    .distinct()
                    .take(5)

                _uiState.update { state ->
                    state.copy(
                        dailySummary = summary,
                        topBooks = books
                    )
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadPeople() {
        viewModelScope.launch {
            try {
                val speakers = cloudApi.getSpeakers()
                val names = speakers
                    .filter { !it.is_primary }
                    .map { it.name }
                    .take(5)

                _uiState.update { state ->
                    state.copy(topPeople = names)
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadMoodData() {
        viewModelScope.launch {
            try {
                val chunks = cloudApi.listChunks(limit = 24)
                val moods = if (chunks.isNotEmpty()) {
                    val maxDuration = chunks.maxOf { it.end_timestamp - it.start_timestamp }
                        .coerceAtLeast(1L)
                    chunks.map { chunk ->
                        val duration = chunk.end_timestamp - chunk.start_timestamp
                        (duration.toFloat() / maxDuration).coerceIn(0f, 1f)
                    }
                } else {
                    emptyList()
                }

                _uiState.update { state ->
                    state.copy(moodData = moods)
                }
            } catch (_: Exception) { }
        }
    }
}
