package com.example.aicompanion.ui.voicecommand

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.example.aicompanion.domain.command.VoiceCommand
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Persistent voice command bar that sits above the bottom navigation.
 * When idle, shows a compact mic + keyboard row.
 * When recording, shows waveform + controls (user can navigate freely).
 * When processing/done, shows status.
 */
@Composable
fun VoiceCommandBar(
    viewModel: VoiceCommandViewModel,
    modifier: Modifier = Modifier,
    onNavigateToPlanMyDay: (capacityMinutes: Int?) -> Unit = {},
    onNavigateToTaskTriage: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    // Show transcript result dialog
    val transcriptResult = uiState.transcriptResult
    if (transcriptResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTranscriptResult() },
            title = { Text("Transcript") },
            text = {
                Text(
                    text = transcriptResult,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(transcriptResult))
                    viewModel.dismissTranscriptResult()
                }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTranscriptResult() }) { Text("Close") }
            }
        )
    }

    // Handle navigation commands emitted by the processor
    val navCmd = uiState.navigationCommand
    LaunchedEffect(navCmd) {
        if (navCmd is VoiceCommand.PlanMyDay) {
            viewModel.clearNavigationCommand()
            onNavigateToPlanMyDay(navCmd.capacityMinutes)
        } else if (navCmd is VoiceCommand.ReviewTasks) {
            viewModel.clearNavigationCommand()
            onNavigateToTaskTriage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = when (uiState.commandState) {
            CommandState.Recording -> MaterialTheme.colorScheme.errorContainer
            CommandState.Processing -> MaterialTheme.colorScheme.surfaceVariant
            CommandState.Success -> MaterialTheme.colorScheme.primaryContainer
            CommandState.Error -> MaterialTheme.colorScheme.errorContainer
            CommandState.TextInput -> MaterialTheme.colorScheme.surfaceVariant
            CommandState.Idle -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = 3.dp
    ) {
        when (uiState.commandState) {
            CommandState.Idle -> {
                IdleBar(
                    isTranscriptMode = uiState.transcriptMode,
                    onRecord = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onTextInput = { viewModel.showTextInput() },
                    onToggleTranscriptMode = { viewModel.toggleTranscriptMode() }
                )
            }

            CommandState.Recording -> {
                RecordingBar(
                    amplitudes = uiState.amplitudes,
                    isTranscriptMode = uiState.transcriptMode,
                    onStop = { viewModel.stopAndProcess() },
                    onCancel = { viewModel.cancel() },
                    onSwitchToText = { viewModel.showTextInput() }
                )
            }

            CommandState.TextInput -> {
                TextInputBar(
                    text = uiState.textDraft,
                    onTextChange = { viewModel.updateTextDraft(it) },
                    onSubmit = { viewModel.submitText() },
                    onCancel = { viewModel.cancel() },
                    onSwitchToVoice = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }

            CommandState.Processing -> {
                StatusBar(message = uiState.message ?: "Processing...", isLoading = true)
            }

            CommandState.Success -> {
                StatusBar(
                    message = uiState.message ?: "Done",
                    isSuccess = true,
                    onDismiss = { viewModel.dismiss() }
                )
            }

            CommandState.Error -> {
                StatusBar(
                    message = uiState.message ?: "Error",
                    isError = true,
                    onDismiss = { viewModel.dismiss() }
                )
            }
        }
    }
}

@Composable
private fun IdleBar(
    isTranscriptMode: Boolean,
    onRecord: () -> Unit,
    onTextInput: () -> Unit,
    onToggleTranscriptMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onTextInput, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Type", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRecord, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Icon(Icons.Filled.Mic, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isTranscriptMode) "Record" else "Voice command", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(4.dp))
        FilterChip(
            selected = isTranscriptMode,
            onClick = onToggleTranscriptMode,
            label = {
                Icon(Icons.Filled.Article, null, Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("Transcript", style = MaterialTheme.typography.labelSmall)
            }
        )
    }
}

@Composable
private fun RecordingBar(
    amplitudes: List<Float>,
    isTranscriptMode: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onSwitchToText: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isTranscriptMode) "Recording transcript..." else "Recording...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSwitchToText, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, "Switch to text", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer)
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "Cancel", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer)
            }
            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Stop, "Stop & process", Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }

        // Waveform
        val waveColor = MaterialTheme.colorScheme.error
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barWidth = size.width / 30f
            amplitudes.takeLast(30).forEachIndexed { i, amp ->
                val barHeight = (amp * size.height).coerceAtLeast(2f)
                val x = i * barWidth + barWidth / 2
                drawLine(
                    color = waveColor,
                    start = Offset(x, size.height / 2 - barHeight / 2),
                    end = Offset(x, size.height / 2 + barHeight / 2),
                    strokeWidth = barWidth * 0.5f
                )
            }
        }
    }
}

@Composable
private fun TextInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onSwitchToVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. \"create task buy groceries due tomorrow\"") },
            minLines = 1,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onSwitchToVoice) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Voice")
                }
            }
            TextButton(
                onClick = onSubmit,
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Submit")
            }
        }
    }
}

@Composable
private fun StatusBar(
    message: String,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            isSuccess -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            isError -> Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}
