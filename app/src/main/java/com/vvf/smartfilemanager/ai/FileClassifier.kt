package com.vvf.smartfilemanager.ai

import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileClassifier(private val repository: IAppRepository) {
    suspend fun classifyFile(fileName: String, mimeType: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext listOf(mimeType.substringBefore("/"), "local")
        }
        try {
            val prompt = "Analyze this file name and mime type, then output exactly 3 tags separated by commas, e.g. receipt,finance,pdf\n" +
                    "Filename: $fileName\n" +
                    "MimeType: $mimeType"
            val (response, _) = repository.callGemini(apiKey, prompt, enableThinkingMode = false)
            response.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            listOf(mimeType.substringBefore("/"), "local")
        }
    }
}
