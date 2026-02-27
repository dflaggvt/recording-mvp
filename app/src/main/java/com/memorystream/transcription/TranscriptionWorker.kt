package com.memorystream.transcription

import android.content.Context
import android.util.Log
import com.memorystream.data.model.ChunkResult
import com.memorystream.data.model.ChunkStatus
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.embedding.OnnxEmbeddingEngine
import com.memorystream.util.ModelManager

class TranscriptionWorker(
    private val context: Context,
    private val repository: MemoryRepository,
    private val embeddingEngine: OnnxEmbeddingEngine
) {
    companion object {
        private const val TAG = "TranscriptionWorker"
    }

    private var whisperEngine: WhisperEngine? = null

    private suspend fun ensureWhisperInitialized() {
        if (whisperEngine?.isInitialized == true) return

        val modelPath = ModelManager.getWhisperModelPath(context)
        if (!ModelManager.ensureModelsReady(context)) {
            throw IllegalStateException("Failed to prepare Whisper model")
        }

        whisperEngine = WhisperEngine(context).also {
            it.initialize(modelPath)
        }
    }

    suspend fun processChunk(chunkResult: ChunkResult) {
        val chunk = findChunkByPath(chunkResult.filePath) ?: run {
            Log.e(TAG, "No chunk entity found for ${chunkResult.filePath}")
            return
        }

        // Phase 1: Transcribe
        try {
            repository.update(chunk.copy(status = ChunkStatus.TRANSCRIBING))
            ensureWhisperInitialized()

            val transcript = whisperEngine!!.transcribe(chunkResult.filePath)
            val summary = generateSummary(transcript)

            repository.update(
                chunk.copy(
                    transcript = transcript,
                    summary = summary,
                    status = ChunkStatus.TRANSCRIBED
                )
            )
            Log.i(TAG, "Transcription complete for chunk ${chunk.id}")

            // Phase 2: Generate embedding
            repository.update(chunk.copy(
                transcript = transcript,
                summary = summary,
                status = ChunkStatus.EMBEDDING
            ))

            if (!embeddingEngine.isInitialized) {
                val embeddingModelPath = ModelManager.getEmbeddingModelPath(context)
                embeddingEngine.initialize(embeddingModelPath)
            }

            val textToEmbed = if (summary.isNotBlank()) "$summary $transcript" else transcript
            val embedding = embeddingEngine.embed(textToEmbed)

            repository.update(
                chunk.copy(
                    transcript = transcript,
                    summary = summary,
                    embeddingVector = embedding,
                    status = ChunkStatus.EMBEDDED
                )
            )
            Log.i(TAG, "Embedding complete for chunk ${chunk.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for chunk ${chunk.id}", e)
            repository.update(chunk.copy(status = ChunkStatus.ERROR))
        }
    }

    private suspend fun findChunkByPath(filePath: String) =
        repository.getChunksByStatus(ChunkStatus.PENDING_TRANSCRIPTION)
            .firstOrNull { it.audioFilePath == filePath }
            ?: repository.getChunksByStatus(ChunkStatus.TRANSCRIBING)
                .firstOrNull { it.audioFilePath == filePath }

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

    fun release() {
        whisperEngine?.release()
        whisperEngine = null
    }
}
