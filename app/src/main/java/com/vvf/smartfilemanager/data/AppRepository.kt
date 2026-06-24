package com.vvf.smartfilemanager.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

class AppRepository(private val db: AppDatabase) : IAppRepository {

    private val fileDao = db.fileDao()
    private val categoryDao = db.categoryDao()
    private val secureStateDao = db.secureStateDao()
    private val chatDao = db.chatMessageDao()

    // Flow Accessors
    override val allLocalNonSafeFiles: Flow<List<FileEntity>> = fileDao.getLocalNonSafeFiles()
    override val allSafeFiles: Flow<List<FileEntity>> = fileDao.getSafeFiles()
    override val duplicateFiles: Flow<List<FileEntity>> = fileDao.getDuplicateFiles()
    override val junkFiles: Flow<List<FileEntity>> = fileDao.getJunkFiles()
    override val chatHistory: Flow<List<ChatMessageEntity>> = chatDao.getChatHistory()

    override val localNonSafeFilesTotalSize: Flow<Long> = fileDao.getLocalNonSafeFilesTotalSize()
    override val junkFilesTotalSize: Flow<Long> = fileDao.getJunkFilesTotalSize()
    override val duplicateFilesTotalSize: Flow<Long> = fileDao.getDuplicateFilesTotalSize()

    override val localNonSafeFilesCount: Flow<Int> = fileDao.getLocalNonSafeFilesCount()
    override val safeFilesCount: Flow<Int> = fileDao.getSafeFilesCount()
    override val duplicateFilesCount: Flow<Int> = fileDao.getDuplicateFilesCount()

    override fun searchLocalNonSafeFiles(query: String, category: String, limit: Int): Flow<List<FileEntity>> {
        return fileDao.searchLocalNonSafeFiles(query, category, limit)
    }

    override fun getScannedDuplicates(limit: Int): Flow<List<FileEntity>> {
        return fileDao.getScannedDuplicates(limit)
    }

    override fun getLargeTempFiles(limit: Int): Flow<List<FileEntity>> {
        return fileDao.getLargeTempFiles(limit)
    }

    override fun getCloudFilesForAccount(email: String): Flow<List<FileEntity>> {
        return fileDao.getCloudFiles(email)
    }

    // Initialize Database with satisfying initial entries if empty
    override suspend fun checkAndInitializeData() {
        val existingFiles = fileDao.getAllFiles().firstOrNull()
        if (existingFiles.isNullOrEmpty()) {
            val initialFiles = listOf(
                // Non-safe, local files
                FileEntity(
                    name = "IMG_20260615_1203.jpg",
                    path = "/storage/emulated/0/DCIM/Camera/IMG_20260615_1203.jpg",
                    size = 4200102,
                    lastModified = 1781550000000L,
                    mimeType = "image/jpeg",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false
                ),
                FileEntity(
                    name = "IMG_20260615_1203_DUPLICATE.jpg",
                    path = "/storage/emulated/0/DCIM/Camera/IMG_20260615_1203_DUPLICATE.jpg",
                    size = 4200102,
                    lastModified = 1781550000000L,
                    mimeType = "image/jpeg",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = true,
                    isJunk = false
                ),
                FileEntity(
                    name = "Tax_Report_Draft_2025.xlsx",
                    path = "/storage/emulated/0/Documents/Tax_Report_Draft_2025.xlsx",
                    size = 1204115,
                    lastModified = 1775820000000L,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false
                ),
                FileEntity(
                    name = "Podcast_Episode_52.mp3",
                    path = "/storage/emulated/0/Music/Podcast_Episode_52.mp3",
                    size = 45980111,
                    lastModified = 1778600000000L,
                    mimeType = "audio/mpeg",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false
                ),
                FileEntity(
                    name = "Rent_Agreement_Signed.pdf",
                    path = "/storage/emulated/0/Download/Rent_Agreement_Signed.pdf",
                    size = 342109,
                    lastModified = 1780520000000L,
                    mimeType = "application/pdf",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false
                ),
                // Safe files containing sensitive items (restored or hidden)
                FileEntity(
                    name = "Backup_Keys_Crypt.txt",
                    path = "/storage/emulated/0/Documents/Backup_Keys_Crypt.txt",
                    size = 1024,
                    lastModified = 1776820000000L,
                    mimeType = "text/plain",
                    isLocal = true,
                    isSafe = true,
                    isDuplicate = false,
                    isJunk = false
                ),
                // Junk files
                FileEntity(
                    name = "cache_3482_temp.log",
                    path = "/storage/emulated/0/Android/data/com.aistudio.smartfilemanager/cache/cache_3482_temp.log",
                    size = 18911200,
                    lastModified = System.currentTimeMillis(),
                    mimeType = "text/plain",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = true
                ),
                FileEntity(
                    name = "Thumbs_Caches_System.db",
                    path = "/storage/emulated/0/Android/data/com.aistudio.smartfilemanager/cache/Thumbs_Caches_System.db",
                    size = 12410123,
                    lastModified = System.currentTimeMillis(),
                    mimeType = "application/octet-stream",
                    isLocal = true,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = true
                ),
                // Cloud Files for default active account (user@example.com)
                FileEntity(
                    name = "Resume_Awasthi_Sach.pdf",
                    path = "GoogleDrive://user@example.com/Documents/Resume_Awasthi_Sach.pdf",
                    size = 1842100,
                    lastModified = 1780990000000L,
                    mimeType = "application/pdf",
                    isLocal = false,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false,
                    cloudAccountEmail = "user@example.com"
                ),
                FileEntity(
                    name = "Project_Proposal_Smart_Manager.gdoc",
                    path = "GoogleDrive://user@example.com/Work/Project_Proposal_Smart_Manager.gdoc",
                    size = 154200,
                    lastModified = 1781200000000L,
                    mimeType = "application/vnd.google-apps.document",
                    isLocal = false,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false,
                    cloudAccountEmail = "user@example.com"
                ),
                FileEntity(
                    name = "Family_Reunion_2026.png",
                    path = "GoogleDrive://user@example.com/Photos/Family_Reunion_2026.png",
                    size = 8410211,
                    lastModified = 1781400000000L,
                    mimeType = "image/png",
                    isLocal = false,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false,
                    cloudAccountEmail = "user@example.com"
                ),
                FileEntity(
                    name = "Weekly_Sync_10_Meeting.mp4",
                    path = "GoogleDrive://user@example.com/Videos/Weekly_Sync_10_Meeting.mp4",
                    size = 89912400,
                    lastModified = 1781100000000L,
                    mimeType = "video/mp4",
                    isLocal = false,
                    isSafe = false,
                    isDuplicate = false,
                    isJunk = false,
                    cloudAccountEmail = "user@example.com"
                )
            )

            fileDao.insertFiles(initialFiles)
        }
    }

