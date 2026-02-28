package com.memorystream.embedding

import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.db.UtteranceEntity
import com.memorystream.data.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed class SearchResult {
    abstract val score: Float
    abstract val text: String
    abstract val timestamp: Long

    data class ChunkResult(
        val chunk: MemoryChunkEntity,
        override val score: Float
    ) : SearchResult() {
        override val text: String get() = chunk.summary ?: chunk.transcript ?: ""
        override val timestamp: Long get() = chunk.startTimestamp
    }

    data class UtteranceResult(
        val utterance: UtteranceEntity,
        override val score: Float
    ) : SearchResult() {
        override val text: String get() = utterance.text
        override val timestamp: Long get() = utterance.timestamp
    }
}

@Singleton
class SemanticSearchEngine @Inject constructor(
    private val embeddingEngine: OpenAIEmbeddingEngine,
    private val repository: MemoryRepository
) {
    suspend fun search(query: String, topK: Int = 10): List<SearchResult> {
        if (!embeddingEngine.isInitialized) {
            return emptyList()
        }

        val queryEmbedding = embeddingEngine.embed(query)

        // Search both layers in parallel
        val chunkResults = searchChunks(queryEmbedding)
        val utteranceResults = searchUtterances(queryEmbedding)

        // Merge and deduplicate by score
        return (chunkResults + utteranceResults)
            .sortedByDescending { it.score }
            .take(topK)
    }

    private suspend fun searchChunks(queryEmbedding: FloatArray): List<SearchResult> {
        return repository.getEmbeddedChunks()
            .mapNotNull { chunk ->
                val vec = chunk.embeddingVector ?: return@mapNotNull null
                if (vec.size != queryEmbedding.size) return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding, vec)
                SearchResult.ChunkResult(chunk, score)
            }
    }

    private suspend fun searchUtterances(queryEmbedding: FloatArray): List<SearchResult> {
        return repository.getEmbeddedUtterances()
            .mapNotNull { utterance ->
                val vec = utterance.embeddingVector ?: return@mapNotNull null
                if (vec.size != queryEmbedding.size) return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding, vec)
                SearchResult.UtteranceResult(utterance, score)
            }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }
}
