package com.memorystream.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioCaptureManager(private val audioManager: AudioManager) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val BUFFER_SIZE: Int = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            4096
        )
    }

    private var audioRecord: AudioRecord? = null
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
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val usbMic = findUsbMicrophone()
            if (usbMic != null) {
                audioRecord?.preferredDevice = usbMic
                Log.i(TAG, "Set preferred device to USB mic: ${usbMic.productName}")
            } else {
                Log.w(TAG, "No USB microphone found, using default input")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            return false
        }
    }

    fun start() {
        audioRecord?.startRecording()
        isCapturing = true
        Log.i(TAG, "Audio capture started")
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
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Audio capture released")
    }

    fun hasUsbMicrophone(): Boolean = findUsbMicrophone() != null
}
