package com.vvf.smartfilemanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalStorageProvider(private val context: Context) : StorageProvider {
    override suspend fun listFiles(path: String): List<StorageFile> = withContext(Dispatchers.IO) {
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()
        val files = folder.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            StorageFile(
                name = file.name,
                path = file.absolutePath,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "vnd.android.document/directory" else getMimeType(file),
                isDirectory = file.isDirectory
            )
        }
    }

    override suspend fun copy(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            val dest = File(destPath)
            if (!src.exists()) return@withContext false
            if (src.isDirectory) {
                dest.mkdirs()
                src.listFiles()?.forEach { child ->
                    copy(child.absolutePath, File(dest, child.name).absolutePath)
                }
                true
            } else {
                dest.parentFile?.mkdirs()
                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun move(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            val dest = File(destPath)
            if (src.renameTo(dest)) {
                true
            } else {
                val copied = copy(sourcePath, destPath)
                if (copied) {
                    delete(sourcePath)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    delete(child.absolutePath)
                }
            }
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun rename(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(path)
            val dest = File(src.parentFile, newName)
            src.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = File(parentPath, folderName)
            folder.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun share(context: Context, path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share File"))
        } catch (e: Exception) {
            // fallback
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "zip" -> "application/zip"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            else -> "*/*"
        }
    }
}
