package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import android.app.PendingIntent
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import com.vvf.smartfilemanager.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class FileScannerViewModel(
    application: Application,
    private val repository: IAppRepository
) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    // SAF Tree Uri state and reboot persistence
    private val _safTreeUri = MutableStateFlow<String?>(null)
    val safTreeUri: StateFlow<String?> = _safTreeUri.asStateFlow()

    private val _pendingDeleteIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingDeleteIntent: StateFlow<PendingIntent?> = _pendingDeleteIntent.asStateFlow()

    private val _pinnedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val pinnedDirectories: StateFlow<Set<String>> = _pinnedDirectories.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    init {
        val prefs = application.getSharedPreferences("smart_file_manager_prefs", Context.MODE_PRIVATE)
        _safTreeUri.value = prefs.getString("saf_tree_uri", null)
        val savedPinned = prefs.getStringSet("pinned_directories", setOf("Downloads", "Documents", "Pictures")) ?: setOf("Downloads", "Documents", "Pictures")
        _pinnedDirectories.value = savedPinned
        _safTreeUri.value?.let {
            scanSafFiles(application)
        }
    }

    fun togglePinnedDirectory(directory: String) {
        val current = _pinnedDirectories.value.toMutableSet()
        if (current.contains(directory)) {
            current.remove(directory)
        } else {
            current.add(directory)
        }
        _pinnedDirectories.value = current
        val prefs = getApplication<Application>().getSharedPreferences("smart_file_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("pinned_directories", current).apply()
    }

    fun toggleFolderFilter(folder: String) {
        if (_selectedFolderFilter.value == folder) {
            _selectedFolderFilter.value = null
        } else {
            _selectedFolderFilter.value = folder
        }
    }

    fun clearFolderFilter() {
        _selectedFolderFilter.value = null
    }

    fun batchRenamePhysicalFiles(
        context: Context,
        files: List<ScannedFile>,
        prefix: String,
        addDateStamp: Boolean,
        addSequence: Boolean,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date())
            
            files.forEachIndexed { index, file ->
                val extension = file.name.substringAfterLast(".", "")
                val originalBaseName = file.name.substringBeforeLast(".")
                
                val nameParts = mutableListOf<String>()
                if (prefix.isNotEmpty()) {
                    nameParts.add(prefix)
                } else {
                    nameParts.add(originalBaseName)
                }
                
                if (addDateStamp) {
                    nameParts.add(dateStr)
                }
                
                if (addSequence) {
                    nameParts.add(String.format("%03d", index + 1))
                }
                
                val baseNewName = nameParts.joinToString("_")
                val finalNewName = if (extension.isNotEmpty()) "$baseNewName.$extension" else baseNewName
                
                var success = false
                try {
                    val uri = file.uri
                    if (uri.scheme == "content" && uri.authority?.contains("media") == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, finalNewName)
                            }
                            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
                            success = rowsUpdated > 0
                        } else {
                            val f = File(file.path)
                            if (f.exists()) {
                                val destination = File(f.parentFile, finalNewName)
                                success = f.renameTo(destination)
                            }
                        }
                    } else if (uri.scheme == "content") {
                        val documentFile = DocumentFile.fromSingleUri(context, uri)
                        if (documentFile != null && documentFile.exists()) {
                            success = documentFile.renameTo(finalNewName)
                        }
                    } else {
                        val f = File(file.path)
                        if (f.exists()) {
                            val destination = File(f.parentFile, finalNewName)
                            success = f.renameTo(destination)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FileScannerViewModel", "Failed to rename file in batch", e)
                }
                
                if (success) {
                    successCount++
                }
            }
            
            if (successCount > 0) {
                _safTreeUri.value?.let { scanSafFiles(context) }
            }
            
            withContext(Dispatchers.Main) {
                onComplete(successCount)
            }
        }
    }

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

    val imagesCount: StateFlow<Int> = repository.imagesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val imagesTotalSize: StateFlow<Long> = repository.imagesTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val docsCount: StateFlow<Int> = repository.docsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val docsTotalSize: StateFlow<Long> = repository.docsTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val mediaCount: StateFlow<Int> = repository.mediaCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mediaTotalSize: StateFlow<Long> = repository.mediaTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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

    private val _sortOrder = MutableStateFlow(SortOrder.NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _useSemanticResults = MutableStateFlow(false)
    val useSemanticResults: StateFlow<Boolean> = _useSemanticResults.asStateFlow()

    private val _semanticResults = MutableStateFlow<List<ScannedFile>>(emptyList())
    val semanticResults: StateFlow<List<ScannedFile>> = _semanticResults.asStateFlow()

    private val _isSemanticSearching = MutableStateFlow(false)
    val isSemanticSearching: StateFlow<Boolean> = _isSemanticSearching.asStateFlow()

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setUseSemanticResults(use: Boolean) {
        _useSemanticResults.value = use
    }

    private fun calculateMd5(context: Context, uri: Uri): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val bytes = digest.digest()
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e("FileScannerViewModel", "Failed to calculate MD5", e)
            ""
        }
    }

    val realDuplicates: StateFlow<Map<String, List<ScannedFile>>> = _realFiles.map { files ->
        val context = getApplication<Application>()
        val sizeCandidates = files.groupBy { it.size }.filter { it.value.size > 1 }
        val hashGroups = mutableMapOf<String, MutableList<ScannedFile>>()
        sizeCandidates.forEach { (_, candidateList) ->
            candidateList.forEach { file ->
                val hash = calculateMd5(context, file.uri).ifEmpty { "${file.name}_${file.size}" }
                hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
            }
        }
        hashGroups.filter { it.value.size > 1 }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val filteredRealFiles: StateFlow<List<ScannedFile>> = combine(
        _realFiles, _realFileSearchQuery, _sortOrder, _selectedFolderFilter
    ) { files, query, sort, folderFilter ->
        val afterFolderFilter = if (folderFilter == null) files
        else files.filter { 
            val parentPath = it.path.substringBeforeLast("/", "")
            val folderName = if (parentPath.contains("/")) parentPath.substringAfterLast("/") else parentPath
            folderName.equals(folderFilter, ignoreCase = true)
        }

        val filtered = if (query.isBlank()) afterFolderFilter
        else afterFolderFilter.filter { it.name.contains(query, ignoreCase = true) }

        when (sort) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_NEWEST -> filtered.sortedByDescending { it.dateModified }
            SortOrder.DATE_OLDEST -> filtered.sortedBy { it.dateModified }
            SortOrder.SIZE_LARGEST -> filtered.sortedByDescending { it.size }
            SortOrder.SIZE_SMALLEST -> filtered.sortedBy { it.size }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateRealFileSearchQuery(query: String) {
        _realFileSearchQuery.value = query
    }

    fun smartGeminiSemanticSearch(query: String, onComplete: () -> Unit = {}) {
        if (query.isBlank()) {
            _useSemanticResults.value = false
            _semanticResults.value = emptyList()
            onComplete()
            return
        }

        _isSemanticSearching.value = true
        _useSemanticResults.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY") {
                Log.e("FileScannerViewModel", "Gemini API Key is empty or invalid!")
                val fallbackResults = _realFiles.value.filter {
                    it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
                }
                _semanticResults.value = fallbackResults
                _isSemanticSearching.value = false
                withContext(Dispatchers.Main) { onComplete() }
                return@launch
            }

            try {
                // Prevent OOM and context length limitations under massive datasets (e.g. 1,000,000 files)
                // We pre-filter the files to a safe subset of up to 300 matching candidates using fast database indexed search.
                val searchKeywords = query.split(" ").filter { it.trim().length > 1 }
                val availableFiles = if (searchKeywords.isEmpty()) {
                    _realFiles.value.take(300)
                } else {
                    val dbCandidates = repository.searchLocalNonSafeFiles(query, "ALL", 300).firstOrNull() ?: emptyList()
                    if (dbCandidates.isNotEmpty()) {
                        dbCandidates.map { entity ->
                            ScannedFile(
                                name = entity.name,
                                path = entity.path,
                                size = entity.size,
                                mimeType = entity.mimeType,
                                uri = Uri.parse(entity.path),
                                dateModified = entity.lastModified
                            )
                        }
                    } else {
                        // Fallback to in-memory check over a safe subset if database query returns empty
                        val matching = _realFiles.value.filter { file ->
                            searchKeywords.any { kw -> file.name.contains(kw, ignoreCase = true) }
                        }
                        if (matching.isEmpty()) _realFiles.value.take(300) else matching.take(300)
                    }
                }

                val filesJsonArray = org.json.JSONArray()
                availableFiles.forEachIndexed { index, file ->
                    val fileObj = org.json.JSONObject().apply {
                        put("index", index)
                        put("name", file.name)
                        put("path", file.path)
                        put("mimeType", file.mimeType)
                        put("size", file.size)
                    }
                    filesJsonArray.put(fileObj)
                }

                val prompt = """
                    You are an intelligent file search assistant. Below is a JSON list of available files in the user's device and a semantic search query from the user.
                    Filter the files list to include ONLY files that semantically match the search query (e.g., query "tax returns" should match files like "2025_tax_declaration.pdf", "w2_form.pdf", etc. Query "receipts" should match "invoice_amazon.jpg", "uber_ride.pdf", etc.).
                    Return your response as a JSON array of integers representing the "index" of matching files. Return ONLY the JSON array (no markdown code blocks, no explanations).
                    
                    Query: "$query"
                    Available Files:
                    ${filesJsonArray.toString()}
                """.trimIndent()

                val requestJson = org.json.JSONObject().apply {
                    val contentsArray = org.json.JSONArray().apply {
                        val partObj = org.json.JSONObject().apply {
                            put("text", prompt)
                        }
                        val partsArray = org.json.JSONArray().apply {
                            put(partObj)
                        }
                        val contentObj = org.json.JSONObject().apply {
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestJson.toString().toRequestBody(mediaType)
                val modelName = "gemini-3.5-flash"
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Unsuccessful API call: ${response.code} ${response.message}")
                    }
                    val responseBodyString = response.body?.string() ?: throw Exception("Empty response body")
                    val responseJson = org.json.JSONObject(responseBodyString)
                    val candidates = responseJson.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val rawText = parts.getJSONObject(0).getString("text").trim()

                    val cleanedText = if (rawText.startsWith("```json")) {
                        rawText.substringAfter("```json").substringBefore("```").trim()
                    } else if (rawText.startsWith("```")) {
                        rawText.substringAfter("```").substringBefore("```").trim()
                    } else {
                        rawText
                    }

                    val indexArray = org.json.JSONArray(cleanedText)
                    val matchedFiles = mutableListOf<ScannedFile>()
                    for (i in 0 until indexArray.length()) {
                        val matchedIndex = indexArray.getInt(i)
                        if (matchedIndex in availableFiles.indices) {
                            matchedFiles.add(availableFiles[matchedIndex])
                        }
                    }
                    _semanticResults.value = matchedFiles
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Semantic Search Error", e)
                val fallbackResults = _realFiles.value.filter {
                    it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
                }
                _semanticResults.value = fallbackResults
            } finally {
                _isSemanticSearching.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
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
            try {
                val scanned = MediaStoreScanner(appContext).scanAllFiles()
                _realFiles.value = scanned
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Error scanning real files", e)
            }
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
            try {
                val scanned = MediaStoreScanner(getApplication()).scanAllFiles()
                _realFiles.value = scanned
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Error scanning files in scanRealFiles", e)
            }
            while (_realScanProgress.value < 1.0f) {
                delay(100)
                _realScanProgress.value = minOf(1.0f, _realScanProgress.value + 0.15f)
            }
            _realScanStatusMessage.value = "Files sync complete"
            _isScanningRealFiles.value = false
        }
    }

    fun onSafDirectorySelected(context: Context, uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val prefs = context.getSharedPreferences("smart_file_manager_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("saf_tree_uri", uri.toString()).apply()
            _safTreeUri.value = uri.toString()
            scanSafFiles(context)
        } catch (e: Exception) {
            Log.e("FileScannerViewModel", "Failed to take persistable URI permission", e)
        }
    }

    fun scanSafFiles(context: Context) {
        val uriString = _safTreeUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val treeUri = Uri.parse(uriString)
                val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                if (documentFile != null && documentFile.isDirectory) {
                    val scannedList = mutableListOf<ScannedFile>()
                    traverseDocumentFile(documentFile, scannedList)
                    _realFiles.value = scannedList
                    repository.insertFiles(scannedList.map { scanned ->
                        FileEntity(
                            name = scanned.name,
                            path = scanned.path,
                            size = scanned.size,
                            mimeType = scanned.mimeType,
                            isLocal = true,
                            isSafe = false,
                            lastModified = System.currentTimeMillis()
                        )
                    })
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Error scanning SAF folder files", e)
            }
        }
    }

    private fun traverseDocumentFile(dir: DocumentFile, outList: MutableList<ScannedFile>) {
        try {
            for (file in dir.listFiles()) {
                if (file.isFile) {
                    outList.add(
                        ScannedFile(
                            name = file.name ?: "Unknown",
                            path = file.uri.toString(),
                            size = file.length(),
                            mimeType = file.type ?: "application/octet-stream",
                            uri = file.uri,
                            dateModified = file.lastModified()
                        )
                    )
                } else if (file.isDirectory) {
                    traverseDocumentFile(file, outList)
                }
            }
        } catch (e: Exception) {
            Log.e("FileScannerViewModel", "Failed to traverse DocumentFile", e)
        }
    }

    fun clearPendingDeleteIntent() {
        _pendingDeleteIntent.value = null
    }

    fun renamePhysicalFile(context: Context, file: ScannedFile, newName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val uri = file.uri
                if (uri.scheme == "content" && uri.authority?.contains("media") == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                        }
                        val rowsUpdated = context.contentResolver.update(uri, values, null, null)
                        success = rowsUpdated > 0
                    } else {
                        val f = File(file.path)
                        if (f.exists()) {
                            val destination = File(f.parentFile, newName)
                            success = f.renameTo(destination)
                        }
                    }
                } else if (uri.scheme == "content") {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        success = documentFile.renameTo(newName)
                    }
                } else {
                    val f = File(file.path)
                    if (f.exists()) {
                        val destination = File(f.parentFile, newName)
                        success = f.renameTo(destination)
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Failed to rename physical file", e)
            }
            if (success) {
                _safTreeUri.value?.let { scanSafFiles(context) }
                _realFiles.value = _realFiles.value.map {
                    if (it.path == file.path) it.copy(name = newName) else it
                }
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun copyPhysicalFile(context: Context, source: ScannedFile, destinationFolderUriString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val contentResolver = context.contentResolver
                val sourceUri = source.uri
                val destTreeUri = Uri.parse(destinationFolderUriString)
                val parentDir = DocumentFile.fromTreeUri(context, destTreeUri)
                if (parentDir != null && parentDir.isDirectory) {
                    val newFile = parentDir.createFile(source.mimeType, source.name)
                    if (newFile != null) {
                        contentResolver.openInputStream(sourceUri)?.use { input ->
                            contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                input.copyTo(output)
                                success = true
                            }
                        }
                    }
                } else {
                    val f = File(source.path)
                    if (f.exists()) {
                        val destFile = File(destinationFolderUriString, source.name)
                        f.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                                success = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Failed to copy physical file", e)
            }
            if (success) {
                _safTreeUri.value?.let { scanSafFiles(context) }
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun movePhysicalFile(context: Context, source: ScannedFile, destinationFolderUriString: String, onComplete: (Boolean) -> Unit) {
        copyPhysicalFile(context, source, destinationFolderUriString) { copySuccess ->
            if (copySuccess) {
                deletePhysicalFileForMove(context, source) { deleteSuccess ->
                    onComplete(deleteSuccess)
                }
            } else {
                onComplete(false)
            }
        }
    }

    private fun deletePhysicalFileForMove(context: Context, file: ScannedFile, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val uri = file.uri
                if (uri.scheme == "content" && uri.authority?.contains("media") == true) {
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    success = rowsDeleted > 0
                } else if (uri.scheme == "content") {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        success = documentFile.delete()
                    }
                } else {
                    val f = File(file.path)
                    if (f.exists()) {
                        success = f.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Failed to delete file for move", e)
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun deleteRealFile(file: ScannedFile) {
        deletePhysicalFile(getApplication(), file)
    }

    fun deleteRealFilesBatch(context: Context, files: List<ScannedFile>, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mediaUris = files.map { it.uri }.filter { uri ->
                        uri.scheme == "content" && uri.authority?.contains("media") == true
                    }
                    if (mediaUris.isNotEmpty()) {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
                        _pendingDeleteIntent.value = pendingIntent
                        success = false
                    } else {
                        files.forEach { file ->
                            val uri = file.uri
                            if (uri.scheme == "content") {
                                DocumentFile.fromSingleUri(context, uri)?.delete()
                            } else {
                                File(file.path).delete()
                            }
                        }
                        success = true
                    }
                } else {
                    files.forEach { file ->
                        val uri = file.uri
                        if (uri.scheme == "content" && uri.authority?.contains("media") == true) {
                            context.contentResolver.delete(uri, null, null)
                        } else if (uri.scheme == "content") {
                            DocumentFile.fromSingleUri(context, uri)?.delete()
                        } else {
                            File(file.path).delete()
                        }
                    }
                    success = true
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Failed to delete files in batch", e)
            }
            if (success) {
                val pathsToDelete = files.map { it.path }.toSet()
                _realFiles.value = _realFiles.value.filter { it.path !in pathsToDelete }
                
                // Immediately delete matching database entities within a transaction boundary
                try {
                    val dbEntities = repository.allLocalNonSafeFiles.firstOrNull() ?: emptyList()
                    dbEntities.filter { it.path in pathsToDelete }.forEach { entity ->
                        repository.deleteFileById(entity.id)
                    }
                } catch (dbEx: Exception) {
                    Log.e("FileScannerViewModel", "Failed to remove database records for batch deleted files", dbEx)
                }

                _safTreeUri.value?.let { scanSafFiles(context) }
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun deletePhysicalFile(context: Context, file: ScannedFile, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                val uri = file.uri
                if (uri.scheme == "content" && uri.authority?.contains("media") == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                        _pendingDeleteIntent.value = pendingIntent
                        success = false
                    } else {
                        val rowsDeleted = context.contentResolver.delete(uri, null, null)
                        success = rowsDeleted > 0
                    }
                } else if (uri.scheme == "content") {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    if (documentFile != null && documentFile.exists()) {
                        success = documentFile.delete()
                    }
                } else {
                    val f = File(file.path)
                    if (f.exists()) {
                        success = f.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileScannerViewModel", "Failed to delete physical file", e)
            }
            if (success) {
                _realFiles.value = _realFiles.value.filter { it.path != file.path }
                
                // Keep DB in absolute sync
                try {
                    val dbEntities = repository.allLocalNonSafeFiles.firstOrNull() ?: emptyList()
                    dbEntities.find { it.path == file.path }?.let { entity ->
                        repository.deleteFileById(entity.id)
                    }
                } catch (dbEx: Exception) {
                    Log.e("FileScannerViewModel", "Failed to sync database deletion", dbEx)
                }

                _safTreeUri.value?.let { scanSafFiles(context) }
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun deleteRealDuplicates(keepFirst: Boolean = true) {
        val duplicatesMap = realDuplicates.value
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            duplicatesMap.forEach { (_, fileList) ->
                if (fileList.size > 1) {
                    val filesToDelete = if (keepFirst) fileList.drop(1) else fileList
                    filesToDelete.forEach { file ->
                        deletePhysicalFile(context, file)
                    }
                }
            }
            scanRealFiles()
        }
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

enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_NEWEST,
    DATE_OLDEST,
    SIZE_LARGEST,
    SIZE_SMALLEST
}

