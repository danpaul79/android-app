package com.example.aicompanion.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Dashboard : NavRoutes("dashboard")
    data object Inbox : NavRoutes("inbox")
    data object Projects : NavRoutes("projects")
    data object ProjectDetail : NavRoutes("project/{projectId}") {
        fun createRoute(id: Long) = "project/$id"
    }
    data object Capture : NavRoutes("capture?projectId={projectId}") {
        fun createRoute(projectId: Long? = null) =
            if (projectId != null) "capture?projectId=$projectId" else "capture"
    }
    data object TaskDetail : NavRoutes("task/{taskId}") {
        fun createRoute(id: Long) = "task/$id"
    }
    data object Search : NavRoutes("search")
    data object Settings : NavRoutes("settings")
    data object PlanMyDay : NavRoutes("plan_my_day")
    data object TranscriptView : NavRoutes("transcript/{filePath}") {
        fun createRoute(filePath: String) = "transcript/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    }
}
