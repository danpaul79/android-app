package com.example.aicompanion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aicompanion.ui.detail.DetailScreen
import com.example.aicompanion.ui.home.HomeScreen
import com.example.aicompanion.ui.record.RecordScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToRecord = {
                    navController.navigate(NavRoutes.Record.route)
                },
                onNavigateToDetail = { id ->
                    navController.navigate(NavRoutes.Detail.createRoute(id))
                }
            )
        }

        composable(NavRoutes.Record.route) {
            RecordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNoteSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(NavRoutes.Detail.createRoute(id))
                }
            )
        }

        composable(
            route = NavRoutes.Detail.route,
            arguments = listOf(
                navArgument("voiceNoteId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val voiceNoteId = backStackEntry.arguments?.getLong("voiceNoteId") ?: return@composable
            DetailScreen(
                voiceNoteId = voiceNoteId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
