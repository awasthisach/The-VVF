package com.vvf.smartfilemanager.data

import android.util.Log

object StructuredLogger {
    fun logEvent(event: String, parameters: Map<String, Any>) {
        val jsonParams = parameters.entries.joinToString(prefix = "{", postfix = "}") { 
            "\"${it.key}\": ${if (it.value is Number || it.value is Boolean) it.value else "\"${it.value}\""}"
        }
        Log.i("StructuredLogger", "EVENT: $event | DATA: $jsonParams")
    }

    fun logDiagnostic(module: String, message: String) {
        Log.d("StructuredLogger", "[Module: $module] DIAGNOSTIC: $message")
    }
}
