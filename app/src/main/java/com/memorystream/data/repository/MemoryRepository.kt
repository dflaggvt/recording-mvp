package com.memorystream.data.repository

import com.memorystream.data.db.MemoryChunkDao
import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.model.ChunkStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryChunkDao
) {
    fun getAllChunks(): Flow<List<MemoryChunkEntity>> = dao.getAllChunks()

    fun getRecentChunks(limit: Int = 50): Flow<List<MemoryChunkEntity>> = dao.getRecentChunks(limit)

    fun getChunkCount(): Flow<Int> = dao.getChunkCount()

    fun getTotalDurationMs(): Flow<Long?> = dao.getTotalRecordingDurationMs()

    suspend fun insert(chunk: MemoryChunkEntity) = dao.insert(chunk)

    suspend fun update(chunk: MemoryChunkEntity) = dao.update(chunk)

    suspend fun getChunkById(id: String): MemoryChunkEntity? = dao.getChunkById(id)

    suspend fun getChunksByStatus(status: ChunkStatus): List<MemoryChunkEntity> =
        dao.getChunksByStatus(status)

    suspend fun getEmbeddedChunks(): List<MemoryChunkEntity> = dao.getEmbeddedChunks()

    suspend fun deleteChunk(id: String) = dao.deleteChunk(id)
}
