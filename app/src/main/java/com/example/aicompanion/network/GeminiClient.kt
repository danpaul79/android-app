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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeminiActionItem(
    val text: String,
    val dueDate: String? = null,
    val priority: String? = null,
    val suggestedProject: String? = null
)

data class GeminiExtractionResult(
    val actionItems: List<GeminiActionItem>,
    val newProject: String? = null,
    val topic: String? = null
)

class GeminiClient {

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val COMMAND_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun extractActionItems(
        transcript: String,
        projectNames: List<String> = emptyList()
    ): Result<GeminiExtractionResult> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("Gemini API key not configured. Add GEMINI_API_KEY to local.properties.")
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildExtractionPrompt(transcript, projectNames)
                val requestJson = buildRequestJson(prompt)

                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(
                        Exception("Gemini API failed (${response.code}): $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response from Gemini"))

                val result = parseExtractionResponse(responseBody)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun extractTopic(transcript: String): Result<String?> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("Gemini API key not configured. Add GEMINI_API_KEY to local.properties.")
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildTopicPrompt(transcript)
                val requestJson = buildRequestJson(prompt)

                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Gemini API failed (${response.code})")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val topic = parseTopicResponse(responseBody)
                Result.success(topic)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildExtractionPrompt(transcript: String, projectNames: List<String> = emptyList()): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val projectSection = if (projectNames.isNotEmpty()) {
            """
Known projects: ${projectNames.joinToString(", ")}
- If a task clearly relates to one of these projects, set "suggestedProject" to that exact project name
- If unclear, set "suggestedProject" to null (task goes to Inbox)"""
        } else ""

