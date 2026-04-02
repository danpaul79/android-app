package com.example.aicompanion.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(
    taskId: Long,
    onNavigateBack: () -> Unit,
    viewModel: FocusTimerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                windowInsets = WindowInsets(0)
            )
        }
    ) { innerPadding ->
        val task = uiState.task
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Task name
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Estimate info
                if (task.estimatedMinutes > 1) {
                    val m = task.estimatedMinutes
                    val label = if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}"
                    Text(
                        text = "Estimated: $label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(40.dp))

                // Timer display
                Box(contentAlignment = Alignment.Center) {
                    val targetSec = uiState.targetSeconds
                    if (targetSec != null) {
                        CircularProgressIndicator(
                            progress = { uiState.progress },
                            modifier = Modifier.size(200.dp),
                            strokeWidth = 8.dp,
                            color = if (uiState.isOvertime) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Show remaining time if countdown, elapsed if count-up
                        val displaySeconds = if (uiState.targetSeconds != null) {
                            uiState.remainingSeconds ?: 0
                        } else {
                            uiState.elapsedSeconds
                        }
                        val minutes = displaySeconds / 60
                        val seconds = displaySeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                            color = if (uiState.isOvertime) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.isOvertime) {
                            Text(
                                text = "overtime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (uiState.targetSeconds == null && uiState.isRunning) {
                            Text(
                                text = "no estimate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Elapsed time (when in countdown mode)
                if (uiState.targetSeconds != null && uiState.elapsedSeconds > 0) {
                    val em = uiState.elapsedSeconds / 60
                    val es = uiState.elapsedSeconds % 60
                    Text(
                        text = "Elapsed: %02d:%02d".format(em, es),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(48.dp))

                if (uiState.isFinished) {
                    // Finished state — show summary and complete option
                    val em = uiState.elapsedSeconds / 60
                    Text(
                        text = "Focused for ${em}m${if (uiState.targetSeconds != null) " / est. ${(uiState.targetSeconds ?: 0) / 60}m" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.completeTask()
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark Complete")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text("Done (keep open)")
                    }
                } else {
                    // Control buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!uiState.isRunning && !uiState.isPaused) {
                            // Not started yet
                            Button(
                                onClick = { viewModel.start() },
                                modifier = Modifier.size(72.dp),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Start", modifier = Modifier.size(32.dp))
                            }
                        } else if (uiState.isRunning) {
                            // Running — show pause and stop
                            FilledTonalButton(
                                onClick = { viewModel.pause() },
                                modifier = Modifier.size(72.dp),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(32.dp))
                            }
                            OutlinedButton(
                                onClick = { viewModel.finish() },
                                modifier = Modifier.size(72.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(32.dp))
                            }
                        } else {
                            // Paused — show resume and stop
                            Button(
                                onClick = { viewModel.start() },
                                modifier = Modifier.size(72.dp),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(32.dp))
                            }
                            OutlinedButton(
                                onClick = { viewModel.finish() },
                                modifier = Modifier.size(72.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
