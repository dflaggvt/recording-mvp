package com.memorystream.data.repository

import com.memorystream.data.db.MemoryChunkDao
import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.db.UtteranceDao
import com.memorystream.data.db.UtteranceEntity
import com.memorystream.data.model.ChunkStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val chunkDao: MemoryChunkDao,
    private val utteranceDao: UtteranceDao
) {
    fun getAllChunks(): Flow<List<MemoryChunkEntity>> = chunkDao.getAllChunks()

    fun getRecentChunks(limit: Int = 50): Flow<List<MemoryChunkEntity>> = chunkDao.getRecentChunks(limit)

    fun getChunkCount(): Flow<Int> = chunkDao.getChunkCount()

    fun getTotalDurationMs(): Flow<Long?> = chunkDao.getTotalRecordingDurationMs()

    suspend fun insert(chunk: MemoryChunkEntity) = chunkDao.insert(chunk)

    suspend fun update(chunk: MemoryChunkEntity) = chunkDao.update(chunk)

    suspend fun getChunkById(id: String): MemoryChunkEntity? = chunkDao.getChunkById(id)

    suspend fun getChunksByStatus(status: ChunkStatus): List<MemoryChunkEntity> =
        chunkDao.getChunksByStatus(status)

    suspend fun getEmbeddedChunks(): List<MemoryChunkEntity> = chunkDao.getEmbeddedChunks()

    suspend fun deleteChunk(id: String) = chunkDao.deleteChunk(id)

    // Utterance methods
    suspend fun insertUtterance(utterance: UtteranceEntity) = utteranceDao.insert(utterance)

    suspend fun updateUtterance(utterance: UtteranceEntity) = utteranceDao.update(utterance)

    suspend fun getEmbeddedUtterances(): List<UtteranceEntity> = utteranceDao.getEmbeddedUtterances()

    suspend fun assignUtterancesToChunk(chunkId: String, startTime: Long, endTime: Long) =
        utteranceDao.assignChunkId(chunkId, startTime, endTime)
}
