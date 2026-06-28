package com.vvf.smartfilemanager.domain

import com.vvf.smartfilemanager.ai.GeminiAIEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateAIEmbeddingUseCase(private val aiEngine: GeminiAIEngine) {
    suspend operator fun invoke(fileName: String, mimeType: String, apiKey: String): List<Float> = withContext(Dispatchers.IO) {
        aiEngine.generateEmbedding(fileName, mimeType, apiKey)
    }
}
