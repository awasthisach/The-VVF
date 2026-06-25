package com.vvf.smartfilemanager.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DuplicateScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i("DuplicateScanWorker", "Background duplicate analysis started [StructuredLog: { event: \"duplicate_scan_worker_start\" }]")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = AppRepository(db)
            
            // Execute the bulk database updates within a background transaction context
            repository.clearAllDuplicateFlags()
            repository.markAllDuplicatesInDatabase()
            
            Log.i("DuplicateScanWorker", "Background duplicate analysis completed [StructuredLog: { event: \"duplicate_scan_worker_success\" }]")
            Result.success()
        } catch (e: Exception) {
            Log.e("DuplicateScanWorker", "Background duplicate analysis failed", e)
            Result.retry()
        }
    }
}
