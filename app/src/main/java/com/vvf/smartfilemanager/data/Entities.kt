package com.vvf.smartfilemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["isLocal", "isSafe", "lastModified"]),
        Index(value = ["isLocal", "isSafe", "mimeType", "lastModified"]),
        Index(value = ["isDuplicate", "isLocal", "lastModified"]),
        Index(value = ["isJunk", "isLocal", "lastModified"]),
        Index(value = ["name", "size"]),
        Index(value = ["cloudAccountEmail"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isLocal: Boolean,
    val isSafe: Boolean = false,
    val isDuplicate: Boolean = false,
    val isJunk: Boolean = false,
    val cloudAccountEmail: String? = null // Non-null if it's simulated Drive file
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String, // e.g., "IMAGES", "VIDEOS", "AUDIO", "DOCUMENTS", "DOWNLOADS"
    val name: String,
    val icon: String
)

@Entity(tableName = "secure_state")
data class SecureStateEntity(
    @PrimaryKey val stateKey: String, // e.g., "SAFE_PIN", "PIN_IS_SET", "PIN_ATTEMPTS"
    val stateValue: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageText: String,
    val sender: String, // "user" or "gemini"
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val thinkingProcess: String? = null
)
