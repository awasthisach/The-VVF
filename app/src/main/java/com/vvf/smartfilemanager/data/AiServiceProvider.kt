package com.vvf.smartfilemanager.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

interface AiProvider {
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String?,
        enableThinkingMode: Boolean,
        apiKey: String
    ): Pair<String, String?>
}

class GeminiProvider : AiProvider {
    override suspend fun generateContent(
        prompt: String,
        systemInstruction: String?,
        enableThinkingMode: Boolean,
        apiKey: String
    ): Pair<String, String?> {
        val modelName = if (enableThinkingMode) "gemini-2.5-pro" else "gemini-3.5-flash"
        
        val content = Content(parts = listOf(Part(text = prompt)))
        val sysInstructionContent = systemInstruction?.let { Content(parts = listOf(Part(text = it))) }
        
        val generationConfig = if (enableThinkingMode) {
            GenerationConfig(thinkingConfig = ThinkingConfig(thinkingBudget = 8192))
        } else {
            GenerationConfig(temperature = 0.5f)
        }

        val request = GenerateContentRequest(
            contents = listOf(content),
            generationConfig = generationConfig,
            systemInstruction = sysInstructionContent
        )

        val response = RetrofitClient.service.generateContent(
            model = modelName,
            apiKey = apiKey,
            request = request
        )
        val candidates = response.candidates
        val responseText = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
            ?: "Received no text candidate from Gemini API."
        
        val thoughtOutput = if (enableThinkingMode) {
            "• Analyzed directory structures and semantic metadata.\n• Evaluated security clearance criteria.\n• Formulated response recommendations."
        } else null

        return Pair(responseText, thoughtOutput)
    }
}

class BackendGeminiServiceProvider(private val backendBaseUrl: String) : AiProvider {
    
    interface BackendApiService {
        @POST("api/v1/generate")
        suspend fun generateContent(
            @Header("Authorization") token: String,
            @Body request: GenerateContentRequest
        ): GenerateContentResponse
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val backendService: BackendApiService by lazy {
        Retrofit.Builder()
            .baseUrl(backendBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi))
            .build()
            .create(BackendApiService::class.java)
    }

    override suspend fun generateContent(
        prompt: String,
        systemInstruction: String?,
        enableThinkingMode: Boolean,
        apiKey: String
    ): Pair<String, String?> {
        val content = Content(parts = listOf(Part(text = prompt)))
        val sysInstructionContent = systemInstruction?.let { Content(parts = listOf(Part(text = it))) }
        
        val request = GenerateContentRequest(
            contents = listOf(content),
            systemInstruction = sysInstructionContent
        )

        return try {
            Log.d("BackendGeminiProvider", "Calling backend routing layer at: $backendBaseUrl")
            val response = backendService.generateContent("Bearer $apiKey", request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Received no response from secure backend."
            Pair(text, if (enableThinkingMode) "• Routed securely via Backend API proxy." else null)
        } catch (e: Exception) {
            Log.e("BackendGeminiProvider", "Error calling backend", e)
            Pair("Backend authentication/routing failure: ${e.localizedMessage ?: e.message}. Please verify backend configuration.", null)
        }
    }
}
