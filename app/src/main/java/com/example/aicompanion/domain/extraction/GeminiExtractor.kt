package com.example.aicompanion.domain.extraction

import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.network.GeminiClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class GeminiExtractor(
    private val geminiClient: GeminiClient
) : ActionItemExtractor {

    override suspend fun extract(
        transcript: String,
        projectNames: List<String>
    ): ExtractionResult {
        val result = geminiClient.extractActionItems(transcript, projectNames)
        val extractionResult = result.getOrThrow()
        val items = extractionResult.actionItems.map { geminiItem ->
            ExtractedItem(
                text = geminiItem.text,
                dueDate = parseDateToEpoch(geminiItem.dueDate),
                priority = parsePriority(geminiItem.priority),
                suggestedProject = geminiItem.suggestedProject
            )
        }
        return ExtractionResult(items = items, newProject = extractionResult.newProject)
    }

    private fun parseDateToEpoch(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank() || dateStr == "null") return null
        return try {
            val date = LocalDate.parse(dateStr)
            date.atTime(LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun parsePriority(priorityStr: String?): Priority {
        return when (priorityStr?.lowercase()) {
            "low" -> Priority.LOW
            "medium" -> Priority.MEDIUM
            "high" -> Priority.HIGH
            "urgent" -> Priority.URGENT
            else -> Priority.NONE
        }
    }
}
