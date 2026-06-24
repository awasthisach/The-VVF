package com.vvf.smartfilemanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = :isLocal ORDER BY lastModified DESC")
    fun getFilesByLocation(isLocal: Boolean): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 0 ORDER BY lastModified DESC")
    fun getLocalNonSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 1 ORDER BY lastModified DESC")
    fun getSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDuplicate = 1 AND isLocal = 1 ORDER BY lastModified DESC")
    fun getDuplicateFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isJunk = 1 AND isLocal = 1 ORDER BY lastModified DESC")
    fun getJunkFiles(): Flow<List<FileEntity>>

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
