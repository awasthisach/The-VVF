package com.vvf.smartfilemanager.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CloudSyncStatus {
    object Idle : CloudSyncStatus
    object Syncing : CloudSyncStatus
    data class Completed(val filesSynced: Int) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

object SyncStateManager {
    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    fun updateStatus(newStatus: CloudSyncStatus) {
        _status.value = newStatus
    }
}
