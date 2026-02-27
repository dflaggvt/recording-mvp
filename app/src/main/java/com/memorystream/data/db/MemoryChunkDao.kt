package com.memorystream.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.memorystream.data.model.ChunkStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: MemoryChunkEntity)

    @Update
    suspend fun update(chunk: MemoryChunkEntity)

    @Query("SELECT * FROM memory_chunks ORDER BY startTimestamp DESC")
    fun getAllChunks(): Flow<List<MemoryChunkEntity>>

    @Query("SELECT * FROM memory_chunks ORDER BY startTimestamp DESC LIMIT :limit")
    fun getRecentChunks(limit: Int = 50): Flow<List<MemoryChunkEntity>>

    @Query("SELECT * FROM memory_chunks WHERE status = :status ORDER BY startTimestamp ASC")
    suspend fun getChunksByStatus(status: ChunkStatus): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks WHERE status = 'EMBEDDED'")
    suspend fun getEmbeddedChunks(): List<MemoryChunkEntity>

    @Query("SELECT * FROM memory_chunks WHERE id = :id")
    suspend fun getChunkById(id: String): MemoryChunkEntity?

    @Query("SELECT COUNT(*) FROM memory_chunks")
    fun getChunkCount(): Flow<Int>

    @Query("SELECT SUM(endTimestamp - startTimestamp) FROM memory_chunks")
    fun getTotalRecordingDurationMs(): Flow<Long?>

    @Query("DELETE FROM memory_chunks WHERE id = :id")
    suspend fun deleteChunk(id: String)
}
