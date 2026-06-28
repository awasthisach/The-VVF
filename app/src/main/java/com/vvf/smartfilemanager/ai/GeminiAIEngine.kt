package com.vvf.smartfilemanager.ai

import android.util.Log
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAIEngine(private val repository: IAppRepository) {

    suspend fun generateEmbedding(fileName: String, mimeType: String, apiKey: String): List<Float> = withContext(Dispatchers.IO) {
        val cached = EmbeddingCache.get(fileName)
        if (cached != null) return@withContext cached

        val embedding = mutableListOf<Float>()
        
        if (apiKey.isBlank()) {
            // Local high-performance deterministic fallback embedding vector
            val hash = fileName.hashCode()
            for (i in 0 until 16) {
                embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
            }
        } else {
            try {
                // Generate a real 16-dimensional semantic representation using Gemini 3.5 Flash
                val prompt = "Create a unique 16-dimensional semantic vector representing the file characteristics.\n" +
                        "Filename: $fileName\n" +
                        "Type: $mimeType\n" +
                        "Output strictly 16 numbers separated by commas, no spaces, no words, e.g. 0.1,0.5,0.2,0.9,0.8,0.3,0.4,0.1,0.2,0.5,0.6,0.3,0.4,0.7,0.8,0.9"
                val (response, _) = repository.callGemini(apiKey, prompt, enableThinkingMode = false)
                val nums = response.trim().split(",").mapNotNull { it.trim().toFloatOrNull() }
                if (nums.size == 16) {
                    embedding.addAll(nums)
                } else {
                    // Fallback
                    val hash = fileName.hashCode()
                    for (i in 0 until 16) {
                        embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiAIEngine", "Failed to generate AI embedding", e)
                val hash = fileName.hashCode()
                for (i in 0 until 16) {
                    embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
                }
            }
        }
        
        EmbeddingCache.put(fileName, embedding)
        embedding
    }
}
