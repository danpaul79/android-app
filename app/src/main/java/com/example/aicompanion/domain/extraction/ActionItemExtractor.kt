package com.example.aicompanion.domain.extraction

interface ActionItemExtractor {
    suspend fun extract(
        transcript: String,
        projectNames: List<String> = emptyList()
    ): List<ExtractedItem>
}
