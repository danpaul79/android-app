package com.example.aicompanion.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Record : NavRoutes("record")
    data object Detail : NavRoutes("detail/{voiceNoteId}") {
        fun createRoute(id: Long) = "detail/$id"
    }
}
