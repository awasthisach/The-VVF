package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vvf.smartfilemanager.data.*
import com.vvf.smartfilemanager.domain.*
import com.vvf.smartfilemanager.ai.*
import com.vvf.smartfilemanager.cloud.*
import com.vvf.smartfilemanager.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.paging.PagingData

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

    init {
        Log.e("VVF_STARTUP", "SmartViewModel constructor STARTED")
        EmbeddingCache.init(application)
    }

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db)

    // Clean Architecture System Initializations
    val cloudSyncManager = CloudSyncManager(application, repository)
    val geminiAIEngine = GeminiAIEngine(repository)
    val semanticSearchEngine = SemanticSearchEngine(repository, geminiAIEngine)
    val fileClassifier = FileClassifier(repository)

    val scanFilesUseCase = ScanFilesUseCase(repository)
    val searchFilesUseCase = SearchFilesUseCase(repository)
    val deleteFilesUseCase = DeleteFilesUseCase(repository)
    val syncMetadataUseCase = SyncMetadataUseCase(cloudSyncManager)

    // Sub-viewmodels
    val themeViewModel = ThemeViewModel(application)
    val settingsViewModel = SettingsViewModel(application)
    val safeFolderViewModel = SafeFolderViewModel(application, repository)
    val duplicateCleanerViewModel = DuplicateCleanerViewModel(application, repository)
    val aiSearchViewModel = AiSearchViewModel(application, repository)
    val fileScannerViewModel = FileScannerViewModel(application, repository)

    init {
        android.util.Log.d("VVF_TRACE", "VVF_TRACE: ViewModel Initialized")
        Log.e("VVF_STARTUP", "SmartViewModel constructor COMPLETED. DB: $db, Repository: $repository")
    }

    // Delegated Theme States & Methods
    val isDarkMode: StateFlow<Boolean> = themeViewModel.isDarkMode
    fun toggleTheme() = themeViewModel.toggleTheme()
    fun setDarkMode(dark: Boolean) = themeViewModel.setDarkMode(dark)

    // Delegated Settings States & Methods
    val highThinkingEnabled: StateFlow<Boolean> = settingsViewModel.highThinkingEnabled
    val googleDriveSyncEnabled: StateFlow<Boolean> = settingsViewModel.googleDriveSyncEnabled
    val isApiPanelExpanded: StateFlow<Boolean> = settingsViewModel.isApiPanelExpanded
    val apiKey: StateFlow<String> = settingsViewModel.apiKey
    fun toggleApiPanel() = settingsViewModel.toggleApiPanel()
    fun setHighThinkingMode(enabled: Boolean) = settingsViewModel.setHighThinkingMode(enabled)
    fun setGoogleDriveSyncEnabled(enabled: Boolean) = settingsViewModel.setGoogleDriveSyncEnabled(enabled)
    fun setApiKey(key: String) = settingsViewModel.setApiKey(key)
    fun getActiveApiKey(): String = settingsViewModel.getActiveApiKey()

    // Delegated Safe Folder States & Methods
    val safeFolderState: StateFlow<SafeFolderState> = safeFolderViewModel.safeFolderState.map {
        when(it) {
            is com.vvf.smartfilemanager.viewmodel.SafeFolderState.PinSetupNeeded -> SafeFolderState.PinSetupNeeded
            is com.vvf.smartfilemanager.viewmodel.SafeFolderState.Locked -> SafeFolderState.Locked
            is com.vvf.smartfilemanager.viewmodel.SafeFolderState.Unlocked -> SafeFolderState.Unlocked
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SafeFolderState.Locked)

    val pinSetupStep: StateFlow<String> = safeFolderViewModel.pinSetupStep
    val inputPinBuffer: StateFlow<String> = safeFolderViewModel.inputPinBuffer
    val pinErrorMessage: StateFlow<String?> = safeFolderViewModel.pinErrorMessage
    val allSafeFiles: StateFlow<List<FileEntity>> = safeFolderViewModel.allSafeFiles
    val pagedSafeFiles: Flow<PagingData<FileEntity>> = safeFolderViewModel.pagedSafeFiles
    fun appendPinDigit(digit: String) = safeFolderViewModel.appendPinDigit(digit)
    fun clearLastPinDigit() = safeFolderViewModel.clearLastPinDigit()
    fun lockSafeFolder() = safeFolderViewModel.lockSafeFolder()
    fun unlockWithBiometrics() = safeFolderViewModel.unlockWithBiometrics()
    fun restoreSelectedFromSafe(selectedIds: Set<Long>) = safeFolderViewModel.restoreSelectedFromSafe(selectedIds)
    fun moveSelectedToSafe(selectedIds: Set<Long>, onClearSelection: () -> Unit) =
        safeFolderViewModel.moveSelectedToSafe(selectedIds, onClearSelection)

    // Delegated Duplicate Cleaner States & Methods
    val duplicateScannerState: StateFlow<ScannerState> = duplicateCleanerViewModel.duplicateScannerState.map {
        when(it) {
            ScannerState.Idle -> ScannerState.Idle
            ScannerState.Scanning -> ScannerState.Scanning
            ScannerState.Finished -> ScannerState.Finished
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScannerState.Idle)

    val duplicateScanProgress: StateFlow<Float> = duplicateCleanerViewModel.duplicateScanProgress
    val cleanerState: StateFlow<CleanerState> = duplicateCleanerViewModel.cleanerState.map {
        when(it) {
            CleanerState.Idle -> CleanerState.Idle
            CleanerState.Scanning -> CleanerState.Scanning
            CleanerState.Cleaning -> CleanerState.Cleaning
            CleanerState.Finished -> CleanerState.Finished
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CleanerState.Idle)

    val cleanerProgress: StateFlow<Float> = duplicateCleanerViewModel.cleanerProgress
    val scannedDuplicates: StateFlow<List<FileEntity>> = duplicateCleanerViewModel.scannedDuplicates
    val pagedScannedDuplicates: Flow<PagingData<FileEntity>> = duplicateCleanerViewModel.pagedScannedDuplicates
    val scannedLargeTempFiles: StateFlow<List<FileEntity>> = duplicateCleanerViewModel.scannedLargeTempFiles
    val duplicateFiles: StateFlow<List<FileEntity>> = duplicateCleanerViewModel.duplicateFiles
    val junkFiles: StateFlow<List<FileEntity>> = duplicateCleanerViewModel.junkFiles
    fun runDuplicateScanner() = duplicateCleanerViewModel.runDuplicateScanner()
    fun dismissDuplicateScanner() = duplicateCleanerViewModel.dismissDuplicateScanner()
    fun deleteSelectedDuplicates() = duplicateCleanerViewModel.deleteSelectedDuplicates()
    fun runJunkCleaner() = duplicateCleanerViewModel.runJunkCleaner()
    fun executeCleanJunk() = duplicateCleanerViewModel.executeCleanJunk()
    fun cancelJunkCleaner() = duplicateCleanerViewModel.cancelJunkCleaner()

    // Delegated File Scanner States & Methods
    val allLocalNonSafeFiles: StateFlow<List<FileEntity>> = fileScannerViewModel.allLocalNonSafeFiles
    val localNonSafeFilesTotalSize: StateFlow<Long> = fileScannerViewModel.localNonSafeFilesTotalSize
    val junkFilesTotalSize: StateFlow<Long> = fileScannerViewModel.junkFilesTotalSize
    val duplicateFilesTotalSize: StateFlow<Long> = fileScannerViewModel.duplicateFilesTotalSize
    val localNonSafeFilesCount: StateFlow<Int> = fileScannerViewModel.localNonSafeFilesCount
    val safeFilesCount: StateFlow<Int> = fileScannerViewModel.safeFilesCount
    val duplicateFilesCount: StateFlow<Int> = fileScannerViewModel.duplicateFilesCount

    val imagesCount: StateFlow<Int> = fileScannerViewModel.imagesCount
    val imagesTotalSize: StateFlow<Long> = fileScannerViewModel.imagesTotalSize
    val docsCount: StateFlow<Int> = fileScannerViewModel.docsCount
    val docsTotalSize: StateFlow<Long> = fileScannerViewModel.docsTotalSize
    val mediaCount: StateFlow<Int> = fileScannerViewModel.mediaCount
    val mediaTotalSize: StateFlow<Long> = fileScannerViewModel.mediaTotalSize
    val selectedCategory: StateFlow<String> = fileScannerViewModel.selectedCategory
    val filteredLocalFiles: Flow<PagingData<FileEntity>> = fileScannerViewModel.filteredLocalFiles
    val localSearchQuery: StateFlow<String> = fileScannerViewModel.localSearchQuery
    val selectedLocalFileIds: StateFlow<Set<Long>> = fileScannerViewModel.selectedLocalFileIds
    val isMultiSelectMode: StateFlow<Boolean> = fileScannerViewModel.isMultiSelectMode
    val realFiles: StateFlow<List<ScannedFile>> = fileScannerViewModel.realFiles
    val realDuplicates: StateFlow<Map<String, List<ScannedFile>>> = fileScannerViewModel.realDuplicates
    val selectedDuplicateUris: StateFlow<Set<String>> = fileScannerViewModel.selectedDuplicateUris
    val isScanningRealFiles: StateFlow<Boolean> = fileScannerViewModel.isScanningRealFiles
    val realScanProgress: StateFlow<Float> = fileScannerViewModel.realScanProgress
    val realScanStatusMessage: StateFlow<String> = fileScannerViewModel.realScanStatusMessage
    val realFileSearchQuery: StateFlow<String> = fileScannerViewModel.realFileSearchQuery
    val filteredRealFiles: StateFlow<List<ScannedFile>> = fileScannerViewModel.filteredRealFiles
    val sortOrder: StateFlow<SortOrder> = fileScannerViewModel.sortOrder
    val useSemanticResults: StateFlow<Boolean> = fileScannerViewModel.useSemanticResults
    val semanticResults: StateFlow<List<ScannedFile>> = fileScannerViewModel.semanticResults
    val isSemanticSearching: StateFlow<Boolean> = fileScannerViewModel.isSemanticSearching
    val safTreeUri: StateFlow<String?> = fileScannerViewModel.safTreeUri
    val pendingDeleteIntent: StateFlow<PendingIntent?> = combine(
        fileScannerViewModel.pendingDeleteIntent,
        safeFolderViewModel.pendingDeleteIntent,
        duplicateCleanerViewModel.pendingDeleteIntent
    ) { scanIntent, safeIntent, duplicateIntent ->
        duplicateIntent ?: safeIntent ?: scanIntent
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val storageSelectedFileIds: StateFlow<Set<Long>> = fileScannerViewModel.storageSelectedFileIds
    val isStorageScanning: StateFlow<Boolean> = fileScannerViewModel.isStorageScanning
    val storageScanProgress: StateFlow<Float> = fileScannerViewModel.storageScanProgress
    val storageCleanerActive: StateFlow<Boolean> = fileScannerViewModel.storageCleanerActive
    val activeViewerFile: StateFlow<FileEntity?> = fileScannerViewModel.activeViewerFile
    val isDocumentScannerActive: StateFlow<Boolean> = fileScannerViewModel.isDocumentScannerActive
    val isCameraCapturing: StateFlow<Boolean> = fileScannerViewModel.isCameraCapturing
    val scannedDocumentFilter: StateFlow<String> = fileScannerViewModel.scannedDocumentFilter
    val scannedFileName: StateFlow<String> = fileScannerViewModel.scannedFileName
    val scannedDocumentSaved: StateFlow<Boolean> = fileScannerViewModel.scannedDocumentSaved
    val connectedAccounts: StateFlow<List<String>> = fileScannerViewModel.connectedAccounts
    val activeAccountEmail: StateFlow<String> = fileScannerViewModel.activeAccountEmail
    val cloudFiles: StateFlow<List<FileEntity>> = fileScannerViewModel.cloudFiles
    val cloudSearchQuery: StateFlow<String> = fileScannerViewModel.cloudSearchQuery
    val selectedCloudFileIds: StateFlow<Set<Long>> = fileScannerViewModel.selectedCloudFileIds
    val isCloudMultiSelectMode: StateFlow<Boolean> = fileScannerViewModel.isCloudMultiSelectMode
    val activeCloudSubTab: StateFlow<Int> = fileScannerViewModel.activeCloudSubTab

    val pinnedDirectories: StateFlow<Set<String>> = fileScannerViewModel.pinnedDirectories
    val selectedFolderFilter: StateFlow<String?> = fileScannerViewModel.selectedFolderFilter

    val allTrashFiles: StateFlow<List<TrashEntity>> = fileScannerViewModel.allTrashFiles
    val pagedTrashFiles: Flow<PagingData<TrashEntity>> = fileScannerViewModel.pagedTrashFiles
    val trashCleanupDays: StateFlow<Int> = fileScannerViewModel.trashCleanupDays

    fun togglePinnedDirectory(directory: String) = fileScannerViewModel.togglePinnedDirectory(directory)
    fun toggleFolderFilter(folder: String) = fileScannerViewModel.toggleFolderFilter(folder)
    fun clearFolderFilter() = fileScannerViewModel.clearFolderFilter()

    fun batchRenamePhysicalFiles(
        context: Context,
        files: List<ScannedFile>,
        prefix: String,
        addDateStamp: Boolean,
        addSequence: Boolean,
        onComplete: (Int) -> Unit
    ) = fileScannerViewModel.batchRenamePhysicalFiles(context, files, prefix, addDateStamp, addSequence, onComplete)

    fun updateSelectedCategory(category: String) = fileScannerViewModel.updateSelectedCategory(category)
    fun scanRealDeviceFiles(context: Context) = fileScannerViewModel.scanRealDeviceFiles(context)
    fun scanRealFiles() = fileScannerViewModel.scanRealFiles()
    fun updateRealFileSearchQuery(query: String) = fileScannerViewModel.updateRealFileSearchQuery(query)
    fun updateSortOrder(order: SortOrder) = fileScannerViewModel.updateSortOrder(order)
    fun setUseSemanticResults(use: Boolean) = fileScannerViewModel.setUseSemanticResults(use)
    fun smartGeminiSemanticSearch(query: String, onComplete: () -> Unit = {}) = fileScannerViewModel.smartGeminiSemanticSearch(getActiveApiKey(), query, onComplete)
    fun addLocalSimulatedFile(name: String, size: Long, mimeType: String, path: String) = fileScannerViewModel.addLocalSimulatedFile(name, size, mimeType, path)
    fun deleteRealFile(file: ScannedFile) = fileScannerViewModel.deleteRealFile(file)
    fun deleteRealFilesBatch(context: Context, files: List<ScannedFile>, onComplete: (Boolean) -> Unit = {}) = fileScannerViewModel.deleteRealFilesBatch(context, files, onComplete)
    fun onSafDirectorySelected(context: Context, uri: Uri) = fileScannerViewModel.onSafDirectorySelected(context, uri)
    fun scanSafFiles(context: Context) = fileScannerViewModel.scanSafFiles(context)
    fun clearPendingDeleteIntent() {
        fileScannerViewModel.clearPendingDeleteIntent()
        safeFolderViewModel.clearPendingDeleteIntent()
        duplicateCleanerViewModel.clearPendingDeleteIntent()
    }
    fun isBiometricEnabled() = safeFolderViewModel.isBiometricEnabled()
    fun enrollBiometrics(activity: androidx.fragment.app.FragmentActivity, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        safeFolderViewModel.enrollBiometrics(activity, pin, onSuccess, onError)
    fun unlockWithBiometricsSecure(activity: androidx.fragment.app.FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) =
        safeFolderViewModel.unlockWithBiometricsSecure(activity, onSuccess, onError)
    fun renamePhysicalFile(context: Context, file: ScannedFile, newName: String, onComplete: (Boolean) -> Unit) = fileScannerViewModel.renamePhysicalFile(context, file, newName, onComplete)
    fun copyPhysicalFile(context: Context, source: ScannedFile, destinationFolderUriString: String, onComplete: (Boolean) -> Unit) = fileScannerViewModel.copyPhysicalFile(context, source, destinationFolderUriString, onComplete)
    fun movePhysicalFile(context: Context, source: ScannedFile, destinationFolderUriString: String, onComplete: (Boolean) -> Unit) = fileScannerViewModel.movePhysicalFile(context, source, destinationFolderUriString, onComplete)
    fun deletePhysicalFile(context: Context, file: ScannedFile, onComplete: (Boolean) -> Unit) = fileScannerViewModel.deletePhysicalFile(context, file, onComplete)
    fun deleteRealDuplicates(keepFirst: Boolean = true) = fileScannerViewModel.deleteRealDuplicates(keepFirst)
    fun toggleLocalFileSelection(id: Long) = fileScannerViewModel.toggleLocalFileSelection(id)
    fun selectAllLocalFiles() = fileScannerViewModel.selectAllLocalFiles()
    fun selectAllLocalFiles(filesList: List<FileEntity>) = fileScannerViewModel.selectAllLocalFiles(filesList)
    fun clearLocalSelection() = fileScannerViewModel.clearLocalSelection()
    fun updateLocalSearchQuery(query: String) = fileScannerViewModel.updateLocalSearchQuery(query)
    fun setStorageCleanerActive(active: Boolean) = fileScannerViewModel.setStorageCleanerActive(active)
    fun toggleStorageSelectedFile(id: Long) = fileScannerViewModel.toggleStorageSelectedFile(id)
    fun selectAllStorageFiles(ids: List<Long>) = fileScannerViewModel.selectAllStorageFiles(ids)
    fun selectNoStorageFiles() = fileScannerViewModel.selectNoStorageFiles()
    fun runStorageCleanerScan() = fileScannerViewModel.runStorageCleanerScan()
    fun deleteSelectedStorageFiles() = fileScannerViewModel.deleteSelectedStorageFiles()
    fun openFileInViewer(file: FileEntity) = fileScannerViewModel.openFileInViewer(file)
    fun closeFileInViewer() = fileScannerViewModel.closeFileInViewer()
    fun setDocumentScannerActive(active: Boolean) = fileScannerViewModel.setDocumentScannerActive(active)
    fun setScannedFilter(filter: String) = fileScannerViewModel.setScannedFilter(filter)
    fun setScannedFileName(name: String) = fileScannerViewModel.setScannedFileName(name)
    fun captureDocument() = fileScannerViewModel.captureDocument()
    fun saveScannedDocument(onSavedCompletable: () -> Unit = {}) = fileScannerViewModel.saveScannedDocument(onSavedCompletable)
    fun deleteLocalFilesByIds(ids: Set<Long>) = fileScannerViewModel.deleteLocalFilesByIds(ids)
    fun createFolder(context: Context, folderName: String, onComplete: (Boolean, String?) -> Unit) = fileScannerViewModel.createFolder(context, folderName, onComplete)
    fun selectActiveCloudAccount(email: String) = fileScannerViewModel.selectActiveCloudAccount(email)
    fun addNewCloudAccount(name: String, email: String) = fileScannerViewModel.addNewCloudAccount(name, email)
    fun logoutVirtualAccount(email: String) = fileScannerViewModel.logoutVirtualAccount(email)
    fun toggleCloudFileSelection(id: Long) = fileScannerViewModel.toggleCloudFileSelection(id)
    fun selectAllCloudFiles(filesList: List<FileEntity>) = fileScannerViewModel.selectAllCloudFiles(filesList)
    fun clearCloudSelection() = fileScannerViewModel.clearCloudSelection()
    fun updateCloudSearchQuery(query: String) = fileScannerViewModel.updateCloudSearchQuery(query)
    fun deleteCloudFilesByIds(ids: Set<Long>) = fileScannerViewModel.deleteCloudFilesByIds(ids)
    fun setCloudSubTab(tab: Int) = fileScannerViewModel.setCloudSubTab(tab)

    fun updateTrashCleanupDays(days: Int) = fileScannerViewModel.updateTrashCleanupDays(days)
    fun restoreFile(trash: TrashEntity) = fileScannerViewModel.restoreFile(trash)
    fun restoreAllTrash() = fileScannerViewModel.restoreAllTrash()
    fun deleteForever(trash: TrashEntity) = fileScannerViewModel.deleteForever(trash)
    fun emptyRecycleBin() = fileScannerViewModel.emptyRecycleBin()

    fun toggleDuplicateSelection(uriString: String) = fileScannerViewModel.toggleDuplicateSelection(uriString)
    fun clearDuplicateSelection() = fileScannerViewModel.clearDuplicateSelection()
    fun selectDuplicatesNewest() = fileScannerViewModel.selectDuplicatesNewest()
    fun selectDuplicatesOldest() = fileScannerViewModel.selectDuplicatesOldest()
    fun selectDuplicatesLargest() = fileScannerViewModel.selectDuplicatesLargest()
    fun bulkDeleteSelectedDuplicates() = fileScannerViewModel.bulkDeleteSelectedDuplicates()
    fun moveSelectedDuplicatesToTrash() = fileScannerViewModel.moveSelectedDuplicatesToTrash()
    fun moveSelectedDuplicatesToFolder(destFolder: java.io.File) = fileScannerViewModel.moveSelectedDuplicatesToFolder(destFolder)
    fun getMediaRootDir(): java.io.File = fileScannerViewModel.getMediaRootDir()
    fun getAllSubFolders(): List<java.io.File> = fileScannerViewModel.getAllSubFolders()
    fun moveSelectedLocalFiles(context: android.content.Context, ids: Set<Long>, destFolder: java.io.File) = fileScannerViewModel.moveSelectedLocalFiles(context, ids, destFolder)
    fun copySelectedLocalFiles(context: android.content.Context, ids: Set<Long>, destFolder: java.io.File) = fileScannerViewModel.copySelectedLocalFiles(context, ids, destFolder)

    // Git simulated tracking states & methods
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

    private val _repositoryFiles = MutableStateFlow<List<GitHubRepoFile>>(emptyList())
    val repositoryFiles: StateFlow<List<GitHubRepoFile>> = _repositoryFiles.asStateFlow()

    private val _selectedRepoFile = MutableStateFlow<GitHubRepoFile?>(null)
    val selectedRepoFile: StateFlow<GitHubRepoFile?> = _selectedRepoFile.asStateFlow()

    private val _activeGitWorkspaceSubTab = MutableStateFlow(0)
    val activeGitWorkspaceSubTab: StateFlow<Int> = _activeGitWorkspaceSubTab.asStateFlow()

    private val _repoChatHistory = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val repoChatHistory: StateFlow<List<ChatMessageEntity>> = _repoChatHistory.asStateFlow()

    private val _isRepoChatGenerating = MutableStateFlow(false)
    val isRepoChatGenerating: StateFlow<Boolean> = _isRepoChatGenerating.asStateFlow()

    private val _conflictedFiles = MutableStateFlow<List<ConflictFile>>(emptyList())
    val conflictedFiles: StateFlow<List<ConflictFile>> = _conflictedFiles.asStateFlow()

    private val _isAiResolvingBlock = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isAiResolvingBlock: StateFlow<Map<String, Boolean>> = _isAiResolvingBlock.asStateFlow()

    val githubRepoPath: StateFlow<String> = fileScannerViewModel.githubRepoPath
    val gitSyncState: StateFlow<GitSyncState> = fileScannerViewModel.gitSyncState

    init {
        loadTrackedRepositories()
        clearRepoChatHistory()
        initConflictFiles()
    }

    fun setGitWorkspaceSubTab(tab: Int) { _activeGitWorkspaceSubTab.value = tab }
    fun selectRepoFile(file: GitHubRepoFile?) { _selectedRepoFile.value = file }

    fun clearRepoChatHistory() {
        val repoName = githubRepoPath.value
        _repoChatHistory.value = listOf(
            ChatMessageEntity(
                id = -1,
                messageText = "Hello! I am Gemini, your dedicated Repository Analyst. I've analyzed **$repoName**.\n\nSelect any code file on the left file explorer panel to discuss its architecture, implementation details, or potential improvements directly with me here!",
                sender = "gemini",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun loadTrackedRepositories() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("${getApplication<Application>().packageName}_preferences", android.content.Context.MODE_PRIVATE)
        val repoPaths = sharedPrefs.getStringSet("tracked_repos_set", null) ?: setOf("google/dagger")
        val list = repoPaths.map { path ->
            val lastSynced = sharedPrefs.getLong("repo_sync_time_$path", 0L)
            val status = sharedPrefs.getString("repo_sync_status_$path", "Synced") ?: "Synced"
            TrackedRepository(path, lastSynced, status)
        }
        _trackedRepositories.value = list.sortedBy { it.path }
    }

    fun saveTrackedRepository(path: String, lastSynced: Long, status: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("${getApplication<Application>().packageName}_preferences", android.content.Context.MODE_PRIVATE)
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
        val sharedPrefs = getApplication<Application>().getSharedPreferences("${getApplication<Application>().packageName}_preferences", android.content.Context.MODE_PRIVATE)
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

    fun updateGithubRepoPath(path: String) {
        fileScannerViewModel.updateGithubRepoPath(path)
        fetchGitHubData(path)
    }

    fun fetchGitHubData(path: String) {
        viewModelScope.launch {
            _isGithubLoading.value = true
            _githubError.value = null
            try {
                _repositoryFiles.value = generateSimulatedRepoFiles(path)
                _selectedRepoFile.value = _repositoryFiles.value.firstOrNull { !it.isDirectory }
                clearRepoChatHistory()
                
                val parts = path.split("/")
                val owner = parts.getOrNull(0) ?: "google"
                val repo = parts.getOrNull(1) ?: "dagger"
                _githubIssues.value = try {
                    GitHubRetrofitClient.service.getOpenIssues(owner, repo)
                } catch(e: Exception) {
                    emptyList()
                }
                _githubCommits.value = try {
                    GitHubRetrofitClient.service.getRecentCommits(owner, repo, "2026-01-01T00:00:00Z")
                } catch(e: Exception) {
                    emptyList()
                }
                saveTrackedRepository(path, System.currentTimeMillis(), "Synced")
            } catch (e: Exception) {
                _githubError.value = e.localizedMessage
                saveTrackedRepository(path, System.currentTimeMillis(), "Failed")
            } finally {
                _isGithubLoading.value = false
            }
        }
    }

    fun renameRepoFile(fileId: String, newName: String) {
        _repositoryFiles.value = _repositoryFiles.value.map { file ->
            if (file.id == fileId) {
                val oldPath = file.path
                val newPath = if (oldPath.contains("/")) oldPath.substringBeforeLast("/") + "/" + newName else newName
                val updatedFile = file.copy(name = newName, path = newPath)
                if (_selectedRepoFile.value?.id == fileId) _selectedRepoFile.value = updatedFile
                updatedFile
            } else file
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
            val replyText = if (key.isBlank()) "Gemini API key is not configured." else {
                try {
                    val (reply, _) = repository.callGemini(key, prompt, "You are an expert repository reviewer.")
                    reply
                } catch (e: Exception) {
                    "Analysis Failed: ${e.localizedMessage}"
                }
            }
            val userAnalysisMsg = ChatMessageEntity(messageText = "Run AI Analysis on file: `${file.name}`", sender = "user", timestamp = System.currentTimeMillis())
            val geminiAnalysisMsg = ChatMessageEntity(messageText = "### AI Code Analysis for `${file.name}`\n\n$replyText", sender = "gemini", timestamp = System.currentTimeMillis())
            _repoChatHistory.value = _repoChatHistory.value + listOf(userAnalysisMsg, geminiAnalysisMsg)
            _isRepoChatGenerating.value = false
            onAnalysisResult(replyText)
        }
    }

    fun sendRepoChatMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(messageText = message, sender = "user", timestamp = System.currentTimeMillis())
            _repoChatHistory.value = _repoChatHistory.value + userMsg
            _isRepoChatGenerating.value = true
            val activeFile = _selectedRepoFile.value
            val fileContext = if (activeFile != null) "The user is viewing '${activeFile.path}' containing:\n```\n${activeFile.content}\n```\n" else "The user is viewing repository directory.\n"
            val fullHistoryPrompt = _repoChatHistory.value.takeLast(6).joinToString("\n") { "${it.sender}: ${it.messageText}" }
            val prompt = """
                You are Gemini, an expert software architect and repository advisor.
                $fileContext
                Here is conversation:
                $fullHistoryPrompt
                Please provide a helpful, concise response.
            """.trimIndent()
            val key = getActiveApiKey()
            if (key.isBlank()) {
                val errorMsg = ChatMessageEntity(messageText = "Gemini API key is not configured.", sender = "gemini", timestamp = System.currentTimeMillis())
                _repoChatHistory.value = _repoChatHistory.value + errorMsg
                _isRepoChatGenerating.value = false
                return@launch
            }
            try {
                val (reply, _) = repository.callGemini(key, prompt, "You are an expert GitHub assistant.", highThinkingEnabled.value)
                val geminiMsg = ChatMessageEntity(messageText = reply, sender = "gemini", timestamp = System.currentTimeMillis())
                _repoChatHistory.value = _repoChatHistory.value + geminiMsg
            } catch(e: Exception) {
                _repoChatHistory.value = _repoChatHistory.value + ChatMessageEntity(messageText = "Failed: ${e.localizedMessage}", sender = "gemini", timestamp = System.currentTimeMillis())
            } finally {
                _isRepoChatGenerating.value = false
            }
        }
    }

    fun generateSimulatedRepoFiles(repoPath: String): List<GitHubRepoFile> {
        val parts = repoPath.split("/")
        val owner = parts.getOrNull(0) ?: "google"
        val repo = parts.getOrNull(1) ?: "dagger"
        val files = mutableListOf<GitHubRepoFile>()
        fun addF(path: String, isDir: Boolean, sizeKB: Long, content: String) {
            files.add(GitHubRepoFile(id = path.hashCode().toString(), path = path, name = path.substringAfterLast("/"), isDirectory = isDir, size = sizeKB * 1024L, content = content, parentPath = if (path.contains("/")) path.substringBeforeLast("/") else ""))
        }
        addF("src", true, 0, "")
        addF("src/main", true, 0, "")
        addF("src/main/java", true, 0, "")
        addF("README.md", false, 4, "# $repo maintained by $owner.\nSynchronized via Gemini.")
        addF("build.gradle.kts", false, 3, "plugins {\n  kotlin(\"jvm\") version \"1.9.22\"\n}")
        return files
    }

    fun initConflictFiles() {
        _conflictedFiles.value = listOf(
            ConflictFile(
                id = "Theme.kt",
                name = "Theme.kt",
                path = "src/main/java/com/vvf/smartfilemanager/ui/Theme.kt",
                blocks = listOf(
                    ConflictBlock(
                        id = "block_theme_colors",
                        lineStart = 45,
                        currentCode = "val LightColorScheme = lightColorScheme(\n    primary = Color(0xFF6200EE),\n    secondary = Color(0xFF03DAC6)\n)",
                        incomingCode = "val LightColorScheme = lightColorScheme(\n    primary = Color(0xFF006C50),\n    secondary = Color(0xFF006A6B)\n)",
                        description = "LightColorScheme palette color definitions conflict between main and feature branch."
                    )
                )
            )
        )
    }

    fun resolveConflictBlock(fileId: String, blockId: String, choice: String, customCode: String? = null) {
        _conflictedFiles.value = _conflictedFiles.value.map { file ->
            if (file.id == fileId) {
                val updatedBlocks = file.blocks.map { block ->
                    if (block.id == blockId) {
                        val resolved = when (choice) {
                            "ours" -> block.currentCode
                            "theirs" -> block.incomingCode
                            "both" -> block.currentCode + "\n\n" + block.incomingCode
                            else -> customCode ?: block.currentCode
                        }
                        block.copy(resolutionChoice = choice, resolvedCode = resolved)
                    } else block
                }
                file.copy(blocks = updatedBlocks, isFullyResolved = updatedBlocks.all { it.resolutionChoice != null })
            } else file
        }
    }

    fun resolveConflictBlockAI(fileId: String, blockId: String) {
        val file = _conflictedFiles.value.find { id -> id.id == fileId } ?: return
        val block = file.blocks.find { id -> id.id == blockId } ?: return
        viewModelScope.launch {
            _isAiResolvingBlock.value = _isAiResolvingBlock.value + (blockId to true)
            val prompt = """
                Resolve this git merge conflict block:
                Current: ${block.currentCode}
                Incoming: ${block.incomingCode}
                Provide ONLY the resolved code block.
            """.trimIndent()
            val key = getActiveApiKey()
            val resolvedText = if (key.isBlank()) block.currentCode else {
                try {
                    val (reply, _) = repository.callGemini(key, prompt, "You are an expert compiler resolving merge conflicts.")
                    reply
                } catch(e: Exception) {
                    block.currentCode
                }
            }
            resolveConflictBlock(fileId, blockId, "ai", resolvedText)
            _isAiResolvingBlock.value = _isAiResolvingBlock.value - blockId
        }
    }

    fun markFileAsCompleted(fileId: String) {
        _conflictedFiles.value = _conflictedFiles.value.filter { it.id != fileId }
    }

    fun resetConflictsDemo() {
        initConflictFiles()
    }

    // Delegated AI Search States & Methods
    val cloudScanQuery: StateFlow<String> = aiSearchViewModel.cloudScanQuery
    val isCloudScanning: StateFlow<Boolean> = aiSearchViewModel.isCloudScanning
    val cloudScanProgress: StateFlow<Float> = aiSearchViewModel.cloudScanProgress
    val semanticScanResults: StateFlow<List<Pair<Long, Int>>> = aiSearchViewModel.semanticScanResults
    val chatHistory: StateFlow<List<ChatMessageEntity>> = aiSearchViewModel.chatHistory
    val chatInput: StateFlow<String> = aiSearchViewModel.chatInput
    val isGeminiGenerating: StateFlow<Boolean> = aiSearchViewModel.isGeminiGenerating
    val activeThinkingProcess: StateFlow<String?> = aiSearchViewModel.activeThinkingProcess
    val isReadmeGenerating: StateFlow<Boolean> = aiSearchViewModel.isReadmeGenerating
    val generatedReadme: StateFlow<String?> = aiSearchViewModel.generatedReadme
    val readmeThinkingProcess: StateFlow<String?> = aiSearchViewModel.readmeThinkingProcess

    fun updateCloudScanQuery(query: String) = aiSearchViewModel.updateCloudScanQuery(query)
    fun runCloudSemanticScan() = aiSearchViewModel.runCloudSemanticScan(cloudFiles.value) { getActiveApiKey() }
    fun updateChatInput(text: String) = aiSearchViewModel.updateChatInput(text)
    fun sendChatMessage() = aiSearchViewModel.sendChatMessage(activeAccountEmail.value, connectedAccounts.value, cloudFiles.value, { getActiveApiKey() }, { highThinkingEnabled.value }, { formatSize(it) })
    fun clearChatHistory() = aiSearchViewModel.clearChatHistory()
    fun generateProjectReadme() = aiSearchViewModel.generateProjectReadme(localNonSafeFilesCount.value, safeFilesCount.value, duplicateFilesCount.value, githubRepoPath.value, connectedAccounts.value.size, { getActiveApiKey() }, { highThinkingEnabled.value })
    fun clearGeneratedReadme() = aiSearchViewModel.clearGeneratedReadme()

    fun triggerFetchSync() = fileScannerViewModel.triggerFetchSync()
    fun triggerSimulateBehind() = fileScannerViewModel.triggerSimulateBehind()
    fun triggerSimulateAhead() = fileScannerViewModel.triggerSimulateAhead()
    fun triggerGitPull() = fileScannerViewModel.triggerGitPull()
    fun triggerGitPush() = fileScannerViewModel.triggerGitPush()
    fun formatSize(bytes: Long): String = fileScannerViewModel.formatSize(bytes)
    fun formatDate(timestamp: Long): String = fileScannerViewModel.formatDate(timestamp)
}
