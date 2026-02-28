package com.memorystream.transcription

import android.util.Log
import com.google.gson.Gson
import com.memorystream.data.model.ChunkStatus
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.embedding.OpenAIEmbeddingEngine
import com.memorystream.intelligence.CommitmentDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class TranscriptionWorker(
    private val repository: MemoryRepository,
    private val embeddingEngine: OpenAIEmbeddingEngine,
    private val commitmentDetector: CommitmentDetector
) {
    companion object {
        private const val TAG = "TranscriptionWorker"
    }

    private val gson = Gson()

    suspend fun processChunk(chunkId: String, transcript: String) {
        val chunk = repository.getChunkById(chunkId) ?: run {
            Log.e(TAG, "No chunk found for id $chunkId")
            return
        }

        if (transcript.isBlank()) {
            Log.w(TAG, "Empty transcript for chunk $chunkId")
            repository.update(chunk.copy(status = ChunkStatus.EMBEDDED, transcript = ""))
            return
        }

        try {
            val summary = generateSummary(transcript)
            repository.update(chunk.copy(
                transcript = transcript,
                summary = summary,
                status = ChunkStatus.TRANSCRIBED
            ))
            Log.i(TAG, "Transcript saved for chunk $chunkId (${transcript.length} chars)")

            // Run commitment detection and embedding in parallel
            coroutineScope {
                val embeddingDeferred = async {
                    val textToEmbed = if (summary.isNotBlank()) "$summary $transcript" else transcript
                    embeddingEngine.embed(textToEmbed)
                }

                val commitmentsDeferred = async {
                    commitmentDetector.detect(transcript)
                }

                val embedding = embeddingDeferred.await()
                val commitments = commitmentsDeferred.await()

                val commitmentsJson = if (commitments.isNotEmpty()) {
                    gson.toJson(commitments)
                } else null

                repository.update(chunk.copy(
                    transcript = transcript,
                    summary = summary,
                    commitments = commitmentsJson,
                    embeddingVector = embedding,
                    status = ChunkStatus.EMBEDDED
                ))

                if (commitments.isNotEmpty()) {
                    Log.i(TAG, "Chunk $chunkId: ${commitments.size} commitments detected")
                    commitments.forEach { c ->
                        Log.i(TAG, "  [${c.type}] ${c.text}")
                    }
                }
                Log.i(TAG, "Processing complete for chunk $chunkId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for chunk $chunkId", e)
            repository.update(chunk.copy(status = ChunkStatus.ERROR))
        }
    }

    private fun generateSummary(transcript: String): String {
        if (transcript.isBlank()) return ""

        val sentences = transcript.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return when {
            sentences.size <= 3 -> sentences.joinToString(". ") + "."
            else -> sentences.take(3).joinToString(". ") + "."
        }.take(500)
    }
}
