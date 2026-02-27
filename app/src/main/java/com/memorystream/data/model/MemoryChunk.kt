package com.memorystream.data.model

enum class ChunkStatus {
    RECORDING,
    PENDING_TRANSCRIPTION,
    TRANSCRIBING,
    TRANSCRIBED,
    EMBEDDING,
    EMBEDDED,
    ERROR
}

data class ChunkResult(
    val filePath: String,
    val startTimestamp: Long,
    val endTimestamp: Long
)
