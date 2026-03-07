package com.memorystream.api

import com.memorystream.BuildConfig

object ApiConfig {
    val cloudRunUrl: String get() = BuildConfig.CLOUD_RUN_URL

    fun isCloudConfigured(): Boolean {
        return cloudRunUrl.isNotBlank() && !cloudRunUrl.contains("YOUR_")
    }
}
