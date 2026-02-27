package com.memorystream.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.memorystream.data.model.ChunkResult
import java.io.File
import java.nio.ByteBuffer

class ChunkRecorder(
    private val outputDir: File,
    private val chunkDurationMs: Long = 5 * 60 * 1000L
) {
    companion object {
        private const val TAG = "ChunkRecorder"
        private const val AAC_BIT_RATE = 64000
        private const val AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var startTimestamp = 0L
    private var outputFile: File? = null
    private var totalBytesWritten = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start(): String {
        startTimestamp = System.currentTimeMillis()
        val fileName = "chunk_${startTimestamp}.m4a"
        outputFile = File(outputDir, fileName).also { it.parentFile?.mkdirs() }

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AudioCaptureManager.SAMPLE_RATE,
            1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        muxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerStarted = false
        trackIndex = -1
        totalBytesWritten = 0

        Log.i(TAG, "Chunk recording started: $fileName")
        return outputFile!!.absolutePath
    }

    fun feedAudio(pcmData: ShortArray, sampleCount: Int) {
        val enc = encoder ?: return
        val totalBytes = sampleCount * 2
        var byteOffset = 0

        // Convert shorts to bytes once
        val pcmBytes = ByteBuffer.allocate(totalBytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .also { it.asShortBuffer().put(pcmData, 0, sampleCount) }
            .array()

        // Feed in chunks that fit the encoder's input buffer
        while (byteOffset < totalBytes) {
            val inputIndex = enc.dequeueInputBuffer(10_000)
            if (inputIndex < 0) break

            val inputBuffer = enc.getInputBuffer(inputIndex) ?: break
            inputBuffer.clear()

            val bytesToWrite = minOf(totalBytes - byteOffset, inputBuffer.remaining())
            inputBuffer.put(pcmBytes, byteOffset, bytesToWrite)
            enc.queueInputBuffer(inputIndex, 0, bytesToWrite, getElapsedMicros(), 0)
            byteOffset += bytesToWrite

            drainEncoder(false)
        }
    }

    fun shouldFinish(): Boolean {
        return System.currentTimeMillis() - startTimestamp >= chunkDurationMs
    }

    fun finish(): ChunkResult {
        val enc = encoder
        if (enc != null) {
            val inputIndex = enc.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                enc.queueInputBuffer(
                    inputIndex, 0, 0, getElapsedMicros(),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainEncoder(true)
        }

        val endTimestamp = System.currentTimeMillis()
        val path = outputFile?.absolutePath ?: ""

        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing encoder", e)
        }

        try {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing muxer", e)
        }

        encoder = null
        muxer = null
        muxerStarted = false

        Log.i(TAG, "Chunk recording finished: $path (${endTimestamp - startTimestamp}ms)")

        return ChunkResult(
            filePath = path,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = encoder ?: return
        val mux = muxer ?: return

        while (true) {
            val outputIndex = enc.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)

            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = mux.addTrack(enc.outputFormat)
                    mux.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val outputBuffer = enc.getOutputBuffer(outputIndex) ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        enc.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        mux.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        totalBytesWritten += bufferInfo.size
                    }

                    enc.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return
                    }
                }
                else -> break
            }
        }
    }

    private fun getElapsedMicros(): Long {
        return (System.currentTimeMillis() - startTimestamp) * 1000
    }
}
