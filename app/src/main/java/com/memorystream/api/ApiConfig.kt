package com.memorystream.api

import com.memorystream.BuildConfig

object ApiConfig {
    val deepgramApiKey: String get() = BuildConfig.DEEPGRAM_API_KEY
    val openaiApiKey: String get() = BuildConfig.OPENAI_API_KEY

    fun isConfigured(): Boolean {
        return deepgramApiKey.isNotBlank() &&
            !deepgramApiKey.contains("YOUR_") &&
            openaiApiKey.isNotBlank() &&
            !openaiApiKey.contains("YOUR_")
    }
}
