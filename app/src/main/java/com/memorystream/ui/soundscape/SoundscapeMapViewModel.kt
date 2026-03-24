package com.memorystream.ui.soundscape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SoundscapeUiState(
    val chunks: List<CloudApi.ChunkSummary> = emptyList(),
    val dayTimestamp: Long = 0L,
    val isLoading: Boolean = false
)

@HiltViewModel
class SoundscapeMapViewModel @Inject constructor(
    private val cloudApi: CloudApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoundscapeUiState())
    val uiState: StateFlow<SoundscapeUiState> = _uiState.asStateFlow()

    fun loadDay(dayTimestamp: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, dayTimestamp = dayTimestamp)
            try {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dayTimestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = cal.timeInMillis
                val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1

                val chunks = cloudApi.getChunksByRange(dayStart, dayEnd)
                    .filter { it.latitude != null && it.longitude != null }
                    .sortedBy { it.start_timestamp }

                _uiState.value = _uiState.value.copy(
                    chunks = chunks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun navigateDay(offset: Int) {
        val current = _uiState.value.dayTimestamp
        if (current == 0L) return
        val newDay = current + offset * 24 * 60 * 60 * 1000L
        loadDay(newDay)
    }
}
