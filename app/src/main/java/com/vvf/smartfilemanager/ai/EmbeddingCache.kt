package com.vvf.smartfilemanager.ai

import java.util.concurrent.ConcurrentHashMap

object EmbeddingCache {
    private val cache = ConcurrentHashMap<String, List<Float>>()

    fun get(fileName: String): List<Float>? = cache[fileName]

    fun put(fileName: String, embedding: List<Float>) {
        cache[fileName] = embedding
    }

    fun clear() {
        cache.clear()
    }
}
