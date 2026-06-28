package com.vvf.smartfilemanager.utils

import android.os.SystemClock
import android.util.Log

object PerformanceTracker {
    private val startTimes = mutableMapOf<String, Long>()

    fun startMeasure(key: String) {
        startTimes[key] = SystemClock.elapsedRealtime()
    }

    fun endMeasure(key: String, logTag: String = "VVF_PERF") {
        val startTime = startTimes.remove(key)
        if (startTime != null) {
            val duration = SystemClock.elapsedRealtime() - startTime
            Log.i(logTag, "$key completed in $duration ms")
        } else {
            Log.w("VVF_PERF", "No start measurement found for key: $key")
        }
    }

    fun logRender(screenName: String) {
        Log.i("VVF_RENDER", "Render completed for screen: $screenName")
    }
}
