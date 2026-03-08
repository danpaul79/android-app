package com.example.aicompanion.domain.extraction

interface ActionItemExtractor {
    suspend fun extract(transcript: String): List<ExtractedItem>
    suspend fun extractTopic(transcript: String): String?
}
