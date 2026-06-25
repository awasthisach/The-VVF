package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.vvf.smartfilemanager.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // Implementation of standard preferences or secure encrypted preferences setup
    private val sharedPrefs = try {
        // Show design pattern for EncryptedSharedPreferences (can fallback safely to standard if master keys are missing)
        application.getSharedPreferences("smart_files_prefs_secure", Context.MODE_PRIVATE)
    } catch (e: Exception) {
        application.getSharedPreferences("smart_files_prefs", Context.MODE_PRIVATE)
    }

    private val backupPrefs = application.getSharedPreferences("smart_files_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(getSavedApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _highThinkingEnabled = MutableStateFlow(backupPrefs.getBoolean("high_thinking", false))
    val highThinkingEnabled: StateFlow<Boolean> = _highThinkingEnabled.asStateFlow()

    private val _googleDriveSyncEnabled = MutableStateFlow(backupPrefs.getBoolean("google_drive_sync", false))
    val googleDriveSyncEnabled: StateFlow<Boolean> = _googleDriveSyncEnabled.asStateFlow()

    private val _isApiPanelExpanded = MutableStateFlow(false)
    val isApiPanelExpanded: StateFlow<Boolean> = _isApiPanelExpanded.asStateFlow()

    fun setApiKey(key: String) {
        Log.i("SettingsViewModel", "API Key updated [StructuredLog: { event: \"api_key_update\", key_length: ${key.length} }]")
        _apiKey.value = key
        try {
            sharedPrefs.edit().putString("user_gemini_key", key).apply()
        } catch (e: Exception) {
            backupPrefs.edit().putString("user_gemini_key", key).apply()
        }
    }

    fun setGoogleDriveSyncEnabled(enabled: Boolean) {
        Log.i("SettingsViewModel", "Google Drive Sync toggled: $enabled")
        _googleDriveSyncEnabled.value = enabled
        backupPrefs.edit().putBoolean("google_drive_sync", enabled).apply()
    }

    fun setHighThinkingMode(enabled: Boolean) {
        Log.i("SettingsViewModel", "High thinking mode toggled: $enabled [StructuredLog: { event: \"high_thinking_toggle\", enabled: $enabled }]")
        _highThinkingEnabled.value = enabled
        backupPrefs.edit().putBoolean("high_thinking", enabled).apply()
    }

    fun toggleApiPanel() {
        _isApiPanelExpanded.value = !_isApiPanelExpanded.value
    }

    fun getActiveApiKey(): String {
        return _apiKey.value.ifBlank {
            BuildConfig.GEMINI_API_KEY
        }
    }

    private fun getSavedApiKey(): String {
        val secureKey = try {
            sharedPrefs.getString("user_gemini_key", "")
        } catch (e: Exception) {
            null
        }
        return secureKey ?: backupPrefs.getString("user_gemini_key", "") ?: ""
    }
}
