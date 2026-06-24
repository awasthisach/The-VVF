package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vvf.smartfilemanager.data.*
import com.vvf.smartfilemanager.BuildConfig
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

    // Global Dark Mode Theme state (Default to dark mode to provide a more developer-friendly interface)
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        _isDarkMode.value = dark
        sharedPrefs.edit().putBoolean("is_dark_mode", dark).apply()
    }

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

    // --- Storage Cleaner Utility States ---
    private val _storageSelectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val storageSelectedFileIds: StateFlow<Set<Long>> = _storageSelectedFileIds.asStateFlow()

    private val _isStorageScanning = MutableStateFlow(false)
    val isStorageScanning: StateFlow<Boolean> = _isStorageScanning.asStateFlow()

    private val _storageScanProgress = MutableStateFlow(0f)
    val storageScanProgress: StateFlow<Float> = _storageScanProgress.asStateFlow()

    private val _storageCleanerActive = MutableStateFlow(false)
    val storageCleanerActive: StateFlow<Boolean> = _storageCleanerActive.asStateFlow()

    val scannedDuplicates: StateFlow<List<FileEntity>> = allLocalNonSafeFiles
        .map { allFiles ->
            val duplicates = allFiles.filter { it.isDuplicate }
            val nameSizeGroups = allFiles.groupBy { it.name + "_" + it.size }
            val matchDupes = nameSizeGroups.filter { it.value.size > 1 }.flatMap { it.value.drop(1) }
            (duplicates + matchDupes).distinctBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannedLargeTempFiles: StateFlow<List<FileEntity>> = allLocalNonSafeFiles
        .map { allFiles ->
            allFiles.filter { file ->
                file.isJunk || 
                file.name.endsWith(".tmp", ignoreCase = true) || 
                file.name.endsWith(".log", ignoreCase = true) || 
                file.name.endsWith(".temp", ignoreCase = true) || 
                file.size >= 5_000_000L // 5MB limit
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Animated Custom File Viewer States ---
    private val _activeViewerFile = MutableStateFlow<FileEntity?>(null)
    val activeViewerFile: StateFlow<FileEntity?> = _activeViewerFile.asStateFlow()

    // --- Document Scanner Interface States ---
    private val _isDocumentScannerActive = MutableStateFlow(false)
    val isDocumentScannerActive: StateFlow<Boolean> = _isDocumentScannerActive.asStateFlow()

    private val _isCameraCapturing = MutableStateFlow(false)
    val isCameraCapturing: StateFlow<Boolean> = _isCameraCapturing.asStateFlow()

    private val _scannedDocumentFilter = MutableStateFlow("original") // original, bw, contrast, grayscale
    val scannedDocumentFilter: StateFlow<String> = _scannedDocumentFilter.asStateFlow()

    private val _scannedFileName = MutableStateFlow("SCAN_2026.pdf")
    val scannedFileName: StateFlow<String> = _scannedFileName.asStateFlow()

    private val _scannedDocumentSaved = MutableStateFlow(false)
    val scannedDocumentSaved: StateFlow<Boolean> = _scannedDocumentSaved.asStateFlow()

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

    // --- Git Merge Conflict States & Functions ---
    private val _conflictedFiles = MutableStateFlow<List<ConflictFile>>(getInitialConflictFiles())
    val conflictedFiles: StateFlow<List<ConflictFile>> = _conflictedFiles.asStateFlow()

    private val _selectedConflictedFile = MutableStateFlow<ConflictFile?>(null)
    val selectedConflictedFile: StateFlow<ConflictFile?> = _selectedConflictedFile.asStateFlow()

    private val _isAiResolvingBlock = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isAiResolvingBlock: StateFlow<Map<String, Boolean>> = _isAiResolvingBlock.asStateFlow()

    private val _gitSyncState = MutableStateFlow(
        GitSyncState(
            status = GitSyncStatus.CONFLICT,
            lastCheckedSecondsAgo = 2,
            localAheadCount = 0,
            remoteAheadCount = 0,
            repositoryName = sharedPrefs.getString("github_repo_path", "google/dagger") ?: "google/dagger",
            latestSyncActionMessage = "Active merge conflicts detected in Theme.kt, AndroidManifest.xml, build.gradle.kts"
        )
    )
    val gitSyncState: StateFlow<GitSyncState> = _gitSyncState.asStateFlow()

    // --- Repository README Generator States & Function ---
    private val _isReadmeGenerating = MutableStateFlow(false)
    val isReadmeGenerating: StateFlow<Boolean> = _isReadmeGenerating.asStateFlow()

    private val _generatedReadme = MutableStateFlow<String?>(null)
    val generatedReadme: StateFlow<String?> = _generatedReadme.asStateFlow()

    private val _readmeThinkingProcess = MutableStateFlow<String?>(null)
    val readmeThinkingProcess: StateFlow<String?> = _readmeThinkingProcess.asStateFlow()

    init {
        // Initialize tracked repository list from sharedPrefs
        loadTrackedRepositories()
        viewModelScope.launch {
            // Check & initialize base mock data if DB is empty
            repository.checkAndInitializeData()
            checkSafeFolderPinState()
            // Fetch initial GitHub Repo data
            fetchGitHubData()
            
            // Start real-time sync observer heartbeat
            startGitSyncHeartbeat()
        }

        // Monitor merge conflict state in real-time to update Sync Status
        viewModelScope.launch {
            _conflictedFiles.collect { files ->
                val hasConflicts = files.any { !it.isFullyResolved }
                _gitSyncState.value = _gitSyncState.value.let { state ->
                    if (hasConflicts) {
                        state.copy(
                            status = GitSyncStatus.CONFLICT,
                            latestSyncActionMessage = "Conflicts active in ${files.count { !it.isFullyResolved }} files. Manual resolution required."
                        )
                    } else if (state.status == GitSyncStatus.CONFLICT) {
                        state.copy(
                            status = GitSyncStatus.SYNCED,
                            latestSyncActionMessage = "All code changes integrated cleanly! Worktree synced."
                        )
                    } else {
                        state
                    }
                }
            }
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

    fun scanRealDeviceFiles(context: Context) {
        viewModelScope.launch {
            repository.scanAndSaveRealFiles(context)
        }
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

    // --- Storage Cleaner Utility Handlers ---
    fun setStorageCleanerActive(active: Boolean) {
        _storageCleanerActive.value = active
        if (active) {
            runStorageCleanerScan()
        } else {
            _storageSelectedFileIds.value = emptySet()
        }
    }

    fun toggleStorageSelectedFile(id: Long) {
        val current = _storageSelectedFileIds.value
        _storageSelectedFileIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAllStorageFiles(ids: List<Long>) {
        _storageSelectedFileIds.value = ids.toSet()
    }

    fun selectNoStorageFiles() {
        _storageSelectedFileIds.value = emptySet()
    }

    fun runStorageCleanerScan() {
        viewModelScope.launch {
            _isStorageScanning.value = true
            _storageScanProgress.value = 0f
            while (_storageScanProgress.value < 1.0f) {
                delay(100)
                _storageScanProgress.value += 0.1f
            }
            _storageScanProgress.value = 1.0f
            _isStorageScanning.value = false
        }
    }

    fun deleteSelectedStorageFiles() {
        val toDelete = _storageSelectedFileIds.value
        viewModelScope.launch {
            toDelete.forEach { id ->
                repository.deleteFileById(id)
            }
            _storageSelectedFileIds.value = emptySet()
        }
    }

    // --- Custom Animated File Viewer Handlers ---
    fun openFileInViewer(file: FileEntity) {
        _activeViewerFile.value = file
    }

    fun closeFileInViewer() {
        _activeViewerFile.value = null
    }

    // --- Document Scanner Interface Handlers ---
    fun setDocumentScannerActive(active: Boolean) {
        _isDocumentScannerActive.value = active
        if (active) {
            _isCameraCapturing.value = false
            _scannedDocumentSaved.value = false
            // Generate a default scanning filename using the current timestamp matching Google Drive format
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            _scannedFileName.value = "SCAN_$timestamp"
            _scannedDocumentFilter.value = "original"
        }
    }

    fun setScannedFilter(filter: String) {
        _scannedDocumentFilter.value = filter
    }

    fun setScannedFileName(name: String) {
        _scannedFileName.value = name
    }

    fun captureDocument() {
        viewModelScope.launch {
            _isCameraCapturing.value = true
            delay(1200) // Shutter simulation with focus delay
            _isCameraCapturing.value = false
            _scannedDocumentSaved.value = true
        }
    }

    fun saveScannedDocument(onSavedCompletable: () -> Unit = {}) {
        val finalTitle = if (_scannedFileName.value.endsWith(".pdf")) _scannedFileName.value else "${_scannedFileName.value}.pdf"
        viewModelScope.launch {
            // Register file entity in Room local storage with appropriate PDF mime type
            val newFile = FileEntity(
                id = 0L,
                name = finalTitle,
                path = "/storage/emulated/0/Documents/Scans/$finalTitle",
                size = (1024L * 1024L * 1.4f).toLong(), // 1.4 MB estimated scanned PDF size
                lastModified = System.currentTimeMillis(),
                mimeType = "application/pdf",
                isLocal = true,
                isSafe = false,
                isDuplicate = false,
                isJunk = false,
                cloudAccountEmail = ""
            )
            repository.insertFile(newFile)
            setDocumentScannerActive(false)
            onSavedCompletable()
        }
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

    private val _trackedRepositories = MutableStateFlow<List<TrackedRepository>>(emptyList())
    val trackedRepositories: StateFlow<List<TrackedRepository>> = _trackedRepositories.asStateFlow()

    fun loadTrackedRepositories() {
        val repoPaths = sharedPrefs.getStringSet("tracked_repos_set", null) ?: setOf("google/dagger")
        val list = repoPaths.map { path ->
            val lastSynced = sharedPrefs.getLong("repo_sync_time_$path", 0L)
            val status = sharedPrefs.getString("repo_sync_status_$path", "Synced") ?: "Synced"
            TrackedRepository(path, lastSynced, status)
        }
        _trackedRepositories.value = list.sortedBy { it.path }
    }

    fun saveTrackedRepository(path: String, lastSynced: Long, status: String) {
        val currentSet = sharedPrefs.getStringSet("tracked_repos_set", null) ?: setOf("google/dagger")
        val newSet = currentSet.toMutableSet()
        newSet.add(path)
        sharedPrefs.edit()
            .putStringSet("tracked_repos_set", newSet)
            .putLong("repo_sync_time_$path", lastSynced)
            .putString("repo_sync_status_$path", status)
            .apply()
        loadTrackedRepositories()
    }

    fun removeTrackedRepository(path: String) {
        val currentSet = sharedPrefs.getStringSet("tracked_repos_set", null) ?: setOf("google/dagger")
        val newSet = currentSet.toMutableSet()
        newSet.remove(path)
        sharedPrefs.edit()
            .putStringSet("tracked_repos_set", newSet)
            .remove("repo_sync_time_$path")
            .remove("repo_sync_status_$path")
            .apply()
        loadTrackedRepositories()
    }

    private val _githubIssues = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val githubIssues: StateFlow<List<GitHubIssue>> = _githubIssues.asStateFlow()

    private val _githubCommits = MutableStateFlow<List<GitHubCommit>>(emptyList())
    val githubCommits: StateFlow<List<GitHubCommit>> = _githubCommits.asStateFlow()

    private val _isGithubLoading = MutableStateFlow(false)
    val isGithubLoading: StateFlow<Boolean> = _isGithubLoading.asStateFlow()

    private val _githubError = MutableStateFlow<String?>(null)
    val githubError: StateFlow<String?> = _githubError.asStateFlow()

    // --- New GitHub Explorer & Conversational Chat states ---
    private val _repositoryFiles = MutableStateFlow<List<GitHubRepoFile>>(emptyList())
    val repositoryFiles: StateFlow<List<GitHubRepoFile>> = _repositoryFiles.asStateFlow()

    private val _selectedRepoFile = MutableStateFlow<GitHubRepoFile?>(null)
    val selectedRepoFile: StateFlow<GitHubRepoFile?> = _selectedRepoFile.asStateFlow()

    private val _activeGitWorkspaceSubTab = MutableStateFlow(0) // 0: Overview, 1: Code Explorer & Chat
    val activeGitWorkspaceSubTab: StateFlow<Int> = _activeGitWorkspaceSubTab.asStateFlow()

    private val _repoChatHistory = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val repoChatHistory: StateFlow<List<ChatMessageEntity>> = _repoChatHistory.asStateFlow()

    private val _isRepoChatGenerating = MutableStateFlow(false)
    val isRepoChatGenerating: StateFlow<Boolean> = _isRepoChatGenerating.asStateFlow()

    fun setGitWorkspaceSubTab(tab: Int) {
        _activeGitWorkspaceSubTab.value = tab
    }

    fun selectRepoFile(file: GitHubRepoFile?) {
        _selectedRepoFile.value = file
    }

    fun clearRepoChatHistory() {
        val repoName = _githubRepoPath.value
        _repoChatHistory.value = listOf(
            ChatMessageEntity(
                id = -1,
                messageText = "Hello! I am Gemini, your dedicated Repository Analyst. I've analyzed **$repoName**.\n\nSelect any code file on the left file explorer panel to discuss its architecture, implementation details, or potential improvements directly with me here!",
                sender = "gemini",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun renameRepoFile(fileId: String, newName: String) {
        _repositoryFiles.value = _repositoryFiles.value.map { file ->
            if (file.id == fileId) {
                // Determine new path
                val oldPath = file.path
                val newPath = if (oldPath.contains("/")) {
                    oldPath.substringBeforeLast("/") + "/" + newName
                } else newName
                val updatedFile = file.copy(name = newName, path = newPath)
                if (_selectedRepoFile.value?.id == fileId) {
                    _selectedRepoFile.value = updatedFile
                }
                updatedFile
            } else {
                file
            }
        }
    }

    fun deleteRepoFile(fileId: String) {
        _repositoryFiles.value = _repositoryFiles.value.filter { it.id != fileId }
        if (_selectedRepoFile.value?.id == fileId) {
            _selectedRepoFile.value = _repositoryFiles.value.firstOrNull { !it.isDirectory }
        }
    }

    fun runAiAnalysis(fileId: String, onAnalysisResult: (String) -> Unit) {
        val file = _repositoryFiles.value.find { it.id == fileId } ?: return
        viewModelScope.launch {
            _isRepoChatGenerating.value = true
            
            val prompt = """
                You are Gemini, an expert software architect. Please perform a deep, high-fidelity AI Analysis on this file from the repository, focusing on design patterns, memory usage, and structural optimization:
                
                File Name: ${file.name}
                Path: ${file.path}
                Content:
                ```
                ${file.content}
                ```
                
                Please provide structured findings with bullet points and visual hierarchy.
            """.trimIndent()
            
            val key = getActiveApiKey()
            val replyText = if (key.isBlank()) {
                "Gemini API key is not configured. Please supply a key to run live code analysis."
            } else {
                try {
                    val (reply, _) = repository.callGemini(
                        apiKey = key,
                        prompt = prompt,
                        systemInstruction = "You are an expert repository reviewer. Write concise but professional code reviews."
                    )
                    reply
                } catch (e: Exception) {
                    "Analysis Failed: ${e.localizedMessage}"
                }
            }
            
            // Append result to repo chat history
            val userAnalysisMsg = ChatMessageEntity(
                messageText = "Run AI Analysis on file: `${file.name}`",
                sender = "user",
                timestamp = System.currentTimeMillis()
            )
            val geminiAnalysisMsg = ChatMessageEntity(
                messageText = "### AI Code Analysis for `${file.name}`\n\n$replyText",
                sender = "gemini",
                timestamp = System.currentTimeMillis()
            )
            _repoChatHistory.value = _repoChatHistory.value + listOf(userAnalysisMsg, geminiAnalysisMsg)
            _isRepoChatGenerating.value = false
            onAnalysisResult(replyText)
        }
    }

    fun sendRepoChatMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                messageText = message,
                sender = "user",
                timestamp = System.currentTimeMillis()
            )
            _repoChatHistory.value = _repoChatHistory.value + userMsg
            
            _isRepoChatGenerating.value = true
            
            val activeFile = _selectedRepoFile.value
            val fileContext = if (activeFile != null) {
                "The user is currently viewing the file '${activeFile.path}' which contains this code:\n" + "```" + "\n${activeFile.content}\n" + "```" + "\n\n"
            } else {
                "The user is viewing the repository directory structure.\n"
            }
            
            val fullHistoryPrompt = _repoChatHistory.value.takeLast(6).joinToString("\n") { 
                "${it.sender}: ${it.messageText}" 
            }
            
            val prompt = """
                You are Gemini, an expert software architect and repository advisor.
                $fileContext
                Here is the recent conversation history:
                $fullHistoryPrompt
                
                Please provide a helpful, concise response analyzing the repository structures or answering the user's specific query. Use Markdown for styling or code blocks if needed.
            """.trimIndent()
            
            val key = getActiveApiKey()
            if (key.isBlank()) {
                val errorMsg = ChatMessageEntity(
                    messageText = "Gemini API key is not configured. Please open the API setup panel or ensure it is set as GEMINI_API_KEY in your env.",
                    sender = "gemini",
                    timestamp = System.currentTimeMillis()
                )
                _repoChatHistory.value = _repoChatHistory.value + errorMsg
                _isRepoChatGenerating.value = false
                return@launch
            }
            
            try {
                val (reply, _) = repository.callGemini(
                    apiKey = key,
                    prompt = prompt,
                    systemInstruction = "You are an expert GitHub assistant. Help the user analyze repository code, structure, issues and commits.",
                    enableThinkingMode = _highThinkingEnabled.value
                )
                
                val geminiMsg = ChatMessageEntity(
                    messageText = reply,
                    sender = "gemini",
                    timestamp = System.currentTimeMillis()
                )
                _repoChatHistory.value = _repoChatHistory.value + geminiMsg
            } catch (e: Exception) {
                val err = ChatMessageEntity(
                    messageText = "Failed to communicate with Gemini: ${e.localizedMessage}",
                    sender = "gemini",
                    timestamp = System.currentTimeMillis()
                )
                _repoChatHistory.value = _repoChatHistory.value + err
            } finally {
                _isRepoChatGenerating.value = false
            }
        }
    }

    fun generateSimulatedRepoFiles(repoPath: String): List<GitHubRepoFile> {
        val cleaned = repoPath.trim().replace("\\s+".toRegex(), "")
        val parts = cleaned.split("/")
        val owner = parts.getOrNull(0) ?: "google"
        val repo = parts.getOrNull(1) ?: "dagger"
        
        val files = mutableListOf<GitHubRepoFile>()
        
        fun addFile(path: String, isDir: Boolean, sizeKB: Long, dynamicContent: String) {
            val name = path.substringAfterLast("/")
            val parent = if (path.contains("/")) path.substringBeforeLast("/") else ""
            files.add(
                GitHubRepoFile(
                    id = path.hashCode().toString(),
                    path = path,
                    name = name,
                    isDirectory = isDir,
                    size = sizeKB * 1024L,
                    content = dynamicContent,
                    parentPath = parent
                )
            )
        }
        
        // Root directories
        addFile("src", true, 0, "")
        addFile("src/main", true, 0, "")
        addFile("src/main/java", true, 0, "")
        addFile("src/main/java/com", true, 0, "")
        addFile("src/main/java/com/vvf", true, 0, "")
        addFile("src/main/java/com/vvf/smartfilemanager", true, 0, "")
        addFile("gradle", true, 0, "")
        addFile("gradle/wrapper", true, 0, "")
        
        // Root files
        addFile("README.md", false, 4, """
            # $repo
            
            Welcome to **$repo**, maintained by **$owner**. This repository has been successfully synchronized using the Gemini code intelligence suite.
            
            ## Features
            - **High-Velocity dependency compilation**: Designed for enterprise-scale integration pipelines.
            - **Type-Safe Injection Interfaces**: Validated compile-time code trees preventing null references.
            - **Multi-Modular Service Routing**: Pre-packaged component charts mapping complex structures.
            
            ## Installation & Usage
            To import $repo into your development configuration:
            ```kotlin
            dependencies {
                implementation("$owner:$repo:2.48.1")
            }
            ```
            
            Configure the central system router:
            ```kotlin
            @Component(modules = [ServiceRouter::class])
            interface EnterpriseRouter {
                fun getAgent(): SecurityAgent
            }
            ```
        """.trimIndent())
        
        addFile("build.gradle.kts", false, 3, """
            plugins {
                kotlin("jvm") version "1.9.22"
                kotlin("kapt") version "1.9.22"
            }
            
            group = "com.vvf.smartfilemanager"
            version = "1.0.0-SNAPSHOT"
            
            repositories {
                mavenCentral()
                google()
            }
            
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("com.google.dagger:dagger:2.48.1")
                kapt("com.google.dagger:dagger-compiler:2.48.1")
            }
        """.trimIndent())
        
        addFile("settings.gradle.kts", false, 1, """
            rootProject.name = "$repo"
            include(":app")
            include(":core")
        """.trimIndent())
        
        // Source files under com/vvf/smartfilemanager
        addFile("src/main/java/com/vvf/smartfilemanager/di", true, 0, "")
        addFile("src/main/java/com/vvf/smartfilemanager/model", true, 0, "")
        addFile("src/main/java/com/vvf/smartfilemanager/service", true, 0, "")
        
        addFile("src/main/java/com/vvf/smartfilemanager/model/User.kt", false, 2, """
            package com.vvf.smartfilemanager.model
            
            /**
             * Business model representing an authorized system persona.
             */
            data class User(
                val id: Long,
                val username: String,
                val email: String,
                val activeRole: String
            )
        """.trimIndent())
        
        addFile("src/main/java/com/vvf/smartfilemanager/service/UserService.kt", false, 3, """
            package com.vvf.smartfilemanager.service
            
            import com.vvf.smartfilemanager.model.User
            import javax.inject.Inject
            import javax.inject.Singleton
            
            @Singleton
            class UserService @Inject constructor(
                private val databaseBroker: DatabaseBroker,
                private val cacheBroker: CacheBroker
            ) {
                fun fetchUserCredentials(userId: Long): User {
                    val raw = databaseBroker.query("SELECT * FROM users WHERE id = " + userId)
                    return User(userId, "simulated_user", "dev@aistudio.com", "ADMIN")
                }
            }
        """.trimIndent())
        
        addFile("src/main/java/com/vvf/smartfilemanager/di/AppModule.kt", false, 3, """
            package com.vvf.smartfilemanager.di
            
            import dagger.Module
            import dagger.Provides
            import javax.inject.Singleton
            
            @Module
            class AppModule {
                @Provides
                @Singleton
                fun provideDatabaseBroker(): DatabaseBroker {
                    return SqliteDatabaseBroker(url = "jdbc:sqlite::memory:")
                }
                
                @Provides
                @Singleton
                fun provideCacheBroker(): CacheBroker {
                    return RedisCacheBroker(ttl = 3600L)
                }
            }
        """.trimIndent())
        
        addFile("src/main/java/com/vvf/smartfilemanager/di/AppComponent.kt", false, 2, """
            package com.vvf.smartfilemanager.di
            
            import dagger.Component
            import com.vvf.smartfilemanager.service.UserService
            import javax.inject.Singleton
            
            @Singleton
            @Component(modules = [AppModule::class])
            interface AppComponent {
                fun getUserService(): UserService
                fun inject(broker: SecurityBroker)
            }
        """.trimIndent())
        
        addFile("gradle/wrapper/gradle-wrapper.properties", false, 1, """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https://services.gradle.org/distributions/gradle-8.5-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent())
        
        return files
    }

    fun setCloudSubTab(tab: Int) {
        _activeCloudSubTab.value = tab
    }

    fun updateGithubRepoPath(path: String) {
        _githubRepoPath.value = path
        sharedPrefs.edit().putString("github_repo_path", path).apply()
        fetchGitHubData(path)
        _gitSyncState.value = _gitSyncState.value.copy(repositoryName = path)
    }

    fun fetchGitHubData(repoPath: String = _githubRepoPath.value) {
        viewModelScope.launch {
            _isGithubLoading.value = true
            _githubError.value = null
            saveTrackedRepository(repoPath, sharedPrefs.getLong("repo_sync_time_$repoPath", 0L), "Syncing")

            // Reload simulated files & reset chat context
            val simulatedFiles = generateSimulatedRepoFiles(repoPath)
            _repositoryFiles.value = simulatedFiles
            _selectedRepoFile.value = simulatedFiles.firstOrNull { it.path == "README.md" } ?: simulatedFiles.firstOrNull { !it.isDirectory }
            clearRepoChatHistory()

            val parts = repoPath.trim().split("/")
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                _githubError.value = "Invalid repository format. Please use 'owner/repo' structure (e.g. google/dagger)"
                _githubIssues.value = getFallbackIssues()
                _githubCommits.value = getFallbackCommits()
                _isGithubLoading.value = false
                saveTrackedRepository(repoPath, sharedPrefs.getLong("repo_sync_time_$repoPath", 0L), "Failed")
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
                saveTrackedRepository(repoPath, System.currentTimeMillis(), "Synced")
            } catch (e: Exception) {
                _githubError.value = "Live Fetch failed: ${e.localizedMessage ?: "Connection Timeout"}. Displaying high-fidelity simulated tracker data for demo."
                _githubIssues.value = getFallbackIssues()
                _githubCommits.value = getFallbackCommits()
                saveTrackedRepository(repoPath, System.currentTimeMillis(), "Synced (Simulated)")
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

    fun selectConflictedFile(fileId: String?) {
        _selectedConflictedFile.value = _conflictedFiles.value.find { it.id == fileId }
    }

    fun resolveConflictBlock(fileId: String, blockId: String, choice: String, customCode: String? = null) {
        _conflictedFiles.value = _conflictedFiles.value.map { file ->
            if (file.id == fileId) {
                val updatedBlocks = file.blocks.map { block ->
                    if (block.id == blockId) {
                        val resolved = when (choice) {
                            "ours" -> block.currentCode
                            "theirs" -> block.incomingCode
                            "both" -> block.currentCode + "\n" + block.incomingCode
                            "ai" -> customCode ?: block.currentCode
                            else -> block.currentCode
                        }
                        block.copy(resolutionChoice = choice, resolvedCode = resolved)
                    } else block
                }
                val isFullyRes = updatedBlocks.all { it.resolutionChoice != null }
                file.copy(blocks = updatedBlocks, isFullyResolved = isFullyRes)
            } else file
        }
        // Update selection reference
        _selectedConflictedFile.value = _conflictedFiles.value.find { it.id == fileId }
    }

    fun resolveConflictBlockAI(fileId: String, blockId: String) {
        val file = _conflictedFiles.value.find { it.id == fileId } ?: return
        val block = file.blocks.find { block -> block.id == blockId } ?: return

        viewModelScope.launch {
            _isAiResolvingBlock.value = _isAiResolvingBlock.value + (blockId to true)
            
            val key = getActiveApiKey()
            if (key.isBlank()) {
                // Heuristic offline smart engine fallback
                delay(1200) // simulated calculation delay
                val resolvedText = when {
                    block.currentCode.contains("darkColorScheme") -> {
                        // Blend modernSkywalker Cyan with classic
                        "val DarkColorScheme = darkColorScheme(\n" +
                        "    primary = Color(0xFF0EA5E9), // Clean Skywalker Blue (AI optimal)\n" +
                        "    secondary = Color(0xFF0284C7),\n" +
                        "    tertiary = Color(0xFF80B3FF) // Blended harmony\n" +
                        ")"
                    }
                    block.currentCode.contains("Theme.SmartFileManager") -> {
                        "        <activity\n            android:name=\".MainActivity\"\n            android:exported=\"true\"\n            android:theme=\"@style/Theme.Material3.Dynamic\"> // Dynamic Material3 theme resolved by AI"
                    }
                    else -> {
                        // Intelligently use latest version format
                        "    val composeVersion = \"1.7.0-beta02\" // System upgraded version auto-selected"
                    }
                }
                resolveConflictBlock(fileId, blockId, "ai", resolvedText)
            } else {
                try {
                    val prompt = """
                        Current Code segment (HEAD):
                        ${block.currentCode}

                        Incoming Code segment (from branch):
                        ${block.incomingCode}

                        Conflict Description: ${block.description}

                        Resolve these conflicting lines of code in the most clean, optimal software development manner.
                        Do not output any explanation, notes, prose, or backtick codes.
                        Output ONLY the final merged/resolved code fragment directly so it is syntactically ready.
                    """.trimIndent()

                    val systemInstruction = "You are a professional git merge conflict resolution compiler. Output raw functional code merge solution only."
                    val (reply, _) = repository.callGemini(key, prompt, systemInstruction)
                    
                    var cleanedReply = reply.trim()
                    if (cleanedReply.startsWith("```")) {
                        val lines = cleanedReply.lines()
                        if (lines.size > 2) {
                            cleanedReply = lines.subList(1, lines.size - 1).joinToString("\n")
                        }
                    }
                    resolveConflictBlock(fileId, blockId, "ai", cleanedReply.trim())
                } catch (e: Exception) {
                    resolveConflictBlock(fileId, blockId, "ai", block.currentCode + "\n" + block.incomingCode)
                }
            }
            _isAiResolvingBlock.value = _isAiResolvingBlock.value - blockId
        }
    }

    fun markFileAsCompleted(fileId: String) {
        _conflictedFiles.value = _conflictedFiles.value.map { file ->
            if (file.id == fileId) file.copy(isFullyResolved = true) else file
        }
        _selectedConflictedFile.value = null
    }

    fun resetConflictsDemo() {
        _conflictedFiles.value = getInitialConflictFiles()
        _selectedConflictedFile.value = null
    }

    private fun startGitSyncHeartbeat() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _gitSyncState.value = _gitSyncState.value.let { state ->
                    state.copy(lastCheckedSecondsAgo = state.lastCheckedSecondsAgo + 1)
                }
                // Randomly, every 30 seconds we do a simulated quick check
                if (_gitSyncState.value.lastCheckedSecondsAgo % 30 == 0) {
                    val currentStatus = _gitSyncState.value.status
                    if (currentStatus != GitSyncStatus.SYNCING && currentStatus != GitSyncStatus.CONFLICT) {
                        // Refresh status text briefly
                        _gitSyncState.value = _gitSyncState.value.copy(
                            lastCheckedSecondsAgo = 0,
                            latestSyncActionMessage = "Auto-scanned branch head. Local files match remote."
                        )
                    }
                }
            }
        }
    }

    fun triggerFetchSync() {
        viewModelScope.launch {
            val hasConflicts = _conflictedFiles.value.any { !it.isFullyResolved }
            _gitSyncState.value = _gitSyncState.value.copy(
                status = GitSyncStatus.SYNCING,
                latestSyncActionMessage = "Fetching remote refs & comparing hash codes..."
            )
            delay(1500)
            _gitSyncState.value = _gitSyncState.value.copy(
                status = if (hasConflicts) GitSyncStatus.CONFLICT else GitSyncStatus.SYNCED,
                lastCheckedSecondsAgo = 0,
                remoteAheadCount = 0,
                localAheadCount = 0,
                latestSyncActionMessage = if (hasConflicts) "Branch check completed. Ongoing conflict resolution required!" else "Successfully aligned with origin/main."
            )
        }
    }

    fun triggerSimulateBehind() {
        val hasConflicts = _conflictedFiles.value.any { !it.isFullyResolved }
        if (hasConflicts) return // Complete conflicts first
        _gitSyncState.value = _gitSyncState.value.copy(
            status = GitSyncStatus.OUT_OF_SYNC,
            remoteAheadCount = 3,
            latestSyncActionMessage = "Remote repository matches 3 new commits. Pull to merge."
        )
    }

    fun triggerSimulateAhead() {
        val hasConflicts = _conflictedFiles.value.any { !it.isFullyResolved }
        if (hasConflicts) return // Complete conflicts first
        _gitSyncState.value = _gitSyncState.value.copy(
            status = GitSyncStatus.AHEAD,
            localAheadCount = 2,
            latestSyncActionMessage = "Workspace ready with 2 commits ahead of origin/main. Push suggested."
        )
    }

    fun triggerGitPull() {
        viewModelScope.launch {
            _gitSyncState.value = _gitSyncState.value.copy(
                status = GitSyncStatus.SYNCING,
                latestSyncActionMessage = "Pulling refs/heads/main and integrating changes..."
            )
            delay(2000)
            _gitSyncState.value = _gitSyncState.value.copy(
                status = GitSyncStatus.SYNCED,
                remoteAheadCount = 0,
                lastCheckedSecondsAgo = 0,
                latestSyncActionMessage = "Fast-forward merged 3 inbound commits cleanly."
            )
        }
    }

    fun triggerGitPush() {
        viewModelScope.launch {
            _gitSyncState.value = _gitSyncState.value.copy(
                status = GitSyncStatus.SYNCING,
                latestSyncActionMessage = "Uploading local objects and fast-forwarding origin/main..."
            )
            delay(2000)
            _gitSyncState.value = _gitSyncState.value.copy(
                status = GitSyncStatus.SYNCED,
                localAheadCount = 0,
                lastCheckedSecondsAgo = 0,
                latestSyncActionMessage = "Successfully pushed references to origin/main. Green build!"
            )
        }
    }

    fun generateProjectReadme() {
        viewModelScope.launch {
            _isReadmeGenerating.value = true
            _generatedReadme.value = null
            _readmeThinkingProcess.value = "• Scanning android package workspace...\n• Analyzing package com.vvf.smartfilemanager\n• Traversing project metadata and database models..."
            
            val liveFilesCount = allLocalNonSafeFiles.value.size
            val liveSafeFilesCount = allSafeFiles.value.size
            val liveDuplicatesCount = duplicateFiles.value.size
            val currentRepo = githubRepoPath.value
            val accountsCount = connectedAccounts.value.size
            
            val systemInstruction = "You are a senior software architect documenting an advanced Android repository."
            val prompt = """
                Generate a comprehensive, professional, markdown-formatted README.md document for the current project.
                
                Codebase Blueprint Checklist:
                - Manifest: AndroidManifest.xml (custom launcher and services)
                - Local Storage: Room implementation under data/ with AppDatabase, FileDao, and ChatMessageDao.
                - Unified Repository: AppRepository handles Room CRUD and coordinates REST communication to external Gemini endpoints.
                - MVVM State: SmartViewModel.kt uses StateFlow properties to coordinate dual-mode (local scanning / cloud sim) and Git branch states.
                - Composable Design: MainLayout.kt implements a Material 3 design system with custom theme tokens, analytics dashboards, and interactive chat loops.
                - Advanced Git Simulation: Interactive branch tracking, heartbeat status checker, and dynamic visual diff resolvers.
                
                Live Application State Metrics:
                - Currently Managed Files: \$liveFilesCount
                - Safe Vault Files: \$liveSafeFilesCount
                - Duplicate Redundant Files: \$liveDuplicatesCount
                - Remote Sync Node: \$currentRepo
                - Active Connected Accounts: \$accountsCount
                
                Please structure the README.md beautifully with:
                1. Project Title & Mission Statement (An modern, elegant title)
                2. Live Metrics Dashboard (Rendered as an ASCII or markdown table showing the real-time stats above)
                3. High-Level Modular Architecture (Explaining Room DB persistence, Retrofit API layer, and MVVM reactive patterns with StateFlow)
                4. Codebase Directory Map (A clean, visual text folder tree showing the main components like ui, data, viewmodel)
                5. Setup & Customization Guide (Focusing on BuildConfig API key setups, Gradle requirements, and manual PIN setups)
                
                Use bold headers, elegant spacing, code blocks with kotlin/xml specifiers, and custom icons/emojis for visual rhythm. Keep the tone professional and informative.
            """.trimIndent()
            
            try {
                val key = getActiveApiKey()
                val enableThinking = highThinkingEnabled.value
                val (reply, thoughts) = repository.callGemini(
                    apiKey = key,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    enableThinkingMode = enableThinking
                )
                
                _generatedReadme.value = reply
                _readmeThinkingProcess.value = thoughts ?: "• Successfully analyzed local source files & db states.\n• Synthesized workspace specifications into markdown."
            } catch (e: Exception) {
                _generatedReadme.value = "An error occurred while generating: \${e.localizedMessage}. Please verify your network connection and API key configuration."
                _readmeThinkingProcess.value = "• Error in API communication: \${e.message}"
            } finally {
                _isReadmeGenerating.value = false
            }
        }
    }

    fun clearGeneratedReadme() {
        _generatedReadme.value = null
        _readmeThinkingProcess.value = null
    }

    fun addLocalSimulatedFile(name: String, size: Long, mimeType: String, path: String) {
        viewModelScope.launch {
            repository.insertFiles(listOf(FileEntity(
                id = 0L,
                name = name,
                path = path,
                size = size,
                lastModified = System.currentTimeMillis(),
                mimeType = mimeType,
                isLocal = true,
                isSafe = false,
                cloudAccountEmail = ""
            )))
        }
    }

    private fun getInitialConflictFiles(): List<ConflictFile> {
        return listOf(
            ConflictFile(
                id = "conf_1",
                name = "Theme.kt",
                path = "app/src/main/java/com/vvf/smartfilemanager/ui/theme/Theme.kt",
                currentBranch = "main",
                incomingBranch = "feature/dynamic-themes",
                blocks = listOf(
                    ConflictBlock(
                        id = "block_1_1",
                        lineStart = 15,
                        currentCode = "val DarkColorScheme = darkColorScheme(\n    primary = Purple80,\n    secondary = PurpleGrey80,\n    tertiary = Pink80\n)",
                        incomingCode = "val DarkColorScheme = darkColorScheme(\n    primary = Color(0xFF0EA5E9), // Skywalker Blue\n    secondary = Color(0xFF0284C7),\n    tertiary = Color(0xFF38BDF8)\n)",
                        description = "Brand primary color palette definition vs cyber Skywalker cyan palette."
                    )
                )
            ),
            ConflictFile(
                id = "conf_2",
                name = "AndroidManifest.xml",
                path = "app/src/main/AndroidManifest.xml",
                currentBranch = "main",
                incomingBranch = "feature/safefolder-activity",
                blocks = listOf(
                    ConflictBlock(
                        id = "block_2_1",
                        lineStart = 22,
                        currentCode = "        <activity\n            android:name=\".MainActivity\"\n            android:exported=\"true\"\n            android:theme=\"@style/Theme.SmartFileManager\">",
                        incomingCode = "        <activity\n            android:name=\".MainActivity\"\n            android:exported=\"true\"\n            android:theme=\"@style/Theme.Material3.Dynamic\">",
                        description = "Sets system activity style manifest configuration."
                    )
                )
            ),
            ConflictFile(
                id = "conf_3",
                name = "build.gradle.kts",
                path = "app/build.gradle.kts",
                currentBranch = "main",
                incomingBranch = "upgrade/compose-compiler",
                blocks = listOf(
                    ConflictBlock(
                        id = "block_3_1",
                        lineStart = 55,
                        currentCode = "    val composeVersion = \"1.5.4\"",
                        incomingCode = "    val composeVersion = \"1.7.0-beta02\"",
                        description = "Upgrades building SDK toolchain environment compiler version."
                    )
                )
            )
        )
    }
}
