package com.example.aicompanion.di

import android.content.Context
import com.example.aicompanion.data.local.AppDatabase
import com.example.aicompanion.data.repository.TaskRepository
import com.example.aicompanion.data.sync.GmailApiClient
import com.example.aicompanion.data.sync.GmailPreferences
import com.example.aicompanion.data.sync.GoogleTasksApiClient
import com.example.aicompanion.data.sync.SyncEngine
import com.example.aicompanion.data.sync.TokenManager
import com.example.aicompanion.domain.gmail.GmailIngestor
import com.example.aicompanion.domain.command.VoiceCommandProcessor
import com.example.aicompanion.domain.extraction.ActionItemExtractor
import com.example.aicompanion.domain.extraction.GeminiExtractor
import com.example.aicompanion.network.GeminiClient
import com.example.aicompanion.network.GitHubIssuesClient
import com.example.aicompanion.network.TranscriptionClient
import com.example.aicompanion.ui.theme.ThemePreferences

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val syncStateDao = database.syncStateDao()
    val geminiClient = GeminiClient()
    val taskRepository = TaskRepository(
        database.actionItemDao(),
        database.projectDao(),
        database.sourceDao(),
        syncStateDao,
        geminiClient,
        context.applicationContext,
        database.taskEventDao()
    )
    val extractor: ActionItemExtractor = GeminiExtractor(geminiClient)
    val transcriptionClient = TranscriptionClient()
    val gitHubIssuesClient = GitHubIssuesClient()
    val voiceCommandProcessor = VoiceCommandProcessor(geminiClient, transcriptionClient, taskRepository)

    val themePreferences = ThemePreferences(context)

    // Google Tasks Sync
    val tokenManager = TokenManager(context)
    private val googleTasksApiClient = GoogleTasksApiClient(tokenManager)
    val syncEngine = SyncEngine(
        apiClient = googleTasksApiClient,
        actionItemDao = database.actionItemDao(),
        projectDao = database.projectDao(),
        syncStateDao = syncStateDao,
        tokenManager = tokenManager
    )

    // Gmail ingestion
    val gmailPreferences = GmailPreferences(context)
    private val gmailApiClient = GmailApiClient(tokenManager)
    val gmailIngestor = GmailIngestor(
        gmailApi = gmailApiClient,
        taskRepository = taskRepository,
        sourceDao = database.sourceDao(),
        extractor = extractor,
        gmailPrefs = gmailPreferences
    )
}
