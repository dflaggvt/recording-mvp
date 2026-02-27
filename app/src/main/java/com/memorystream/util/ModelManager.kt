package com.memorystream.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ModelManager {

    private const val TAG = "ModelManager"
    private const val WHISPER_MODEL_FILENAME = "ggml-tiny.bin"
    private const val EMBEDDING_MODEL_FILENAME = "all-MiniLM-L6-v2.onnx"
    private const val MODELS_DIR = "models"

    fun getWhisperModelPath(context: Context): String {
        return File(context.filesDir, "$MODELS_DIR/$WHISPER_MODEL_FILENAME").absolutePath
    }

    fun getEmbeddingModelPath(context: Context): String {
        return File(context.filesDir, "$MODELS_DIR/$EMBEDDING_MODEL_FILENAME").absolutePath
    }

    suspend fun ensureModelsReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val whisperReady = copyAssetIfNeeded(context, WHISPER_MODEL_FILENAME, modelsDir)
        val embeddingReady = copyAssetIfNeeded(context, EMBEDDING_MODEL_FILENAME, modelsDir)

        whisperReady && embeddingReady
    }

    private fun copyAssetIfNeeded(context: Context, assetName: String, destDir: File): Boolean {
        val destFile = File(destDir, assetName)
        if (destFile.exists() && destFile.length() > 0) {
            Log.i(TAG, "Model already exists: ${destFile.absolutePath}")
            return true
        }

        return try {
            Log.i(TAG, "Copying model from assets: $assetName")
            context.assets.open("$MODELS_DIR/$assetName").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            Log.i(TAG, "Model copied: ${destFile.absolutePath} (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model: $assetName", e)
            false
        }
    }
}
