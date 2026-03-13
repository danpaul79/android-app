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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.aicompanion.ui.settings.HelpScreen
import com.example.aicompanion.ui.settings.SettingsScreen
import com.example.aicompanion.ui.settings.TranscriptViewScreen
import com.example.aicompanion.ui.plan.PlanMyDayScreen
import com.example.aicompanion.ui.triage.TaskTriageScreen
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
fun AppNavHost(
    navController: NavHostController,
    deepLinkTaskId: Long? = null,
    openPlanMyDay: Boolean = false,
    openTaskTriage: Boolean = false,
    openCapture: Boolean = false,
    openVoiceCommand: Boolean = false
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }
    val showVoiceBar = currentRoute?.startsWith("capture") != true

    val inboxViewModel: com.example.aicompanion.ui.inbox.InboxViewModel = viewModel()
    val inboxState by inboxViewModel.uiState.collectAsState()
    val projectsViewModel: com.example.aicompanion.ui.projects.ProjectsViewModel = viewModel()
    val projectsState by projectsViewModel.uiState.collectAsState()

    val voiceCommandViewModel: VoiceCommandViewModel = viewModel()

    // Deep-link from notification: navigate to task detail on first composition
    LaunchedEffect(deepLinkTaskId) {
        if (deepLinkTaskId != null) {
            navController.navigate(NavRoutes.TaskDetail.createRoute(deepLinkTaskId))
        }
    }

    // Deep-link from morning check-in: open Plan My Day directly
    LaunchedEffect(openPlanMyDay) {
        if (openPlanMyDay) {
            navController.navigate(NavRoutes.PlanMyDay.route)
        }
    }

    // Deep-link from morning notification: open Task Triage
    LaunchedEffect(openTaskTriage) {
        if (openTaskTriage) {
            navController.navigate(NavRoutes.TaskTriage.route)
        }
    }

    // Deep-link from widget/shortcut: open Capture screen (auto-start recording)
    LaunchedEffect(openCapture) {
        if (openCapture) {
            navController.navigate("capture") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Deep-link from widget/shortcut: open voice command text input
    LaunchedEffect(openVoiceCommand) {
        if (openVoiceCommand) {
            voiceCommandViewModel.showTextInput()
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                // Voice command bar — persistent across ALL screens except Capture (which has its own recorder)
                if (showVoiceBar) {
                    VoiceCommandBar(
                        viewModel = voiceCommandViewModel,
                        onNavigateToPlanMyDay = { _ ->
                            navController.navigate(NavRoutes.PlanMyDay.route)
                        },
                        onNavigateToTaskTriage = {
                            navController.navigate(NavRoutes.TaskTriage.route)
                        }
                    )
                }

                if (showBottomBar) {
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
                                    val badge = when {
                                        item.route == "inbox" && inboxState.inboxCount > 0 -> "${inboxState.inboxCount}"
                                        item.route == "projects" && projectsState.undatedCount > 0 -> "${projectsState.undatedCount}"
                                        else -> null
                                    }
                                    if (badge != null) {
                                        BadgedBox(badge = { Badge { Text(badge) } }) {
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
                    },
                    onNavigateToPlanMyDay = {
                        navController.navigate(NavRoutes.PlanMyDay.route)
                    },
                    onNavigateToTriage = {
                        navController.navigate(NavRoutes.TaskTriage.route)
                    }
                )
            }

            composable("inbox") {
                InboxScreen(
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
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
                    },
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    },
                    onNavigateToSearch = {
                        navController.navigate("search")
                    },
                    viewModel = projectsViewModel
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
                    },
                    onNavigateToHelp = {
                        navController.navigate(NavRoutes.Help.route)
                    }
                )
            }

            composable(NavRoutes.Help.route) {
                HelpScreen(
                    onNavigateBack = { navController.popBackStack() }
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

            composable(NavRoutes.PlanMyDay.route) {
                PlanMyDayScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    }
                )
            }

            composable(NavRoutes.TaskTriage.route) {
                TaskTriageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTask = { id ->
                        navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                    }
                )
            }
        }
    }
}
