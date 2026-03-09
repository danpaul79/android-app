package com.example.aicompanion.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.aicompanion.ui.capture.CaptureScreen
import com.example.aicompanion.ui.dashboard.DashboardScreen
import com.example.aicompanion.ui.inbox.InboxScreen
import com.example.aicompanion.ui.projects.ProjectDetailScreen
import com.example.aicompanion.ui.projects.ProjectsScreen
import com.example.aicompanion.ui.search.SearchScreen
import com.example.aicompanion.ui.task.TaskDetailScreen
import com.example.aicompanion.ui.settings.SettingsScreen
import com.example.aicompanion.ui.settings.TranscriptViewScreen
import com.example.aicompanion.ui.trash.TrashScreen
import com.example.aicompanion.ui.voicecommand.VoiceCommandBar
import com.example.aicompanion.ui.voicecommand.VoiceCommandViewModel

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Dashboard", Icons.Filled.Dashboard, "dashboard"),
    BottomNavItem("Inbox", Icons.Filled.Inbox, "inbox"),
    BottomNavItem("Capture", Icons.Filled.Mic, "capture"),
    BottomNavItem("Projects", Icons.Filled.Folder, "projects")
)

@Composable
fun AppNavHost(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    val inboxViewModel: com.example.aicompanion.ui.inbox.InboxViewModel = viewModel()
    val inboxState by inboxViewModel.uiState.collectAsState()

    val voiceCommandViewModel: VoiceCommandViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // Voice command bar — persistent across all main screens
                    VoiceCommandBar(viewModel = voiceCommandViewModel)

                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    if (item.route == "inbox" && inboxState.inboxCount > 0) {
                                        BadgedBox(badge = {
                                            Badge { Text("${inboxState.inboxCount}") }
                                        }) {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    } else {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    onNavigateToInbox = {
                        navController.navigate("inbox") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToCapture = {
                        navController.navigate("capture") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTrash = {
                        navController.navigate("trash")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("inbox") {
                InboxScreen(
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    },
                    viewModel = inboxViewModel
                )
            }

            composable(
                route = "capture?projectId={projectId}",
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId")
                    ?.takeIf { it != -1L }
                CaptureScreen(
                    projectId = projectId,
                    onNavigateBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }

            composable("projects") {
                ProjectsScreen(
                    onNavigateToProject = { id ->
                        navController.navigate(NavRoutes.ProjectDetail.createRoute(id))
                    },
                    onNavigateToTrash = {
                        navController.navigate("trash")
                    }
                )
            }

            composable("trash") {
                TrashScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("search") {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onViewTranscript = { filePath ->
                        navController.navigate(NavRoutes.TranscriptView.createRoute(filePath))
                    }
                )
            }

            composable(
                route = NavRoutes.TranscriptView.route,
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val filePath = backStackEntry.arguments?.getString("filePath")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: return@composable
                TranscriptViewScreen(
                    filePath = filePath,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = NavRoutes.ProjectDetail.route,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
                ProjectDetailScreen(
                    projectId = projectId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    },
                    onNavigateToCapture = { projId ->
                        navController.navigate(NavRoutes.Capture.createRoute(projId))
                    }
                )
            }

            composable(
                route = NavRoutes.TaskDetail.route,
                arguments = listOf(
                    navArgument("taskId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getLong("taskId") ?: return@composable
                TaskDetailScreen(
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
