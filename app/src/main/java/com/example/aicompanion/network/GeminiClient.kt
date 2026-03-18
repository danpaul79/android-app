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
    val dropDeadDate: String? = null,
    val priority: String? = null,
    val suggestedProject: String? = null,
    val estimatedMinutes: Int? = null,
    val suggestedTags: List<String>? = null
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
    {
      "text": "description of the action item",
      "dueDate": "YYYY-MM-DD or null",
      "dropDeadDate": "YYYY-MM-DD or null",
      "priority": "none|low|medium|high|urgent",
      "suggestedProject": "project name or null",
      "estimatedMinutes": 30,
      "suggestedTags": ["errand", "computer"]
    }
  ]
}

Today's date is $today. Use this to resolve relative dates like "today", "tomorrow", "next week", etc.
$projectSection

Rules:
- Each action item should be a clear, concise task
- Only include genuine action items (things someone needs to do)
- dueDate: a soft/aspirational date if mentioned (e.g. "I want to do this Saturday"). Set to null if not mentioned.
- dropDeadDate: a true hard deadline — only set if the speaker implies real consequences for missing it (e.g. "must be done by Friday", "deadline is the 15th", "expires on"). Set to null otherwise.
- Infer priority from language: "urgent"/"ASAP"/"critical" = urgent, "important"/"soon" = high, "when you get a chance"/"low priority" = low, otherwise = none
- estimatedMinutes: estimate how long the task will realistically take. Use multiples of 10 (10, 20, 30, 60, 90, 120). Quick tasks like calls/emails = 10-20 min. Research/writing = 30-60 min. Complex tasks = 90-120 min.
- suggestedTags: suggest relevant context tags from this set: ["computer", "errand", "phone-call", "waiting-for", "home", "quick", "creative", "financial"]. Only include tags that clearly apply. Empty array if none fit.
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
                // and the actual text response in a later part. Find the last text part.
                // With responseMimeType=json, the value may be a JSONArray or JSONObject
                // directly rather than a String.
                var commands: List<JSONObject>? = null
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        val raw = part.get("text")
                        commands = when (raw) {
                            is JSONArray -> (0 until raw.length()).map { raw.getJSONObject(it) }
                            is JSONObject -> listOf(raw)
                            is String -> {
                                val clean = raw
                                    .replace(Regex("```json\\s*"), "")
                                    .replace(Regex("```\\s*"), "")
                                    .trim()
                                if (clean.startsWith("[")) {
                                    val arr = JSONArray(clean)
                                    (0 until arr.length()).map { arr.getJSONObject(it) }
                                } else {
                                    listOf(JSONObject(clean))
                                }
                            }
                            else -> null
                        }
                    }
                }

                if (commands == null) {
                    return@withContext Result.failure(Exception("No text in response"))
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
3. "change_due_date" — change a task's soft/aspirational due date (e.g. "change due date of buy groceries to Friday", "move dentist to next week")
4. "set_drop_dead_date" — set a hard deadline for a task (e.g. "set drop dead date for passport to July 25", "the deadline for renew passport is July 25", "passport must be done by July 25", "set the holy cow date")
5. "move_task" — move a task to a different project (e.g. "move buy groceries to Home project")
6. "delete_task" — trash a task (e.g. "delete buy groceries", "remove the dentist task")
7. "rename_task" — rename or update a task's name (e.g. "rename buy groceries to buy organic groceries", "update the summer camp task to add Eliza and Lauren at the end")
8. "create_project" — create a new project/folder/list (e.g. "create a project called Home", "create a folder named Vacation", "make a new list called Shopping", "create project Evans driver's license")
9. "plan_my_day" — open the day planning screen, optionally with a capacity (e.g. "plan my day", "help me plan my day", "I have 45 minutes", "I have an hour today", "what should I work on", "what can I get done in 2 hours")
10. "review_tasks" — open the task triage/review screen (e.g. "review my tasks", "triage", "what needs attention", "clean up my tasks")
11. "set_recurrence" — make a task repeat on a schedule or stop repeating (e.g. "make buy groceries weekly", "set water plants to repeat every 3 days", "make workout recurring daily", "stop repeating buy groceries", "remove recurrence from water plants")
12. "unrecognized" — if the command doesn't match any of the above

The words "project", "folder", and "list" are synonyms. Any of them means create a project.
"drop dead date", "deadline", "holy cow date", "must be done by" all refer to set_drop_dead_date.

Return this JSON structure. If the transcript contains multiple commands, return an array. For a single command, return just the object:
{
  "command": "create_task|complete_task|change_due_date|set_drop_dead_date|move_task|delete_task|rename_task|create_project|plan_my_day|review_tasks|set_recurrence|unrecognized",
  "taskName": "exact or closest matching task name from the list, or the new task name for create_task",
  "projectName": "project name if mentioned, or the new project name for create_project, or null",
  "dueDate": "YYYY-MM-DD or null",
  "priority": "none|low|medium|high|urgent",
  "newName": "the final complete task name for rename_task, or null",
  "capacityMinutes": "integer minutes for plan_my_day if the user specified a duration (e.g. 45 for '45 minutes', 60 for 'an hour'), or null",
  "recurrenceRule": "DAILY|WEEKLY|MONTHLY|YEARLY or null (for set_recurrence; null = stop repeating)",
  "recurrenceInterval": "integer, default 1 (e.g. 'every 2 weeks' = 2, 'every 3 days' = 3)"
}

Rules:
- For existing tasks, match to the closest task name from the list. Use the EXACT name from the list.
- For create_task, use the user's wording as the task name (clean and concise).
- For rename_task: "newName" must be the FINAL desired task name text, not a description of what to change. Apply the user's requested modification to the current task name and return the result.
- For set_drop_dead_date: use the "dueDate" field to carry the date value.
- For plan_my_day: extract duration if mentioned ("an hour" = 60, "90 minutes" = 90, "2 hours" = 120); set capacityMinutes to null if no duration mentioned.
- For set_recurrence: "recurring", "repeating", "repeat", "every day/week/month/year" all map to set_recurrence. Use recurrenceRule (DAILY/WEEKLY/MONTHLY/YEARLY) and recurrenceInterval (default 1). To stop: set recurrenceRule to null.
- If a project is mentioned, match it to the closest project name from the list.
- Infer priority from language cues (urgent/ASAP = urgent, important = high, etc.), default to "none".

Voice command transcript:
$transcript"""
    }

    data class TaskEnrichment(
        val id: Long,
        val estimatedMinutes: Int,
        val tags: List<String>
    )

    /**
     * Enrich a batch of tasks with effort estimates and context tags.
     * Tasks are passed as id+text pairs; only tasks needing enrichment should be sent.
     * Returns a map of task ID -> enrichment result.
     */
    suspend fun enrichTasks(tasks: List<Pair<Long, String>>): Result<List<TaskEnrichment>> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return Result.failure(Exception("Gemini API key not configured."))

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildEnrichmentPrompt(tasks)
                val requestJson = buildRequestJson(prompt)

                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(Exception("Gemini API failed (${response.code}): $errorBody"))
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val enrichments = parseEnrichmentResponse(responseBody, tasks)
                Result.success(enrichments)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildEnrichmentPrompt(tasks: List<Pair<Long, String>>): String {
        val taskList = tasks.joinToString("\n") { (id, text) -> "$id: $text" }
        return """Analyze these tasks and return effort estimates and context tags for each.

Tasks (id: text):
$taskList

Return a JSON array with one entry per task:
[
  {
    "id": 123,
    "estimatedMinutes": 30,
    "tags": ["errand", "phone-call"]
  }
]

Rules:
- estimatedMinutes: REQUIRED — always provide a best-guess estimate even for vague tasks. Use multiples of 10: 10, 20, 30, 60, 90, or 120. NEVER return 0 or null. If unsure, default to 30. Examples: quick phone call = 10, send email = 10, read/review doc = 20, buy something at a store = 30, schedule appointment = 10, research topic = 60, write something = 60, complex multi-step task = 90-120.
- tags: choose from ["computer", "errand", "phone-call", "waiting-for", "home", "quick", "creative", "financial"]. Only include clearly applicable tags. Empty array if none fit.
- Return ONLY the JSON array, no other text. Every task ID from the input must have an entry."""
    }

    private fun parseEnrichmentResponse(responseBody: String, tasks: List<Pair<Long, String>>): List<TaskEnrichment> {
        val json = JSONObject(responseBody)
        val candidates = json.getJSONArray("candidates")
        if (candidates.length() == 0) return emptyList()

        val content = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val arr = JSONArray(content)
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.getJSONObject(i)
            val id = item.optLong("id", -1)
            if (id < 0) return@mapNotNull null
            val tagsArr = item.optJSONArray("tags")
            val tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList()
            TaskEnrichment(
                id = id,
                estimatedMinutes = item.optInt("estimatedMinutes", 30).coerceAtLeast(10),
                tags = tags
            )
        }
    }

    data class BreakdownItem(
        val text: String,
        val estimatedMinutes: Int,
        val suggestedTags: List<String>
    )

    suspend fun breakdownTask(
        taskText: String,
        taskNotes: String?,
        existingProjects: List<String> = emptyList()
    ): Result<List<BreakdownItem>> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return Result.failure(Exception("Gemini API key not configured."))

        return withContext(Dispatchers.IO) {
            try {
                val notesSection = if (!taskNotes.isNullOrBlank()) "\nNotes: $taskNotes" else ""
                val projectSection = if (existingProjects.isNotEmpty()) {
                    "\nKnown projects: ${existingProjects.joinToString(", ")}"
                } else ""

                val prompt = """Break down this task into 2-5 concrete, actionable subtasks.

Task: "$taskText"$notesSection$projectSection

Return a JSON array:
[
  {
    "text": "concrete subtask description",
    "estimatedMinutes": 30,
    "suggestedTags": ["computer"]
  }
]

Rules:
- Each subtask should be a single, clear action that can be completed in one sitting
- estimatedMinutes: use multiples of 10 (10, 20, 30, 60, 90). Never return 0.
- suggestedTags: choose from ["computer", "errand", "phone-call", "waiting-for", "home", "quick", "creative", "financial"]. Only include clearly applicable tags. Empty array if none fit.
- Return ONLY the JSON array, no other text"""

                val requestJson = buildRequestJson(prompt)
                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(Exception("Gemini API failed (${response.code}): $errorBody"))
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val json = JSONObject(responseBody)
                val content = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .replace(Regex("```json\\s*"), "")
                    .replace(Regex("```\\s*"), "")
                    .trim()

                val arr = JSONArray(content)
                val items = (0 until arr.length()).map { i ->
                    val item = arr.getJSONObject(i)
                    val tagsArr = item.optJSONArray("suggestedTags")
                    val tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList()
                    BreakdownItem(
                        text = item.getString("text"),
                        estimatedMinutes = item.optInt("estimatedMinutes", 30).coerceAtLeast(10),
                        suggestedTags = tags
                    )
                }
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
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
            val tagsArray = item.optJSONArray("suggestedTags")
            val tags = if (tagsArray != null) {
                (0 until tagsArray.length()).map { tagsArray.getString(it) }
            } else emptyList()
            GeminiActionItem(
                text = item.getString("text"),
                dueDate = if (item.isNull("dueDate")) null else item.optString("dueDate").takeIf { it.isNotBlank() && it != "null" },
                dropDeadDate = if (item.isNull("dropDeadDate")) null else item.optString("dropDeadDate").takeIf { it.isNotBlank() && it != "null" },
                priority = if (item.isNull("priority")) null else item.optString("priority"),
                suggestedProject = if (item.isNull("suggestedProject")) null else item.optString("suggestedProject").takeIf { it.isNotBlank() && it != "null" },
                estimatedMinutes = item.optInt("estimatedMinutes", 0).takeIf { it > 0 },
                suggestedTags = tags.ifEmpty { null }
            )
        }
        val newProject = if (parsed.has("newProject") && !parsed.isNull("newProject"))
            parsed.optString("newProject").takeIf { it.isNotBlank() }
        else null
        return GeminiExtractionResult(actionItems = items, newProject = newProject)
    }

    /**
     * Ask Gemini an insight question about the user's tasks.
     * @param question The user's question
     * @param taskDataContext A summary of all task data (active tasks, events, projects, etc.)
     * @return The AI's response as a plain text string
     */
    suspend fun askInsight(question: String, taskDataContext: String): Result<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return Result.failure(Exception("Gemini API key not configured."))

        return withContext(Dispatchers.IO) {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd, EEEE", Locale.US).format(Date())
                val prompt = """You are an AI productivity coach analyzing a user's task data in their personal task management app called Pocket Pilot. Answer their question thoughtfully and concisely using the data provided.

Today's date: $today

## Task Data
$taskDataContext

## User's Question
$question

## Instructions
- Answer based ONLY on the data provided. If you don't have enough data, say so.
- Be concise but insightful. Use bullet points or short paragraphs.
- Include specific task names and numbers when relevant.
- If you spot patterns or actionable suggestions, mention them.
- Keep your response under 300 words.
- Use plain text only (no markdown headers or code blocks)."""

                val requestJson = buildInsightRequestJson(prompt)
                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(Exception("Gemini API failed (${response.code}): $errorBody"))
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val text = parseTopicResponse(responseBody)
                    ?: return@withContext Result.failure(Exception("No response text"))
                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildInsightRequestJson(prompt: String): String {
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
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
        return json.toString()
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
