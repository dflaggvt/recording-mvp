package com.memorystream.di

import android.content.Context
import com.memorystream.data.db.AppDatabase
import com.memorystream.data.db.MemoryChunkDao
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.embedding.OnnxEmbeddingEngine
import com.memorystream.embedding.SemanticSearchEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.create(context)
    }

    @Provides
    @Singleton
    fun provideMemoryChunkDao(database: AppDatabase): MemoryChunkDao {
        return database.memoryChunkDao()
    }

    @Provides
    @Singleton
    fun provideMemoryRepository(dao: MemoryChunkDao): MemoryRepository {
        return MemoryRepository(dao)
    }

    @Provides
    @Singleton
    fun provideEmbeddingEngine(@ApplicationContext context: Context): OnnxEmbeddingEngine {
        return OnnxEmbeddingEngine(context)
    }

    @Provides
    @Singleton
    fun provideSemanticSearchEngine(
        embeddingEngine: OnnxEmbeddingEngine,
        repository: MemoryRepository
    ): SemanticSearchEngine {
        return SemanticSearchEngine(embeddingEngine, repository)
    }
}
