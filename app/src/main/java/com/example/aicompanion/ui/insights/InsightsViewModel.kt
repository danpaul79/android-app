package com.example.aicompanion.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

data class InsightsUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AICompanionApplication).container
    private val repo = container.taskRepository
    private val geminiClient = container.geminiClient

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    fun askQuestion(question: String) {
        // Add user message
        val userMessage = ChatMessage(text = question, isUser = true)
        val loadingMessage = ChatMessage(text = "", isUser = false, isLoading = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage + loadingMessage,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                val context = repo.getInsightContext()
                val result = geminiClient.askInsight(question, context)
                val responseText = result.getOrElse { e ->
                    "Sorry, I couldn't process that question. ${e.message}"
                }
                val aiMessage = ChatMessage(text = responseText, isUser = false)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.dropLast(1) + aiMessage,
                    isLoading = false
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Something went wrong: ${e.message}",
                    isUser = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.dropLast(1) + errorMessage,
                    isLoading = false
                )
            }
        }
    }

    fun clearChat() {
        _uiState.value = InsightsUiState()
    }
}
