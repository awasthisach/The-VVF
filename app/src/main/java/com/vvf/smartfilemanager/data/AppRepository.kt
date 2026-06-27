package com.vvf.smartfilemanager.data

import android.content.Context
import android.util.Log
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import androidx.room.withTransaction
import java.io.File

class AppRepository(private val db: AppDatabase) : IAppRepository {

    private val fileDao = db.fileDao()
    private val categoryDao = db.categoryDao()
    private val secureStateDao = db.secureStateDao()
    private val chatDao = db.chatMessageDao()
    private val trashDao = db.trashDao()

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

    override val imagesCount: Flow<Int> = fileDao.getImagesCount()
    override val imagesTotalSize: Flow<Long> = fileDao.getImagesTotalSize()
    override val docsCount: Flow<Int> = fileDao.getDocsCount()
    override val docsTotalSize: Flow<Long> = fileDao.getDocsTotalSize()
    override val mediaCount: Flow<Int> = fileDao.getMediaCount()
    override val mediaTotalSize: Flow<Long> = fileDao.getMediaTotalSize()

    override fun getUriForPath(context: Context, path: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(path)
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )
        for (uri in uris) {
            try {
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                        if (idIndex != -1) {
                            val id = cursor.getLong(idIndex)
                            return ContentUris.withAppendedId(uri, id)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next URI
            }
        }
        return null
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex != -1) {
                        return cursor.getString(dataIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun getMediaStoreUriForRestore(context: Context, name: String, mimeType: String, originalPath: String): Uri? {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (mimeType.startsWith("image/")) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/")
                } else if (mimeType.startsWith("video/")) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/")
                } else if (mimeType.startsWith("audio/")) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/")
                } else {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/")
                }
            }
        }
        val targetUri = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        return try {
            contentResolver.insert(targetUri, contentValues)
        } catch (e: Exception) {
            Log.e("AppRepository", "Error inserting into MediaStore: $name", e)
            null
        }
    }

    private fun calculateFileSha256(context: Context, path: String, size: Long): String? {
        val contentUri = getUriForPath(context, path)
        val inputStream = if (contentUri != null) {
            try {
                context.contentResolver.openInputStream(contentUri)
            } catch (e: Exception) {
                null
            }
        } else {
            val file = File(path)
            if (file.exists() && file.isFile) {
                try {
                    file.inputStream()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        } ?: return null

        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            inputStream.use { input ->
                if (size <= 1024 * 1024) {
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        digest.update(buffer, 0, bytesRead)
                        bytesRead = input.read(buffer)
                    }
                } else {
                    val sampleSize = 100 * 1024
                    val buffer = ByteArray(sampleSize)

                    // 1. Read first 100 KB
                    var bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        digest.update(buffer, 0, bytesRead)
                    }

                    // 2. Read middle 100 KB
                    try {
                        val skipAmount = (size / 2) - sampleSize
                        if (skipAmount > 0) {
                            input.skip(skipAmount)
                        }
                        bytesRead = input.read(buffer)
                        if (bytesRead > 0) {
                            digest.update(buffer, 0, bytesRead)
                        }
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Failed to skip/read middle for fast hashing", e)
                    }

                    // 3. Read last 100 KB
                    try {
                        val skipAmount = size - (size / 2) - sampleSize * 2
                        if (skipAmount > 0) {
                            input.skip(skipAmount)
                        }
                        bytesRead = input.read(buffer)
                        if (bytesRead > 0) {
                            digest.update(buffer, 0, bytesRead)
                        }
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Failed to skip/read tail for fast hashing", e)
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error calculating fast hash", e)
            null
        }
    }

    override suspend fun markAllDuplicatesInDatabase(context: Context) = withContext(Dispatchers.IO) {
        val allFiles = fileDao.getAllLocalNonSafeFilesList()
        val sizeGroups = allFiles.filter { it.size > 0 }.groupBy { it.size }.filter { it.value.size > 1 }

        fileDao.clearAllDuplicateFlags()

        val duplicatesToMark = mutableListOf<Long>()

        sizeGroups.forEach { (_, filesWithSameSize) ->
            val hashGroups = filesWithSameSize.groupBy { fileEntity ->
                calculateFileSha256(context, fileEntity.path, fileEntity.size)
            }

            hashGroups.forEach { (hash, filesWithSameHash) ->
                if (hash != null && filesWithSameHash.size > 1) {
                    val sorted = filesWithSameHash.sortedBy { it.id }
                    for (i in 1 until sorted.size) {
                        duplicatesToMark.add(sorted[i].id)
                    }
                }
            }
        }

        if (duplicatesToMark.isNotEmpty()) {
            fileDao.markIdsAsDuplicates(duplicatesToMark)
        }
    }

    override suspend fun clearAllDuplicateFlags() = withContext(Dispatchers.IO) {
        fileDao.clearAllDuplicateFlags()
    }

    override fun searchLocalNonSafeFiles(query: String, category: String, limit: Int): Flow<List<FileEntity>> {
        return fileDao.searchLocalNonSafeFiles(query, category, limit)
    }

    override fun getPagedFiles(
        query: String,
        category: String,
        sortOrder: com.vvf.smartfilemanager.viewmodel.SortOrder
    ): Flow<androidx.paging.PagingData<FileEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                when (sortOrder) {
                    com.vvf.smartfilemanager.viewmodel.SortOrder.NAME_ASC -> fileDao.getPagedFilesNameAsc(query, category)
                    com.vvf.smartfilemanager.viewmodel.SortOrder.NAME_DESC -> fileDao.getPagedFilesNameDesc(query, category)
                    com.vvf.smartfilemanager.viewmodel.SortOrder.DATE_NEWEST -> fileDao.getPagedFilesDateNewest(query, category)
                    com.vvf.smartfilemanager.viewmodel.SortOrder.DATE_OLDEST -> fileDao.getPagedFilesDateOldest(query, category)
                    com.vvf.smartfilemanager.viewmodel.SortOrder.SIZE_LARGEST -> fileDao.getPagedFilesSizeLargest(query, category)
                    com.vvf.smartfilemanager.viewmodel.SortOrder.SIZE_SMALLEST -> fileDao.getPagedFilesSizeSmallest(query, category)
                    else -> fileDao.getPagedFilesDateNewest(query, category)
                }
            }
        ).flow
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
    override suspend fun updateFiles(files: List<FileEntity>) = fileDao.updateFiles(files)
    override suspend fun deleteFile(file: FileEntity) = fileDao.deleteFile(file)
    override suspend fun deleteFileById(id: Long) = fileDao.deleteFileById(id)
    override suspend fun cleanAllJunk() = withContext(Dispatchers.IO) {
        try {
            val junkList = fileDao.getJunkFilesSync()
            junkList.forEach { file ->
                try {
                    val f = File(file.path)
                    if (f.exists()) {
                        f.delete()
                    }
                } catch (e: Exception) {
                    Log.e("AppRepository", "Failed to physically delete junk file ${file.path}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error resolving junk files for physical deletion", e)
        }
        fileDao.clearAllJunk()
    }

    override suspend fun moveFilesToSafe(context: Context, ids: Set<Long>): List<Uri> = withContext(Dispatchers.IO) {
        val safeFolderDir = File(context.filesDir, "safe_folder_files")
        if (!safeFolderDir.exists()) {
            safeFolderDir.mkdirs()
        }
        val files = fileDao.getFilesByIds(ids.toList())
        val mediaUrisToDelete = mutableListOf<Uri>()

        files.forEach { fileEntity ->
            val encryptedFile = File(safeFolderDir, "${fileEntity.id}.enc")
            var encryptionSuccess = false

            val contentUri = getUriForPath(context, fileEntity.path)
            if (contentUri != null) {
                try {
                    context.contentResolver.openInputStream(contentUri)?.use { input ->
                        encryptedFile.outputStream().use { output ->
                            encryptionSuccess = com.vvf.smartfilemanager.security.CryptoHelper.encryptStream(input, output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppRepository", "Failed to encrypt via content Uri: ${fileEntity.path}", e)
                }
            }

            if (!encryptionSuccess) {
                val srcFile = File(fileEntity.path)
                if (srcFile.exists()) {
                    try {
                        srcFile.inputStream().use { input ->
                            encryptedFile.outputStream().use { output ->
                                encryptionSuccess = com.vvf.smartfilemanager.security.CryptoHelper.encryptStream(input, output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Failed to encrypt via direct File: ${fileEntity.path}", e)
                    }
                }
            }

            if (encryptionSuccess) {
                if (contentUri != null && contentUri.scheme == "content" && contentUri.authority?.contains("media") == true) {
                    mediaUrisToDelete.add(contentUri)
                    val updatedEntity = fileEntity.copy(
                        path = encryptedFile.absolutePath,
                        isSafe = true,
                        cloudAccountEmail = fileEntity.path
                    )
                    fileDao.updateFile(updatedEntity)
                } else {
                    val srcFile = File(fileEntity.path)
                    if (srcFile.exists()) {
                        srcFile.delete()
                    }
                    val updatedEntity = fileEntity.copy(
                        path = encryptedFile.absolutePath,
                        isSafe = true,
                        cloudAccountEmail = fileEntity.path
                    )
                    fileDao.updateFile(updatedEntity)
                }
            }
        }
        mediaUrisToDelete
    }

    override suspend fun restoreFilesFromSafe(context: Context, ids: Set<Long>) = withContext(Dispatchers.IO) {
        val files = fileDao.getFilesByIds(ids.toList())
        files.forEach { fileEntity ->
            if (fileEntity.isSafe && fileEntity.cloudAccountEmail != null) {
                val encryptedFile = File(fileEntity.path)
                val originalPath = fileEntity.cloudAccountEmail
                var restoreSuccess = false
                var restoredUri: Uri? = null

                if (encryptedFile.exists()) {
                    try {
                        restoredUri = getMediaStoreUriForRestore(context, fileEntity.name, fileEntity.mimeType, originalPath)
                        if (restoredUri != null) {
                            context.contentResolver.openOutputStream(restoredUri)?.use { outputStream ->
                                encryptedFile.inputStream().use { inputStream ->
                                    restoreSuccess = com.vvf.smartfilemanager.security.CryptoHelper.decryptStream(inputStream, outputStream)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppRepository", "Failed to restore file to MediaStore: $originalPath", e)
                    }

                    if (!restoreSuccess) {
                        val destFile = File(originalPath)
                        val parentDir = destFile.parentFile
                        if (parentDir != null && !parentDir.exists()) {
                            parentDir.mkdirs()
                        }
                        try {
                            destFile.outputStream().use { outputStream ->
                                encryptedFile.inputStream().use { inputStream ->
                                    restoreSuccess = com.vvf.smartfilemanager.security.CryptoHelper.decryptStream(inputStream, outputStream)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AppRepository", "Failed to restore file to raw File path: $originalPath", e)
                        }
                    }

                    if (restoreSuccess) {
                        encryptedFile.delete()
                        val finalPath = if (restoredUri != null) {
                            getPathFromUri(context, restoredUri) ?: originalPath
                        } else {
                            originalPath
                        }
                        val updatedEntity = fileEntity.copy(
                            path = finalPath,
                            isSafe = false,
                            cloudAccountEmail = null
                        )
                        fileDao.updateFile(updatedEntity)
                    } else {
                        restoredUri?.let { uri ->
                            try {
                                context.contentResolver.delete(uri, null, null)
                            } catch (e: Exception) {
                                Log.e("AppRepository", "Failed to clean up failed restore URI: $uri", e)
                            }
                        }
                    }
                }
            }
        }
    }

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
    private fun hashPin(pin: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val salt = "VVF_SMART_FILE_MANAGER_SECURE_SALT_2026"
            val hashBytes = digest.digest((pin + salt).toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            pin // Safe fallback (SHA-256 is guaranteed to be available on all Android platforms)
        }
    }

    override suspend fun getPIN(): String? {
        return secureStateDao.getStateByKey("SAFE_PIN")?.stateValue
    }

    override suspend fun setPIN(pin: String) {
        val hashedPin = hashPin(pin)
        secureStateDao.insertState(SecureStateEntity("SAFE_PIN", hashedPin))
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
    private var aiServiceProvider: AiProvider = GeminiProvider()

    fun setAiServiceProvider(provider: AiProvider) {
        aiServiceProvider = provider
    }

    // Gemini API integration service
    override suspend fun callGemini(
        apiKey: String,
        prompt: String,
        systemInstruction: String?,
        enableThinkingMode: Boolean
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            // Secure delegation via the abstraction layer to prevent client exposure
            aiServiceProvider.generateContent(
                prompt = prompt,
                systemInstruction = systemInstruction,
                enableThinkingMode = enableThinkingMode,
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "Error calling AI provider: ", e)
            Pair("Failed via provider delegation: ${e.localizedMessage ?: e.message}", null)
        }
    }

    // Trash System Implementation
    override val allTrashFiles: Flow<List<TrashEntity>> = trashDao.getAllTrash()

    override suspend fun insertTrash(trash: TrashEntity): Long = withContext(Dispatchers.IO) {
        trashDao.insertTrash(trash)
    }

    override suspend fun deleteTrash(trash: TrashEntity) = withContext(Dispatchers.IO) {
        trashDao.deleteTrash(trash)
    }

    override suspend fun deleteTrashById(id: Long) = withContext(Dispatchers.IO) {
        trashDao.deleteTrashById(id)
    }

    override suspend fun clearAllTrash() = withContext(Dispatchers.IO) {
        trashDao.clearAllTrash()
    }

    override suspend fun getTrashById(id: Long): TrashEntity? = withContext(Dispatchers.IO) {
        trashDao.getTrashById(id)
    }

    override suspend fun deleteTrashBeforeTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        trashDao.deleteTrashBeforeTimestamp(timestamp)
    }

    override suspend fun getFileByPath(path: String): FileEntity? = withContext(Dispatchers.IO) {
        fileDao.getFileByPath(path)
    }

    override suspend fun getFilesByPaths(paths: List<String>): List<FileEntity> = withContext(Dispatchers.IO) {
        fileDao.getFilesByPaths(paths)
    }

    override suspend fun getFilesByIds(ids: List<Long>): List<FileEntity> = withContext(Dispatchers.IO) {
        fileDao.getFilesByIds(ids)
    }

    override suspend fun getFilteredFileIds(query: String, category: String): List<Long> = withContext(Dispatchers.IO) {
        fileDao.getFilteredFileIds(query, category)
    }

    override fun getPagedSafeFiles(): Flow<androidx.paging.PagingData<FileEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { fileDao.getSafeFilesPaged() }
        ).flow
    }

    override fun getPagedScannedDuplicates(): Flow<androidx.paging.PagingData<FileEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { fileDao.getScannedDuplicatesPaged() }
        ).flow
    }

    override fun getPagedTrashFiles(): Flow<androidx.paging.PagingData<TrashEntity>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { trashDao.getAllTrashPaged() }
        ).flow
    }
}
