package com.example.aicompanion.network

import com.example.aicompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class TranscriptionResult(
    val transcript: String,
    val rawJson: String
)

class TranscriptionClient {

    companion object {
        private const val DEEPGRAM_URL =
            "https://api.deepgram.com/v1/listen?model=nova-3&language=en&smart_format=true&utterances=true&diarize=true&paragraphs=true"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(audioFile: File): Result<TranscriptionResult> {
        val apiKey = BuildConfig.DEEPGRAM_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(
                Exception("Deepgram API key not configured. Add DEEPGRAM_API_KEY to local.properties.")
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val mimeType = guessMimeType(audioFile.name)

                // Stream the file to Deepgram without buffering entirely in memory
                val requestBody = object : RequestBody() {
                    override fun contentType() = mimeType.toMediaType()
                    override fun contentLength() = audioFile.length()
                    override fun writeTo(sink: BufferedSink) {
                        audioFile.source().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }

                val request = Request.Builder()
                    .url(DEEPGRAM_URL)
                    .addHeader("Authorization", "Token $apiKey")
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
