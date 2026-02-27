package com.memorystream.ui.home

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isRecording: Boolean = false,
    val hasUsbMic: Boolean = false,
    val chunkCount: Int = 0,
    val totalDurationMs: Long = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val repository: MemoryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentChunks: StateFlow<List<MemoryChunkEntity>> = repository
        .getRecentChunks(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getChunkCount().collect { count ->
                _uiState.value = _uiState.value.copy(chunkCount = count)
            }
        }
        viewModelScope.launch {
            repository.getTotalDurationMs().collect { duration ->
                _uiState.value = _uiState.value.copy(
                    totalDurationMs = duration ?: 0
                )
            }
        }
        checkUsbMic()
    }

    fun toggleRecording() {
        val newRecording = !_uiState.value.isRecording
        _uiState.value = _uiState.value.copy(isRecording = newRecording)

        if (newRecording) {
            RecordingService.startRecording(application)
        } else {
            RecordingService.stopRecording(application)
        }
    }

    private fun checkUsbMic() {
        val audioManager = application.getSystemService(AudioManager::class.java)
        val hasUsb = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        _uiState.value = _uiState.value.copy(hasUsbMic = hasUsb)
    }

    fun refreshUsbStatus() {
        checkUsbMic()
    }
}
