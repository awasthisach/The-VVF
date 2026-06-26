package com.vvf.smartfilemanager.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafStorageProvider(private val context: Context) : StorageProvider {
    override suspend fun listFiles(path: String): List<StorageFile> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val docFile = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
            val children = docFile.listFiles()
            children.map { child ->
                StorageFile(
                    name = child.name ?: "Unknown",
                    path = child.uri.toString(),
                    size = child.length(),
                    lastModified = child.lastModified(),
                    mimeType = child.type ?: "*/*",
                    isDirectory = child.isDirectory,
                    uriString = child.uri.toString()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun copy(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcUri = Uri.parse(sourcePath)
            val destUri = Uri.parse(destPath)
            val srcDoc = DocumentFile.fromSingleUri(context, srcUri) ?: return@withContext false
            val destDir = DocumentFile.fromTreeUri(context, destUri) ?: return@withContext false
            val newFile = destDir.createFile(srcDoc.type ?: "*/*", srcDoc.name ?: "copied_file") ?: return@withContext false
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun move(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        val copied = copy(sourcePath, destPath)
        if (copied) {
            delete(sourcePath)
        } else {
            false
        }
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val doc = if (path.contains("tree")) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
            doc?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun rename(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val doc = DocumentFile.fromSingleUri(context, uri)
            doc?.renameTo(newName) ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(parentPath)
            val parentDir = DocumentFile.fromTreeUri(context, uri) ?: return@withContext false
            parentDir.createDirectory(folderName) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun share(context: Context, path: String) {
        try {
            val uri = Uri.parse(path)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share File"))
        } catch (e: Exception) {
            // fallback
        }
    }
}