    // CRUD database actions
    override suspend fun insertFile(file: FileEntity): Long = fileDao.insertFile(file)
    override suspend fun insertFiles(files: List<FileEntity>) = fileDao.insertFiles(files)
    override suspend fun updateFile(file: FileEntity) = fileDao.updateFile(file)
    override suspend fun deleteFile(file: FileEntity) = fileDao.deleteFile(file)
    override suspend fun deleteFileById(id: Long) = fileDao.deleteFileById(id)
    override suspend fun cleanAllJunk() = fileDao.clearAllJunk()
    override suspend fun moveFilesToSafe(ids: Set<Long>) = fileDao.moveFilesToSafe(ids)
    override suspend fun restoreFilesFromSafe(ids: Set<Long>) = fileDao.restoreFilesFromSafe(ids)

    override suspend fun scanAndSaveRealFiles(context: Context) = withContext(Dispatchers.IO) {
        val scanner = MediaStoreScanner(context)
        fileDao.clearLocalNonSafeFiles()
        scanner.scanAllFilesPaged(chunkSize = 1000) { chunk ->
            if (chunk.isNotEmpty()) {
                val fileEntities = chunk.map { scanned ->
                    FileEntity(
                        name = scanned.name,
                        path = scanned.path,
                        size = scanned.size,
                        lastModified = System.currentTimeMillis(),
                        mimeType = scanned.mimeType,
                        isLocal = true,
                        isSafe = false,
                        isDuplicate = false,
                        isJunk = false
                    )
                }
                // Insert chunk into the database, keeping peak JVM memory extremely low (O(1) with respect to total device files)
                try {
                    db.withTransaction {
                        fileDao.insertFiles(fileEntities)
                    }
                } catch (e: Exception) {
                    Log.e("AppRepository", "Failed to insert batch chunk of size ${chunk.size}", e)
                }
            }
        }
    }

    // PIN / Secure Safe State Logic
    override suspend fun getPIN(): String? {
        return secureStateDao.getStateByKey("SAFE_PIN")?.stateValue
    }

    override suspend fun setPIN(pin: String) {
        secureStateDao.insertState(SecureStateEntity("SAFE_PIN", pin))
    }

    override suspend fun getIsPinSet(): Boolean {
        return getPIN() != null
    }

    // Chat Message DB Logic
    override suspend fun insertMessage(messageText: String, sender: String, isThinking: Boolean, thinkingProcess: String?) {
        chatDao.insertMessage(
            ChatMessageEntity(
                messageText = messageText,
                sender = sender,
                timestamp = System.currentTimeMillis(),
                isThinking = isThinking,
                thinkingProcess = thinkingProcess
            )
        )
    }

    override suspend fun clearChatHistory() {
        chatDao.clearHistory()
    }

    // Secure pluggable AI Service Provider
    private var aiServiceProvider: IAiServiceProvider = DirectGeminiServiceProvider()

    fun setAiServiceProvider(provider: IAiServiceProvider) {
        aiServiceProvider = provider
    }

    // Gemini API integration service
    override suspend fun callGemini(
        apiKey: String,
        prompt: String,
        systemInstruction: String?,
        enableThinkingMode: Boolean
    ): Pair<String, String?> {
        try {
            // Secure delegation via the abstraction layer to prevent client exposure
            return aiServiceProvider.generateContent(
                prompt = prompt,
                systemInstruction = systemInstruction,
                enableThinkingMode = enableThinkingMode,
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "Error calling AI provider: ", e)
            return Pair("Failed via provider delegation: ${e.localizedMessage ?: e.message}", null)
        }
    }
}
