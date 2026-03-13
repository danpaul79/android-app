package com.example.aicompanion.ui.feedback

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FeedbackUiState(
    val description: String = "",
    val screenshotUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val successIssueUrl: String? = null,
    val successIssueNumber: Int? = null,
    val error: String? = null,
    val isConfigured: Boolean = true
)

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val gitHubClient = (application as AICompanionApplication).container.gitHubIssuesClient

    private val _uiState = MutableStateFlow(FeedbackUiState(isConfigured = gitHubClient.isConfigured()))
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun onDescriptionChange(text: String) {
        _uiState.value = _uiState.value.copy(description = text, error = null)
    }

    fun addScreenshot(uri: Uri) {
        val current = _uiState.value.screenshotUris
        if (current.size < 3 && uri !in current) {
            _uiState.value = _uiState.value.copy(screenshotUris = current + uri)
        }
    }

    fun removeScreenshot(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            screenshotUris = _uiState.value.screenshotUris - uri
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.description.isBlank()) return
        if (state.isSubmitting) return

        _uiState.value = state.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            val title = generateTitle(state.description)
            val body = buildIssueBody(state.description)

            gitHubClient.createIssue(title, body).fold(
                onSuccess = { issue ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successIssueUrl = issue.htmlUrl,
                        successIssueNumber = issue.number
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = e.message ?: "Failed to submit feedback"
                    )
                }
            )
        }
    }

    fun resetForAnother() {
        _uiState.value = FeedbackUiState(isConfigured = gitHubClient.isConfigured())
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun generateTitle(description: String): String {
        val firstLine = description.lines().first().trim()
        return if (firstLine.length <= 80) firstLine
        else firstLine.take(77) + "..."
    }

    private fun buildIssueBody(description: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val app = getApplication<Application>()
        val appVersion = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }

        val screenshotNote = if (_uiState.value.screenshotUris.isNotEmpty()) {
            "\n\n> ${_uiState.value.screenshotUris.size} screenshot(s) attached in-app (not uploaded to GitHub)\n"
        } else ""

        return """## Description
$description
$screenshotNote
## Device Info
- **Device**: ${Build.MANUFACTURER} ${Build.MODEL}
- **Android**: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
- **App version**: $appVersion
- **Timestamp**: $timestamp

---
*Submitted via Pocket Pilot in-app feedback*"""
    }
}
