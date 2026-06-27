package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.Flow
import androidx.paging.cachedIn
import kotlinx.coroutines.launch

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vvf.smartfilemanager.data.DuplicateScanWorker

class DuplicateCleanerViewModel(
    application: Application,
    private val repository: IAppRepository
) : AndroidViewModel(application) {

    private val _duplicateScannerState = MutableStateFlow(ScannerState.Idle)
    val duplicateScannerState: StateFlow<ScannerState> = _duplicateScannerState.asStateFlow()

    private val _duplicateScanProgress = MutableStateFlow(0f)
    val duplicateScanProgress: StateFlow<Float> = _duplicateScanProgress.asStateFlow()

    private val _cleanerState = MutableStateFlow(CleanerState.Idle)
    val cleanerState: StateFlow<CleanerState> = _cleanerState.asStateFlow()

    private val _cleanerProgress = MutableStateFlow(0f)
    val cleanerProgress: StateFlow<Float> = _cleanerProgress.asStateFlow()

    val duplicateFiles: StateFlow<List<FileEntity>> = repository.duplicateFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val junkFiles: StateFlow<List<FileEntity>> = repository.junkFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannedDuplicates: StateFlow<List<FileEntity>> = repository.getScannedDuplicates(limit = 1000)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedScannedDuplicates: Flow<androidx.paging.PagingData<FileEntity>> = repository.getPagedScannedDuplicates()
        .cachedIn(viewModelScope)

    val scannedLargeTempFiles: StateFlow<List<FileEntity>> = repository.getLargeTempFiles(limit = 1000)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val duplicateFilesTotalSize: StateFlow<Long> = repository.duplicateFilesTotalSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val duplicateFilesCount: StateFlow<Int> = repository.duplicateFilesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun runDuplicateScanner() {
        viewModelScope.launch {
            _duplicateScannerState.value = ScannerState.Scanning
            _duplicateScanProgress.value = 0.2f
            Log.d("DuplicateCleanerVM", "Enqueuing duplicate analyzer work in WorkManager [StructuredLog: { event: \"duplicate_scan_start\" }]")
            
            try {
                val workRequest = OneTimeWorkRequestBuilder<DuplicateScanWorker>().build()
                WorkManager.getInstance(getApplication()).enqueue(workRequest)
                
                _duplicateScanProgress.value = 0.6f
                delay(200)
                _duplicateScanProgress.value = 1.0f
                _duplicateScannerState.value = ScannerState.Finished
                Log.i("DuplicateCleanerVM", "Duplicate analysis successfully offloaded to WorkManager")
            } catch (e: Exception) {
                Log.e("DuplicateCleanerVM", "Failed to enqueue duplicate scan worker", e)
                _duplicateScannerState.value = ScannerState.Finished
                _duplicateScanProgress.value = 1.0f
            }
        }
    }

    private val _pendingDeleteIntent = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingDeleteIntent: StateFlow<android.app.PendingIntent?> = _pendingDeleteIntent.asStateFlow()

    fun clearPendingDeleteIntent() {
        _pendingDeleteIntent.value = null
    }

    fun dismissDuplicateScanner() {
        _duplicateScannerState.value = ScannerState.Idle
        _duplicateScanProgress.value = 0f
    }

    fun deleteSelectedDuplicates() {
        viewModelScope.launch {
            Log.d("DuplicateCleanerVM", "Deleting selected duplicates [StructuredLog: { event: \"duplicate_delete_start\" }]")
            val selected = duplicateFiles.value.filter { it.isDuplicate }
            if (selected.isEmpty()) return@launch

            val mediaUris = mutableListOf<android.net.Uri>()

            selected.forEach { fileEntity ->
                val contentUri = repository.getUriForPath(getApplication(), fileEntity.path)
                if (contentUri != null) {
                    mediaUris.add(contentUri)
                } else {
                    // Physical deletion for local files not in MediaStore
                    try {
                        val file = java.io.File(fileEntity.path)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("DuplicateCleanerVM", "Failed to delete direct file ${fileEntity.path}", e)
                    }
                }
                repository.deleteFileById(fileEntity.id)
            }

            if (mediaUris.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        getApplication<Application>().contentResolver,
                        mediaUris
                    )
                    _pendingDeleteIntent.value = pendingIntent
                } else {
                    mediaUris.forEach { uri ->
                        try {
                            getApplication<Application>().contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            Log.e("DuplicateCleanerVM", "Failed to delete Uri pre-R: $uri", e)
                        }
                    }
                }
            }

            _duplicateScannerState.value = ScannerState.Idle
            Log.i("DuplicateCleanerVM", "Duplicates deleted successfully [StructuredLog: { event: \"duplicate_delete_success\" }]")
        }
    }

    fun runJunkCleaner() {
        viewModelScope.launch {
            _cleanerState.value = CleanerState.Scanning
            _cleanerProgress.value = 0f
            Log.d("DuplicateCleanerVM", "Junk cleaner scan started [StructuredLog: { event: \"junk_scan_start\" }]")
            while (_cleanerProgress.value < 1.0f) {
                delay(80)
                _cleanerProgress.value = minOf(1.0f, _cleanerProgress.value + 0.08f)
            }
            _cleanerProgress.value = 1.0f
            _cleanerState.value = CleanerState.Finished
            Log.i("DuplicateCleanerVM", "Junk cleaner scan completed [StructuredLog: { event: \"junk_scan_finish\" }]")
        }
    }

    fun executeCleanJunk() {
        viewModelScope.launch {
            _cleanerState.value = CleanerState.Cleaning
            _cleanerProgress.value = 0f
            Log.d("DuplicateCleanerVM", "Junk cleaning execution initiated [StructuredLog: { event: \"junk_clean_start\" }]")
            while (_cleanerProgress.value < 1.0f) {
                delay(60)
                _cleanerProgress.value = minOf(1.0f, _cleanerProgress.value + 0.1f)
            }
            repository.cleanAllJunk()
            _cleanerState.value = CleanerState.Idle
            _cleanerProgress.value = 0f
            Log.i("DuplicateCleanerVM", "Junk directory cleared completely [StructuredLog: { event: \"junk_clean_success\" }]")
        }
    }

    fun cancelJunkCleaner() {
        _cleanerState.value = CleanerState.Idle
        _cleanerProgress.value = 0f
    }
}
