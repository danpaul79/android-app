package com.example.aicompanion.audio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TranscriptFileHelper {

    /**
     * Saves a transcript to the Downloads folder so it's visible in file managers.
     * Uses the audio file's name as the base name.
     * Filename format: {audioName} - Transcript.txt
     *
     * Also saves a local copy alongside the audio file in the app directory.
     *
     * Returns the display path of the saved transcript.
     */
    fun saveTranscript(
        context: Context,
        audioFilePath: String,
        transcript: String,
        originalFileName: String? = null
    ): String {
        val audioFile = File(audioFilePath)
        val baseName = originalFileName?.substringBeforeLast('.') ?: audioFile.nameWithoutExtension
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val transcriptFileName = "$baseName - Transcript $timestamp.txt"

        // Save local copy alongside audio in app directory
        val localFile = File(audioFile.parentFile, transcriptFileName)
        localFile.writeText(transcript)

        // Also save to Downloads for easy access
        saveToDownloads(context, transcriptFileName, transcript, "text/plain")

        return localFile.absolutePath
    }

    /**
     * Saves the raw Deepgram JSON response alongside the audio file.
     */
    fun saveRawJson(audioFilePath: String, json: String, originalFileName: String? = null): String {
        val audioFile = File(audioFilePath)
        val baseName = originalFileName?.substringBeforeLast('.') ?: audioFile.nameWithoutExtension
        val jsonFile = File(
            audioFile.parentFile,
            "$baseName - Deepgram ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        )
        jsonFile.writeText(json)
        return jsonFile.absolutePath
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AI Companion")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    os.write(content.toByteArray())
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
        } else {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AI Companion"
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            File(downloadsDir, fileName).writeText(content)
        }
    }
}
