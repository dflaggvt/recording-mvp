package com.memorystream.ui.speakers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnrollmentState(
    val isProcessing: Boolean = false,
    val name: String = "",
    val isPrimary: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class SpeakersViewModel @Inject constructor(
    private val cloudApi: CloudApi
) : ViewModel() {

    companion object {
        private const val TAG = "SpeakersViewModel"
    }

    private val _speakers = MutableStateFlow<List<CloudApi.SpeakerResult>>(emptyList())
    val speakers: StateFlow<List<CloudApi.SpeakerResult>> = _speakers.asStateFlow()

    private val _enrollmentState = MutableStateFlow(EnrollmentState())
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    init {
        loadSpeakers()
    }

    private fun loadSpeakers() {
        viewModelScope.launch {
            _speakers.value = try {
                cloudApi.getSpeakers()
            } catch (_: Exception) { emptyList() }
        }
    }

    fun updateName(name: String) {
        _enrollmentState.value = _enrollmentState.value.copy(name = name, error = null)
    }

    fun togglePrimary() {
        _enrollmentState.value = _enrollmentState.value.copy(
            isPrimary = !_enrollmentState.value.isPrimary
        )
    }

    fun enrollSpeaker() {
        val state = _enrollmentState.value
        if (state.name.isBlank()) {
            _enrollmentState.value = state.copy(error = "Please enter a name")
            return
        }

        viewModelScope.launch {
            _enrollmentState.value = _enrollmentState.value.copy(isProcessing = true)
            try {
                val speaker = cloudApi.createSpeaker(
                    name = state.name,
                    isPrimary = state.isPrimary
                )
                if (speaker != null) {
                    _enrollmentState.value = EnrollmentState(success = true)
                    loadSpeakers()
                    Log.i(TAG, "Speaker created: ${speaker.name}")
                } else {
                    _enrollmentState.value = _enrollmentState.value.copy(
                        isProcessing = false,
                        error = "Failed to create speaker. Try again."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Speaker creation failed", e)
                _enrollmentState.value = _enrollmentState.value.copy(
                    isProcessing = false,
                    error = "Failed: ${e.message}"
                )
            }
        }
    }

    fun resetEnrollment() {
        _enrollmentState.value = EnrollmentState()
    }

    fun deleteSpeaker(speaker: CloudApi.SpeakerResult) {
        viewModelScope.launch {
            cloudApi.deleteSpeaker(speaker.id)
            loadSpeakers()
        }
    }
}
