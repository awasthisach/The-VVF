package com.vvf.smartfilemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileEntity::class,
        CategoryEntity::class,
        SecureStateEntity::class,
        ChatMessageEntity::class,
        TrashEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun secureStateDao(): SecureStateDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_files_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
