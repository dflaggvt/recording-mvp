package com.memorystream.intelligence

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Commitment(
    val text: String,
    val type: String,
    val confidence: Float = 0.8f
)

@Singleton
class CommitmentDetector @Inject constructor() {

    companion object {
        private const val TAG = "CommitmentDetector"
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"

        private const val SYSTEM_PROMPT = """You analyze conversation transcripts and extract commitments, promises, decisions, and plans.

For each item found, return a JSON array of objects with:
- "text": the exact or near-exact quote
- "type": one of "promise", "decision", "plan", "preference", "reminder"

Only include clear, actionable items. Skip filler, greetings, and vague statements.
If nothing is found, return an empty array: []

Return ONLY the JSON array, no other text."""
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
        Log.i(TAG, "Commitment detector initialized")
    }

    suspend fun detect(transcript: String): List<Commitment> = withContext(Dispatchers.IO) {
        if (!isInitialized || transcript.isBlank()) {
            return@withContext emptyList()
        }

        try {
            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", transcript)
                })
            }

            val body = JsonObject().apply {
                addProperty("model", MODEL)
                add("messages", messages)
                addProperty("temperature", 0.1)
                addProperty("max_tokens", 1000)
            }

            val request = Request.Builder()
                .url(CHAT_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI API error ${response.code}: $responseBody")
                return@withContext emptyList()
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val content = json
                .getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
                .trim()

            val cleanedContent = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val commitmentType = object : TypeToken<List<Commitment>>() {}.type
            val commitments: List<Commitment> = gson.fromJson(cleanedContent, commitmentType)

            Log.i(TAG, "Detected ${commitments.size} commitments")
            commitments
        } catch (e: Exception) {
            Log.e(TAG, "Commitment detection failed", e)
            emptyList()
        }
    }
}
