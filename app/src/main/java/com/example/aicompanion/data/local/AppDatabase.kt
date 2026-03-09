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
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project
import com.example.aicompanion.data.local.entity.Source

@Database(
    entities = [Project::class, ActionItem::class, Source::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun sourceDao(): SourceDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Add future migrations here. Example:
        // val MIGRATION_3_4 = migration(3, 4) {
        //     it.execSQL("ALTER TABLE action_items ADD COLUMN tags TEXT")
        // }

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
                    // Add migrations here as they're created:
                    // .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
