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

    data class SetDropDeadDate(val taskName: String, val dropDeadDate: Long?) : VoiceCommand()

    data class MoveTask(val taskName: String, val projectName: String) : VoiceCommand()

    data class DeleteTask(val taskName: String) : VoiceCommand()

    data class RenameTask(val taskName: String, val newName: String) : VoiceCommand()

    data class CreateProject(val name: String) : VoiceCommand()

    /** "Plan my day" / "I have X minutes" — navigate to PlanMyDay screen. */
    data class PlanMyDay(val capacityMinutes: Int? = null) : VoiceCommand()

    /** "Review my tasks" / "triage" — navigate to Task Triage screen. */
    data object ReviewTasks : VoiceCommand()

    data class Unrecognized(val transcript: String) : VoiceCommand()
}
