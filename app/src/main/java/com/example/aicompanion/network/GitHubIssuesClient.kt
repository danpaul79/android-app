package com.example.aicompanion.network

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
