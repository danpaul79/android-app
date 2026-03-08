package com.example.aicompanion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.aicompanion.data.local.dao.ActionItemDao
import com.example.aicompanion.data.local.dao.VoiceNoteDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.VoiceNote

@Database(
    entities = [VoiceNote::class, ActionItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun actionItemDao(): ActionItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_companion.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
