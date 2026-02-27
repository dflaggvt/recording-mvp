package com.memorystream.audio

import android.util.Log
import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.model.ChunkResult
import com.memorystream.data.model.ChunkStatus
import com.memorystream.data.repository.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AudioChunkScheduler(
    private val audioCaptureManager: AudioCaptureManager,
    private val repository: MemoryRepository,
    private val outputDir: File,
    private val chunkDurationMs: Long = 5 * 60 * 1000L,
    val processingChannel: Channel<ChunkResult> = Channel(Channel.UNLIMITED)
) {
    companion object {
        private const val TAG = "AudioChunkScheduler"
    }

    private var recordingJob: Job? = null
    private var chunkRecorder: ChunkRecorder? = null
    var chunkCount = 0
        private set
    var isRunning = false
        private set

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        recordingJob = scope.launch {
            Log.i(TAG, "Chunk scheduler started")
            while (isActive && isRunning) {
                try {
                    recordOneChunk()
                } catch (e: Exception) {
                    Log.e(TAG, "Chunk recording failed, starting next", e)
                    kotlinx.coroutines.delay(2000)
                }
            }
            Log.i(TAG, "Chunk scheduler stopped")
        }
    }

    private suspend fun recordOneChunk() {
        val recorder = ChunkRecorder(outputDir, chunkDurationMs)
        chunkRecorder = recorder

        val chunkId = UUID.randomUUID().toString()
        val filePath = recorder.start()

        val entity = MemoryChunkEntity(
            id = chunkId,
            startTimestamp = System.currentTimeMillis(),
            endTimestamp = 0,
            audioFilePath = filePath,
            status = ChunkStatus.RECORDING
        )
        repository.insert(entity)

        audioCaptureManager.readLoop { buffer, count ->
            recorder.feedAudio(buffer, count)

            if (recorder.shouldFinish()) {
                audioCaptureManager.stop()
            }
        }

        val result = recorder.finish()
        chunkRecorder = null
        chunkCount++

        repository.update(
            entity.copy(
                endTimestamp = result.endTimestamp,
                status = ChunkStatus.PENDING_TRANSCRIPTION
            )
        )

        processingChannel.send(result.copy(filePath = filePath))
        Log.i(TAG, "Chunk $chunkCount completed: $filePath")

        // Reinitialize capture for next chunk
        if (isRunning) {
            audioCaptureManager.initialize()
            audioCaptureManager.start()
        }
    }

    fun stop() {
        isRunning = false
        audioCaptureManager.stop()
        recordingJob?.cancel()
        recordingJob = null
        chunkRecorder = null
        Log.i(TAG, "Chunk scheduler stopped, total chunks: $chunkCount")
    }
}
