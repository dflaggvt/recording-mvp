package com.memorystream.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioCaptureManager(private val audioManager: AudioManager) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val BUFFER_SIZE: Int = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            4096
        )
    }

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    var isCapturing = false
        private set

    var onAudioBuffer: ((ShortArray, Int) -> Unit)? = null

    fun findUsbMicrophone(): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        try {
            releaseAudioEffects()
            val oldRecord = audioRecord
            audioRecord = null
            try { oldRecord?.stop() } catch (_: Exception) {}
            try { oldRecord?.release() } catch (_: Exception) {}

            val newRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            )

            if (newRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                newRecord.release()
                return false
            }

            audioRecord = newRecord

            val usbMic = findUsbMicrophone()
            if (usbMic != null) {
                newRecord.preferredDevice = usbMic
                Log.i(TAG, "Set preferred device to USB mic: ${usbMic.productName}")
            } else {
                Log.w(TAG, "No USB microphone found, using default input")
            }

            attachAudioEffects()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            return false
        }
    }

    private fun attachAudioEffects() {
        val sessionId = audioRecord?.audioSessionId ?: return

        // NoiseSuppressor intentionally disabled — it removes spectral detail
        // that differentiates speakers, hurting diarization accuracy.
        Log.i(TAG, "NoiseSuppressor disabled for better diarization quality")

        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AutomaticGainControl attached and enabled")
            }
        } else {
            Log.w(TAG, "AutomaticGainControl not available on this device")
        }

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also {
                it.enabled = true
                Log.i(TAG, "AcousticEchoCanceler attached and enabled")
            }
        } else {
            Log.w(TAG, "AcousticEchoCanceler not available on this device")
        }
    }

    private fun releaseAudioEffects() {
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null
        echoCanceler?.release()
        echoCanceler = null
    }

    fun start() {
        audioRecord?.startRecording()
        isCapturing = true
        Log.i(TAG, "Audio capture started (VOICE_RECOGNITION + AudioEffects)")
    }

    suspend fun readLoop(onBuffer: (ShortArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val buffer = ShortArray(BUFFER_SIZE / 2)
        while (isActive && isCapturing) {
            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (readCount > 0) {
                onBuffer(buffer, readCount)
                onAudioBuffer?.invoke(buffer, readCount)
            } else if (readCount < 0) {
                Log.e(TAG, "AudioRecord read error: $readCount")
                break
            }
        }
    }

    fun stop() {
        isCapturing = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
    }

    fun release() {
        stop()
        releaseAudioEffects()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Audio capture released")
    }

    fun hasUsbMicrophone(): Boolean = findUsbMicrophone() != null
}
