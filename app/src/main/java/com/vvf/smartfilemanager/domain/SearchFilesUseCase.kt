package com.vvf.smartfilemanager.domain

import com.vvf.smartfilemanager.data.FileEntity
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.flow.Flow

class SearchFilesUseCase(private val repository: IAppRepository) {
    operator fun invoke(query: String, category: String, limit: Int = 100): Flow<List<FileEntity>> {
        return repository.searchLocalNonSafeFiles(query, category, limit)
    }
}
