package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface SafeFolderState {
    object PinSetupNeeded : SafeFolderState
    object Locked : SafeFolderState
    object Unlocked : SafeFolderState
}

enum class ScannerState {
    Idle, Scanning, Finished
}

enum class CleanerState {
    Idle, Scanning, Cleaning, Finished
}

class SmartViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val repository = AppRepository(database)

    private val sharedPrefs = context.getSharedPreferences("smart_files_prefs", Context.MODE_PRIVATE)

    // Api config state
    private val _apiKey = MutableStateFlow(sharedPrefs.getString("user_gemini_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _highThinkingEnabled = MutableStateFlow(sharedPrefs.getBoolean("high_thinking", false))
    val highThinkingEnabled: StateFlow<Boolean> = _highThinkingEnabled.asStateFlow()

    private val _isApiPanelExpanded = MutableStateFlow(false)
    val isApiPanelExpanded: StateFlow<Boolean> = _isApiPanelExpanded.asStateFlow()

    // Local Files reactive flows
    val allLocalNonSafeFiles: StateFlow<List<FileEntity>> = repository.allLocalNonSafeFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSafeFiles: StateFlow<List<FileEntity>> = repository.allSafeFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val duplicateFiles: StateFlow<List<FileEntity>> = repository.duplicateFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val junkFiles: StateFlow<List<FileEntity>> = repository.junkFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Local list filtering & Multi-selection
    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery: StateFlow<String> = _localSearchQuery.asStateFlow()

    private val _selectedLocalFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedLocalFileIds: StateFlow<Set<Long>> = _selectedLocalFileIds.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    // Scanning & Cleaning Engine States
    private val _duplicateScannerState = MutableStateFlow(ScannerState.Idle)
    val duplicateScannerState: StateFlow<ScannerState> = _duplicateScannerState.asStateFlow()

    private val _duplicateScanProgress = MutableStateFlow(0f)
    val duplicateScanProgress: StateFlow<Float> = _duplicateScanProgress.asStateFlow()

    private val _cleanerState = MutableStateFlow(CleanerState.Idle)
    val cleanerState: StateFlow<CleanerState> = _cleanerState.asStateFlow()

    private val _cleanerProgress = MutableStateFlow(0f)
    val cleanerProgress: StateFlow<Float> = _cleanerProgress.asStateFlow()

    // Safe Folder States
    private val _safeFolderState = MutableStateFlow<SafeFolderState>(SafeFolderState.Locked)
    val safeFolderState: StateFlow<SafeFolderState> = _safeFolderState.asStateFlow()

    private val _pinSetupStep = MutableStateFlow("ENTER_PIN") // ENTER_PIN, CONFIRM_PIN, VERIFY
    val pinSetupStep: StateFlow<String> = _pinSetupStep.asStateFlow()

    private val _tempPinForSetup = MutableStateFlow("")
    private val _inputPinBuffer = MutableStateFlow("")
    val inputPinBuffer: StateFlow<String> = _inputPinBuffer.asStateFlow()

    private val _pinErrorMessage = MutableStateFlow<String?>(null)
    val pinErrorMessage: StateFlow<String?> = _pinErrorMessage.asStateFlow()

    // Google Cloud Sim States
    private val _connectedAccounts = MutableStateFlow(
        listOf("awasthi.sach@gmail.com", "workspace.admin@corporation.com", "sac.personal@outlook.com")
    )
    val connectedAccounts: StateFlow<List<String>> = _connectedAccounts.asStateFlow()

    private val _activeAccountEmail = MutableStateFlow("awasthi.sach@gmail.com")
    val activeAccountEmail: StateFlow<String> = _activeAccountEmail.asStateFlow()

    val cloudFiles: StateFlow<List<FileEntity>> = _activeAccountEmail
        .flatMapLatest { email -> repository.getCloudFilesForAccount(email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cloudSearchQuery = MutableStateFlow("")
    val cloudSearchQuery: StateFlow<String> = _cloudSearchQuery.asStateFlow()

    private val _selectedCloudFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCloudFileIds: StateFlow<Set<Long>> = _selectedCloudFileIds.asStateFlow()

    private val _isCloudMultiSelectMode = MutableStateFlow(false)
    val isCloudMultiSelectMode: StateFlow<Boolean> = _isCloudMultiSelectMode.asStateFlow()

    // Cloud Semantic Scanning States
    private val _isCloudScanning = MutableStateFlow(false)
    val isCloudScanning: StateFlow<Boolean> = _isCloudScanning.asStateFlow()

    private val _cloudScanProgress = MutableStateFlow(0f)
    val cloudScanProgress: StateFlow<Float> = _cloudScanProgress.asStateFlow()

    private val _cloudScanQuery = MutableStateFlow("Find finance receipts and resume documents")
    val cloudScanQuery: StateFlow<String> = _cloudScanQuery.asStateFlow()

    private val _semanticScanResults = MutableStateFlow<List<Pair<Long, Int>>>(emptyList()) // Pair<FileId, MatchPercentage>
    val semanticScanResults: StateFlow<List<Pair<Long, Int>>> = _semanticScanResults.asStateFlow()

    // AI Chat History Flows
    val chatHistory: StateFlow<List<ChatMessageEntity>> = repository.chatHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _isGeminiGenerating = MutableStateFlow(false)
    val isGeminiGenerating: StateFlow<Boolean> = _isGeminiGenerating.asStateFlow()

    private val _activeThinkingProcess = MutableStateFlow<String?>(null)
    val activeThinkingProcess: StateFlow<String?> = _activeThinkingProcess.asStateFlow()

    init {
        viewModelScope.launch {
            // Check & initialize base mock data if DB is empty
            repository.checkAndInitializeData()
            checkSafeFolderPinState()
            // Fetch initial GitHub Repo data
            fetchGitHubData()
        }
    }

    // --- Key config & save ---
    fun setApiKey(key: String) {
        _apiKey.value = key
        sharedPrefs.edit().putString("user_gemini_key", key).apply()
    }

    fun setHighThinkingMode(enabled: Boolean) {
        _highThinkingEnabled.value = enabled
        sharedPrefs.edit().putBoolean("high_thinking", enabled).apply()
    }

    fun toggleApiPanel() {
        _isApiPanelExpanded.value = !_isApiPanelExpanded.value
    }

    private fun getActiveApiKey(): String {
        return _apiKey.value.ifBlank {
            BuildConfig.GEMINI_API_KEY
        }
    }

    // --- Local Files Multi-Selection ---
    fun toggleLocalFileSelection(id: Long) {
        val current = _selectedLocalFileIds.value
        _selectedLocalFileIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
        _isMultiSelectMode.value = _selectedLocalFileIds.value.isNotEmpty()
    }

    fun selectAllLocalFiles(filesList: List<FileEntity>) {
        _selectedLocalFileIds.value = filesList.map { it.id }.toSet()
        _isMultiSelectMode.value = true
    }

    fun clearLocalSelection() {
        _selectedLocalFileIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun updateLocalSearchQuery(query: String) {
        _localSearchQuery.value = query
    }

    // --- Clean & Scan Actions ---
    fun runDuplicateScanner() {
        viewModelScope.launch {
            _duplicateScannerState.value = ScannerState.Scanning
            _duplicateScanProgress.value = 0f
            while (_duplicateScanProgress.value < 1.0f) {
                delay(120)
                _duplicateScanProgress.value += 0.1f
            }
            _duplicateScanProgress.value = 1.0f
            _duplicateScannerState.value = ScannerState.Finished
        }
    }

    fun dismissDuplicateScanner() {
        _duplicateScannerState.value = ScannerState.Idle
        _duplicateScanProgress.value = 0f
    }

    fun deleteSelectedDuplicates() {
        viewModelScope.launch {
            duplicateFiles.value.filter { it.isDuplicate }.forEach { file ->
                repository.deleteFile(file)
            }
            _duplicateScannerState.value = ScannerState.Idle
        }
    }

    fun runJunkCleaner() {
        viewModelScope.launch {
            _cleanerState.value = CleanerState.Scanning
            _cleanerProgress.value = 0f
            while (_cleanerProgress.value < 1.0f) {
                delay(80)
                _cleanerProgress.value += 0.08f
            }
            _cleanerProgress.value = 1.0f
            _cleanerState.value = CleanerState.Finished
        }
    }

    fun executeCleanJunk() {
        viewModelScope.launch {
            _cleanerState.value = CleanerState.Cleaning
            _cleanerProgress.value = 0f
            while (_cleanerProgress.value < 1.0f) {
                delay(60)
                _cleanerProgress.value += 0.1f
            }
            repository.cleanAllJunk()
            _cleanerState.value = CleanerState.Idle
            _cleanerProgress.value = 0f
        }
    }

    fun cancelJunkCleaner() {
        _cleanerState.value = CleanerState.Idle
        _cleanerProgress.value = 0f
    }

    // --- Safe Folder State Actions ---
    private suspend fun checkSafeFolderPinState() {
        val hasPin = repository.getIsPinSet()
        _safeFolderState.value = if (hasPin) SafeFolderState.Locked else SafeFolderState.PinSetupNeeded
    }

    fun appendPinDigit(digit: String) {
        if (_inputPinBuffer.value.length < 4) {
            _inputPinBuffer.value += digit
            _pinErrorMessage.value = null
        }

        if (_inputPinBuffer.value.length == 4) {
            viewModelScope.launch {
                handleCompletedPinInput()
            }
        }
    }

    fun clearLastPinDigit() {
        if (_inputPinBuffer.value.isNotEmpty()) {
            _inputPinBuffer.value = _inputPinBuffer.value.dropLast(1)
        }
    }

    private suspend fun handleCompletedPinInput() {
        val pin = _inputPinBuffer.value
        _inputPinBuffer.value = "" // Clear buffer instantly

        when (val currState = _safeFolderState.value) {
            is SafeFolderState.PinSetupNeeded -> {
                if (_pinSetupStep.value == "ENTER_PIN") {
                    _tempPinForSetup.value = pin
                    _pinSetupStep.value = "CONFIRM_PIN"
                } else if (_pinSetupStep.value == "CONFIRM_PIN") {
                    if (pin == _tempPinForSetup.value) {
                        repository.setPIN(pin)
                        _safeFolderState.value = SafeFolderState.Unlocked
                        _pinSetupStep.value = "ENTER_PIN"
                        _tempPinForSetup.value = ""
                    } else {
                        _pinErrorMessage.value = "PINs do note match. Attempt re-entry."
                        _pinSetupStep.value = "ENTER_PIN"
                        _tempPinForSetup.value = ""
                    }
                }
            }
            is SafeFolderState.Locked -> {
                val savedPin = repository.getPIN()
                if (pin == savedPin) {
                    _safeFolderState.value = SafeFolderState.Unlocked
                    _pinErrorMessage.value = null
                } else {
                    _pinErrorMessage.value = "Incorrect 4-digit PIN sequence. Access denied."
                }
            }
            is SafeFolderState.Unlocked -> {
                // Already unlocked
            }
        }
    }

    fun lockSafeFolder() {
        _safeFolderState.value = SafeFolderState.Locked
    }

    // Move Selected Local Files into safe folder
    fun moveSelectedToSafe() {
        viewModelScope.launch {
            val selectedIds = _selectedLocalFileIds.value
            allLocalNonSafeFiles.value.forEach { file ->
                if (selectedIds.contains(file.id)) {
                    repository.updateFile(file.copy(isSafe = true))
                }
            }
            clearLocalSelection()
        }
    }

    // Restore selected files from safe folder back to home
    fun restoreSelectedFromSafe(selectedIds: Set<Long>) {
        viewModelScope.launch {
            allSafeFiles.value.forEach { file ->
                if (selectedIds.contains(file.id)) {
                    repository.updateFile(file.copy(isSafe = false))
                }
            }
        }
    }

    fun deleteLocalFilesByIds(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                repository.deleteFileById(id)
            }
            clearLocalSelection()
        }
    }

    // --- Google Cloud Sim Controllers ---
    fun selectActiveCloudAccount(email: String) {
        _activeAccountEmail.value = email
        clearCloudSelection()
    }

    fun addNewCloudAccount(name: String, email: String) {
        if (email.isNotBlank() && !_connectedAccounts.value.contains(email)) {
            _connectedAccounts.value = _connectedAccounts.value + email
            _activeAccountEmail.value = email
            // Seed base values for the added simulated email
            viewModelScope.launch {
                val demoFiles = listOf(
                    FileEntity(
                        name = "Confidential_Financial_Overview.xlsx",
                        path = "GoogleDrive://$email/Share/Confidential_Financial_Overview.xlsx",
                        size = 2390100,
                        lastModified = System.currentTimeMillis() - 86400000L,
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        isLocal = false,
                        isSafe = false,
                        cloudAccountEmail = email
                    ),
                    FileEntity(
                        name = "Personal_Holidays_Booking_Report.pdf",
                        path = "GoogleDrive://$email/Documents/Personal_Holidays_Booking_Report.pdf",
                        size = 465220,
                        lastModified = System.currentTimeMillis() - 41000000L,
                        mimeType = "application/pdf",
                        isLocal = false,
                        isSafe = false,
                        cloudAccountEmail = email
                    )
                )
                repository.insertFiles(demoFiles)
            }
        }
    }

    fun logoutVirtualAccount(email: String) {
        viewModelScope.launch {
            val remaining = _connectedAccounts.value.filter { it != email }
            if (remaining.isNotEmpty()) {
                _connectedAccounts.value = remaining
                if (_activeAccountEmail.value == email) {
                    _activeAccountEmail.value = remaining.first()
                }
            } else {
                _connectedAccounts.value = listOf("guest@cloudmanager.com")
                _activeAccountEmail.value = "guest@cloudmanager.com"
            }
            clearCloudSelection()
        }
    }

    fun toggleCloudFileSelection(id: Long) {
        val current = _selectedCloudFileIds.value
        _selectedCloudFileIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
        _isCloudMultiSelectMode.value = _selectedCloudFileIds.value.isNotEmpty()
    }

    fun selectAllCloudFiles(filesList: List<FileEntity>) {
        _selectedCloudFileIds.value = filesList.map { it.id }.toSet()
        _isCloudMultiSelectMode.value = true
    }

    fun clearCloudSelection() {
        _selectedCloudFileIds.value = emptySet()
        _isCloudMultiSelectMode.value = false
    }

    fun updateCloudSearchQuery(query: String) {
        _cloudSearchQuery.value = query
    }

    fun deleteCloudFilesByIds(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                repository.deleteFileById(id)
            }
            clearCloudSelection()
        }
    }

    fun updateCloudScanQuery(query: String) {
        _cloudScanQuery.value = query
    }

    // Cloud Semantic Scanning Action
    fun runCloudSemanticScan() {
        val query = _cloudScanQuery.value
        val email = _activeAccountEmail.value
        viewModelScope.launch {
            _isCloudScanning.value = true
            _cloudScanProgress.value = 0f
            _semanticScanResults.value = emptyList()

            val filesToScan = cloudFiles.value
            val resultsList = mutableListOf<Pair<Long, Int>>()

            if (filesToScan.isNotEmpty()) {
                val step = 1.0f / filesToScan.size
                for (index in filesToScan.indices) {
                    val file = filesToScan[index]
                    delay(350) // Satisfying visual delay ticker

                    // Run semantic matching: If API key is available, we can ask Gemini for a smart matching index!
                    // Otherwise, we calculate a smart simulated match based on string keywords for robustness.
                    val matchPercent = if (getActiveApiKey().isNotBlank()) {
                        val prompt = "Evaluate if this file represents a good semantic fit for the user query details.\n" +
                                "User query: \"$query\"\n" +
                                "Filename: \"${file.name}\"\n" +
                                "MimeType: \"${file.mimeType}\"\n" +
                                "Respond with exactly an integer percentage value between 0 and 100 without symbols or explanations, e.g., '85' or '10'."
                        val (reply, _) = repository.callGemini(
                            apiKey = getActiveApiKey(),
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

    // --- Gemini Chat Actions ---
    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun sendChatMessage() {
        val message = _chatInput.value.trim()
        if (message.isBlank()) return

        _chatInput.value = ""
        viewModelScope.launch {
            // 1. Insert User Message
            repository.insertMessage(messageText = message, sender = "user")

            // 2. Load API key for call
            val key = getActiveApiKey()
            if (key.isBlank()) {
                repository.insertMessage(
                    messageText = "Gemini API key is not configured. Please open the collapsible Setup panel above, input your key, and tap 'Apply'.\n\n(Alternatively, ensure it is set in your .env / Secrets panel as GEMINI_API_KEY).",
                    sender = "gemini"
                )
                return@launch
            }

            _isGeminiGenerating.value = true
            _activeThinkingProcess.value = if (_highThinkingEnabled.value) {
                "Initializing advanced thinking tree...\nContacting Gemini models secure api..."
            } else null

            // 3. Prepare contextual system instructions using the current file catalog for context!
            // This is a dynamic, highly advanced system integration! Gemini knows exactly what's actually on the files DB!
            val localFiles = repository.allLocalNonSafeFiles.firstOrNull()?.joinToString { "${it.name} (${formatSize(it.size)})" } ?: "None"
            val safeFiles = repository.allSafeFiles.firstOrNull()?.joinToString { it.name } ?: "None"
            val cloudFilesList = cloudFiles.value.joinToString { "${it.name} (${formatSize(it.size)})" } ?: "None"
            val accounts = _connectedAccounts.value.joinToString()

            val systemInstruction = "You are the smart file and cloud manager virtual assistant. " +
                    "Here is the user's active file systems metadata:\n" +
                    "- Connected Cloud Accounts: [$accounts]\n" +
                    "- Active Cloud Account: ${_activeAccountEmail.value}\n" +
                    "- Local Unlocked Files: [$localFiles]\n" +
                    "- Active cloud files: [$cloudFilesList]\n" +
                    "- Secure Safe Files (Encrypted and locked): [$safeFiles]\n\n" +
                    "Your objective is to provide intelligent, helpful answers to users about their directories, files organizing, general technical file questions, or cloud synchronizations. " +
                    "Keep answers highly professional, aesthetically pleasing, and accurate matching their actual files."

            // 4. Fire API call
            val (reply, thoughts) = repository.callGemini(
                apiKey = key,
                prompt = message,
                systemInstruction = systemInstruction,
                enableThinkingMode = _highThinkingEnabled.value
            )

            _activeThinkingProcess.value = null
            _isGeminiGenerating.value = false

            // 5. Save and render reply
            repository.insertMessage(
                messageText = reply,
                sender = "gemini",
                isThinking = _highThinkingEnabled.value,
                thinkingProcess = thoughts
            )
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }

    // --- GitHub Tracker States & Functions ---
    private val _activeCloudSubTab = MutableStateFlow(0) // 0: Google Drive Sim, 1: GitHub Tracker
    val activeCloudSubTab: StateFlow<Int> = _activeCloudSubTab.asStateFlow()

    private val _githubRepoPath = MutableStateFlow(sharedPrefs.getString("github_repo_path", "google/dagger") ?: "google/dagger")
    val githubRepoPath: StateFlow<String> = _githubRepoPath.asStateFlow()

    private val _githubIssues = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val githubIssues: StateFlow<List<GitHubIssue>> = _githubIssues.asStateFlow()

    private val _githubCommits = MutableStateFlow<List<GitHubCommit>>(emptyList())
    val githubCommits: StateFlow<List<GitHubCommit>> = _githubCommits.asStateFlow()

    private val _isGithubLoading = MutableStateFlow(false)
    val isGithubLoading: StateFlow<Boolean> = _isGithubLoading.asStateFlow()

    private val _githubError = MutableStateFlow<String?>(null)
    val githubError: StateFlow<String?> = _githubError.asStateFlow()

    fun setCloudSubTab(tab: Int) {
        _activeCloudSubTab.value = tab
    }

    fun updateGithubRepoPath(path: String) {
        _githubRepoPath.value = path
        sharedPrefs.edit().putString("github_repo_path", path).apply()
        fetchGitHubData(path)
    }

    fun fetchGitHubData(repoPath: String = _githubRepoPath.value) {
        viewModelScope.launch {
            _isGithubLoading.value = true
            _githubError.value = null
            
            val parts = repoPath.trim().split("/")
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                _githubError.value = "Invalid repository format. Please use 'owner/repo' structure (e.g. google/dagger)"
                _githubIssues.value = getFallbackIssues()
                _githubCommits.value = getFallbackCommits()
                _isGithubLoading.value = false
                return@launch
            }

            val owner = parts[0].trim()
            val repo = parts[1].trim()

            try {
                // Fetch issues via retrofit client
                val liveIssues = GitHubRetrofitClient.service.getOpenIssues(owner, repo)
                _githubIssues.value = liveIssues

                // Calculate date 30 days ago
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val sinceDate = sdf.format(calendar.time)

                // Fetch commits via retrofit client
                val liveCommits = GitHubRetrofitClient.service.getRecentCommits(owner, repo, sinceDate)
                _githubCommits.value = liveCommits
            } catch (e: Exception) {
                _githubError.value = "Live Fetch failed: ${e.localizedMessage ?: "Connection Timeout"}. Displaying high-fidelity simulated tracker data for demo."
                _githubIssues.value = getFallbackIssues()
                _githubCommits.value = getFallbackCommits()
            } finally {
                _isGithubLoading.value = false
            }
        }
    }

    private fun getFallbackIssues(): List<GitHubIssue> {
        val user1 = GitHubUser("octocat", "https://avatars.githubusercontent.com/u/5832347?v=4")
        val user2 = GitHubUser("dev_johndoe", null)
        val user3 = GitHubUser("alice_coder", null)

        return listOf(
            GitHubIssue(101, 412, "Fix Memory Leak in Safe Folder transition", "open", "https://github.com", user1, "2026-06-15T12:00:00Z", 4),
            GitHubIssue(102, 410, "Improve Byte-comparison performance for duplicate scanner", "open", "https://github.com", user2, "2026-06-14T09:30:00Z", 2),
            GitHubIssue(103, 408, "Failed Google Drive folder synchronization with special chars", "open", "https://github.com", user3, "2026-06-12T15:45:00Z", 8),
            GitHubIssue(104, 395, "Room Schema migration crashes on device orientation change", "open", "https://github.com", user1, "2026-06-10T08:00:00Z", 0),
            GitHubIssue(105, 381, "Integrate Dynamic Material Theme Accent colors in Cloud Manager", "open", "https://github.com", user2, "2026-06-08T18:20:00Z", 1)
        )
    }

    private fun getFallbackCommits(): List<GitHubCommit> {
        val commits = mutableListOf<GitHubCommit>()
        val calendar = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        
        // Generate a nice commit distribution spread across last 30 days
        val commitDistribution = listOf(
            5, 8, 3, 0, 0, 4, 12, 6, 2, 0, 0, 5, 10, 8, 4, 1, 0, 3, 9, 7, 2, 0, 0, 4, 15, 6, 2, 1, 0, 5
        )

        for (i in 0 until 30) {
            val daysAgo = 29 - i
            val currentDayCal = java.util.Calendar.getInstance()
            currentDayCal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            val formattedDate = sdf.format(currentDayCal.time)
            
            val count = commitDistribution.getOrElse(i) { 2 }
            for (c in 0 until count) {
                commits.add(
                    GitHubCommit(
                        sha = "sha_${daysAgo}_$c",
                        commit = CommitInfo(
                            message = "Productivity improvement commit step $daysAgo-$c",
                            author = CommitAuthor("Team Developer", formattedDate)
                        )
                    )
                )
            }
        }
        return commits
    }

    // --- Format Helpers (Exposed for UI use) ---
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes.toDouble() / 1_000_000_000)
            bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes.toDouble() / 1_000_000)
            bytes >= 1_000 -> String.format(Locale.getDefault(), "%.1f KB", bytes.toDouble() / 1_000)
            else -> "$bytes B"
        }
    }

    fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val netDate = Date(timestamp)
            sdf.format(netDate)
        } catch (e: Exception) {
            "Unknown Date"
        }
    }
}
