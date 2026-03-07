package com.memorystream.service

import android.util.Log
import com.memorystream.api.CloudApi
import com.memorystream.data.model.ChunkResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads completed audio chunks to GCS and triggers the cloud processing pipeline.
 * Used when cloud mode is enabled; replaces local Deepgram/OpenAI processing.
 */
@Singleton
class CloudChunkUploader @Inject constructor(
    private val cloudApi: CloudApi
) {
    companion object {
        private const val TAG = "CloudChunkUploader"
    }

    suspend fun uploadAndProcess(
        chunkResult: ChunkResult,
        latitude: Double? = null,
        longitude: Double? = null,
        placeName: String? = null
    ): Boolean {
        val file = File(chunkResult.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file not found: ${chunkResult.filePath}")
            return false
        }

        // Step 1: Get a signed upload URL
        val uploadInfo = cloudApi.getUploadUrl(file.name)
        if (uploadInfo == null) {
            Log.e(TAG, "Failed to get upload URL for ${file.name}")
            return false
        }

        // Step 2: Upload the audio file to GCS
        val uploaded = cloudApi.uploadAudioFile(uploadInfo.upload_url, file)
        if (!uploaded) {
            Log.e(TAG, "Failed to upload audio file ${file.name}")
            return false
        }

        Log.i(TAG, "Audio uploaded to ${uploadInfo.gcs_path}")

        // Step 3: Create chunk record (triggers Workflow automatically)
        val response = cloudApi.createChunk(
            CloudApi.CreateChunkRequest(
                start_timestamp = chunkResult.startTimestamp,
                end_timestamp = chunkResult.endTimestamp,
                audio_gcs_path = uploadInfo.gcs_path,
                latitude = latitude,
                longitude = longitude,
                place_name = placeName
            )
        )

        if (response == null) {
            Log.e(TAG, "Failed to create cloud chunk record")
            return false
        }

        Log.i(TAG, "Cloud chunk created: ${response.chunk_id}, pipeline triggered")

        if (file.delete()) {
            Log.d(TAG, "Local audio file deleted: ${file.name}")
        } else {
            Log.w(TAG, "Failed to delete local audio file: ${file.name}")
        }

        return true
    }
}
