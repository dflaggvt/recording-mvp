package com.memorystream.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        init {
            System.loadLibrary("whisper_jni")
        }
    }

    private var nativeContext: Long = 0
    var isInitialized = false
        private set

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing Whisper with model: $modelPath")
        nativeContext = nativeInit(modelPath)
        isInitialized = nativeContext != 0L
        if (!isInitialized) {
            Log.e(TAG, "Failed to initialize Whisper model")
        } else {
            Log.i(TAG, "Whisper model loaded successfully")
        }
    }

    suspend fun transcribe(audioFilePath: String): String = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("WhisperEngine not initialized")
        }

        Log.i(TAG, "Transcribing: $audioFilePath")
        val pcmData = decodeAudioToPcm(audioFilePath)
        Log.i(TAG, "Decoded ${pcmData.size} PCM samples")

        val result = nativeTranscribe(nativeContext, pcmData)
        Log.i(TAG, "Transcription complete: ${result.length} chars")
        result
    }

    private suspend fun decodeAudioToPcm(filePath: String): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }

            if (audioTrack < 0) {
                extractor.release()
                throw IllegalArgumentException("No audio track found in $filePath")
            }

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmBytes = mutableListOf<ByteArray>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmBytes.add(chunk)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            // Convert 16-bit PCM bytes to float samples normalized to [-1, 1]
            val totalBytes = pcmBytes.sumOf { it.size }
            val result = FloatArray(totalBytes / 2)
            var offset = 0
            for (chunk in pcmBytes) {
                val shortBuffer = ByteBuffer.wrap(chunk)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                while (shortBuffer.hasRemaining() && offset < result.size) {
                    result[offset++] = shortBuffer.get().toFloat() / 32768.0f
                }
            }

            result
        }

    fun release() {
        if (nativeContext != 0L) {
            nativeRelease(nativeContext)
            nativeContext = 0
            isInitialized = false
            Log.i(TAG, "Whisper engine released")
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(context: Long, audioData: FloatArray): String
    private external fun nativeRelease(context: Long)
}
