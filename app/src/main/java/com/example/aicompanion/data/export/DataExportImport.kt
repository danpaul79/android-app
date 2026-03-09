package com.example.aicompanion.data.export

import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.Project
import org.json.JSONArray
import org.json.JSONObject

data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val projects: List<Project>,
    val tasks: List<ActionItem>
)

object DataExportImport {

    fun toJson(data: ExportData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)

        val projectsArray = JSONArray()
        for (project in data.projects) {
            projectsArray.put(JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("color", project.color)
                put("icon", project.icon)
                put("sortOrder", project.sortOrder)
                put("isArchived", project.isArchived)
                put("createdAt", project.createdAt)
            })
        }
        root.put("projects", projectsArray)

        val tasksArray = JSONArray()
        for (task in data.tasks) {
            tasksArray.put(JSONObject().apply {
                put("id", task.id)
                put("projectId", task.projectId ?: JSONObject.NULL)
                put("text", task.text)
                put("notes", task.notes ?: JSONObject.NULL)
                put("dueDate", task.dueDate ?: JSONObject.NULL)
                put("priority", task.priority.name)
                put("isCompleted", task.isCompleted)
                put("completedAt", task.completedAt ?: JSONObject.NULL)
                put("createdAt", task.createdAt)
                put("updatedAt", task.updatedAt)
            })
        }
        root.put("tasks", tasksArray)

        return root.toString(2)
    }

    fun fromJson(json: String): ExportData {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", 0)

        val projects = mutableListOf<Project>()
        val projectsArray = root.getJSONArray("projects")
        for (i in 0 until projectsArray.length()) {
            val obj = projectsArray.getJSONObject(i)
            projects.add(
                Project(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    color = obj.optInt("color", Project.DEFAULT_COLOR),
                    icon = obj.optString("icon", "folder"),
                    sortOrder = obj.optInt("sortOrder", 0),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val tasks = mutableListOf<ActionItem>()
        val tasksArray = root.getJSONArray("tasks")
        for (i in 0 until tasksArray.length()) {
            val obj = tasksArray.getJSONObject(i)
            tasks.add(
                ActionItem(
                    id = obj.getLong("id"),
                    projectId = if (obj.isNull("projectId")) null else obj.getLong("projectId"),
                    text = obj.getString("text"),
                    notes = if (obj.isNull("notes")) null else obj.optString("notes"),
                    dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"),
                    priority = try {
                        Priority.valueOf(obj.optString("priority", "NONE"))
                    } catch (_: Exception) {
                        Priority.NONE
                    },
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        return ExportData(
            version = version,
            exportedAt = exportedAt,
            projects = projects,
            tasks = tasks
        )
    }
}
