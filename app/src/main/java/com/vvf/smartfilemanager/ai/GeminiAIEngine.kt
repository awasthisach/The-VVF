package com.vvf.smartfilemanager.ai

import android.util.Log
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class GeminiAIEngine(private val repository: IAppRepository) {

    private val activeRequests = ConcurrentHashMap<String, Deferred<List<Float>>>()

    suspend fun generateEmbedding(fileName: String, mimeType: String, apiKey: String): List<Float> = withContext(Dispatchers.IO) {
        val normalizedName = fileName.trim().lowercase()
        val normalizedMime = mimeType.trim().lowercase()
        val cacheKey = if (normalizedMime == "text/plain" || normalizedMime.isEmpty()) {
            "query_${normalizedName}"
        } else {
            "file_${normalizedName}_${normalizedMime}"
        }

        // 1. Check persistent memory cache (instant, zero-cost)
        val cached = EmbeddingCache.get(cacheKey)
        if (cached != null) return@withContext cached

        // 2. Request Coalescing (Deduplication) for parallel matching requests
        val deferred = coroutineScope {
            activeRequests.getOrPut(cacheKey) {
                async(Dispatchers.IO) {
                    val embedding = mutableListOf<Float>()
                    if (apiKey.isBlank()) {
                        // Fast, deterministic offline fallback
                        val hash = cacheKey.hashCode()
                        for (i in 0 until 16) {
                            embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
                        }
                    } else {
                        try {
                            // Generate a 16-dimensional semantic representation using Gemini 3.5 Flash
                            val prompt = "Create a unique 16-dimensional semantic vector representing the file characteristics.\n" +
                                    "Filename: $fileName\n" +
                                    "Type: $mimeType\n" +
                                    "Output strictly 16 numbers separated by commas, no spaces, no words, e.g. 0.1,0.5,0.2,0.9,0.8,0.3,0.4,0.1,0.2,0.5,0.6,0.3,0.4,0.7,0.8,0.9"
                            val (response, _) = repository.callGemini(apiKey, prompt, enableThinkingMode = false)
                            val nums = response.trim().split(",").mapNotNull { it.trim().toFloatOrNull() }
                            if (nums.size == 16) {
                                embedding.addAll(nums)
                            } else {
                                val hash = cacheKey.hashCode()
                                for (i in 0 until 16) {
                                    embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GeminiAIEngine", "Failed to generate AI embedding, using offline fallback", e)
                            val hash = cacheKey.hashCode()
                            for (i in 0 until 16) {
                                embedding.add(kotlin.math.abs(((hash xor (i * 1234567)) % 100) / 100f))
                            }
                        }
                    }
                    EmbeddingCache.put(cacheKey, embedding)
                    activeRequests.remove(cacheKey)
                    embedding
                }
            }
        }

        deferred.await()
    }
}
