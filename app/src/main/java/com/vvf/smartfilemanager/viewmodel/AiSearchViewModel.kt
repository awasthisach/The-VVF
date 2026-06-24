package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vvf.smartfilemanager.data.ChatMessageEntity
import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiSearchViewModel(
    application: Application,
    private val repository: IAppRepository
) : AndroidViewModel(application) {

    private var semanticScanJob: kotlinx.coroutines.Job? = null

    private val _isCloudScanning = MutableStateFlow(false)
    val isCloudScanning: StateFlow<Boolean> = _isCloudScanning.asStateFlow()

    private val _cloudScanProgress = MutableStateFlow(0f)
    val cloudScanProgress: StateFlow<Float> = _cloudScanProgress.asStateFlow()

    private val _cloudScanQuery = MutableStateFlow("Find finance receipts and resume documents")
    val cloudScanQuery: StateFlow<String> = _cloudScanQuery.asStateFlow()

    private val _semanticScanResults = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val semanticScanResults: StateFlow<List<Pair<Long, Int>>> = _semanticScanResults.asStateFlow()

    val chatHistory: StateFlow<List<ChatMessageEntity>> = repository.chatHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _isGeminiGenerating = MutableStateFlow(false)
    val isGeminiGenerating: StateFlow<Boolean> = _isGeminiGenerating.asStateFlow()

    private val _activeThinkingProcess = MutableStateFlow<String?>(null)
    val activeThinkingProcess: StateFlow<String?> = _activeThinkingProcess.asStateFlow()

    private val _isReadmeGenerating = MutableStateFlow(false)
    val isReadmeGenerating: StateFlow<Boolean> = _isReadmeGenerating.asStateFlow()

    private val _generatedReadme = MutableStateFlow<String?>(null)
    val generatedReadme: StateFlow<String?> = _generatedReadme.asStateFlow()

    private val _readmeThinkingProcess = MutableStateFlow<String?>(null)
    val readmeThinkingProcess: StateFlow<String?> = _readmeThinkingProcess.asStateFlow()

    fun updateCloudScanQuery(query: String) {
        _cloudScanQuery.value = query
    }

    fun runCloudSemanticScan(
        cloudFiles: List<FileEntity>,
        apiKeyProvider: () -> String
    ) {
        val query = _cloudScanQuery.value
        semanticScanJob?.cancel()
        semanticScanJob = viewModelScope.launch {
            _isCloudScanning.value = true
            _cloudScanProgress.value = 0f
            _semanticScanResults.value = emptyList()
            Log.d("AiSearchViewModel", "Cloud semantic scan initiated for: $query [StructuredLog: { event: \"semantic_scan_start\", query: \"$query\" }]")

            val resultsList = mutableListOf<Pair<Long, Int>>()
            if (cloudFiles.isNotEmpty()) {
                val step = 1.0f / cloudFiles.size
                for (index in cloudFiles.indices) {
                    val file = cloudFiles[index]
                    delay(350)

                    val matchPercent = if (apiKeyProvider().isNotBlank()) {
                        val prompt = "Evaluate if this file represents a good semantic fit for the user query details.\n" +
                                "User query: \"$query\"\n" +
                                "Filename: \"${file.name}\"\n" +
                                "MimeType: \"${file.mimeType}\"\n" +
                                "Respond with exactly an integer percentage value between 0 and 100 without symbols or explanations, e.g., '85' or '10'."
                        val (reply, _) = repository.callGemini(
                            apiKey = apiKeyProvider(),
                            prompt = prompt,
                            enableThinkingMode = false
                        )
                        reply.trim().filter { it.isDigit() }.toIntOrNull() ?: calculateSimulatedKeywordMatch(file.name, query)
                    } else {
                        calculateSimulatedKeywordMatch(file.name, query)
                    }

                    resultsList.add(Pair(file.id, matchPercent))
                    _cloudScanProgress.value = (index + 1) * step
                }
            }

            _semanticScanResults.value = resultsList
            _cloudScanProgress.value = 1.0f
            _isCloudScanning.value = false
            Log.i("AiSearchViewModel", "Cloud semantic scan finished [StructuredLog: { event: \"semantic_scan_finish\", count: ${resultsList.size} }]")
        }
    }

    private fun calculateSimulatedKeywordMatch(filename: String, query: String): Int {
        val nameLower = filename.lowercase()
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(" ", "_", "-").filter { it.length > 2 }
        if (queryWords.isEmpty()) return 10

        var matches = 0
        for (word in queryWords) {
            if (nameLower.contains(word)) matches++
        }

        return when {
            matches == 0 -> (10..30).random()
            matches == 1 -> (45..65).random()
            else -> (75..98).random()
        }
    }

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun sendChatMessage(
        activeEmail: String,
        connectedAccounts: List<String>,
        cloudFilesList: List<FileEntity>,
        apiKeyProvider: () -> String,
        highThinkingProvider: () -> Boolean,
        formatSizeFormatter: (Long) -> String
    ) {
        val message = _chatInput.value.trim()
        if (message.isBlank()) return
        if (_isGeminiGenerating.value) return

        _chatInput.value = ""
        viewModelScope.launch {
            repository.insertMessage(messageText = message, sender = "user")
            Log.d("AiSearchViewModel", "Inserted user message [StructuredLog: { event: \"chat_msg_user\" }]")

            val key = apiKeyProvider()
            if (key.isBlank()) {
                repository.insertMessage(
                    messageText = "Gemini API key is not configured. Please open the collapsible Setup panel above, input your key, and tap 'Apply'.\n\n(Alternatively, ensure it is set in your .env / Secrets panel as GEMINI_API_KEY).",
                    sender = "gemini"
                )
                return@launch
            }

            val enableThinking = highThinkingProvider()
            _isGeminiGenerating.value = true
            _activeThinkingProcess.value = if (enableThinking) {
                "Initializing advanced thinking tree...\nContacting Gemini models secure api..."
            } else null

            val localFilesList = repository.allLocalNonSafeFiles.firstOrNull() ?: emptyList()
            val localFilesTruncated = localFilesList.take(20)
            val localFilesStr = localFilesTruncated.joinToString { "${it.name} (${formatSizeFormatter(it.size)})" } + 
                    (if (localFilesList.size > 20) " ... and ${localFilesList.size - 20} more files" else "")

            val safeFilesList = repository.allSafeFiles.firstOrNull() ?: emptyList()
            val safeFilesTruncated = safeFilesList.take(20)
            val safeFilesStr = safeFilesTruncated.joinToString { it.name } + 
                    (if (safeFilesList.size > 20) " ... and ${safeFilesList.size - 20} more files" else "")

            val cloudFilesTruncated = cloudFilesList.take(20)
            val cloudFilesStr = cloudFilesTruncated.joinToString { "${it.name} (${formatSizeFormatter(it.size)})" } + 
                    (if (cloudFilesList.size > 20) " ... and ${cloudFilesList.size - 20} more files" else "")

            val systemInstruction = "You are the smart file and cloud manager virtual assistant. " +
                    "Here is the user's active file systems metadata:\n" +
                    "- Connected Cloud Accounts: [${connectedAccounts.joinToString()}]\n" +
                    "- Active Cloud Account: $activeEmail\n" +
                    "- Local Unlocked Files: [$localFilesStr]\n" +
                    "- Active cloud files: [$cloudFilesStr]\n" +
                    "- Secure Safe Files (Encrypted and locked): [$safeFilesStr]\n\n" +
                    "Your objective is to provide intelligent, helpful answers to users about their directories, files organizing, general technical file questions, or cloud synchronizations. " +
                    "Keep answers highly professional, aesthetically pleasing, and accurate matching their actual files."

            val (reply, thoughts) = repository.callGemini(
                apiKey = key,
                prompt = message,
                systemInstruction = systemInstruction,
                enableThinkingMode = enableThinking
            )

            _activeThinkingProcess.value = null
            _isGeminiGenerating.value = false

            repository.insertMessage(
                messageText = reply,
                sender = "gemini",
                isThinking = enableThinking,
                thinkingProcess = thoughts
            )
            Log.i("AiSearchViewModel", "Generated AI response [StructuredLog: { event: \"chat_msg_gemini_success\", length: ${reply.length} }]")
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            Log.d("AiSearchViewModel", "Cleared chat history [StructuredLog: { event: \"chat_history_cleared\" }]")
        }
    }

    fun generateProjectReadme(
        localFilesCount: Int,
        safeFilesCount: Int,
        duplicatesCount: Int,
        currentRepo: String,
        accountsCount: Int,
        apiKeyProvider: () -> String,
        highThinkingProvider: () -> Boolean
    ) {
        if (_isReadmeGenerating.value) return
        viewModelScope.launch {
            _isReadmeGenerating.value = true
            _generatedReadme.value = null
            _readmeThinkingProcess.value = "• Scanning android package workspace...\n• Analyzing package com.vvf.smartfilemanager\n• Traversing project metadata and database models..."
            Log.d("AiSearchViewModel", "Project README generation triggered [StructuredLog: { event: \"readme_gen_start\" }]")

            val systemInstruction = "You are a senior software architect documenting an advanced Android repository."
            val prompt = """
                Generate a comprehensive, professional, markdown-formatted README.md document for the current project.
                
                Codebase Blueprint Checklist:
                - Manifest: AndroidManifest.xml (custom launcher and services)
                - Local Storage: Room implementation under data/ with AppDatabase, FileDao, and ChatMessageDao.
                - Unified Repository: AppRepository handles Room CRUD and coordinates REST communication to external Gemini endpoints.
                - MVVM State: SmartViewModel.kt uses StateFlow properties to coordinate dual-mode (local scanning / cloud sim) and Git branch states.
                - Composable Design: MainLayout.kt implements a Material 3 design system with custom theme tokens, analytics dashboards, and interactive chat loops.
                - Advanced Git Simulation: Interactive branch tracking, heartbeat status checker, and visual diff resolvers.
                
                Live Application State Metrics:
                - Currently Managed Files: $localFilesCount
                - Safe Vault Files: $safeFilesCount
                - Duplicate Redundant Files: $duplicatesCount
                - Remote Sync Node: $currentRepo
                - Active Connected Accounts: $accountsCount
                
                Please structure the README.md beautifully with:
                1. Project Title & Mission Statement (An modern, elegant title)
                2. Live Metrics Dashboard (Rendered as an ASCII or markdown table showing the real-time stats above)
                3. High-Level Modular Architecture (Explaining Room DB persistence, Retrofit API layer, and MVVM reactive patterns with StateFlow)
                4. Codebase Directory Map (A clean, visual text folder tree showing the main components like ui, data, viewmodel)
                5. Setup & Customization Guide (Focusing on BuildConfig API key setups, Gradle requirements, and manual PIN setups)
                
                Use bold headers, elegant spacing, code blocks with kotlin/xml specifiers, and custom icons/emojis for visual rhythm. Keep the tone professional and informative.
            """.trimIndent()

            try {
                val key = apiKeyProvider()
                val enableThinking = highThinkingProvider()
                val (reply, thoughts) = repository.callGemini(
                    apiKey = key,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    enableThinkingMode = enableThinking
                )

                _generatedReadme.value = reply
                _readmeThinkingProcess.value = thoughts ?: "• Successfully analyzed local source files & db states.\n• Synthesized workspace specifications into markdown."
                Log.i("AiSearchViewModel", "Project README document generated [StructuredLog: { event: \"readme_gen_success\" }]")
            } catch (e: Exception) {
                _generatedReadme.value = "An error occurred while generating: ${e.localizedMessage}. Please verify your network connection and API key configuration."
                _readmeThinkingProcess.value = "• Error in API communication: ${e.message}"
                Log.e("AiSearchViewModel", "README generation failed", e)
            } finally {
                _isReadmeGenerating.value = false
            }
        }
    }

    fun clearGeneratedReadme() {
        _generatedReadme.value = null
        _readmeThinkingProcess.value = null
    }
}
