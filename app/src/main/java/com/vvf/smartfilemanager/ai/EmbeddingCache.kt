package com.vvf.smartfilemanager.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object EmbeddingCache {
    private val cache = ConcurrentHashMap<String, List<Float>>()
    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("vvf_embedding_cache", Context.MODE_PRIVATE)
        sharedPrefs = prefs
        try {
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    val floatList = value.split(",").mapNotNull { it.trim().toFloatOrNull() }
                    if (floatList.isNotEmpty()) {
                        cache[key] = floatList
                    }
                }
            }
            Log.i("VVF_PERF", "EmbeddingCache loaded ${cache.size} cached items from persistent storage")
        } catch (e: Exception) {
            Log.e("VVF_PERF", "Failed to load cache from persistent preferences", e)
        }
    }

    fun get(fileName: String): List<Float>? = cache[fileName]

    fun put(fileName: String, embedding: List<Float>) {
        if (embedding.isEmpty()) return
        cache[fileName] = embedding
        sharedPrefs?.let { prefs ->
            try {
                val strValue = embedding.joinToString(",")
                prefs.edit().putString(fileName, strValue).apply()
            } catch (e: Exception) {
                Log.e("VVF_PERF", "Failed to save embedding to persistent preferences", e)
            }
        }
    }

    fun clear() {
        cache.clear()
        sharedPrefs?.edit()?.clear()?.apply()
    }
}
