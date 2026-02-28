package com.memorystream.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UtteranceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(utterance: UtteranceEntity)

    @Update
    suspend fun update(utterance: UtteranceEntity)

    @Query("SELECT * FROM utterances WHERE isEmbedded = 1")
    suspend fun getEmbeddedUtterances(): List<UtteranceEntity>

    @Query("SELECT * FROM utterances WHERE chunkId = :chunkId ORDER BY timestamp ASC")
    suspend fun getUtterancesForChunk(chunkId: String): List<UtteranceEntity>

    @Query("UPDATE utterances SET chunkId = :chunkId WHERE chunkId IS NULL AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun assignChunkId(chunkId: String, startTime: Long, endTime: Long)
}
