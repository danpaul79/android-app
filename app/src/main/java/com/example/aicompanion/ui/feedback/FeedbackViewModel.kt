package com.example.aicompanion.ui.feedback

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicompanion.AICompanionApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class FeedbackUiState(
    val description: String = "",
    val screenshotUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val successIssueUrl: String? = null,
    val successIssueNumber: Int? = null,
    val error: String? = null,
    val warning: String? = null,
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
            try {
                // Upload screenshots if any
                val screenshotUrls = mutableListOf<String>()
                if (state.screenshotUris.isNotEmpty()) {
                    // Ensure the feedback-assets branch exists
                    gitHubClient.ensureFeedbackBranch().onFailure { e ->
                        Log.w("Feedback", "Failed to create feedback branch: ${e.message}")
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    for ((index, uri) in state.screenshotUris.withIndex()) {
                        val bytes = readAndCompressImage(uri)
                        if (bytes != null) {
                            val fileName = "${timestamp}_${UUID.randomUUID().toString().take(8)}_${index + 1}.jpg"
                            gitHubClient.uploadImage(bytes, fileName).onSuccess { url ->
                                screenshotUrls.add(url)
                            }.onFailure { e ->
                                Log.w("Feedback", "Failed to upload screenshot ${index + 1}: ${e.message}")
                            }
                        }
                    }
                }

                val screenshotWarning = if (state.screenshotUris.isNotEmpty() && screenshotUrls.isEmpty()) {
                    "Screenshots could not be uploaded (check GitHub token permissions)"
                } else if (screenshotUrls.size < state.screenshotUris.size) {
                    "${state.screenshotUris.size - screenshotUrls.size} screenshot(s) could not be uploaded"
                } else null

                val title = generateTitle(state.description)
                val body = buildIssueBody(state.description, screenshotUrls)

                gitHubClient.createIssue(title, body).fold(
                    onSuccess = { issue ->
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            successIssueUrl = issue.htmlUrl,
                            successIssueNumber = issue.number,
                            warning = screenshotWarning
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            error = e.message ?: "Failed to submit feedback"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Failed to submit feedback"
                )
            }
        }
    }

    fun resetForAnother() {
        _uiState.value = FeedbackUiState(isConfigured = gitHubClient.isConfigured())
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Read an image URI, downscale to max 1024px on longest side, compress as JPEG.
     * Returns null if the image can't be read.
     */
    private fun readAndCompressImage(uri: Uri): ByteArray? {
        return try {
            val app = getApplication<Application>()
            val inputStream = app.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            // Downscale to max 1024px to keep uploads small
            val maxDim = 1024
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                val newWidth = (original.width * scale).toInt()
                val newHeight = (original.height * scale).toInt()
                Bitmap.createScaledBitmap(original, newWidth, newHeight, true).also {
                    if (it !== original) original.recycle()
                }
            } else {
                original
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            scaled.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.w("Feedback", "Failed to read screenshot: ${e.message}")
            null
        }
    }

    private fun generateTitle(description: String): String {
        val firstLine = description.lines().first().trim()
        return if (firstLine.length <= 80) firstLine
        else firstLine.take(77) + "..."
    }

    private fun buildIssueBody(description: String, screenshotUrls: List<String>): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val app = getApplication<Application>()
        val appVersion = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }

        val screenshotSection = if (screenshotUrls.isNotEmpty()) {
            val images = screenshotUrls.mapIndexed { i, url ->
                "### Screenshot ${i + 1}\n![Screenshot ${i + 1}]($url)"
            }.joinToString("\n\n")
            "\n\n## Screenshots\n$images\n"
        } else ""

        return """## Description
$description
$screenshotSection
## Device Info
- **Device**: ${Build.MANUFACTURER} ${Build.MODEL}
- **Android**: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
- **App version**: $appVersion
- **Timestamp**: $timestamp

---
*Submitted via Pocket Pilot in-app feedback*"""
    }
}
