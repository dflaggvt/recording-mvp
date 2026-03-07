package com.memorystream.ui.commitments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommitmentsViewModel @Inject constructor(
    private val cloudApi: CloudApi
) : ViewModel() {

    private val _commitments = MutableStateFlow<List<CloudApi.InsightResult>>(emptyList())
    val commitments: StateFlow<List<CloudApi.InsightResult>> = _commitments.asStateFlow()

    private val _completingIds = MutableStateFlow<Set<String>>(emptySet())
    val completingIds: StateFlow<Set<String>> = _completingIds.asStateFlow()

    init {
        loadCommitments()
    }

    private fun loadCommitments() {
        viewModelScope.launch {
            _commitments.value = try {
                cloudApi.getInsights(type = "commitment")
            } catch (_: Exception) { emptyList() }
        }
    }

    fun complete(insightId: String) {
        viewModelScope.launch {
            _completingIds.value = _completingIds.value + insightId
            delay(500)
            cloudApi.dismissInsight(insightId)
            _commitments.value = _commitments.value.filter { it.id != insightId }
            _completingIds.value = _completingIds.value - insightId
        }
    }
}
