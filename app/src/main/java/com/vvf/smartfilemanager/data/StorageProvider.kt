package com.vvf.smartfilemanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

interface StorageProvider {
    suspend fun listFiles(path: String): List<StorageFile>
    suspend fun copy(sourcePath: String, destPath: String): Boolean
    suspend fun move(sourcePath: String, destPath: String): Boolean
    suspend fun delete(path: String): Boolean
    suspend fun rename(path: String, newName: String): Boolean
    suspend fun createFolder(parentPath: String, folderName: String): Boolean
    suspend fun share(context: Context, path: String)
}

data class StorageFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val uriString: String? = null
)
