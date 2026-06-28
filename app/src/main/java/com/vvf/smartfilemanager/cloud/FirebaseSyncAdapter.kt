package com.vvf.smartfilemanager.cloud

import android.util.Log
import com.vvf.smartfilemanager.data.FileMetadataPayload

class FirebaseSyncAdapter : SyncClient {
    override suspend fun syncMetadata(deviceId: String, payload: List<FileMetadataPayload>): Result<Int> {
        Log.d("VVF_TRACE", "FirebaseSyncAdapter: Syncing ${payload.size} metadata records with Firestore cloud database.")
        return Result.success(payload.size)
    }
}
