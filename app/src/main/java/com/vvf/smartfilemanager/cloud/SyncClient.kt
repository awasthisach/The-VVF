package com.vvf.smartfilemanager.cloud

import com.vvf.smartfilemanager.data.FileMetadataPayload

interface SyncClient {
    suspend fun syncMetadata(deviceId: String, payload: List<FileMetadataPayload>): Result<Int>
}
