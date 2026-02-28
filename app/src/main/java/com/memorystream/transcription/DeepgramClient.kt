package com.memorystream.transcription

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class DeepgramClient(private val apiKey: String) {

    companion object {
        private const val TAG = "DeepgramClient"
        private const val DEEPGRAM_WS_URL =
            "wss://api.deepgram.com/v1/listen" +
                "?model=nova-2" +
                "&encoding=linear16" +
                "&sample_rate=16000" +
                "&channels=1" +
                "&punctuate=true" +
                "&interim_results=true" +
                "&smart_format=true" +
                "&utterance_end_ms=1500"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val finalSegments = mutableListOf<String>()

    var isConnected = false
        private set

    var onFinalUtterance: ((String, Long) -> Unit)? = null

    fun connect() {
        val request = Request.Builder()
            .url(DEEPGRAM_WS_URL)
            .addHeader("Authorization", "Token $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.i(TAG, "WebSocket connected to Deepgram")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                isConnected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                isConnected = false
            }
        })
    }

    fun sendAudio(pcmData: ShortArray, sampleCount: Int) {
        if (!isConnected) return

        val byteBuffer = ByteBuffer.allocate(sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(pcmData, 0, sampleCount)
        webSocket?.send(byteBuffer.array().toByteString(0, sampleCount * 2))
    }

    fun getAndClearFinalTranscript(): String {
        val transcript = finalSegments.joinToString(" ").trim()
        finalSegments.clear()
        _liveTranscript.value = ""
        return transcript
    }

    fun disconnect() {
        webSocket?.send("{\"type\": \"CloseStream\"}")
        webSocket?.close(1000, "Recording stopped")
        webSocket = null
        isConnected = false
        Log.i(TAG, "Disconnected from Deepgram")
    }

    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val channel = json.getAsJsonObject("channel") ?: return
            val alternatives = channel.getAsJsonArray("alternatives")
            if (alternatives == null || alternatives.size() == 0) return

            val alt = alternatives[0].asJsonObject
            val transcript = alt.get("transcript")?.asString ?: return
            if (transcript.isBlank()) return

            val isFinal = json.get("is_final")?.asBoolean ?: false

            if (isFinal) {
                finalSegments.add(transcript)
                _liveTranscript.value = finalSegments.joinToString(" ")
                Log.d(TAG, "Final: $transcript")
                onFinalUtterance?.invoke(transcript, System.currentTimeMillis())
            } else {
                val currentFinals = finalSegments.joinToString(" ")
                _liveTranscript.value = if (currentFinals.isBlank()) transcript
                    else "$currentFinals $transcript"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Deepgram message", e)
        }
    }
}
