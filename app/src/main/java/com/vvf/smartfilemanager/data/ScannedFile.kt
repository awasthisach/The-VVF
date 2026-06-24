package com.vvf.smartfilemanager.data

import android.net.Uri

data class ScannedFile(
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri
)
