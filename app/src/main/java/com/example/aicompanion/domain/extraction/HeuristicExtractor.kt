package com.example.aicompanion.domain.extraction

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class HeuristicExtractor : ActionItemExtractor {

    private val actionPatterns = listOf(
        Regex("(?i)\\b(need to|have to|got to|gotta|must|should)\\b"),
        Regex("(?i)\\b(don't forget|remember to|make sure)\\b"),
        Regex("(?i)\\b(call|email|send|text|message|contact)\\b"),
        Regex("(?i)\\b(buy|pick up|get|grab|order)\\b"),
        Regex("(?i)\\b(schedule|book|set up|arrange|plan)\\b"),
        Regex("(?i)\\b(follow up|check on|look into|figure out)\\b"),
        Regex("(?i)\\b(finish|complete|submit|review|prepare|update)\\b"),
        Regex("(?i)\\b(pay|renew|cancel|sign up)\\b")
    )

    private val topicPatterns = listOf(
        Regex("(?i)\\babout\\s+(?:the\\s+)?([A-Za-z][A-Za-z\\s]{1,30})"),
        Regex("(?i)\\bregarding\\s+(?:the\\s+)?([A-Za-z][A-Za-z\\s]{1,30})"),
        Regex("(?i)\\bmeeting\\s+with\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)"),
        Regex("(?i)\\bcall\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)"),
        Regex("(?i)\\bemail\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)")
    )

    override suspend fun extract(transcript: String): List<ExtractedItem> {
        val sentences = splitSentences(transcript)
        return sentences.mapNotNull { sentence ->
            val isAction = actionPatterns.any { it.containsMatchIn(sentence) }
            if (isAction) {
                val dueDate = extractDate(sentence)
                ExtractedItem(
                    text = sentence.trim(),
                    dueDate = dueDate
                )
            } else null
        }
    }

    override suspend fun extractTopic(transcript: String): String? {
        for (pattern in topicPatterns) {
            val match = pattern.find(transcript)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.length > 3 }
    }

    private fun extractDate(text: String): Long? {
        val today = LocalDate.now()

        // "tomorrow"
        if (text.contains(Regex("(?i)\\btomorrow\\b"))) {
            return toEpochMillis(today.plusDays(1))
        }

        // "today"
        if (text.contains(Regex("(?i)\\btoday\\b"))) {
            return toEpochMillis(today)
        }

        // "next week"
        if (text.contains(Regex("(?i)\\bnext\\s+week\\b"))) {
            return toEpochMillis(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))
        }

        // "next Monday", "next Tuesday", etc.
        for (day in DayOfWeek.entries) {
            val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            if (text.contains(Regex("(?i)\\bnext\\s+$dayName\\b"))) {
                return toEpochMillis(today.with(TemporalAdjusters.next(day)))
            }
        }

        // "by Friday", "on Monday", etc.
        for (day in DayOfWeek.entries) {
            val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            if (text.contains(Regex("(?i)\\b(by|on|this)\\s+$dayName\\b"))) {
                val next = today.with(TemporalAdjusters.nextOrSame(day))
                return toEpochMillis(next)
            }
        }

        // "in N days"
        val inDaysMatch = Regex("(?i)\\bin\\s+(\\d+)\\s+days?\\b").find(text)
        if (inDaysMatch != null) {
            val days = inDaysMatch.groupValues[1].toLongOrNull()
            if (days != null) return toEpochMillis(today.plusDays(days))
        }

        // "in N weeks"
        val inWeeksMatch = Regex("(?i)\\bin\\s+(\\d+)\\s+weeks?\\b").find(text)
        if (inWeeksMatch != null) {
            val weeks = inWeeksMatch.groupValues[1].toLongOrNull()
            if (weeks != null) return toEpochMillis(today.plusWeeks(weeks))
        }

        // Month day patterns: "March 15", "March 15th", "Jan 5"
        val monthDayMatch = Regex(
            "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|" +
            "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b"
        ).find(text)
        if (monthDayMatch != null) {
            val monthStr = monthDayMatch.groupValues[1]
            val dayNum = monthDayMatch.groupValues[2].toIntOrNull()
            if (dayNum != null) {
                val month = parseMonth(monthStr)
                if (month != null) {
                    var date = today.withMonth(month).withDayOfMonth(dayNum.coerceIn(1, 28))
                    if (date.isBefore(today)) date = date.plusYears(1)
                    return toEpochMillis(date)
                }
            }
        }

        // Numeric date: "3/15", "03/15"
        val numericMatch = Regex("\\b(\\d{1,2})/(\\d{1,2})\\b").find(text)
        if (numericMatch != null) {
            val month = numericMatch.groupValues[1].toIntOrNull()
            val day = numericMatch.groupValues[2].toIntOrNull()
            if (month != null && day != null && month in 1..12 && day in 1..31) {
                var date = today.withMonth(month).withDayOfMonth(day.coerceIn(1, 28))
                if (date.isBefore(today)) date = date.plusYears(1)
                return toEpochMillis(date)
            }
        }

        return null
    }

    private fun parseMonth(str: String): Int? {
        return when (str.lowercase().take(3)) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
            "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
            "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> null
        }
    }

    private fun toEpochMillis(date: LocalDate): Long {
        return date.atTime(LocalTime.of(9, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
