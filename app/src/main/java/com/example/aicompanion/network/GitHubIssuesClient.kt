package com.example.aicompanion.network

import android.util.Base64
import com.example.aicompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubIssue(val number: Int, val htmlUrl: String)

class GitHubIssuesClient {

    companion object {
        private const val BASE_URL = "https://api.github.com/repos/danpaul79/android-app"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = BuildConfig.GITHUB_PAT.isNotBlank()

    /**
     * Upload an image to the repo and return its raw GitHub URL.
     * Uses the GitHub Contents API: PUT /repos/{owner}/{repo}/contents/{path}
     */
    suspend fun uploadImage(
        imageBytes: ByteArray,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_PAT
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("GitHub token not configured"))
        }

        val base64Content = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val path = "feedback-screenshots/$fileName"

        val jsonBody = JSONObject().apply {
            put("message", "Feedback screenshot: $fileName")
            put("content", base64Content)
            put("branch", "feedback-assets")
        }

        val request = Request.Builder()
            .url("$BASE_URL/contents/$path")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .put(jsonBody.toString().toRequestBody(JSON_TYPE))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Image upload failed (${response.code}): $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val downloadUrl = json.getJSONObject("content").getString("download_url")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensure the feedback-assets branch exists (created from main if needed).
     */
    suspend fun ensureFeedbackBranch(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_PAT
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("GitHub token not configured"))
        }

        // Check if branch already exists
        val checkRequest = Request.Builder()
            .url("$BASE_URL/git/ref/heads/feedback-assets")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .get()
            .build()

        try {
            val checkResponse = client.newCall(checkRequest).execute()
            checkResponse.body?.close()
            if (checkResponse.isSuccessful) {
                return@withContext Result.success(Unit) // Branch exists
            }

            // Get main branch SHA
            val mainRequest = Request.Builder()
                .url("$BASE_URL/git/ref/heads/main")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            val mainResponse = client.newCall(mainRequest).execute()
            val mainBody = mainResponse.body?.string() ?: ""
            if (!mainResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get main branch: $mainBody"))
            }
            val mainSha = JSONObject(mainBody).getJSONObject("object").getString("sha")

            // Create branch
            val createBody = JSONObject().apply {
                put("ref", "refs/heads/feedback-assets")
                put("sha", mainSha)
            }
            val createRequest = Request.Builder()
                .url("$BASE_URL/git/refs")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(createBody.toString().toRequestBody(JSON_TYPE))
                .build()

            val createResponse = client.newCall(createRequest).execute()
            val createResponseBody = createResponse.body?.string() ?: ""
            if (!createResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to create branch: $createResponseBody")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createIssue(
        title: String,
        body: String,
        labels: List<String> = listOf("feedback")
    ): Result<GitHubIssue> = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_PAT
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("GitHub token not configured"))
        }

        val jsonBody = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("labels", JSONArray(labels))
        }

        val request = Request.Builder()
            .url("$BASE_URL/issues")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .post(jsonBody.toString().toRequestBody(JSON_TYPE))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("GitHub API error (${response.code}): $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            Result.success(
                GitHubIssue(
                    number = json.getInt("number"),
                    htmlUrl = json.getString("html_url")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
