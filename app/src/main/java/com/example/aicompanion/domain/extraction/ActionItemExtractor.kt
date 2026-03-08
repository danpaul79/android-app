package com.example.aicompanion.domain.extraction

data class ExtractionResult(
    val items: List<ExtractedItem>,
    val newProject: String? = null
)

interface ActionItemExtractor {
    suspend fun extract(
        transcript: String,
        projectNames: List<String> = emptyList()
    ): ExtractionResult
}