        return """Extract action items from the following transcript. Return a JSON object with this exact structure:
{
  "newProject": "project name or null",
  "actionItems": [
    {"text": "description of the action item", "dueDate": "YYYY-MM-DD or null", "priority": "none|low|medium|high|urgent", "suggestedProject": "project name or null"}
  ]
}

Today's date is $today. Use this to resolve relative dates like "today", "tomorrow", "next week", etc.
$projectSection

Rules:
- Each action item should be a clear, concise task
- Only include genuine action items (things someone needs to do)
- If a due date is mentioned or implied (including relative dates), include it in YYYY-MM-DD format
- If no due date is mentioned, set dueDate to null
- Infer priority from language: "urgent"/"ASAP"/"critical" = urgent, "important"/"soon" = high, "when you get a chance"/"low priority" = low, otherwise = none
- If the speaker asks to create/start a new project (e.g. "create a new project called X", "start a project for X", "new project: X"), set "newProject" to the project name. Tasks related to that project should have "suggestedProject" set to the same name.
- If no new project is requested, set "newProject" to null
- If no action items exist, return {"actionItems": [], "newProject": null}
- Return ONLY the JSON, no other text

Transcript:
$transcript"""
    }

    suspend fun parseCommand(
        transcript: String,
        taskNames: List<String> = emptyList(),
        projectNames: List<String> = emptyList()
    ): Result<List<JSONObject>> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Gemini API key not configured."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildCommandPrompt(transcript, taskNames, projectNames)
                val requestJson = buildCommandRequestJson(prompt)

                val request = Request.Builder()
                    .url("$COMMAND_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(
                        Exception("Gemini API failed (${response.code}): $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val json = JSONObject(responseBody)
                val parts = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")

                // With thinking enabled, the model returns a "thought" part first
                // and the actual text response in a later part. Find the text part.
                var text: String? = null
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        text = part.getString("text")
                    }
                }

                if (text == null) {
                    return@withContext Result.failure(Exception("No text in response"))
                }

                val cleanJson = text
                    .replace(Regex("```json\\s*"), "")
                    .replace(Regex("```\\s*"), "")
                    .trim()

                // Support both single object and array of commands
                val commands = if (cleanJson.startsWith("[")) {
                    val arr = JSONArray(cleanJson)
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } else {
                    listOf(JSONObject(cleanJson))
                }

                Result.success(commands)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildCommandPrompt(
        transcript: String,
        taskNames: List<String>,
        projectNames: List<String>
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val taskSection = if (taskNames.isNotEmpty()) {
            "\nExisting tasks:\n${taskNames.joinToString("\n") { "- $it" }}"
        } else ""
        val projectSection = if (projectNames.isNotEmpty()) {
            "\nExisting projects: ${projectNames.joinToString(", ")}"
        } else ""

        return """Parse this voice command for a task management app. Return a JSON object with the command type and parameters.

Today's date is $today. Use this to resolve relative dates like "today", "tomorrow", "next Monday", "next week", etc.
$taskSection
$projectSection

Supported commands:
1. "create_task" — create a new task (e.g. "create task buy groceries", "add task call dentist due tomorrow in Health")
2. "complete_task" — mark a task as done (e.g. "complete buy groceries", "mark dentist appointment as done")
3. "change_due_date" — change a task's due date (e.g. "change due date of buy groceries to Friday", "move dentist to next week")
4. "move_task" — move a task to a different project (e.g. "move buy groceries to Home project")
5. "delete_task" — trash a task (e.g. "delete buy groceries", "remove the dentist task")
6. "rename_task" — rename or update a task's name (e.g. "rename buy groceries to buy organic groceries", "update the summer camp task to add Eliza and Lauren at the end")
7. "unrecognized" — if the command doesn't match any of the above

Return this JSON structure. If the transcript contains multiple commands, return an array. For a single command, return just the object:
{
  "command": "create_task|complete_task|change_due_date|move_task|delete_task|rename_task|unrecognized",
  "taskName": "exact or closest matching task name from the list, or the new task name for create_task",
  "projectName": "project name if mentioned, or null",
  "dueDate": "YYYY-MM-DD or null",
  "priority": "none|low|medium|high|urgent",
  "newName": "the final complete task name for rename_task, or null"
}

Rules:
- For existing tasks, match to the closest task name from the list. Use the EXACT name from the list.
- For create_task, use the user's wording as the task name (clean and concise).
- For rename_task: "newName" must be the FINAL desired task name text, not a description of what to change. Apply the user's requested modification to the current task name and return the result.
- If a project is mentioned, match it to the closest project name from the list.
- Infer priority from language cues (urgent/ASAP = urgent, important = high, etc.), default to "none".

Voice command transcript:
$transcript"""
    }

    private fun buildTopicPrompt(transcript: String): String {
        return """What is the main topic of the following transcript? Respond with ONLY a short topic label (1-5 words). No explanation, no punctuation, just the topic.

Transcript:
$transcript"""
    }

    private fun buildRequestJson(prompt: String): String {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 2048)
            })
        }
        return json.toString()
    }

    private fun buildCommandRequestJson(prompt: String): String {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 1.0)
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", "low")
                })
            })
        }
        return json.toString()
    }

    private fun parseExtractionResponse(responseBody: String): GeminiExtractionResult {
        val json = JSONObject(responseBody)
        val candidates = json.getJSONArray("candidates")
        if (candidates.length() == 0) {
            return GeminiExtractionResult(emptyList(), null)
        }

        val content = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        // Strip markdown code fences if present
        val cleanJson = content
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val parsed = JSONObject(cleanJson)
        val itemsArray = parsed.getJSONArray("actionItems")
        val items = (0 until itemsArray.length()).map { i ->
            val item = itemsArray.getJSONObject(i)
            GeminiActionItem(
                text = item.getString("text"),
                dueDate = if (item.isNull("dueDate")) null else item.optString("dueDate"),
                priority = if (item.isNull("priority")) null else item.optString("priority"),
                suggestedProject = if (item.isNull("suggestedProject")) null else item.optString("suggestedProject")
            )
        }
        val newProject = if (parsed.has("newProject") && !parsed.isNull("newProject"))
            parsed.optString("newProject").takeIf { it.isNotBlank() }
        else null
        return GeminiExtractionResult(actionItems = items, newProject = newProject)
    }

    private fun parseTopicResponse(responseBody: String): String? {
        val json = JSONObject(responseBody)
        val candidates = json.getJSONArray("candidates")
        if (candidates.length() == 0) return null

        val text = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        return text.ifBlank { null }
    }
}
