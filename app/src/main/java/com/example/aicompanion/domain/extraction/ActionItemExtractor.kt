package com.example.aicompanion.domain.extraction

interface ActionItemExtractor {
    fun extract(transcript: String): List<ExtractedItem>
    fun extractTopic(transcript: String): String?
}
