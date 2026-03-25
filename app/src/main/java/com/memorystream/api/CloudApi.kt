package com.memorystream.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudApi @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "CloudApi"
    }

    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    val baseUrl: String get() = ApiConfig.cloudRunUrl

    /**
     * Validates that the base URL is configured before making API calls.
     * Throws [IllegalStateException] if the URL is blank or contains placeholder text.
     */
    private fun requireBaseUrl(): String {
        val url = baseUrl
        if (url.isBlank() || url.contains("YOUR_")) {
            throw IllegalStateException(
                "Cloud API URL is not configured. Set CLOUD_RUN_URL in local.properties or build config."
            )
        }
        return url
    }

    // ── Upload ──────────────────────────────────────────────────────────────

    data class UploadUrlResponse(val upload_url: String, val gcs_path: String)

    suspend fun getUploadUrl(filename: String): UploadUrlResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/upload-url?filename=${URLEncoder.encode(filename, "UTF-8")}")
            .post("".toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to get upload URL: ${response.code}")
                    return@withContext null
                }
                gson.fromJson(response.body?.string(), UploadUrlResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get upload URL", e)
            null
        }
    }

    suspend fun uploadAudioFile(uploadUrl: String, file: File): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(uploadUrl)
            .put(file.readBytes().toRequestBody("audio/mp4".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Audio upload failed: ${response.code}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio upload failed", e)
            false
        }
    }

    // ── Chunks ──────────────────────────────────────────────────────────────

    data class CreateChunkRequest(
        val chunk_id: String? = null,
        val user_id: String = "default",
        val start_timestamp: Long,
        val end_timestamp: Long,
        val audio_gcs_path: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val place_name: String? = null
    )

    data class CreateChunkResponse(val chunk_id: String, val status: String)

    suspend fun createChunk(req: CreateChunkRequest): CreateChunkResponse? = withContext(Dispatchers.IO) {
        val body = gson.toJson(req).toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$baseUrl/api/chunks")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create chunk failed: ${response.code} ${response.body?.string()?.take(200)}")
                    return@withContext null
                }
                gson.fromJson(response.body?.string(), CreateChunkResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create chunk failed", e)
            null
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────

    data class SearchChunkResult(
        val id: String,
        val transcript: String?,
        val summary: String?,
        val start_timestamp: Long,
        val place_name: String?,
        val similarity: Float
    )

    data class SearchUtteranceResult(
        val id: String,
        val chunk_id: String?,
        val text: String,
        val timestamp: Long,
        val speaker_id: String?,
        val similarity: Float
    )

    data class SearchResponse(
        val chunks: List<SearchChunkResult>,
        val utterances: List<SearchUtteranceResult>,
        val answer: String? = null
    )

    suspend fun search(query: String, limit: Int = 10): SearchResponse? = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("query" to query, "limit" to limit))
            .toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$baseUrl/api/search")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search failed: ${response.code}")
                    return@withContext null
                }
                gson.fromJson(response.body?.string(), SearchResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            null
        }
    }

    // ── Chunks list ─────────────────────────────────────────────────────────

    data class ChunkSummary(
        val id: String,
        val start_timestamp: Long,
        val end_timestamp: Long,
        val transcript: String?,
        val summary: String?,
        val status: String,
        val place_name: String?,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    suspend fun listChunks(limit: Int = 30): List<ChunkSummary> = withContext(Dispatchers.IO) {
        val url = requireBaseUrl()
        val request = Request.Builder()
            .url("$url/api/chunks?limit=$limit")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<ChunkSummary>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "List chunks failed", e)
            emptyList()
        }
    }

    // ── Chunks by range ─────────────────────────────────────────────────────

    suspend fun getChunksByRange(start: Long, end: Long): List<ChunkSummary> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/chunks/by-range?start=$start&end=$end")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<ChunkSummary>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get chunks by range failed", e)
            emptyList()
        }
    }

    // ── Daily summaries ─────────────────────────────────────────────────────

    data class DaySummaryResponse(
        val day_timestamp: Long,
        val chunk_count: Int,
        val total_duration_ms: Long,
        val places: String?
    )

    suspend fun getDailySummaries(limit: Int = 14, offset: Int = 0): List<DaySummaryResponse> = withContext(Dispatchers.IO) {
        val url = requireBaseUrl()
        val request = Request.Builder()
            .url("$url/api/daily-summaries?limit=$limit&offset=$offset")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<DaySummaryResponse>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get daily summaries failed", e)
            emptyList()
        }
    }

    // ── Insights ────────────────────────────────────────────────────────────

    data class InsightResult(
        val id: String,
        val type: String,
        val title: String,
        val body: String,
        val source_timestamp: Long,
        val created_at: Long,
        val place_hint: String?
    )

    suspend fun getInsights(
        limit: Int = 20,
        type: String? = null,
        start: Long? = null,
        end: Long? = null
    ): List<InsightResult> = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("$baseUrl/api/insights?limit=$limit")
        if (type != null) urlBuilder.append("&type=${URLEncoder.encode(type, "UTF-8")}")
        if (start != null) urlBuilder.append("&start=$start")
        if (end != null) urlBuilder.append("&end=$end")

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val t = object : TypeToken<List<InsightResult>>() {}.type
                gson.fromJson(response.body?.string(), t) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get insights failed", e)
            emptyList()
        }
    }

    suspend fun dismissInsight(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/insights/$id/dismiss")
            .put("".toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "Dismiss insight failed", e)
            false
        }
    }

    // ── Daily summary generation ────────────────────────────────────────────

    data class NarrativeResponse(val narrative: String?)

    suspend fun generateDaySummary(dayTimestamp: Long): NarrativeResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/daily-summary/generate?day_timestamp=$dayTimestamp")
            .post("".toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body?.string(), NarrativeResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generate day summary failed", e)
            null
        }
    }

    // ── Audio URL ───────────────────────────────────────────────────────────

    data class AudioUrlResponse(val audio_url: String)

    suspend fun getAudioUrl(chunkId: String): AudioUrlResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/chunks/$chunkId/audio-url")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body?.string(), AudioUrlResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get audio URL failed", e)
            null
        }
    }

    // ── Utterances ──────────────────────────────────────────────────────────

    data class UtteranceResult(
        val id: String,
        val chunk_id: String?,
        val timestamp: Long,
        val end_timestamp: Long?,
        val text: String,
        val speaker_id: String?,
        val diarization_label: Int?,
        val consolidated_speaker_id: String?
    )

    suspend fun getUtterances(chunkId: String): List<UtteranceResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/utterances?chunk_id=$chunkId")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<UtteranceResult>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get utterances failed", e)
            emptyList()
        }
    }

    // ── Speakers ────────────────────────────────────────────────────────────

    data class SpeakerResult(
        val id: String,
        val name: String,
        val is_primary: Boolean,
        val enrolled_at: Long,
        val color: Int
    )

    suspend fun getSpeakers(): List<SpeakerResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/speakers")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<SpeakerResult>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get speakers failed", e)
            emptyList()
        }
    }

    suspend fun createSpeaker(
        name: String,
        isPrimary: Boolean = false,
        color: Int = 0
    ): SpeakerResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/speakers?name=${URLEncoder.encode(name, "UTF-8")}&is_primary=$isPrimary&color=$color")
            .post("".toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body?.string(), SpeakerResult::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create speaker failed", e)
            null
        }
    }

    suspend fun deleteSpeaker(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/speakers/$id")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "Delete speaker failed", e)
            false
        }
    }

    // ── Exclusion Zones ────────────────────────────────────────────────────

    data class ExclusionZoneResult(
        val id: String,
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val radius_meters: Double
    )

    suspend fun getExclusionZones(userId: String = "default"): List<ExclusionZoneResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/exclusion-zones?user_id=$userId")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val type = object : TypeToken<List<ExclusionZoneResult>>() {}.type
                gson.fromJson(response.body?.string(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get exclusion zones failed", e)
            emptyList()
        }
    }

    suspend fun createExclusionZone(
        label: String, lat: Double, lng: Double, radius: Double, userId: String = "default"
    ): ExclusionZoneResult? = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf(
            "label" to label,
            "latitude" to lat,
            "longitude" to lng,
            "radius_meters" to radius,
            "user_id" to userId
        )).toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$baseUrl/api/exclusion-zones")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create exclusion zone failed: ${response.code}")
                    return@withContext null
                }
                gson.fromJson(response.body?.string(), ExclusionZoneResult::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create exclusion zone failed", e)
            null
        }
    }

    suspend fun deleteExclusionZone(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/exclusion-zones/$id")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "Delete exclusion zone failed", e)
            false
        }
    }

    // ── Health ──────────────────────────────────────────────────────────────

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
