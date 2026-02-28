package com.memorystream.embedding

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIEmbeddingEngine @Inject constructor() {

    companion object {
        private const val TAG = "OpenAIEmbeddingEngine"
        private const val EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings"
        private const val MODEL = "text-embedding-3-small"
        const val EMBEDDING_DIM = 1536
    }

    private var apiKey: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    var isInitialized = false
        private set

    fun initialize(apiKey: String) {
        this.apiKey = apiKey
        isInitialized = apiKey.isNotBlank()
        Log.i(TAG, "OpenAI embedding engine initialized")
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("OpenAIEmbeddingEngine not initialized")
        }

        val truncated = if (text.length > 8000) text.take(8000) else text

        val body = JsonObject().apply {
            addProperty("model", MODEL)
            addProperty("input", truncated)
        }

        val request = Request.Builder()
            .url(EMBEDDINGS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from OpenAI")

        if (!response.isSuccessful) {
            Log.e(TAG, "OpenAI API error ${response.code}: $responseBody")
            throw RuntimeException("OpenAI API error: ${response.code}")
        }

        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val embeddingArray = json
            .getAsJsonArray("data")
            .get(0).asJsonObject
            .getAsJsonArray("embedding")

        val result = FloatArray(embeddingArray.size())
        for (i in 0 until embeddingArray.size()) {
            result[i] = embeddingArray[i].asFloat
        }

        Log.i(TAG, "Generated embedding: ${result.size} dims")
        result
    }

    fun release() {
        isInitialized = false
    }
}
