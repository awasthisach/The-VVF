package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vvf.smartfilemanager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

class FileScannerViewModel(
    application: Application,
    private val repository: IAppRepository
) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    // --- Local Files ---
    val allLocalNonSafeFiles: StateFlow<List<FileEntity>> = repository.allLocalNonSafeFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localNonSafeFilesTotalSize: StateFlow<Long> = repository.localNonSafeFilesTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val junkFilesTotalSize: StateFlow<Long> = repository.junkFilesTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val duplicateFilesTotalSize: StateFlow<Long> = repository.duplicateFilesTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val localNonSafeFilesCount: StateFlow<Int> = repository.localNonSafeFilesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val safeFilesCount: StateFlow<Int> = repository.safeFilesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val duplicateFilesCount: StateFlow<Int> = repository.duplicateFilesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery: StateFlow<String> = _localSearchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    fun updateSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredLocalFiles: StateFlow<List<FileEntity>> = combine(
        _localSearchQuery, _selectedCategory
    ) { query, category ->
        Pair(query, category)
    }.flatMapLatest { (query, category) ->
        repository.searchLocalNonSafeFiles(query, category, limit = 1000)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedLocalFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedLocalFileIds: StateFlow<Set<Long>> = _selectedLocalFileIds.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    // --- Real Scan ---
    private val _realFiles = MutableStateFlow<List<ScannedFile>>(emptyList())
    val realFiles: StateFlow<List<ScannedFile>> = _realFiles.asStateFlow()

    private val _realScanProgress = MutableStateFlow(0f)
    val realScanProgress: StateFlow<Float> = _realScanProgress.asStateFlow()

    private val _realScanStatusMessage = MutableStateFlow("")
    val realScanStatusMessage: StateFlow<String> = _realScanStatusMessage.asStateFlow()

    private val _realFileSearchQuery = MutableStateFlow("")
    val realFileSearchQuery: StateFlow<String> = _realFileSearchQuery.asStateFlow()

    private val _isScanningRealFiles = MutableStateFlow(false)
    val isScanningRealFiles: StateFlow<Boolean> = _isScanningRealFiles.asStateFlow()

    val realDuplicates: StateFlow<Map<String, List<ScannedFile>>> = _realFiles.map { files ->
        files.groupBy { "${it.name}_${it.size}" }
            .filter { it.value.size > 1 }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val filteredRealFiles: StateFlow<List<ScannedFile>> = combine(
        _realFiles, _realFileSearchQuery
    ) { files, query ->
        if (query.isBlank()) files
        else files.filter { it.name.contains(query, ignoreCase = true) }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateRealFileSearchQuery(query: String) {
        _realFileSearchQuery.value = query
    }

    fun addLocalSimulatedFile(name: String, size: Long, mimeType: String, path: String) {
        viewModelScope.launch {
            repository.insertFile(
                FileEntity(
                    name = name,
                    size = size,
                    mimeType = mimeType,
                    path = path,
                    isLocal = true,
                    isSafe = false,
                    lastModified = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Storage Cleaner ---
    private val _storageSelectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val storageSelectedFileIds: StateFlow<Set<Long>> = _storageSelectedFileIds.asStateFlow()

    private val _isStorageScanning = MutableStateFlow(false)
    val isStorageScanning: StateFlow<Boolean> = _isStorageScanning.asStateFlow()

    private val _storageScanProgress = MutableStateFlow(0f)
    val storageScanProgress: StateFlow<Float> = _storageScanProgress.asStateFlow()

    private val _storageCleanerActive = MutableStateFlow(false)
    val storageCleanerActive: StateFlow<Boolean> = _storageCleanerActive.asStateFlow()

    // --- Viewer ---
    private val _activeViewerFile = MutableStateFlow<FileEntity?>(null)
    val activeViewerFile: StateFlow<FileEntity?> = _activeViewerFile.asStateFlow()

    // --- Doc Scanner ---
    private val _isDocumentScannerActive = MutableStateFlow(false)
    val isDocumentScannerActive: StateFlow<Boolean> = _isDocumentScannerActive.asStateFlow()

    private val _isCameraCapturing = MutableStateFlow(false)
    val isCameraCapturing: StateFlow<Boolean> = _isCameraCapturing.asStateFlow()

    private val _scannedDocumentFilter = MutableStateFlow("original")
    val scannedDocumentFilter: StateFlow<String> = _scannedDocumentFilter.asStateFlow()

    private val _scannedFileName = MutableStateFlow("SCAN_2026.pdf")
    val scannedFileName: StateFlow<String> = _scannedFileName.asStateFlow()

    private val _scannedDocumentSaved = MutableStateFlow(false)
    val scannedDocumentSaved: StateFlow<Boolean> = _scannedDocumentSaved.asStateFlow()

    // --- Cloud Accounts ---
    private val _connectedAccounts = MutableStateFlow(listOf("user@example.com"))
    val connectedAccounts: StateFlow<List<String>> = _connectedAccounts.asStateFlow()

    private val _activeAccountEmail = MutableStateFlow("user@example.com")
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

    // --- GitHub Tracker ---
    private val _githubRepoPath = MutableStateFlow("google/dagger")
    val githubRepoPath: StateFlow<String> = _githubRepoPath.asStateFlow()

    private val _trackedRepositories = MutableStateFlow<List<TrackedRepository>>(emptyList())
    val trackedRepositories: StateFlow<List<TrackedRepository>> = _trackedRepositories.asStateFlow()

    private val _githubIssues = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val githubIssues: StateFlow<List<GitHubIssue>> = _githubIssues.asStateFlow()

    private val _githubCommits = MutableStateFlow<List<GitHubCommit>>(emptyList())
    val githubCommits: StateFlow<List<GitHubCommit>> = _githubCommits.asStateFlow()

    private val _isGithubLoading = MutableStateFlow(false)
    val isGithubLoading: StateFlow<Boolean> = _isGithubLoading.asStateFlow()

    private val _githubError = MutableStateFlow<String?>(null)
    val githubError: StateFlow<String?> = _githubError.asStateFlow()

    // --- Git Merge Conflict & Sync State ---
    private val _conflictedFiles = MutableStateFlow<List<ConflictFile>>(emptyList())
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
            repositoryName = "google/dagger",
            latestSyncActionMessage = "Active merge conflicts detected in Theme.kt, AndroidManifest.xml, build.gradle.kts"
        )
    )
    val gitSyncState: StateFlow<GitSyncState> = _gitSyncState.asStateFlow()

    // --- Layout Sub-Tabs ---
    private val _gitWorkspaceSubTab = MutableStateFlow(0)
    val gitWorkspaceSubTab: StateFlow<Int> = _gitWorkspaceSubTab.asStateFlow()

    private val _selectedRepoFile = MutableStateFlow<GitHubRepoFile?>(null)
    val selectedRepoFile: StateFlow<GitHubRepoFile?> = _selectedRepoFile.asStateFlow()

    private val _repoChatHistory = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val repoChatHistory: StateFlow<List<ChatMessageEntity>> = _repoChatHistory.asStateFlow()

    private val _activeCloudSubTab = MutableStateFlow(0)
    val activeCloudSubTab: StateFlow<Int> = _activeCloudSubTab.asStateFlow()

    // --- Background scanning WorkManager enqueue ---
    fun runBackgroundScanViaWorkManager() {
        Log.i("FileScannerViewModel", "Enqueuing BackgroundScanWorker via WorkManager [StructuredLog: { event: \"workmanager_enqueue\" }]")
        val scanRequest = OneTimeWorkRequestBuilder<BackgroundScanWorker>().build()
        workManager.enqueue(scanRequest)
    }

    // Ported scan actions
    fun scanRealDeviceFiles(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isScanningRealFiles.value = true
            _realScanStatusMessage.value = "Accessing device media structures..."
            _realScanProgress.value = 0.1f
            delay(150)
            _isScanningRealFiles.value = true
            _realScanStatusMessage.value = "Reading directories and mime tables..."
            _realScanProgress.value = 0.5f
            delay(200)
            repository.scanAndSaveRealFiles(appContext)
            _realScanStatusMessage.value = "Finalizing secure file structures indexing..."
            _realScanProgress.value = 0.9f
            delay(100)
            _realScanProgress.value = 1.0f
            _realScanStatusMessage.value = "Indexing successful!"
            Log.i("FileScannerViewModel", "Device file scan complete [StructuredLog: { event: \"device_scan_finish\" }]")
            _isScanningRealFiles.value = false
        }
    }

    fun scanRealFiles() {
        // Enqueue via WorkManager for complete robust offline execution, but also trigger immediate local visual state
        runBackgroundScanViaWorkManager()
        viewModelScope.launch {
            _isScanningRealFiles.value = true
            _realScanProgress.value = 0f
            _realScanStatusMessage.value = "Locating files..."
            while (_realScanProgress.value < 1.0f) {
                delay(100)
                _realScanProgress.value = minOf(1.0f, _realScanProgress.value + 0.15f)
            }
            _realScanStatusMessage.value = "Files sync complete"
            _isScanningRealFiles.value = false
        }
    }

    fun deleteRealFile(file: ScannedFile) {
        viewModelScope.launch {
            val f = File(file.path)
            if (f.exists()) {
                f.delete()
            }
            _realFiles.value = _realFiles.value.filter { it.path != file.path }
            Log.d("FileScannerViewModel", "Deleted real file at path: ${file.path}")
        }
    }

    fun deleteRealDuplicates(keepFirst: Boolean = true) {
        // Simulated duplicate deletion
        _realFiles.value = _realFiles.value.distinctBy { Pair(it.name, it.size) }
    }

    fun toggleLocalFileSelection(id: Long) {
        val current = _selectedLocalFileIds.value
        _selectedLocalFileIds.value = if (current.contains(id)) current - id else current + id
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

    fun setStorageCleanerActive(active: Boolean) {
        _storageCleanerActive.value = active
        if (active) runStorageCleanerScan()
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
                delay(120)
                _storageScanProgress.value = minOf(1.0f, _storageScanProgress.value + 0.1f)
            }
            _isStorageScanning.value = false
        }
    }

    fun deleteSelectedStorageFiles() {
        viewModelScope.launch {
            val selected = _storageSelectedFileIds.value
            selected.forEach { id -> repository.deleteFileById(id) }
            _storageSelectedFileIds.value = emptySet()
        }
    }

    fun openFileInViewer(file: FileEntity) { _activeViewerFile.value = file }
    fun closeFileInViewer() { _activeViewerFile.value = null }
    fun setDocumentScannerActive(active: Boolean) { _isDocumentScannerActive.value = active }
    fun setScannedFilter(filter: String) { _scannedDocumentFilter.value = filter }
    fun setScannedFileName(name: String) { _scannedFileName.value = name }
    fun captureDocument() {
        viewModelScope.launch {
            _isCameraCapturing.value = true
            delay(1500)
            _isCameraCapturing.value = false
            _scannedDocumentSaved.value = true
        }
    }

    fun saveScannedDocument(onSavedCompletable: () -> Unit = {}) {
        viewModelScope.launch {
            val newFile = FileEntity(
                name = _scannedFileName.value,
                path = "/storage/emulated/0/Documents/" + _scannedFileName.value,
                size = (150_000..850_000).random().toLong(),
                lastModified = System.currentTimeMillis(),
                mimeType = "application/pdf",
                isLocal = true
            )
            repository.insertFile(newFile)
            _scannedDocumentSaved.value = false
            _isDocumentScannerActive.value = false
            onSavedCompletable()
        }
    }

    fun deleteLocalFilesByIds(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteFileById(it) }
            clearLocalSelection()
        }
    }

    fun selectActiveCloudAccount(email: String) { _activeAccountEmail.value = email }
    fun addNewCloudAccount(name: String, email: String) {
        val current = _connectedAccounts.value.toMutableList()
        if (!current.contains(email)) current.add(email)
        _connectedAccounts.value = current
        _activeAccountEmail.value = email
    }

    fun logoutVirtualAccount(email: String) {
        val current = _connectedAccounts.value.toMutableList()
        current.remove(email)
        _connectedAccounts.value = current
        if (_activeAccountEmail.value == email && current.isNotEmpty()) {
            _activeAccountEmail.value = current.first()
        }
    }

    fun toggleCloudFileSelection(id: Long) {
        val current = _selectedCloudFileIds.value
        _selectedCloudFileIds.value = if (current.contains(id)) current - id else current + id
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

    fun updateCloudSearchQuery(query: String) { _cloudSearchQuery.value = query }

    fun deleteCloudFilesByIds(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteFileById(it) }
            clearCloudSelection()
        }
    }

    fun updateGithubRepoPath(path: String) { _githubRepoPath.value = path }
    fun setCloudSubTab(tab: Int) { _activeCloudSubTab.value = tab }
    fun setGitWorkspaceSubTab(tab: Int) { _gitWorkspaceSubTab.value = tab }
    fun selectRepoFile(file: GitHubRepoFile?) { _selectedRepoFile.value = file }
    fun clearRepoChatHistory() { _repoChatHistory.value = emptyList() }

    fun renameRepoFile(fileId: String, newName: String) {
        // Rename simulated code workspace file
    }

    fun deleteRepoFile(fileId: String) {
        // Delete simulated code file
    }

    fun runAiAnalysis(fileId: String, onAnalysisResult: (String) -> Unit) {
        viewModelScope.launch {
            delay(1000)
            onAnalysisResult("Ai analysis resolved cleanly for $fileId")
        }
    }

    fun sendRepoChatMessage(message: String) {
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(sender = "user", messageText = message, timestamp = System.currentTimeMillis())
            _repoChatHistory.value = _repoChatHistory.value + userMsg
            delay(800)
            val responseMsg = ChatMessageEntity(sender = "gemini", messageText = "AI assistance for git repo ready", timestamp = System.currentTimeMillis())
            _repoChatHistory.value = _repoChatHistory.value + responseMsg
        }
    }

    fun selectConflictedFile(fileId: String?) {
        _selectedConflictedFile.value = _conflictedFiles.value.find { it.id == fileId }
    }

    fun resolveConflictBlock(fileId: String, blockId: String, choice: String, customCode: String? = null) {
        // Resolve merge block
    }

    fun resolveConflictBlockAI(fileId: String, blockId: String) {
        // Resolve merge block with AI
    }

    fun markFileAsCompleted(fileId: String) {
        // Mark merge resolution complete
    }

    fun resetConflictsDemo() {
        // Reset merge demo
    }

    fun triggerFetchSync() {}
    fun triggerSimulateBehind() {}
    fun triggerSimulateAhead() {}
    fun triggerGitPull() {}
    fun triggerGitPush() {}

    fun setConflictedFiles(files: List<ConflictFile>) {
        _conflictedFiles.value = files
    }

    fun updateGitSyncState(state: GitSyncState) {
        _gitSyncState.value = state
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1] + ""
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
