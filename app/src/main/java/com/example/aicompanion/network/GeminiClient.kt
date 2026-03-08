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
    val topic: String?
)

class GeminiClient {

    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
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
- If no action items exist, return {"actionItems": []}
- Return ONLY the JSON, no other text

Transcript:
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
        return GeminiExtractionResult(actionItems = items, topic = null)
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
