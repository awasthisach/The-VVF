package com.vvf.smartfilemanager.domain

import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteFilesUseCase(private val repository: IAppRepository) {
    suspend operator fun invoke(file: FileEntity) = withContext(Dispatchers.IO) {
        repository.deleteFile(file)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        repository.deleteFileById(id)
    }
}
