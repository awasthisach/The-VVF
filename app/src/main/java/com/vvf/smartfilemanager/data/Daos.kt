package com.vvf.smartfilemanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files ORDER BY lastModified DESC")
    suspend fun getAllFilesList(): List<FileEntity>

    @Query("SELECT * FROM files ORDER BY lastModified DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedFilesList(limit: Int, offset: Int): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getFilesCount(): Int

    @Query("SELECT * FROM files WHERE isLocal = :isLocal ORDER BY lastModified DESC")
    fun getFilesByLocation(isLocal: Boolean): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 0 ORDER BY lastModified DESC LIMIT 1000")
    fun getLocalNonSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDuplicate = 1 AND isLocal = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getDuplicateFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isJunk = 1 AND isLocal = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getJunkFiles(): Flow<List<FileEntity>>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR name LIKE '%' || :query || '%')
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
        )
        ORDER BY lastModified DESC 
        LIMIT :limit
    """)
    fun searchLocalNonSafeFiles(query: String, category: String, limit: Int): Flow<List<FileEntity>>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isLocal = 1 AND isSafe = 0")
    fun getLocalNonSafeFilesTotalSize(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isJunk = 1 AND isLocal = 1")
    fun getJunkFilesTotalSize(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isDuplicate = 1 AND isLocal = 1")
    fun getDuplicateFilesTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM files WHERE isLocal = 1 AND isSafe = 0")
    fun getLocalNonSafeFilesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE isLocal = 1 AND isSafe = 1")
    fun getSafeFilesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE isDuplicate = 1 AND isLocal = 1")
    fun getDuplicateFilesCount(): Flow<Int>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (isDuplicate = 1 
             OR (name || '_' || size) IN (
                 SELECT (name || '_' || size) 
                 FROM files 
                 WHERE isLocal = 1 AND isSafe = 0 
                 GROUP BY name, size 
                 HAVING COUNT(*) > 1
             )
        )
        LIMIT :limit
    """)
    fun getScannedDuplicates(limit: Int): Flow<List<FileEntity>>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (isJunk = 1 
             OR name LIKE '%.tmp' 
             OR name LIKE '%.log' 
             OR name LIKE '%.temp' 
             OR size >= 5000000
        )
        ORDER BY size DESC 
        LIMIT :limit
    """)
    fun getLargeTempFiles(limit: Int): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 0 AND cloudAccountEmail = :email ORDER BY lastModified DESC")
    fun getCloudFiles(email: String): Flow<List<FileEntity>>

    @Query("DELETE FROM files WHERE isLocal = 1 AND isSafe = 0")
    suspend fun clearLocalNonSafeFiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM files WHERE isLocal = 1 AND isJunk = 1")
    suspend fun clearAllJunk()

    @Query("UPDATE files SET isSafe = 1 WHERE id IN (:ids)")
    suspend fun moveFilesToSafe(ids: Set<Long>)

    @Query("UPDATE files SET isSafe = 0 WHERE id IN (:ids)")
    suspend fun restoreFilesFromSafe(ids: Set<Long>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
}

@Dao
interface SecureStateDao {
    @Query("SELECT * FROM secure_state WHERE stateKey = :key LIMIT 1")
    suspend fun getStateByKey(key: String): SecureStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: SecureStateEntity)

    @Query("DELETE FROM secure_state WHERE stateKey = :key")
    suspend fun deleteStateByKey(key: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatHistory(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}
