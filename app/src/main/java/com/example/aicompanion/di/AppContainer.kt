package com.example.aicompanion.di

import android.content.Context
import com.example.aicompanion.data.local.AppDatabase
import com.example.aicompanion.data.repository.TaskRepository
import com.example.aicompanion.domain.extraction.ActionItemExtractor
import com.example.aicompanion.domain.extraction.GeminiExtractor
import com.example.aicompanion.network.GeminiClient
import com.example.aicompanion.network.TranscriptionClient

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val taskRepository = TaskRepository(
        database.actionItemDao(),
        database.projectDao(),
        database.sourceDao()
    )
    val geminiClient = GeminiClient()
    val extractor: ActionItemExtractor = GeminiExtractor(geminiClient)
    val transcriptionClient = TranscriptionClient()
}
