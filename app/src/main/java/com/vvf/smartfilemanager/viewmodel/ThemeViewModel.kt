package com.vvf.smartfilemanager.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("smart_files_prefs", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        Log.d("ThemeViewModel", "Theme changed: dark_mode = $dark [StructuredLog: { event: \"theme_change\", value: $dark }]")
        _isDarkMode.value = dark
        sharedPrefs.edit().putBoolean("is_dark_mode", dark).apply()
    }

    fun toggleTheme() {
        setDarkMode(!_isDarkMode.value)
    }
}
