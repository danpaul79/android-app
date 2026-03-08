package com.example.aicompanion.audio

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TranscriptFileHelper {

    /**
     * Saves a transcript as a text file in the same directory as the audio file.
     * Filename format: {audioName} - Transcript {yyyyMMdd_HHmmss}.txt
     *
     * Returns the path to the saved transcript file.
     */
    fun saveTranscript(audioFilePath: String, transcript: String): String {
        val audioFile = File(audioFilePath)
        val audioName = audioFile.nameWithoutExtension
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val transcriptFile = File(
            audioFile.parentFile,
            "$audioName - Transcript $timestamp.txt"
        )
        transcriptFile.writeText(transcript)
        return transcriptFile.absolutePath
    }

    /**
     * Saves the raw Deepgram JSON response alongside the audio file.
     */
    fun saveRawJson(audioFilePath: String, json: String): String {
        val audioFile = File(audioFilePath)
        val audioName = audioFile.nameWithoutExtension
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val jsonFile = File(
            audioFile.parentFile,
            "$audioName - Deepgram $timestamp.json"
        )
        jsonFile.writeText(json)
        return jsonFile.absolutePath
    }
}
