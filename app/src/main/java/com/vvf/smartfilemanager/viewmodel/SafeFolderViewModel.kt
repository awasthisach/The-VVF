package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import androidx.paging.cachedIn
import kotlinx.coroutines.launch

class SafeFolderViewModel(
    application: Application,
    private val repository: IAppRepository
) : AndroidViewModel(application) {

    private val _safeFolderState = MutableStateFlow<SafeFolderState>(SafeFolderState.Locked)
    val safeFolderState: StateFlow<SafeFolderState> = _safeFolderState.asStateFlow()

    private val _pinSetupStep = MutableStateFlow("ENTER_PIN") // ENTER_PIN, CONFIRM_PIN, VERIFY
    val pinSetupStep: StateFlow<String> = _pinSetupStep.asStateFlow()

    private val _tempPinForSetup = MutableStateFlow("")
    private val _inputPinBuffer = MutableStateFlow("")
    val inputPinBuffer: StateFlow<String> = _inputPinBuffer.asStateFlow()

    private val _pinErrorMessage = MutableStateFlow<String?>(null)
    val pinErrorMessage: StateFlow<String?> = _pinErrorMessage.asStateFlow()

    val allSafeFiles: StateFlow<List<FileEntity>> = repository.allSafeFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedSafeFiles: Flow<androidx.paging.PagingData<FileEntity>> = repository.getPagedSafeFiles()
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            checkSafeFolderPinState()
        }
    }

    suspend fun checkSafeFolderPinState() {
        val hasPin = repository.getIsPinSet()
        _safeFolderState.value = if (hasPin) SafeFolderState.Locked else SafeFolderState.PinSetupNeeded
        Log.d("SafeFolderViewModel", "Initial PIN state check. hasPin = $hasPin [StructuredLog: { event: \"safe_pin_state\", has_pin: $hasPin }]")
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

    private fun hashPin(pin: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val salt = "VVF_SMART_FILE_MANAGER_SECURE_SALT_2026"
            val hashBytes = digest.digest((pin + salt).toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin
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
                    Log.d("SafeFolderViewModel", "PIN Setup: First entry completed [StructuredLog: { event: \"pin_setup_first_step\" }]")
                } else if (_pinSetupStep.value == "CONFIRM_PIN") {
                    if (pin == _tempPinForSetup.value) {
                        repository.setPIN(pin)
                        _safeFolderState.value = SafeFolderState.Unlocked
                        _pinSetupStep.value = "ENTER_PIN"
                        _tempPinForSetup.value = ""
                        Log.i("SafeFolderViewModel", "PIN Setup successful. Safe unlocked [StructuredLog: { event: \"pin_setup_success\" }]")
                    } else {
                        _pinErrorMessage.value = "PINs do not match. Please try again."
                        _pinSetupStep.value = "ENTER_PIN"
                        _tempPinForSetup.value = ""
                        Log.w("SafeFolderViewModel", "PIN Setup failed: Mismatch [StructuredLog: { event: \"pin_setup_mismatch\" }]")
                    }
                }
            }
            is SafeFolderState.Locked -> {
                val savedPin = repository.getPIN()
                if (hashPin(pin) == savedPin) {
                    _safeFolderState.value = SafeFolderState.Unlocked
                    _pinErrorMessage.value = null
                    Log.i("SafeFolderViewModel", "Safe Folder Unlocked successfully [StructuredLog: { event: \"safe_unlock_success\" }]")
                } else {
                    _pinErrorMessage.value = "Incorrect 4-digit PIN sequence. Access denied."
                    Log.w("SafeFolderViewModel", "Safe Folder Unlock failed: Wrong PIN [StructuredLog: { event: \"safe_unlock_fail\" }]")
                }
            }
            is SafeFolderState.Unlocked -> {
                // Already unlocked
            }
        }
    }

    fun lockSafeFolder() {
        _safeFolderState.value = SafeFolderState.Locked
        Log.d("SafeFolderViewModel", "Safe Folder locked [StructuredLog: { event: \"safe_locked\" }]")
    }

    fun unlockWithBiometrics() {
        _safeFolderState.value = SafeFolderState.Unlocked
        _pinErrorMessage.value = null
        Log.i("SafeFolderViewModel", "Safe Folder Unlocked via Biometrics [StructuredLog: { event: \"safe_unlock_biometrics_success\" }]")
    }

    fun moveSelectedToSafe(selectedIds: Set<Long>, onClearSelection: () -> Unit) {
        viewModelScope.launch {
            repository.moveFilesToSafe(getApplication(), selectedIds)
            Log.i("SafeFolderViewModel", "Moved files to Safe Folder [StructuredLog: { event: \"files_moved_to_safe\", count: ${selectedIds.size} }]")
            onClearSelection()
        }
    }

    fun restoreSelectedFromSafe(selectedIds: Set<Long>) {
        viewModelScope.launch {
            repository.restoreFilesFromSafe(getApplication(), selectedIds)
            Log.i("SafeFolderViewModel", "Restored files from Safe Folder [StructuredLog: { event: \"files_restored\", count: ${selectedIds.size} }]")
        }
    }
}
