package com.memorystream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "utterances")
data class UtteranceEntity(
    @PrimaryKey val id: String,
    val chunkId: String? = null,
    val timestamp: Long,
    val text: String,
    val embeddingVector: FloatArray? = null,
    val isEmbedded: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UtteranceEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
