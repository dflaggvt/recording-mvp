package com.memorystream.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.memorystream.MemoryStreamApp
import com.memorystream.R
import com.memorystream.audio.AudioCaptureManager
import com.memorystream.audio.AudioChunkScheduler
import com.memorystream.data.model.ChunkResult
import com.memorystream.data.model.ChunkStatus
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.embedding.OnnxEmbeddingEngine
import com.memorystream.transcription.TranscriptionWorker
import com.memorystream.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.memorystream.action.START"
        const val ACTION_STOP = "com.memorystream.action.STOP"
        private const val NOTIFICATION_ID = 1

        fun startRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject lateinit var repository: MemoryRepository
    @Inject lateinit var embeddingEngine: OnnxEmbeddingEngine

    private val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioCaptureManager: AudioCaptureManager? = null
    private var chunkScheduler: AudioChunkScheduler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var transcriptionWorker: TranscriptionWorker? = null
    private var processingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        Log.i(TAG, "Starting recording service")

        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val capture = AudioCaptureManager(audioManager)

        if (!capture.initialize()) {
            Log.e(TAG, "Failed to initialize audio capture")
            stopSelf()
            return
        }

        audioCaptureManager = capture
        capture.start()

        val outputDir = getExternalFilesDir("audio_chunks")!!
        val scheduler = AudioChunkScheduler(capture, repository, outputDir)
        chunkScheduler = scheduler

        transcriptionWorker = TranscriptionWorker(this, repository, embeddingEngine)

        // Launch processing coroutine in its own scope so it survives recording stop
        processingJob = processingScope.launch {
            for (chunkResult in scheduler.processingChannel) {
                processChunk(chunkResult)
            }
        }

        scheduler.start(recordingScope)
    }

    private suspend fun processChunk(chunkResult: ChunkResult) {
        try {
            Log.i(TAG, "Processing chunk: ${chunkResult.filePath}")
            transcriptionWorker?.processChunk(chunkResult)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process chunk: ${chunkResult.filePath}", e)
            // Mark chunk as error but don't crash the pipeline
            val chunks = repository.getChunksByStatus(ChunkStatus.TRANSCRIBING)
            chunks.forEach { chunk ->
                if (chunk.audioFilePath == chunkResult.filePath) {
                    repository.update(chunk.copy(status = ChunkStatus.ERROR))
                }
            }
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "Stopping recording service")
        chunkScheduler?.stop()
        audioCaptureManager?.release()
        recordingScope.cancel()

        // Close the channel so the processing loop can drain and finish
        chunkScheduler?.processingChannel?.close()

        // Wait for in-flight processing to complete, then shut down
        processingScope.launch {
            Log.i(TAG, "Waiting for in-flight processing to complete...")
            processingJob?.join()
            Log.i(TAG, "Processing complete, shutting down service")
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MemoryStreamApp.RECORDING_CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MemoryStream::RecordingWakeLock"
        ).apply {
            acquire()
        }
        Log.i(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        chunkScheduler?.stop()
        audioCaptureManager?.release()
        releaseWakeLock()
        recordingScope.cancel()
        // Give processing scope a generous timeout to finish current work
        processingScope.launch {
            withTimeoutOrNull(600_000) { processingJob?.join() }
            processingScope.cancel()
        }
        Log.i(TAG, "Recording service destroyed")
    }
}
