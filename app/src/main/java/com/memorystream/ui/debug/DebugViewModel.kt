package com.memorystream.ui.debug

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorystream.api.CloudApi
import com.memorystream.audio.AudioCaptureManager
import com.memorystream.audio.AudioPlaybackManager
import com.memorystream.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChunkDebugInfo(
    val chunk: CloudApi.ChunkSummary,
    val utterances: List<CloudApi.UtteranceResult> = emptyList()
)

data class CostEstimate(
    val totalChunks: Int = 0,
    val totalDurationMinutes: Float = 0f,
    val totalMonthlyCost: Float = 0f,
    val recordingHoursPerDay: Float = 0f,
    val chunksPerDay: Float = 0f
)

data class SystemInfo(
    val sampleRate: Int = AudioCaptureManager.SAMPLE_RATE,
    val channels: String = "Mono",
    val encoding: String = "AAC-LC",
    val bitrate: String = "192 kbps",
    val isRecording: Boolean = false,
    val totalAudioFilesMB: Float = 0f,
    val pendingUploadFiles: Int = 0
)

data class DebugUiState(
    val chunks: List<ChunkDebugInfo> = emptyList(),
    val selectedChunk: ChunkDebugInfo? = null,
    val totalChunks: Int = 0,
    val totalInsights: Int = 0,
    val costEstimate: CostEstimate = CostEstimate(),
    val systemInfo: SystemInfo = SystemInfo(),
    val isLoading: Boolean = false,
    val lastRefreshed: Long = 0,
    val lastError: String? = null
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val application: Application,
    private val cloudApi: CloudApi,
    val playbackManager: AudioPlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    init {
        loadChunks()
        loadCostEstimate()
        loadSystemInfo()
        startAutoRefresh()
        observeRecordingState()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                loadChunks()
                loadSystemInfo()
            }
        }
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            RecordingService.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(
                    systemInfo = _uiState.value.systemInfo.copy(isRecording = recording)
                )
            }
        }
    }

    fun loadChunks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, lastError = null)
            try {
                val allChunks = cloudApi.listChunks(limit = 30)

                val debugInfos = allChunks.map { chunk ->
                    val utterances = try {
                        cloudApi.getUtterances(chunk.id)
                    } catch (_: Exception) { emptyList() }
                    ChunkDebugInfo(chunk = chunk, utterances = utterances)
                }

                val totalInsights = try {
                    cloudApi.getInsights(limit = 100).size
                } catch (_: Exception) { 0 }

                _uiState.value = _uiState.value.copy(
                    chunks = debugInfos,
                    totalChunks = allChunks.size,
                    totalInsights = totalInsights,
                    isLoading = false,
                    lastRefreshed = System.currentTimeMillis(),
                    lastError = null
                )
            } catch (e: Exception) {
                Log.e("DebugViewModel", "Error loading chunks", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastError = e.message ?: "Failed to load"
                )
            }
        }
    }

    fun loadSystemInfo() {
        viewModelScope.launch {
            try {
                val isRecording = RecordingService.isRecording.value

                val audioDir = application.getExternalFilesDir("audio_chunks")
                val audioFiles = audioDir?.listFiles { f -> f.extension == "m4a" } ?: emptyArray()
                val totalAudioBytes = audioFiles.sumOf { it.length() }
                val totalAudioMB = totalAudioBytes / (1024f * 1024f)

                _uiState.value = _uiState.value.copy(
                    systemInfo = SystemInfo(
                        isRecording = isRecording,
                        totalAudioFilesMB = totalAudioMB,
                        pendingUploadFiles = audioFiles.size
                    )
                )
            } catch (e: Exception) {
                Log.e("DebugViewModel", "System info failed", e)
            }
        }
    }

    fun loadCostEstimate() {
        viewModelScope.launch {
            try {
                val allChunks = cloudApi.listChunks(limit = 100)
                if (allChunks.isEmpty()) return@launch

                val totalChunks = allChunks.size
                val totalDurationMs = allChunks.sumOf { maxOf(0L, it.end_timestamp - it.start_timestamp) }
                val totalMinutes = totalDurationMs / 60_000f

                val firstChunk = allChunks.minByOrNull { it.start_timestamp }
                val lastChunk = allChunks.maxByOrNull { it.start_timestamp }
                val spanDays = if (firstChunk != null && lastChunk != null) {
                    maxOf(1f, (lastChunk.start_timestamp - firstChunk.start_timestamp) / (24 * 60 * 60 * 1000f))
                } else 1f

                val chunksPerDay = totalChunks / spanDays
                val minutesPerDay = totalMinutes / spanDays
                val hoursPerDay = minutesPerDay / 60f

                val deepgramBatch = minutesPerDay * 0.0043f * 30
                val openaiProcessing = chunksPerDay * 0.01f * 30
                val total = deepgramBatch + openaiProcessing

                _uiState.value = _uiState.value.copy(
                    costEstimate = CostEstimate(
                        totalChunks = totalChunks,
                        totalDurationMinutes = totalMinutes,
                        totalMonthlyCost = total,
                        recordingHoursPerDay = hoursPerDay,
                        chunksPerDay = chunksPerDay
                    )
                )
            } catch (e: Exception) {
                Log.e("DebugViewModel", "Cost estimate failed", e)
            }
        }
    }

    fun selectChunk(chunkId: String) {
        viewModelScope.launch {
            val chunk = _uiState.value.chunks.firstOrNull { it.chunk.id == chunkId } ?: return@launch
            _uiState.value = _uiState.value.copy(selectedChunk = chunk)
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedChunk = null)
    }

    fun refreshAll() {
        loadChunks()
        loadCostEstimate()
        loadSystemInfo()
    }
}
