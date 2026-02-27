package com.memorystream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.memorystream.data.model.ChunkStatus

@Entity(tableName = "memory_chunks")
data class MemoryChunkEntity(
    @PrimaryKey val id: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val transcript: String? = null,
    val summary: String? = null,
    val embeddingVector: FloatArray? = null,
    val audioFilePath: String,
    val status: ChunkStatus = ChunkStatus.RECORDING
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemoryChunkEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
