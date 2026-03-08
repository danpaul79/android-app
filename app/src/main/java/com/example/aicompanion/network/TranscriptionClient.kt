package com.example.aicompanion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class TranscriptionResult(
    val transcript: String,
    val rawJson: String
)

class TranscriptionClient {

    companion object {
        const val CLOUD_FUNCTION_URL =
            "https://us-central1-transcription-app-481721.cloudfunctions.net/stream-audio-to-deepgram"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS) // long recordings can take a while
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(audioFile: File): Result<TranscriptionResult> {
        return withContext(Dispatchers.IO) {
            try {
                val mimeType = guessMimeType(audioFile.name)
                val requestBody = audioFile.asRequestBody(mimeType.toMediaType())

                val request = Request.Builder()
                    .url(CLOUD_FUNCTION_URL)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(
                        Exception("Transcription failed (${response.code}): $errorBody")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                val json = JSONObject(responseBody)
                val transcript = extractTranscript(json)

                Result.success(
                    TranscriptionResult(
                        transcript = transcript,
                        rawJson = responseBody
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractTranscript(json: JSONObject): String {
        try {
            val channels = json.getJSONObject("results")
                .getJSONArray("channels")
            if (channels.length() == 0) return ""

            val alternatives = channels.getJSONObject(0)
                .getJSONArray("alternatives")
            if (alternatives.length() == 0) return ""

            val alt = alternatives.getJSONObject(0)

            // Prefer paragraphs transcript (includes speaker labels)
            if (alt.has("paragraphs")) {
                val paragraphs = alt.getJSONObject("paragraphs")
                if (paragraphs.has("transcript")) {
                    return paragraphs.getString("transcript")
                }
            }

            // Fall back to plain transcript
            return alt.optString("transcript", "")
        } catch (e: Exception) {
            return ""
        }
    }

    private fun guessMimeType(filename: String): String {
        return when (filename.substringAfterLast('.').lowercase()) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }
}
