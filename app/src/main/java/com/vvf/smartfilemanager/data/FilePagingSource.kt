package com.vvf.smartfilemanager.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

class FilePagingSource(
    private val fileDao: FileDao
) : PagingSource<Int, FileEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileEntity> {
        return try {
            val position = params.key ?: 0
            val limit = params.loadSize
            
            val subList = fileDao.getPagedFilesList(limit = limit, offset = position)
            val totalCount = fileDao.getFilesCount()

            LoadResult.Page(
                data = subList,
                prevKey = if (position == 0) null else maxOf(0, position - limit),
                nextKey = if (subList.isEmpty() || position + limit >= totalCount) null else position + limit
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FileEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
