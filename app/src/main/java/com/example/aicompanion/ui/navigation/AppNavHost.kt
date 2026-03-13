package com.example.aicompanion.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.aicompanion.ui.feedback.FeedbackScreen
import com.example.aicompanion.ui.settings.HelpScreen
import com.example.aicompanion.ui.settings.SettingsScreen
import com.example.aicompanion.ui.settings.TranscriptViewScreen
import com.example.aicompanion.ui.plan.PlanMyDayScreen
import com.example.aicompanion.ui.triage.TaskTriageScreen
import com.example.aicompanion.ui.trash.TrashScreen
import com.example.aicompanion.ui.voicecommand.VoiceCommandBar
import com.example.aicompanion.ui.voicecommand.VoiceCommandViewModel
import kotlinx.coroutines.launch

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Dashboard", Icons.Filled.Dashboard, "dashboard"),
    BottomNavItem("Inbox", Icons.Filled.Inbox, "inbox"),
    BottomNavItem("Projects", Icons.Filled.Folder, "projects"),
    BottomNavItem("Settings", Icons.Filled.Settings, "settings")
)

@Composable
fun AppNavHost(
    navController: NavHostController,
    deepLinkTaskId: Long? = null,
    openPlanMyDay: Boolean = false,
    openTaskTriage: Boolean = false,
    openCapture: Boolean = false,
    openVoiceCommand: Boolean = false,
    sharedMediaUri: android.net.Uri? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute == "main"
    val showVoiceBar = currentRoute == "main" || (currentRoute != null && !currentRoute.startsWith("capture"))

    val inboxViewModel: com.example.aicompanion.ui.inbox.InboxViewModel = viewModel()
    val inboxState by inboxViewModel.uiState.collectAsState()
    val projectsViewModel: com.example.aicompanion.ui.projects.ProjectsViewModel = viewModel()
    val projectsState by projectsViewModel.uiState.collectAsState()

    val voiceCommandViewModel: VoiceCommandViewModel = viewModel()

    // Pager state for swipeable tabs
    val pagerState = rememberPagerState(initialPage = 0) { bottomNavItems.size }
    val pagerScope = rememberCoroutineScope()

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

    // Deep-link from morning notification: open Task Triage (smart candidates, not full mode)
    LaunchedEffect(openTaskTriage) {
        if (openTaskTriage) {
            navController.navigate(NavRoutes.TaskTriage.createRoute(fullMode = false))
        }
    }

    // Deep-link from widget/shortcut: navigate to Capture screen
    LaunchedEffect(openCapture) {
        if (openCapture) {
            navController.navigate("capture_standalone")
        }
    }

    // Share intent: navigate to Capture screen with shared media
    LaunchedEffect(sharedMediaUri) {
        if (sharedMediaUri != null) {
            navController.navigate("capture_shared")
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
                // Voice command bar — persistent on main screens except Settings tab
                val onSettingsTab = currentRoute == "main" && pagerState.currentPage == 3
                if (showVoiceBar && !onSettingsTab) {
                    VoiceCommandBar(
                        viewModel = voiceCommandViewModel,
                        onNavigateToPlanMyDay = { _ ->
                            navController.navigate(NavRoutes.PlanMyDay.route)
                        },
                        onNavigateToTaskTriage = {
                            navController.navigate(NavRoutes.TaskTriage.createRoute(fullMode = true))
                        }
                    )
                }

                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEachIndexed { index, item ->
                            val selected = pagerState.currentPage == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        pagerScope.launch {
                                            pagerState.animateScrollToPage(index)
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
            startDestination = "main",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("main") {
                HorizontalPager(
                    state = pagerState,
                ) { page ->
                    when (page) {
                        0 -> DashboardScreen(
                            onNavigateToTask = { id ->
                                navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                            onNavigateToInbox = {
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onNavigateToCapture = {
                                navController.navigate("capture_standalone")
                            },
                            onNavigateToTrash = {
                                navController.navigate("trash")
                            },
                            onNavigateToSettings = {
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            },
                            onNavigateToPlanMyDay = {
                                navController.navigate(NavRoutes.PlanMyDay.route)
                            },
                            onNavigateToTriage = {
                                navController.navigate(NavRoutes.TaskTriage.createRoute(fullMode = true))
                            }
                        )
                        1 -> InboxScreen(
                            onNavigateToTask = { id ->
                                navController.navigate(NavRoutes.TaskDetail.createRoute(id))
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                            viewModel = inboxViewModel
                        )
                        2 -> ProjectsScreen(
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
                        3 -> SettingsScreen(
                            onNavigateBack = {
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            onViewTranscript = { filePath ->
                                navController.navigate(NavRoutes.TranscriptView.createRoute(filePath))
                            },
                            onNavigateToHelp = {
                                navController.navigate(NavRoutes.Help.route)
                            },
                            onNavigateToFeedback = {
                                navController.navigate(NavRoutes.Feedback.route)
                            }
                        )
                    }
                }
            }

            // Standalone Capture screen (from Dashboard top bar)
            composable("capture_standalone") {
                CaptureScreen(
                    projectId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() }
                )
            }

            // Capture screen with shared media (from share intent)
            composable("capture_shared") {
                CaptureScreen(
                    projectId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                    sharedMediaUri = sharedMediaUri
                )
            }

            // Capture with projectId (from Project Detail → capture into project)
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

            composable(NavRoutes.Feedback.route) {
                FeedbackScreen(
                    onNavigateBack = { navController.popBackStack() }
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

            composable(
                route = NavRoutes.TaskTriage.route,
                arguments = listOf(
                    navArgument("fullMode") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
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
