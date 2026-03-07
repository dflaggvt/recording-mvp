package com.memorystream.data.model

data class ChunkResult(
    val filePath: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null
)
