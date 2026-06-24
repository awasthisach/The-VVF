package com.vvf.smartfilemanager.data

import android.util.Log

object CrashlyticsTracker {
    fun logNonFatal(throwable: Throwable) {
        Log.e("CrashlyticsTracker", "Non-fatal exception recorded: ${throwable.localizedMessage ?: throwable.message}", throwable)
    }

    fun setCustomKey(key: String, value: String) {
        Log.d("CrashlyticsTracker", "Custom telemetry metadata key added: $key = $value")
    }

    fun setUserId(userId: String) {
        Log.i("CrashlyticsTracker", "Active session user bound: $userId")
    }
}
