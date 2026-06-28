package com.vvf.smartfilemanager.domain

import android.content.Context
import com.vvf.smartfilemanager.data.IAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanFilesUseCase(private val repository: IAppRepository) {
    suspend operator fun invoke(context: Context) = withContext(Dispatchers.IO) {
        repository.scanAndSaveRealFiles(context)
    }
}
