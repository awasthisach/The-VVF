package com.vvf.smartfilemanager.ai

import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.sqrt

class SemanticSearchEngine(
    private val repository: IAppRepository,
    private val aiEngine: GeminiAIEngine
) {
    suspend fun searchSemantically(query: String, apiKey: String): List<Pair<FileEntity, Float>> {
        // 1. Get all local files
        val files = repository.allLocalNonSafeFiles.firstOrNull() ?: emptyList()
        if (files.isEmpty()) return emptyList()

        // 2. Compute query embedding vector
        val queryVector = aiEngine.generateEmbedding(query, "text/plain", apiKey)

        // 3. Score files based on cosine similarity
        val results = files.map { file ->
            val fileVector = aiEngine.generateEmbedding(file.name, file.mimeType, apiKey)
            val score = cosineSimilarity(queryVector, fileVector)
            Pair(file, score)
        }

        // 4. Sort by score descending
        return results.sortedByDescending { it.second }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
