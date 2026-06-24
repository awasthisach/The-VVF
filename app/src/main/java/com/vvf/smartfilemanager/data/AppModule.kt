package com.vvf.smartfilemanager.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideFileDao(db: AppDatabase): FileDao {
        return db.fileDao()
    }

    @Provides
    @Singleton
    fun provideAppRepository(db: AppDatabase): IAppRepository {
        return AppRepository(db)
    }
}
