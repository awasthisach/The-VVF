package com.vvf.smartfilemanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource

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

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY lastModified DESC
    """)
    fun getPagedFilesDateNewest(query: String, category: String): PagingSource<Int, FileEntity>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY lastModified ASC
    """)
    fun getPagedFilesDateOldest(query: String, category: String): PagingSource<Int, FileEntity>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun getPagedFilesNameAsc(query: String, category: String): PagingSource<Int, FileEntity>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY name COLLATE NOCASE DESC
    """)
    fun getPagedFilesNameDesc(query: String, category: String): PagingSource<Int, FileEntity>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY size DESC
    """)
    fun getPagedFilesSizeLargest(query: String, category: String): PagingSource<Int, FileEntity>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
        ORDER BY size ASC
    """)
    fun getPagedFilesSizeSmallest(query: String, category: String): PagingSource<Int, FileEntity>
    @Query("SELECT * FROM files WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE path IN (:paths)")
    suspend fun getFilesByPaths(paths: List<String>): List<FileEntity>

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun getFilesByIds(ids: List<Long>): List<FileEntity>

    @Query("""
        SELECT id FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:query = '' OR id IN (SELECT rowid FROM files_fts WHERE files_fts MATCH :query))
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
            OR (:category = 'ARCHIVES' AND (name LIKE '%.zip' OR name LIKE '%.rar' OR name LIKE '%.7z' OR name LIKE '%.tar' OR name LIKE '%.gz' OR name LIKE '%.bz2'))
            OR (:category = 'APK' AND name LIKE '%.apk')
            OR (:category = 'LARGE' AND size > 104857600)
            OR (:category = 'EMPTY_FOLDERS' AND (size = 0 AND (mimeType = 'resource/folder' OR mimeType LIKE '%directory%')))
        )
    """)
    suspend fun getFilteredFileIds(query: String, category: String): List<Long>

    @Query("SELECT * FROM files WHERE isLocal = :isLocal ORDER BY lastModified DESC")
    fun getFilesByLocation(isLocal: Boolean): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 0 ORDER BY lastModified DESC LIMIT 1000")
    fun getLocalNonSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getSafeFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 1 ORDER BY lastModified DESC")
    fun getSafeFilesPaged(): PagingSource<Int, FileEntity>

    @Query("SELECT * FROM files WHERE isDuplicate = 1 AND isLocal = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getDuplicateFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE isDuplicate = 1 AND isLocal = 1 ORDER BY lastModified DESC")
    fun getDuplicateFilesPaged(): PagingSource<Int, FileEntity>

    @Query("SELECT * FROM files WHERE isJunk = 1 AND isLocal = 1 ORDER BY lastModified DESC LIMIT 1000")
    fun getJunkFiles(): Flow<List<FileEntity>>

    @Query("""
        SELECT files.* FROM files 
        JOIN files_fts ON files.id = files_fts.rowid
        WHERE files.isLocal = 1 AND files.isSafe = 0 
        AND files_fts.name MATCH :ftsQuery
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
        )
        ORDER BY lastModified DESC 
        LIMIT :limit
    """)
    fun searchLocalNonSafeFilesFts(ftsQuery: String, category: String, limit: Int): Flow<List<FileEntity>>

    @Query("""
        SELECT * FROM files 
        WHERE isLocal = 1 AND isSafe = 0 
        AND (:category = 'ALL' 
            OR (:category = 'IMAGES' AND mimeType LIKE 'image/%')
            OR (:category = 'VIDEOS' AND mimeType LIKE 'video/%')
            OR (:category = 'AUDIO' AND mimeType LIKE 'audio/%')
            OR (:category = 'DOCUMENTS' AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%'))
        )
        ORDER BY lastModified DESC 
        LIMIT :limit
    """)
    fun searchLocalNonSafeFilesEmptyQuery(category: String, limit: Int): Flow<List<FileEntity>>

    fun searchLocalNonSafeFiles(query: String, category: String, limit: Int): Flow<List<FileEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            searchLocalNonSafeFilesEmptyQuery(category, limit)
        } else {
            val ftsQuery = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { "$it*" }
            searchLocalNonSafeFilesFts(ftsQuery, category, limit)
        }
    }

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
        AND (isDuplicate = 1 
             OR (name || '_' || size) IN (
                 SELECT (name || '_' || size) 
                 FROM files 
                 WHERE isLocal = 1 AND isSafe = 0 
                 GROUP BY name, size 
                 HAVING COUNT(*) > 1
             )
        )
        ORDER BY lastModified DESC
    """)
    fun getScannedDuplicatesPaged(): PagingSource<Int, FileEntity>

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

    @Query("SELECT COUNT(*) FROM files WHERE isLocal = 1 AND isSafe = 0 AND mimeType LIKE 'image/%'")
    fun getImagesCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isLocal = 1 AND isSafe = 0 AND mimeType LIKE 'image/%'")
    fun getImagesTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM files WHERE isLocal = 1 AND isSafe = 0 AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%')")
    fun getDocsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isLocal = 1 AND isSafe = 0 AND (mimeType LIKE '%pdf%' OR mimeType LIKE '%sheet%' OR mimeType LIKE '%document%' OR mimeType LIKE '%text%')")
    fun getDocsTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM files WHERE isLocal = 1 AND isSafe = 0 AND (mimeType LIKE 'video/%' OR mimeType LIKE 'audio/%')")
    fun getMediaCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE isLocal = 1 AND isSafe = 0 AND (mimeType LIKE 'video/%' OR mimeType LIKE 'audio/%')")
    fun getMediaTotalSize(): Flow<Long>

    @Query("SELECT * FROM files WHERE isLocal = 1 AND isSafe = 0")
    suspend fun getAllLocalNonSafeFilesList(): List<FileEntity>

    @Query("UPDATE files SET isDuplicate = 1 WHERE id IN (:ids)")
    suspend fun markIdsAsDuplicates(ids: List<Long>)

    @Query("UPDATE files SET isDuplicate = 0 WHERE isDuplicate = 1")
    suspend fun clearAllDuplicateFlags()

    @Query("DELETE FROM files WHERE isLocal = 1 AND isSafe = 0")
    suspend fun clearLocalNonSafeFiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Update
    suspend fun updateFiles(files: List<FileEntity>)

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

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun getAllTrashPaged(): PagingSource<Int, TrashEntity>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    suspend fun getAllTrashList(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE id = :id LIMIT 1")
    suspend fun getTrashById(id: Long): TrashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(trash: TrashEntity): Long

    @Delete
    suspend fun deleteTrash(trash: TrashEntity)

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun deleteTrashById(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clearAllTrash()

    @Query("DELETE FROM trash WHERE deletedAt < :timestamp")
    suspend fun deleteTrashBeforeTimestamp(timestamp: Long)
}
