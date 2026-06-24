package com.vvf.smartfilemanager.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

class MediaStoreScanner(private val context: Context) {

    fun scanAllFiles(): List<ScannedFile> {
        val filesList = mutableListOf<ScannedFile>()
        val contentResolver = context.contentResolver

        // Projection for media queries
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )

        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image",
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video",
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "audio"
        )

        for ((uri, type) in uris) {
            try {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                    if (nameIndex != -1 && dataIndex != -1 && sizeIndex != -1 && mimeTypeIndex != -1 && idIndex != -1) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIndex)
                            val name = cursor.getString(nameIndex) ?: continue
                            val path = cursor.getString(dataIndex) ?: continue
                            val size = cursor.getLong(sizeIndex)
                            val mimeType = cursor.getString(mimeTypeIndex) ?: "application/octet-stream"
                            val fileUri = ContentUris.withAppendedId(uri, id)

                            filesList.add(
                                ScannedFile(
                                    name = name,
                                    path = path,
                                    size = size,
                                    mimeType = mimeType,
                                    uri = fileUri
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaStoreScanner", "Error scanning URI: $uri", e)
            }
        }
        return filesList
    }

    fun findDuplicates(files: List<ScannedFile>): Map<String, List<ScannedFile>> {
        val grouped = files.groupBy { "${it.name}_${it.size}" }
        return grouped.filter { it.value.size > 1 }
    }

    fun deleteFile(uri: Uri): Boolean {
        return try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            Log.e("MediaStoreScanner", "Error deleting file: $uri", e)
            false
        }
    }
}
