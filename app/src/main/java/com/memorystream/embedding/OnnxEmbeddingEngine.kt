package com.memorystream.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class OnnxEmbeddingEngine @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "OnnxEmbeddingEngine"
        const val EMBEDDING_DIM = 384
    }

    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    var isInitialized = false
        private set

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing ONNX embedding engine: $modelPath")
            environment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = environment!!.createSession(modelPath, sessionOptions)
            isInitialized = true
            Log.i(TAG, "ONNX embedding engine initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX engine", e)
            isInitialized = false
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("OnnxEmbeddingEngine not initialized")
        }

        val env = environment!!
        val sess = session!!

        // Tokenize: for models with built-in tokenizer (ONNX Runtime Extensions),
        // we pass raw text. For standard models, we use simple tokenization.
        val inputNames = sess.inputNames.toList()

        val result = if (inputNames.any { it.contains("input_ids") }) {
            embedWithTokenIds(env, sess, text)
        } else {
            embedWithRawText(env, sess, text)
        }

        normalize(result)
    }

    private fun embedWithRawText(
        env: OrtEnvironment,
        session: OrtSession,
        text: String
    ): FloatArray {
        val inputTensor = OnnxTensor.createTensor(env, arrayOf(text))
        val output = session.run(mapOf(session.inputNames.first() to inputTensor))
        val embeddings = output[0].value as Array<FloatArray>
        inputTensor.close()
        output.close()
        return embeddings[0]
    }

    private fun embedWithTokenIds(
        env: OrtEnvironment,
        session: OrtSession,
        text: String
    ): FloatArray {
        // Simple whitespace tokenization fallback
        // In production, this should use WordPiece. For MVP with ONNX Runtime Extensions
        // model that includes tokenizer, embedWithRawText path is used instead.
        val tokens = simpleTokenize(text)
        val inputIds = Array(1) { tokens.map { it.toLong() }.toLongArray() }
        val attentionMask = Array(1) { LongArray(tokens.size) { 1L } }
        val tokenTypeIds = Array(1) { LongArray(tokens.size) { 0L } }

        val inputs = mutableMapOf<String, OnnxTensor>()
        inputs["input_ids"] = OnnxTensor.createTensor(env, inputIds)
        inputs["attention_mask"] = OnnxTensor.createTensor(env, attentionMask)
        if (session.inputNames.contains("token_type_ids")) {
            inputs["token_type_ids"] = OnnxTensor.createTensor(env, tokenTypeIds)
        }

        val output = session.run(inputs)

        // Mean pooling over token embeddings
        val rawOutput = output[0].value
        val embeddings = when (rawOutput) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val arr = rawOutput as Array<Array<FloatArray>>
                meanPool(arr[0])
            }
            else -> FloatArray(EMBEDDING_DIM)
        }

        inputs.values.forEach { it.close() }
        output.close()
        return embeddings
    }

    private fun simpleTokenize(text: String): List<Int> {
        val vocabSize = 30522
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val ids = mutableListOf(101) // [CLS]
        for (word in words.take(510)) {
            // Map hash to valid vocab range [1000, vocabSize-1]
            val hash = (word.hashCode() and Int.MAX_VALUE) % (vocabSize - 1000) + 1000
            ids.add(hash)
        }
        ids.add(102) // [SEP]
        return ids
    }

    private fun meanPool(tokenEmbeddings: Array<FloatArray>): FloatArray {
        val dim = tokenEmbeddings[0].size
        val result = FloatArray(dim)
        for (token in tokenEmbeddings) {
            for (i in token.indices) {
                result[i] += token[i]
            }
        }
        val n = tokenEmbeddings.size.toFloat()
        for (i in result.indices) {
            result[i] /= n
        }
        return result
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    fun release() {
        session?.close()
        environment?.close()
        session = null
        environment = null
        isInitialized = false
        Log.i(TAG, "ONNX embedding engine released")
    }
}
