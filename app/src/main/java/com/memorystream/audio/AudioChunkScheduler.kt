package com.memorystream.audio

import android.util.Log
import com.memorystream.data.model.ChunkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class ZoneCheckResult(val zoneName: String)

class AudioChunkScheduler(
    private val audioCaptureManager: AudioCaptureManager,
    private val outputDir: File,
    private val chunkDurationMs: Long = 5 * 60 * 1000L,
    val processingChannel: Channel<ChunkResult> = Channel(Channel.UNLIMITED),
    private val locationCallback: (suspend () -> Triple<Double?, Double?, String?>)? = null,
    private val zoneCheck: (suspend (Double, Double) -> ZoneCheckResult?)? = null
) {
    companion object {
        private const val TAG = "AudioChunkScheduler"
    }

    private var recordingJob: Job? = null
    @Volatile
    private var chunkRecorder: ChunkRecorder? = null
    val chunkCount = AtomicInteger(0)

    @Volatile
    var isRunning = false
        private set

    @Volatile
    private var currentLocation: Triple<Double?, Double?, String?>? = null

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        recordingJob = scope.launch {
            Log.i(TAG, "Chunk scheduler started")
            while (isActive && isRunning) {
                try {
                    recordOneChunk()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Chunk recording failed, starting next", e)
                    kotlinx.coroutines.delay(2000)
                }
            }
            Log.i(TAG, "Chunk scheduler stopped")
        }
    }

    var onZonePause: ((String?) -> Unit)? = null

    private suspend fun recordOneChunk() {
        // Check exclusion zones before recording
        val locData = try { locationCallback?.invoke() } catch (_: Exception) { null }
        currentLocation = locData

        if (locData != null && locData.first != null && locData.second != null && zoneCheck != null) {
            val zoneResult = try { zoneCheck.invoke(locData.first!!, locData.second!!) } catch (_: Exception) { null }
            if (zoneResult != null) {
                Log.i(TAG, "Inside exclusion zone: ${zoneResult.zoneName}, skipping chunk")
                onZonePause?.invoke(zoneResult.zoneName)
                delay(chunkDurationMs)
                onZonePause?.invoke(null)
                currentLocation = null
                return
            }
        }
        onZonePause?.invoke(null)

        val recorder = ChunkRecorder(outputDir, chunkDurationMs)
        chunkRecorder = recorder

        var recorderFinished = false

        try {
            val filePath = recorder.start()

            audioCaptureManager.readLoop { buffer, count ->
                recorder.feedAudio(buffer, count)
                if (recorder.shouldFinish()) {
                    audioCaptureManager.stop()
                }
            }

            val result = recorder.finish()
            recorderFinished = true
            chunkRecorder = null
            chunkCount.incrementAndGet()

            val enrichedResult = result.copy(
                filePath = filePath,
                latitude = locData?.first,
                longitude = locData?.second,
                placeName = locData?.third
            )

            processingChannel.send(enrichedResult)
            currentLocation = null
            Log.i(TAG, "Chunk ${chunkCount.get()} completed: $filePath")
        } finally {
            if (!recorderFinished) {
                try {
                    val result = recorder.finish()
                    finalizePartialChunk(result)
                } catch (e: Exception) {
                    Log.w(TAG, "Error finishing recorder in cleanup", e)
                }
                chunkRecorder = null
            }
        }

        if (isRunning) {
            audioCaptureManager.initialize()
            audioCaptureManager.start()
        }
    }

    fun stop() {
        isRunning = false
        audioCaptureManager.stop()

        val recorder = chunkRecorder
        if (recorder != null) {
            try {
                val result = recorder.finish()
                finalizePartialChunkBlocking(result)
            } catch (e: Exception) {
                Log.w(TAG, "Error finishing recorder on stop", e)
            }
            chunkRecorder = null
        }

        recordingJob?.cancel()
        recordingJob = null
        Log.i(TAG, "Chunk scheduler stopped, total chunks: ${chunkCount.get()}")
    }

    private suspend fun finalizePartialChunk(result: ChunkResult) {
        if (result.filePath.isBlank()) return
        val loc = currentLocation
        val enriched = result.copy(
            latitude = loc?.first,
            longitude = loc?.second,
            placeName = loc?.third
        )
        processingChannel.send(enriched)
        chunkCount.incrementAndGet()
        currentLocation = null
        Log.i(TAG, "Partial chunk finalized and queued: ${result.filePath}")
    }

    private fun finalizePartialChunkBlocking(result: ChunkResult) {
        if (result.filePath.isBlank()) return
        try {
            runBlocking(Dispatchers.IO) {
                finalizePartialChunk(result)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to finalize partial chunk on stop", e)
        }
    }
}
