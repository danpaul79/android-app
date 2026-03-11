package com.example.aicompanion.domain.command

import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.repository.TaskRepository
import com.example.aicompanion.network.GeminiClient
import com.example.aicompanion.network.TranscriptionClient
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class CommandResult(
    val success: Boolean,
    val message: String,
    val command: VoiceCommand? = null,
    val transcript: String? = null
)

class VoiceCommandProcessor(
    private val geminiClient: GeminiClient,
    private val transcriptionClient: TranscriptionClient,
    private val repo: TaskRepository
) {

    suspend fun processAudioFile(audioFile: File): CommandResult {
        // Step 1: Transcribe
        val transcriptionResult = transcriptionClient.transcribe(audioFile)
        val transcript = transcriptionResult.getOrElse {
            return CommandResult(false, "Transcription failed: ${it.message}")
        }.transcript

        if (transcript.isBlank()) {
            return CommandResult(false, "Could not understand audio")
        }

        return processTranscript(transcript).copy(transcript = transcript)
    }

    suspend fun processTranscript(transcript: String): CommandResult {
        // Strip speaker labels (e.g. "Speaker 0: ") from diarized transcripts
        val cleanTranscript = transcript.replace(Regex("Speaker \\d+:\\s*"), "").trim()
        if (cleanTranscript.isBlank()) {
            return CommandResult(false, "Could not understand audio")
        }

        // Step 2: Get context (existing tasks + projects)
        val tasks = repo.getAllActiveItemTexts()
        val taskNames = tasks.map { it.text }
        val projectNames = repo.getAllProjectNames()

        // Step 3: Parse command(s) via Gemini
        val parseResult = geminiClient.parseCommand(cleanTranscript, taskNames, projectNames)
        val jsonList = parseResult.getOrElse {
            return CommandResult(false, "Command parsing failed: ${it.message}", transcript = transcript)
        }

        // Step 4: Deduplicate and execute each command
        val seen = mutableSetOf<String>()
        val results = mutableListOf<CommandResult>()
        for (json in jsonList) {
            val key = json.toString()
            if (key in seen) continue
            seen.add(key)
            val command = parseJson(json, transcript)
            val result = executeCommand(command, tasks, projectNames)
            results.add(result)
        }

        // Summarize results
        val successes = results.filter { it.success }
        val failures = results.filter { !it.success }
        return when {
            results.size == 1 -> results.first().copy(transcript = transcript)
            failures.isEmpty() -> CommandResult(
                true,
                successes.joinToString("\n") { it.message },
                successes.lastOrNull()?.command,
                transcript = transcript
            )
            successes.isEmpty() -> CommandResult(
                false,
                failures.joinToString("\n") { it.message },
                failures.lastOrNull()?.command,
                transcript = transcript
            )
            else -> CommandResult(
                true,
                (successes.map { it.message } + failures.map { "Failed: ${it.message}" }).joinToString("\n"),
                successes.lastOrNull()?.command,
                transcript = transcript
            )
        }
    }

    private fun parseJson(json: JSONObject, transcript: String): VoiceCommand {
        val commandType = json.optString("command", "unrecognized")
        val taskName = json.optString("taskName", "").takeIf { it.isNotBlank() }
        val projectName = json.optString("projectName", "").takeIf { it.isNotBlank() && !json.isNull("projectName") }
        val dueDateStr = json.optString("dueDate", "").takeIf { it.isNotBlank() && !json.isNull("dueDate") }
        val priorityStr = json.optString("priority", "none")
        val newName = json.optString("newName", "").takeIf { it.isNotBlank() && !json.isNull("newName") }

        val dueDate = dueDateStr?.let { parseDateToMillis(it) }
        val priority = parsePriority(priorityStr)

        return when (commandType) {
            "create_task" -> VoiceCommand.CreateTask(
                text = taskName ?: return VoiceCommand.Unrecognized(transcript),
                projectName = projectName,
                dueDate = dueDate,
                priority = priority
            )
            "complete_task" -> VoiceCommand.CompleteTask(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript)
            )
            "change_due_date" -> VoiceCommand.ChangeDueDate(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript),
                dueDate = dueDate
            )
            "set_drop_dead_date" -> VoiceCommand.SetDropDeadDate(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript),
                dropDeadDate = dueDate  // Gemini returns it in the dueDate field
            )
            "move_task" -> VoiceCommand.MoveTask(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript),
                projectName = projectName ?: return VoiceCommand.Unrecognized(transcript)
            )
            "delete_task" -> VoiceCommand.DeleteTask(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript)
            )
            "rename_task" -> VoiceCommand.RenameTask(
                taskName = taskName ?: return VoiceCommand.Unrecognized(transcript),
                newName = newName ?: return VoiceCommand.Unrecognized(transcript)
            )
            "create_project" -> VoiceCommand.CreateProject(
                name = projectName ?: taskName ?: return VoiceCommand.Unrecognized(transcript)
            )
            "plan_my_day" -> {
                val minutes = json.optInt("capacityMinutes", 0).takeIf { it > 0 }
                VoiceCommand.PlanMyDay(capacityMinutes = minutes)
            }
            else -> VoiceCommand.Unrecognized(transcript)
        }
    }

    private suspend fun executeCommand(
        command: VoiceCommand,
        tasks: List<ActionItem>,
        projectNames: List<String>
    ): CommandResult {
        return when (command) {
            is VoiceCommand.CreateTask -> {
                val projectId = command.projectName?.let { name ->
                    resolveProjectId(name)
                }
                repo.createTask(
                    text = command.text,
                    projectId = projectId,
                    dueDate = command.dueDate,
                    priority = command.priority
                )
                val dest = command.projectName ?: "Inbox"
                CommandResult(true, "Created: ${command.text} → $dest", command)
            }

            is VoiceCommand.CompleteTask -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                repo.toggleCompleted(task.id, true)
                CommandResult(true, "Completed: ${task.text}", command)
            }

            is VoiceCommand.ChangeDueDate -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                repo.setDueDate(task.id, command.dueDate)
                val dateStr = command.dueDate?.let {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(it)
                } ?: "none"
                CommandResult(true, "Due date → $dateStr: ${task.text}", command)
            }

            is VoiceCommand.SetDropDeadDate -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                repo.setDropDeadDate(task.id, command.dropDeadDate)
                val dateStr = command.dropDeadDate?.let {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(it)
                } ?: "none"
                CommandResult(true, "Deadline → $dateStr: ${task.text}", command)
            }

            is VoiceCommand.MoveTask -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                val projectId = resolveProjectId(command.projectName)
                    ?: return CommandResult(false, "Project not found: \"${command.projectName}\"", command)
                repo.assignToProject(task.id, projectId)
                CommandResult(true, "Moved to ${command.projectName}: ${task.text}", command)
            }

            is VoiceCommand.DeleteTask -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                repo.trashTask(task.id)
                CommandResult(true, "Trashed: ${task.text}", command)
            }

            is VoiceCommand.RenameTask -> {
                val task = findTask(command.taskName, tasks)
                    ?: return CommandResult(false, "Task not found: \"${command.taskName}\"", command)
                repo.updateTaskText(task.id, command.newName)
                CommandResult(true, "Renamed: ${task.text} → ${command.newName}", command)
            }

            is VoiceCommand.CreateProject -> {
                repo.createProject(command.name)
                CommandResult(true, "Created project: ${command.name}", command)
            }

            is VoiceCommand.PlanMyDay -> {
                val msg = if (command.capacityMinutes != null)
                    "Opening plan for ${command.capacityMinutes}m..."
                else
                    "Opening Plan My Day..."
                CommandResult(true, msg, command)
            }

            is VoiceCommand.Unrecognized -> {
                CommandResult(false, "Didn't understand: \"${command.transcript}\"", command)
            }
        }
    }

    private fun findTask(name: String, tasks: List<ActionItem>): ActionItem? {
        // Exact match first
        tasks.find { it.text.equals(name, ignoreCase = true) }?.let { return it }
        // Containment match
        tasks.find { it.text.contains(name, ignoreCase = true) || name.contains(it.text, ignoreCase = true) }?.let { return it }
        // Word overlap match
        val nameWords = name.lowercase().split(" ").toSet()
        return tasks.maxByOrNull { task ->
            val taskWords = task.text.lowercase().split(" ").toSet()
            nameWords.intersect(taskWords).size
        }?.takeIf { task ->
            val taskWords = task.text.lowercase().split(" ").toSet()
            val overlap = nameWords.intersect(taskWords).size
            overlap >= 2 || (overlap == 1 && nameWords.size <= 2)
        }
    }

    private suspend fun resolveProjectId(name: String): Long? {
        val projects = repo.getAllProjectNamesWithIds()
        // Exact match
        projects.find { it.first.equals(name, ignoreCase = true) }?.let { return it.second }
        // Contains match
        projects.find { it.first.contains(name, ignoreCase = true) || name.contains(it.first, ignoreCase = true) }?.let { return it.second }
        return null
    }

    private fun parseDateToMillis(dateStr: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(dateStr) ?: return null
            // Set to noon local time to avoid timezone issues
            val cal = java.util.Calendar.getInstance()
            cal.time = date
            cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePriority(str: String): Priority {
        return when (str.lowercase()) {
            "low" -> Priority.LOW
            "medium" -> Priority.MEDIUM
            "high" -> Priority.HIGH
            "urgent" -> Priority.URGENT
            else -> Priority.NONE
        }
    }
}
