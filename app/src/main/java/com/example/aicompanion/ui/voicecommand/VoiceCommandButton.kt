package com.example.aicompanion.ui.voicecommand

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun VoiceCommandButton(
    modifier: Modifier = Modifier,
    viewModel: VoiceCommandViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    Box(modifier = modifier) {
        when (uiState.commandState) {
            CommandState.Idle -> {
                SmallFloatingActionButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice command")
                }
            }

            CommandState.Recording -> {
                RecordingOverlay(
                    amplitudes = uiState.amplitudes,
                    onStop = { viewModel.stopAndProcess() },
                    onCancel = { viewModel.cancel() }
                )
            }

            CommandState.Processing -> {
                StatusCard(
                    message = uiState.message ?: "Processing...",
                    isLoading = true,
                    onDismiss = { viewModel.cancel() }
                )
            }

            CommandState.Success -> {
                StatusCard(
                    message = uiState.message ?: "Done",
                    isSuccess = true,
                    onDismiss = { viewModel.dismiss() }
                )
            }

            CommandState.Error -> {
                StatusCard(
                    message = uiState.message ?: "Error",
                    isError = true,
                    onDismiss = { viewModel.dismiss() }
                )
            }
        }
    }
}

@Composable
private fun RecordingOverlay(
    amplitudes: List<Float>,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Listening...",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = pulseAlpha)
            )

            Spacer(Modifier.height(8.dp))

            // Mini waveform
            val waveColor = MaterialTheme.colorScheme.error
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val barWidth = size.width / 20f
                amplitudes.takeLast(20).forEachIndexed { i, amp ->
                    val barHeight = (amp * size.height).coerceAtLeast(2f)
                    val x = i * barWidth + barWidth / 2
                    drawLine(
                        color = waveColor,
                        start = Offset(x, size.height / 2 - barHeight / 2),
                        end = Offset(x, size.height / 2 + barHeight / 2),
                        strokeWidth = barWidth * 0.6f
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        "Cancel",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.Filled.Stop, "Stop and process")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    message: String,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    isError: Boolean = false,
    onDismiss: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isSuccess -> MaterialTheme.colorScheme.primaryContainer
            isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "statusColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                isSuccess -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                isError -> Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(200.dp)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}
