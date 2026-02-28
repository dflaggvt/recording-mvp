package com.memorystream.di

import android.content.Context
import com.memorystream.api.ApiConfig
import com.memorystream.data.db.AppDatabase
import com.memorystream.data.db.MemoryChunkDao
import com.memorystream.data.db.UtteranceDao
import com.memorystream.data.repository.MemoryRepository
import com.memorystream.embedding.OpenAIEmbeddingEngine
import com.memorystream.embedding.SemanticSearchEngine
import com.memorystream.intelligence.CommitmentDetector
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
    fun provideUtteranceDao(database: AppDatabase): UtteranceDao {
        return database.utteranceDao()
    }

    @Provides
    @Singleton
    fun provideMemoryRepository(chunkDao: MemoryChunkDao, utteranceDao: UtteranceDao): MemoryRepository {
        return MemoryRepository(chunkDao, utteranceDao)
    }

    @Provides
    @Singleton
    fun provideEmbeddingEngine(): OpenAIEmbeddingEngine {
        return OpenAIEmbeddingEngine().also {
            it.initialize(ApiConfig.openaiApiKey)
        }
    }

    @Provides
    @Singleton
    fun provideCommitmentDetector(): CommitmentDetector {
        return CommitmentDetector().also {
            it.initialize(ApiConfig.openaiApiKey)
        }
    }

    @Provides
    @Singleton
    fun provideSemanticSearchEngine(
        embeddingEngine: OpenAIEmbeddingEngine,
        repository: MemoryRepository
    ): SemanticSearchEngine {
        return SemanticSearchEngine(embeddingEngine, repository)
    }
}
