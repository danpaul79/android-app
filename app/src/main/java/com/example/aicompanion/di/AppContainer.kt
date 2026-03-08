package com.example.aicompanion.di

import android.content.Context
import com.example.aicompanion.auth.GoogleAuthManager
import com.example.aicompanion.data.local.AppDatabase
import com.example.aicompanion.data.repository.CaptureRepository
import com.example.aicompanion.domain.extraction.ActionItemExtractor
import com.example.aicompanion.domain.extraction.HeuristicExtractor
import com.example.aicompanion.network.TranscriptionClient

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val repository = CaptureRepository(database.voiceNoteDao(), database.actionItemDao())
    val extractor: ActionItemExtractor = HeuristicExtractor()
    val authManager = GoogleAuthManager(context)
    val transcriptionClient = TranscriptionClient()
}
