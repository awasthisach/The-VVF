package com.vvf.smartfilemanager.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface IAppRepository {
    val allLocalNonSafeFiles: Flow<List<FileEntity>>
    val allSafeFiles: Flow<List<FileEntity>>
    val duplicateFiles: Flow<List<FileEntity>>
    val junkFiles: Flow<List<FileEntity>>
    val chatHistory: Flow<List<ChatMessageEntity>>

    val localNonSafeFilesTotalSize: Flow<Long>
    val junkFilesTotalSize: Flow<Long>
    val duplicateFilesTotalSize: Flow<Long>

    val localNonSafeFilesCount: Flow<Int>
    val safeFilesCount: Flow<Int>
    val duplicateFilesCount: Flow<Int>

    val imagesCount: Flow<Int>
    val imagesTotalSize: Flow<Long>
    val docsCount: Flow<Int>
    val docsTotalSize: Flow<Long>
    val mediaCount: Flow<Int>
    val mediaTotalSize: Flow<Long>

    suspend fun markAllDuplicatesInDatabase()
    suspend fun clearAllDuplicateFlags()

    fun searchLocalNonSafeFiles(query: String, category: String, limit: Int): Flow<List<FileEntity>>
    fun getScannedDuplicates(limit: Int): Flow<List<FileEntity>>
    fun getLargeTempFiles(limit: Int): Flow<List<FileEntity>>

    fun getCloudFilesForAccount(email: String): Flow<List<FileEntity>>
    suspend fun checkAndInitializeData()
    suspend fun insertFile(file: FileEntity): Long
    suspend fun insertFiles(files: List<FileEntity>)
    suspend fun updateFile(file: FileEntity)
    suspend fun updateFiles(files: List<FileEntity>)
    suspend fun deleteFile(file: FileEntity)
    suspend fun deleteFileById(id: Long)
    suspend fun cleanAllJunk()
    suspend fun moveFilesToSafe(ids: Set<Long>)
    suspend fun restoreFilesFromSafe(ids: Set<Long>)
    suspend fun scanAndSaveRealFiles(context: Context)
    suspend fun getPIN(): String?
    suspend fun setPIN(pin: String)
    suspend fun getIsPinSet(): Boolean
    suspend fun insertMessage(
        messageText: String,
        sender: String,
        isThinking: Boolean = false,
        thinkingProcess: String? = null
    )
    suspend fun clearChatHistory()
    suspend fun callGemini(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
        enableThinkingMode: Boolean = false
    ): Pair<String, String?>

    // Trash System
    val allTrashFiles: Flow<List<TrashEntity>>
    suspend fun insertTrash(trash: TrashEntity): Long
    suspend fun deleteTrash(trash: TrashEntity)
    suspend fun deleteTrashById(id: Long)
    suspend fun clearAllTrash()
    suspend fun getTrashById(id: Long): TrashEntity?
    suspend fun deleteTrashBeforeTimestamp(timestamp: Long)
}
