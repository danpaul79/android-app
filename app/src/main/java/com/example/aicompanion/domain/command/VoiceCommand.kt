package com.example.aicompanion.domain.command

import com.example.aicompanion.data.local.entity.Priority

sealed class VoiceCommand {
    data class CreateTask(
        val text: String,
        val projectName: String? = null,
        val dueDate: Long? = null,
        val priority: Priority = Priority.NONE
    ) : VoiceCommand()

    data class CompleteTask(val taskName: String) : VoiceCommand()

    data class ChangeDueDate(val taskName: String, val dueDate: Long?) : VoiceCommand()

    data class MoveTask(val taskName: String, val projectName: String) : VoiceCommand()

    data class DeleteTask(val taskName: String) : VoiceCommand()

    data class RenameTask(val taskName: String, val newName: String) : VoiceCommand()

    data class Unrecognized(val transcript: String) : VoiceCommand()
}
