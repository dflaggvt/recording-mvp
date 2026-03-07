package com.memorystream.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResultItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val speakerId: String?,
    val placeName: String?,
    val similarity: Float
)

data class SearchUiState(
    val query: String = "",
    val answer: String? = null,
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val isSynthesizing: Boolean = false,
    val hasSearched: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val cloudApi: CloudApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _recentInsights = MutableStateFlow<List<CloudApi.InsightResult>>(emptyList())
    val recentInsights: StateFlow<List<CloudApi.InsightResult>> = _recentInsights.asStateFlow()

    private val _primarySpeakerName = MutableStateFlow<String?>(null)
    val primarySpeakerName: StateFlow<String?> = _primarySpeakerName.asStateFlow()

    init {
        viewModelScope.launch {
            _recentInsights.value = try {
                cloudApi.getInsights(limit = 3)
            } catch (_: Exception) { emptyList() }
        }
        viewModelScope.launch {
            _primarySpeakerName.value = try {
                cloudApi.getSpeakers().firstOrNull { it.is_primary }?.name
            } catch (_: Exception) { null }
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun clearSearch() {
        _uiState.value = SearchUiState()
    }

    fun dismissInsight(id: String) {
        viewModelScope.launch {
            cloudApi.dismissInsight(id)
            _recentInsights.value = _recentInsights.value.filter { it.id != id }
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                answer = null
            )
            try {
                val response = cloudApi.search(query, limit = 15)
                if (response != null) {
                    val results = mutableListOf<SearchResultItem>()

                    response.chunks.forEach { chunk ->
                        results.add(SearchResultItem(
                            id = chunk.id,
                            text = chunk.summary ?: chunk.transcript ?: "",
                            timestamp = chunk.start_timestamp,
                            speakerId = null,
                            placeName = chunk.place_name,
                            similarity = chunk.similarity
                        ))
                    }
                    response.utterances.forEach { utt ->
                        results.add(SearchResultItem(
                            id = utt.id,
                            text = utt.text,
                            timestamp = utt.timestamp,
                            speakerId = utt.speaker_id,
                            placeName = null,
                            similarity = utt.similarity
                        ))
                    }
                    results.sortByDescending { it.similarity }

                    _uiState.value = _uiState.value.copy(
                        results = results,
                        isSearching = false,
                        isSynthesizing = false,
                        hasSearched = true,
                        answer = response.answer
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        hasSearched = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    isSynthesizing = false,
                    hasSearched = true
                )
            }
        }
    }
}
