package com.vvf.smartfilemanager.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log

object MediaStoreScanner {

    fun scanLocalFiles(context: Context): List<FileEntity> {
        val filesList = mutableListOf<FileEntity>()
        val contentResolver = context.contentResolver

        // Projection for media queries
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )

        // Uris to query
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image",
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video",
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "audio",
            MediaStore.Files.getContentUri("external") to "file"
        )

        for ((uri, type) in uris) {
            try {
                // If it's MediaStore.Files, filter by non-media to avoid duplicates
                val selection = if (type == "file") {
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}"
                } else {
                    null
                }

                contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                    // Verify index existence safely
                    if (nameIndex != -1 && dataIndex != -1 && sizeIndex != -1 && dateModifiedIndex != -1 && mimeTypeIndex != -1) {
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameIndex) ?: continue
                            val path = cursor.getString(dataIndex) ?: continue
                            val size = cursor.getLong(sizeIndex)
                            val lastModified = cursor.getLong(dateModifiedIndex) * 1000L // Convert to ms
                            val mimeType = cursor.getString(mimeTypeIndex) ?: "application/octet-stream"

                            // Avoid adding duplicates of the same path
                            if (filesList.none { it.path == path }) {
                                filesList.add(
                                    FileEntity(
                                        name = name,
                                        path = path,
                                        size = size,
                                        lastModified = if (lastModified > 0) lastModified else System.currentTimeMillis(),
                                        mimeType = mimeType,
                                        isLocal = true,
                                        isSafe = false,
                                        isDuplicate = false,
                                        isJunk = size < 1024 && (name.endsWith(".tmp") || name.startsWith("temp_") || name.endsWith(".log"))
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaStoreScanner", "Error scanning URI: $uri", e)
            }
        }

        // Post-process to tag actual duplicates (same size and different names)
        val sizeGroups = filesList.groupBy { it.size }
        val finalFiles = filesList.map { file ->
            val sameSizeFiles = sizeGroups[file.size] ?: emptyList()
            val hasDuplicate = sameSizeFiles.size > 1 && sameSizeFiles.any { it.name != file.name }
            file.copy(isDuplicate = hasDuplicate)
        }

        return finalFiles
    }
}
