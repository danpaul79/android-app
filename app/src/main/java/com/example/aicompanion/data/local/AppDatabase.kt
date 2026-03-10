package com.example.aicompanion.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.ProjectDao
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.dao.SyncStateDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.local.entity.SyncState

@Database(
    entities = [Project::class, ActionItem::class, Source::class, SyncState::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun sourceDao(): SourceDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = migration(3, 4) {
            it.execSQL("ALTER TABLE action_items ADD COLUMN googleTaskId TEXT")
            it.execSQL("ALTER TABLE action_items ADD COLUMN googleTaskListId TEXT")
            it.execSQL("ALTER TABLE action_items ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            it.execSQL("ALTER TABLE projects ADD COLUMN googleTaskListId TEXT")
            it.execSQL("ALTER TABLE projects ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            it.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_state (
                    id INTEGER NOT NULL PRIMARY KEY,
                    lastSyncTimestamp INTEGER,
                    lastSyncedVersion INTEGER NOT NULL DEFAULT 0,
                    inboxTaskListId TEXT,
                    syncEnabled INTEGER NOT NULL DEFAULT 0,
                    googleAccountEmail TEXT
                )
            """.trimIndent())
        }

        private fun migration(from: Int, to: Int, migrate: (SupportSQLiteDatabase) -> Unit) =
            object : Migration(from, to) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    Log.i(TAG, "Migrating database from version $from to $to")
                    migrate(db)
                }
            }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_companion.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
