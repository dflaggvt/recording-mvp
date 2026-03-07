package com.memorystream.di

import android.content.Context
import com.memorystream.audio.AudioPlaybackManager
import com.memorystream.service.LocationProvider
import com.memorystream.service.ExclusionZoneManager
import com.memorystream.service.PlaceResolver
import com.memorystream.api.CloudApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAudioPlaybackManager(): AudioPlaybackManager {
        return AudioPlaybackManager()
    }

    @Provides
    @Singleton
    fun provideCloudApi(client: OkHttpClient): CloudApi {
        return CloudApi(client)
    }

    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        return LocationProvider(context)
    }

    @Provides
    @Singleton
    fun providePlaceResolver(): PlaceResolver {
        return PlaceResolver()
    }

    @Provides
    @Singleton
    fun provideExclusionZoneManager(@ApplicationContext context: Context): ExclusionZoneManager {
        return ExclusionZoneManager(context)
    }
}
