package com.vvf.smartfilemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileEntity::class,
        FileFtsEntity::class,
        CategoryEntity::class,
        SecureStateEntity::class,
        ChatMessageEntity::class,
        TrashEntity::class
    ],
    version = 3,
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

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `files_fts` USING fts4(content=`files`, `name`)")
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS files_fts_before_destroy BEFORE DELETE ON files BEGIN
                        DELETE FROM files_fts WHERE rowid = OLD.id;
                    END
                """.trimIndent())
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS files_fts_after_insert AFTER INSERT ON files BEGIN
                        INSERT INTO files_fts(rowid, name) VALUES (NEW.id, NEW.name);
                    END
                """.trimIndent())
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS files_fts_after_update AFTER UPDATE ON files BEGIN
                        UPDATE files_fts SET name = NEW.name WHERE rowid = NEW.id;
                    END
                """.trimIndent())
                database.execSQL("INSERT INTO files_fts(rowid, name) SELECT id, name FROM files")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_files_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
