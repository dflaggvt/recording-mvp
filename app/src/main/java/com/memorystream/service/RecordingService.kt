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
import android.app.NotificationManager
import com.memorystream.audio.AudioCaptureManager
import com.memorystream.audio.AudioChunkScheduler
import com.memorystream.audio.ZoneCheckResult
import com.memorystream.data.model.ChunkResult
import com.memorystream.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.memorystream.action.START"
        const val ACTION_STOP = "com.memorystream.action.STOP"
        private const val NOTIFICATION_ID = 1
        private const val WAKELOCK_TIMEOUT_MS = 8 * 60 * 60 * 1000L

        private const val ZONE_PAUSE_NOTIFICATION_ID = 2

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _isPausedForZone = MutableStateFlow<String?>(null)
        val isPausedForZone: StateFlow<String?> = _isPausedForZone.asStateFlow()

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

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var placeResolver: PlaceResolver
    @Inject lateinit var cloudChunkUploader: CloudChunkUploader
    @Inject lateinit var exclusionZoneManager: ExclusionZoneManager

    private val serviceJob = SupervisorJob()
    private val recordingScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val processingScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var audioCaptureManager: AudioCaptureManager? = null
    private var chunkScheduler: AudioChunkScheduler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var processingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            else -> {
                Log.w(TAG, "Received null or unknown action, stopping self")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (chunkScheduler?.isRunning == true) {
            Log.w(TAG, "Recording already in progress, ignoring duplicate start")
            return
        }

        Log.i(TAG, "Starting recording service (cloud-only mode)")

        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        val outputDir = getExternalFilesDir("audio_chunks") ?: run {
            Log.e(TAG, "External storage unavailable")
            stopSelf()
            return
        }

        // Retry any orphaned audio files from previous crashes
        retryOrphanedFiles(outputDir)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val capture = AudioCaptureManager(audioManager)

        if (!capture.initialize()) {
            Log.e(TAG, "Failed to initialize audio capture")
            stopSelf()
            return
        }

        audioCaptureManager = capture
        _isRecording.value = true

        capture.onAudioBuffer = { _, _ -> }
        capture.start()

        val scheduler = AudioChunkScheduler(
            capture, outputDir,
            locationCallback = {
                val loc = locationProvider.getCurrentLocation()
                val place = placeResolver.resolve(loc)
                Triple(loc?.latitude, loc?.longitude, place)
            },
            zoneCheck = { lat, lng ->
                val zone = exclusionZoneManager.isInsideAnyZone(lat, lng)
                zone?.let { ZoneCheckResult(it.label) }
            }
        )
        scheduler.onZonePause = { zoneName ->
            if (zoneName != null) {
                _isPausedForZone.value = zoneName
                showZonePauseNotification(zoneName)
            } else if (_isPausedForZone.value != null) {
                _isPausedForZone.value = null
                dismissZonePauseNotification()
            }
        }
        chunkScheduler = scheduler

        processingJob = processingScope.launch {
            for (chunkResult in scheduler.processingChannel) {
                processChunkCloud(chunkResult)
            }
        }

        scheduler.start(recordingScope)
    }

    private fun retryOrphanedFiles(outputDir: File) {
        processingScope.launch {
            val orphans = outputDir.listFiles { f -> f.extension == "m4a" } ?: return@launch
            if (orphans.isEmpty()) return@launch

            Log.i(TAG, "Found ${orphans.size} orphaned audio files, retrying upload")
            for (file in orphans) {
                try {
                    val chunkResult = ChunkResult(
                        filePath = file.absolutePath,
                        startTimestamp = file.lastModified(),
                        endTimestamp = file.lastModified() + 300_000
                    )
                    processChunkCloud(chunkResult)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to retry orphan ${file.name}", e)
                }
            }
        }
    }

    private suspend fun processChunkCloud(chunkResult: ChunkResult) {
        try {
            Log.i(TAG, "Uploading chunk: ${chunkResult.filePath}")
            val success = cloudChunkUploader.uploadAndProcess(
                chunkResult = chunkResult,
                latitude = chunkResult.latitude,
                longitude = chunkResult.longitude,
                placeName = chunkResult.placeName
            )
            if (success) {
                Log.i(TAG, "Cloud chunk uploaded: ${chunkResult.filePath}")
            } else {
                Log.e(TAG, "Cloud upload failed: ${chunkResult.filePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud processing failed: ${chunkResult.filePath}", e)
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "Stopping recording service")

        chunkScheduler?.stop()
        chunkScheduler?.processingChannel?.close()
        audioCaptureManager?.release()

        CoroutineScope(Dispatchers.Default).launch {
            Log.i(TAG, "Waiting for in-flight processing to complete...")
            try {
                processingJob?.join()
            } catch (_: Exception) {}

            _isRecording.value = false
            _isPausedForZone.value = null
            dismissZonePauseNotification()
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
            .setContentTitle("Listening...")
            .setContentText("MemoryStream is active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showZonePauseNotification(zoneName: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MemoryStreamApp.RECORDING_CHANNEL_ID)
            .setContentTitle("Recording paused")
            .setContentText("Privacy zone: $zoneName")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ZONE_PAUSE_NOTIFICATION_ID, notification)
    }

    private fun dismissZonePauseNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ZONE_PAUSE_NOTIFICATION_ID)
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MemoryStream::RecordingWakeLock"
        ).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }
        Log.i(TAG, "Wake lock acquired (${WAKELOCK_TIMEOUT_MS / 3600000}h timeout)")
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
        _isRecording.value = false
        _isPausedForZone.value = null
        releaseWakeLock()
        serviceJob.cancel()
        Log.i(TAG, "Recording service destroyed")
    }
}
