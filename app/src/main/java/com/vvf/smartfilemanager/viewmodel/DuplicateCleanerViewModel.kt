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
import kotlinx.coroutines.launch

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

    val scannedLargeTempFiles: StateFlow<List<FileEntity>> = repository.getLargeTempFiles(limit = 1000)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun runDuplicateScanner() {
        viewModelScope.launch {
            _duplicateScannerState.value = ScannerState.Scanning
            _duplicateScanProgress.value = 0f
            Log.d("DuplicateCleanerVM", "Starting duplicate files scan [StructuredLog: { event: \"duplicate_scan_start\" }]")
            while (_duplicateScanProgress.value < 1.0f) {
                delay(120)
                _duplicateScanProgress.value = minOf(1.0f, _duplicateScanProgress.value + 0.1f)
            }
            _duplicateScanProgress.value = 1.0f
            _duplicateScannerState.value = ScannerState.Finished
            Log.i("DuplicateCleanerVM", "Duplicate files scan finished [StructuredLog: { event: \"duplicate_scan_finish\", found: ${scannedDuplicates.value.size} }]")
        }
    }

    fun dismissDuplicateScanner() {
        _duplicateScannerState.value = ScannerState.Idle
        _duplicateScanProgress.value = 0f
    }

    fun deleteSelectedDuplicates() {
        viewModelScope.launch {
            Log.d("DuplicateCleanerVM", "Deleting selected duplicates [StructuredLog: { event: \"duplicate_delete_start\" }]")
            duplicateFiles.value.filter { it.isDuplicate }.forEach { file ->
                repository.deleteFile(file)
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
