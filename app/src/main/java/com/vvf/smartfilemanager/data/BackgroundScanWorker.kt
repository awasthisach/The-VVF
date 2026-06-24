package com.vvf.smartfilemanager.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i("BackgroundScanWorker", "Background file sync worker started [StructuredLog: { event: \"bg_scan_worker_start\" }]")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = AppRepository(db)
            repository.scanAndSaveRealFiles(applicationContext)
            Log.i("BackgroundScanWorker", "Background file sync worker successfully completed [StructuredLog: { event: \"bg_scan_worker_success\" }]")
            Result.success()
        } catch (e: Exception) {
            Log.e("BackgroundScanWorker", "Background file sync worker failed", e)
            Result.retry()
        }
    }
}
