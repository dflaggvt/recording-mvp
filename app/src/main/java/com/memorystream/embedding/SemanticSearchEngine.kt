package com.memorystream.embedding

import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResult(
    val chunk: MemoryChunkEntity,
    val score: Float
)

@Singleton
class SemanticSearchEngine @Inject constructor(
    private val embeddingEngine: OnnxEmbeddingEngine,
    private val repository: MemoryRepository
) {
    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        if (!embeddingEngine.isInitialized) {
            return emptyList()
        }

        val queryEmbedding = embeddingEngine.embed(query)
        val embeddedChunks = repository.getEmbeddedChunks()

        return embeddedChunks
            .mapNotNull { chunk ->
                val chunkVector = chunk.embeddingVector ?: return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding, chunkVector)
                SearchResult(chunk, score)
            }
            .sortedByDescending { it.score }
            .take(topK)
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
