package com.example.aicompanion.domain.extraction

import com.example.aicompanion.network.GeminiClient
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class GeminiExtractor(
    private val geminiClient: GeminiClient
) : ActionItemExtractor {

    override suspend fun extract(transcript: String): List<ExtractedItem> {
        val result = geminiClient.extractActionItems(transcript)
        val extractionResult = result.getOrThrow()
        return extractionResult.actionItems.map { geminiItem ->
            ExtractedItem(
                text = geminiItem.text,
                dueDate = parseDateToEpoch(geminiItem.dueDate)
            )
        }
    }

    override suspend fun extractTopic(transcript: String): String? {
        val result = geminiClient.extractTopic(transcript)
        return result.getOrThrow()
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
}
