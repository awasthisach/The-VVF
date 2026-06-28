package com.vvf.smartfilemanager.domain

import com.vvf.smartfilemanager.data.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncMetadataUseCase(private val syncManager: CloudSyncManager) {
    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        syncManager.performMetadataSync()
    }
}
