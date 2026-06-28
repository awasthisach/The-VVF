package com.vvf.smartfilemanager.utils

import android.util.Log

object StructuredLogger {
    fun logPerf(action: String, durationMs: Long) {
        Log.i("VVF_PERF", "ACTION: $action | DURATION: $durationMs ms")
    }

    fun logRender(screen: String) {
        Log.i("VVF_RENDER", "SCREEN: $screen rendered")
    }

    fun logTrace(tag: String, message: String) {
        Log.d("VVF_TRACE", "[$tag] $message")
    }
}
