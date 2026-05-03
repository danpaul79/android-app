package com.example.aicompanion.domain.gmail

import android.util.Log
import com.example.aicompanion.data.local.dao.SourceDao
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Source
import com.example.aicompanion.data.local.entity.SourceType
import com.example.aicompanion.data.repository.TaskRepository
import com.example.aicompanion.data.sync.GmailApiClient
import com.example.aicompanion.data.sync.GmailMessage
import com.example.aicompanion.data.sync.GmailPreferences
import com.example.aicompanion.domain.extraction.ActionItemExtractor

class GmailIngestor(
    private val gmailApi: GmailApiClient,
    private val taskRepository: TaskRepository,
    private val sourceDao: SourceDao,
    private val extractor: ActionItemExtractor,
    private val gmailPrefs: GmailPreferences
) {

    companion object {
        private const val TAG = "GmailIngestor"
        private const val MAX_BODY_CHARS = 8000
    }

    data class IngestResult(
        val messagesScanned: Int,
        val messagesProcessed: Int,
        val tasksCreated: Int,
        val skippedAlreadyProcessed: Int,
        val errors: List<String>
    )

    suspend fun ingestNewEmails(maxMessages: Int = 25): IngestResult {
        val days = gmailPrefs.lookbackDays.coerceIn(1, 14)
        // Use exclusions instead of category:primary so this works whether
        // or not inbox tabs are enabled on the account.
        val query = "in:inbox newer_than:${days}d " +
            "-category:promotions -category:social -category:updates -category:forums"
        Log.i(TAG, "Gmail query: $query")

        val ids = try {
            gmailApi.listMessageIds(query, maxMessages)
        } catch (e: Exception) {
            val msg = "List failed: ${e.message}"
            Log.e(TAG, msg, e)
            gmailPrefs.lastError = msg
            return IngestResult(0, 0, 0, 0, listOf(msg))
        }
        Log.i(TAG, "Gmail returned ${ids.size} message IDs")

        val projects = taskRepository.getAllProjectNames()
        var processed = 0
        var skipped = 0
        var tasksCreated = 0
        val errors = mutableListOf<String>()

        for (id in ids) {
            if (sourceDao.existsByRef(id, SourceType.EMAIL)) {
                skipped++
                continue
            }

            try {
                val msg = gmailApi.getMessage(id)
                val tasksAdded = processMessage(msg, projects)
                tasksCreated += tasksAdded
                processed++
            } catch (e: Exception) {
                val err = "msg=$id: ${e.message}"
                Log.w(TAG, err)
                errors.add(err)
            }
        }

        gmailPrefs.lastIngestTime = System.currentTimeMillis()
        gmailPrefs.lastIngestTaskCount = tasksCreated
        gmailPrefs.lastIngestMessageCount = processed
        gmailPrefs.lastError = if (errors.isEmpty()) null else errors.first().take(200)

        return IngestResult(
            messagesScanned = ids.size,
            messagesProcessed = processed,
            tasksCreated = tasksCreated,
            skippedAlreadyProcessed = skipped,
            errors = errors
        )
    }

    private suspend fun processMessage(msg: GmailMessage, projectNames: List<String>): Int {
        val text = buildExtractionText(msg)
        val source = Source(
            type = SourceType.EMAIL,
            rawContent = text,
            sourceRef = msg.id,
            processedAt = System.currentTimeMillis()
        )

        val result = extractor.extract(text, projectNames)
        if (result.items.isEmpty()) {
            // Insert source so we don't re-scan this message
            sourceDao.insert(source)
            return 0
        }

        val now = System.currentTimeMillis()
        val items = result.items.map { ei ->
            val tagsLine = ei.suggestedTags.joinToString(" ") { "#$it" }
            val notes = buildString {
                if (tagsLine.isNotBlank()) appendLine(tagsLine)
                appendLine("From: ${msg.from}")
                appendLine("Email: ${msg.subject}")
            }.trim()
            ActionItem(
                projectId = null, // land in Inbox for review
                text = ei.text,
                notes = notes,
                dueDate = ei.dueDate,
                dropDeadDate = ei.dropDeadDate,
                priority = ei.priority,
                estimatedMinutes = ei.estimatedMinutes,
                createdAt = now,
                updatedAt = now
            )
        }
        taskRepository.saveFromSource(source, items)
        return items.size
    }

    private fun buildExtractionText(msg: GmailMessage): String {
        val body = msg.body.take(MAX_BODY_CHARS)
        return buildString {
            appendLine("From: ${msg.from}")
            appendLine("Subject: ${msg.subject}")
            if (msg.date.isNotBlank()) appendLine("Date: ${msg.date}")
            appendLine()
            append(body)
        }
    }
}
