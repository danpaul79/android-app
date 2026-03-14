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
import com.example.aicompanion.data.local.dao.TaskEventDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.local.entity.SyncState
import com.example.aicompanion.data.local.entity.TaskEvent

@Database(
    entities = [Project::class, ActionItem::class, Source::class, SyncState::class, TaskEvent::class],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun sourceDao(): SourceDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun taskEventDao(): TaskEventDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_8_9 = migration(8, 9) {
            it.execSQL("ALTER TABLE action_items ADD COLUMN todaySortOrder INTEGER")
        }

        val MIGRATION_7_8 = migration(7, 8) {
            it.execSQL("ALTER TABLE action_items ADD COLUMN recurrenceRule TEXT")
            it.execSQL("ALTER TABLE action_items ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
        }

        val MIGRATION_6_7 = migration(6, 7) {
            it.execSQL("""
                CREATE TABLE IF NOT EXISTS task_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    taskId INTEGER NOT NULL,
                    eventType TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    projectId INTEGER,
                    tags TEXT,
                    estimatedMinutes INTEGER NOT NULL DEFAULT 0,
                    metadata TEXT
                )
            """.trimIndent())
            it.execSQL("CREATE INDEX IF NOT EXISTS index_task_events_taskId ON task_events (taskId)")
            it.execSQL("CREATE INDEX IF NOT EXISTS index_task_events_eventType ON task_events (eventType)")
        }

        val MIGRATION_5_6 = migration(5, 6) {
            it.execSQL("ALTER TABLE action_items ADD COLUMN dueDateLocked INTEGER NOT NULL DEFAULT 0")
        }

        val MIGRATION_4_5 = migration(4, 5) {
            it.execSQL("ALTER TABLE action_items ADD COLUMN dropDeadDate INTEGER")
            it.execSQL("ALTER TABLE action_items ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 0")
        }

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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
