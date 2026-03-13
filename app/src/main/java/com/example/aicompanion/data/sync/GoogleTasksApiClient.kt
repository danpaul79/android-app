package com.example.aicompanion.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Google Tasks REST API using OkHttp.
 * Consistent with how the app calls Deepgram and Gemini.
 */
class GoogleTasksApiClient(private val tokenManager: TokenManager) {

    companion object {
        private const val TAG = "GoogleTasksApi"
        private const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // --- Task Lists ---

    suspend fun listTaskLists(): List<GoogleTaskList> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GoogleTaskList>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$BASE_URL/users/@me/lists?maxResults=100")
                pageToken?.let { append("&pageToken=$it") }
            }
            val json = executeGet(url)
            json.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    results.add(GoogleTaskList.fromJson(items.getJSONObject(i)))
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        results
    }

    suspend fun createTaskList(title: String): GoogleTaskList = withContext(Dispatchers.IO) {
        val body = JSONObject().put("title", title)
        val json = executePost("$BASE_URL/users/@me/lists", body)
        GoogleTaskList.fromJson(json)
    }

    suspend fun updateTaskList(id: String, title: String): GoogleTaskList = withContext(Dispatchers.IO) {
        val body = JSONObject().put("title", title)
        val json = executePatch("$BASE_URL/users/@me/lists/$id", body)
        GoogleTaskList.fromJson(json)
    }

    suspend fun deleteTaskList(id: String) = withContext(Dispatchers.IO) {
        executeDelete("$BASE_URL/users/@me/lists/$id")
    }

    // --- Tasks ---

    suspend fun listTasks(
        taskListId: String,
        updatedMin: String? = null,
        showDeleted: Boolean = false,
        showCompleted: Boolean = true,
        showHidden: Boolean = false
    ): List<GoogleTask> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GoogleTask>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$BASE_URL/lists/$taskListId/tasks?maxResults=100")
                append("&showDeleted=$showDeleted")
                append("&showCompleted=$showCompleted")
                append("&showHidden=$showHidden")
                updatedMin?.let { append("&updatedMin=$it") }
                pageToken?.let { append("&pageToken=$it") }
            }
            val json = executeGet(url)
            json.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    results.add(GoogleTask.fromJson(items.getJSONObject(i)))
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        results
    }

    suspend fun createTask(taskListId: String, task: GoogleTask): GoogleTask = withContext(Dispatchers.IO) {
        val json = executePost("$BASE_URL/lists/$taskListId/tasks", task.toJson())
        GoogleTask.fromJson(json)
    }

    suspend fun updateTask(taskListId: String, taskId: String, task: GoogleTask): GoogleTask = withContext(Dispatchers.IO) {
        val json = executePatch("$BASE_URL/lists/$taskListId/tasks/$taskId", task.toJson())
        GoogleTask.fromJson(json)
    }

    suspend fun deleteTask(taskListId: String, taskId: String) = withContext(Dispatchers.IO) {
        executeDelete("$BASE_URL/lists/$taskListId/tasks/$taskId")
    }

    // --- HTTP helpers ---

    private suspend fun executeGet(url: String): JSONObject {
        val token = tokenManager.getAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return executeRequest(request, token)
    }

    private suspend fun executePost(url: String, body: JSONObject): JSONObject {
        val token = tokenManager.getAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return executeRequest(request, token)
    }

    private suspend fun executePatch(url: String, body: JSONObject): JSONObject {
        val token = tokenManager.getAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return executeRequest(request, token)
    }

    private suspend fun executeDelete(url: String) {
        val token = tokenManager.getAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            if (response.code == 401) {
                tokenManager.invalidateToken(token)
            }
            throw GoogleTasksApiException(response.code, errorBody)
        }
    }

    private suspend fun executeRequest(request: Request, token: String): JSONObject {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"

        if (!response.isSuccessful) {
            if (response.code == 401) {
                tokenManager.invalidateToken(token)
            }
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw GoogleTasksApiException(response.code, responseBody)
        }

        return JSONObject(responseBody)
    }
}

class GoogleTasksApiException(val code: Int, val body: String) :
    Exception("Google Tasks API error $code: $body")

// --- Data classes for Google Tasks API responses ---

data class GoogleTaskList(
    val id: String,
    val title: String,
    val updated: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject) = GoogleTaskList(
            id = json.getString("id"),
            title = json.getString("title"),
            updated = json.optString("updated").takeIf { it.isNotEmpty() }
        )
    }

    fun toJson() = JSONObject().apply {
        put("title", title)
    }
}

data class GoogleTask(
    val id: String? = null,
    val title: String,
    val notes: String? = null,
    val due: String? = null,
    val status: String = "needsAction",  // "needsAction" or "completed"
    val completed: String? = null,
    val deleted: Boolean = false,
    val updated: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject) = GoogleTask(
            id = json.optString("id").takeIf { it.isNotEmpty() },
            title = json.optString("title", ""),
            notes = json.optString("notes").takeIf { it.isNotEmpty() },
            due = json.optString("due").takeIf { it.isNotEmpty() },
            status = json.optString("status", "needsAction"),
            completed = json.optString("completed").takeIf { it.isNotEmpty() },
            deleted = json.optBoolean("deleted", false),
            updated = json.optString("updated").takeIf { it.isNotEmpty() }
        )
    }

    fun toJson() = JSONObject().apply {
        put("title", title)
        notes?.let { put("notes", it) }
        due?.let { put("due", it) }
        put("status", status)
        completed?.let { put("completed", it) }
    }
}
