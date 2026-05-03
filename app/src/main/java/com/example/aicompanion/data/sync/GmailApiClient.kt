package com.example.aicompanion.data.sync

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GmailApiClient(private val tokenManager: TokenManager) {

    companion object {
        private const val TAG = "GmailApi"
        private const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun listMessageIds(query: String, maxResults: Int = 50): List<String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/messages?q=$encoded&maxResults=$maxResults"
        val json = executeGet(url)
        val ids = mutableListOf<String>()
        json.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                ids.add(arr.getJSONObject(i).getString("id"))
            }
        }
        ids
    }

    suspend fun getMessage(id: String): GmailMessage = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/messages/$id?format=full"
        val json = executeGet(url)
        parseMessage(json)
    }

    private fun parseMessage(json: JSONObject): GmailMessage {
        val id = json.getString("id")
        val snippet = json.optString("snippet", "")
        val payload = json.optJSONObject("payload")
        val headers = payload?.optJSONArray("headers")
        var subject = ""
        var from = ""
        var date = ""
        if (headers != null) {
            for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                when (h.optString("name").lowercase()) {
                    "subject" -> subject = h.optString("value", "")
                    "from" -> from = h.optString("value", "")
                    "date" -> date = h.optString("value", "")
                }
            }
        }
        val body = payload?.let { extractBody(it) }.orEmpty()
        return GmailMessage(
            id = id,
            subject = subject,
            from = from,
            date = date,
            snippet = snippet,
            body = body.ifBlank { snippet }
        )
    }

    /**
     * Walk the MIME tree, prefer text/plain. Fallback to text/html (stripped).
     */
    private fun extractBody(part: JSONObject): String {
        val mimeType = part.optString("mimeType", "")
        val bodyData = part.optJSONObject("body")?.optString("data", "").orEmpty()

        if (mimeType == "text/plain" && bodyData.isNotEmpty()) {
            return decodeBase64Url(bodyData)
        }

        val parts = part.optJSONArray("parts")
        if (parts != null) {
            // First pass: prefer text/plain
            for (i in 0 until parts.length()) {
                val sub = parts.getJSONObject(i)
                val subBody = extractBody(sub)
                if (subBody.isNotBlank() && sub.optString("mimeType").startsWith("text/plain")) {
                    return subBody
                }
            }
            // Second pass: anything textual
            for (i in 0 until parts.length()) {
                val sub = parts.getJSONObject(i)
                val subBody = extractBody(sub)
                if (subBody.isNotBlank()) return subBody
            }
        }

        if (mimeType == "text/html" && bodyData.isNotEmpty()) {
            return stripHtml(decodeBase64Url(bodyData))
        }
        return ""
    }

    private fun decodeBase64Url(data: String): String = try {
        String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode body: ${e.message}")
        ""
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("\\s+"), " ")
            .trim()

    private suspend fun executeGet(url: String): JSONObject {
        val token = tokenManager.getAccessToken(TokenManager.GMAIL_SCOPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            if (response.code == 401) tokenManager.invalidateToken(token)
            Log.e(TAG, "Gmail API error ${response.code}: $responseBody")
            throw GmailApiException(response.code, responseBody)
        }
        return JSONObject(responseBody)
    }
}

class GmailApiException(val code: Int, val body: String) :
    Exception("Gmail API error $code: $body")

data class GmailMessage(
    val id: String,
    val subject: String,
    val from: String,
    val date: String,
    val snippet: String,
    val body: String
)
