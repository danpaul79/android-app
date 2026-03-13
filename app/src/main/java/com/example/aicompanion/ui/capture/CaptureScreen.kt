package com.example.aicompanion.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.audio.RecorderState
import com.example.aicompanion.data.local.entity.Priority
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    projectId: Long?,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    sharedMediaUri: android.net.Uri? = null,
    viewModel: CaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current

    // Set project context on first composition
    LaunchedEffect(projectId) {
        viewModel.setProjectContext(projectId)
    }

    // Handle shared media: enable transcript-only mode and process the file
    LaunchedEffect(sharedMediaUri) {
        if (sharedMediaUri != null) {
            if (!uiState.transcriptOnly) viewModel.toggleTranscriptOnly()
            viewModel.onAudioFilePicked(sharedMediaUri, context)
        }
    }

    // Navigate when done
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onDone()
    }

    // Keep screen on while recording or paused
    val isActiveRecording = uiState.recorderState is RecorderState.Recording ||
            uiState.recorderState is RecorderState.Paused
    DisposableEffect(isActiveRecording) {
        view.keepScreenOn = isActiveRecording
        onDispose { view.keepScreenOn = false }
    }

    // Poll amplitude while recording
    LaunchedEffect(uiState.recorderState) {
        if (uiState.recorderState is RecorderState.Recording) {
            while (true) {
                viewModel.recordAmplitude()
                delay(100L)
            }
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.startRecording()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onAudioFilePicked(it, context) } }

    val isRecording = uiState.recorderState is RecorderState.Recording
    val isPaused = uiState.recorderState is RecorderState.Paused
    val hasAudio = uiState.recorderState is RecorderState.Completed
    val hasTranscript = uiState.transcript.isNotBlank()

    val title = when {
        uiState.projectName != null -> "Capture: ${uiState.projectName}"
        else -> "Capture"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState.extractedItems.isNotEmpty() && !uiState.isSaving && !uiState.isDone) {
                FloatingActionButton(
                    onClick = { viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Save, "Save")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode toggle: Transcript only vs Extract tasks
            if (!hasAudio && !uiState.isTranscribing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (uiState.transcriptOnly) "Transcript only" else "Extract tasks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = !uiState.transcriptOnly,
                            onCheckedChange = { viewModel.toggleTranscriptOnly() }
                        )
                    }
                }
            }

            // Recording controls
            item {
                RecordingSection(
                    isRecording = isRecording,
                    isPaused = isPaused,
                    hasAudio = hasAudio,
                    hasAudioPermission = hasAudioPermission,
                    amplitudes = uiState.amplitudes,
                    recorderState = uiState.recorderState,
                    onStart = {
                        if (hasAudioPermission) viewModel.startRecording()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { viewModel.stopRecording() },
                    onPause = { viewModel.pauseRecording() },
                    onResume = { viewModel.resumeRecording() },
                    onCancel = { viewModel.cancelRecording() },
                    onPickFile = { filePickerLauncher.launch("audio/*") }
                )
            }

            // Text input option (when not recording and no audio yet)
            if (!isRecording && !isPaused && !hasAudio && !hasTranscript && !uiState.isTranscribing) {
                item {
                    var textInput by remember { mutableStateOf("") }
                    Column {
                        Text(
                            "Or type your notes:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type tasks, notes, or ideas...") },
                            minLines = 3,
                            maxLines = 8
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.submitTextInput(textInput)
                                textInput = ""
                            },
                            enabled = textInput.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Submit")
                        }
                    }
                }
            }

            // Audio file info
            if (hasAudio) {
                item {
                    val filePath = uiState.audioFilePath ?: ""
                    val fileName = uiState.selectedFileName ?: File(filePath).name
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Transcribing indicator
            if (uiState.isTranscribing) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Transcribing...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Transcription error
            if (uiState.transcriptionError != null) {
                item {
                    Text(uiState.transcriptionError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Transcript display
            if (hasTranscript) {
                item {
                    Text("Transcript", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(uiState.transcript, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.transcript))
                            android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        if (uiState.transcriptFilePath != null) {
                            TextButton(onClick = { shareTranscriptFile(context, uiState.transcriptFilePath!!) }) {
                                Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }

            // Extract / Re-extract button
            if (hasTranscript && !uiState.isExtracting) {
                if (uiState.transcriptOnly && uiState.extractedItems.isEmpty()) {
                    item {
                        Button(
                            onClick = { viewModel.extractActionItems() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Extract Action Items")
                        }
                    }
                } else if (uiState.extractedItems.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.extractActionItems() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Re-extract Action Items")
                        }
                    }
                }
            }

            // Extracting indicator
            if (uiState.isExtracting) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Extracting action items...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Extraction error
            if (uiState.extractionError != null) {
                item {
                    Text(uiState.extractionError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // New project banner
            if (uiState.newProjectName != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "New project",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    uiState.newProjectName!!,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            IconButton(onClick = { viewModel.dismissNewProject() }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Don't create project",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Extracted items
            if (uiState.extractedItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Action Items", style = MaterialTheme.typography.titleMedium)
                        if (uiState.projectName != null) {
                            Text(
                                "-> ${uiState.projectName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "-> Inbox",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(uiState.extractedItems) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isDuplicate)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.isDuplicate)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (item.isDuplicate) {
                                    Text(
                                        "Possible duplicate" + if (item.duplicateOf != null) " of: ${item.duplicateOf}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.dueDate != null) {
                                        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.dueDate))
                                        Text("Due: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (item.priority != Priority.NONE) {
                                        Text(
                                            item.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (item.priority) {
                                                Priority.URGENT -> MaterialTheme.colorScheme.error
                                                Priority.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                else -> MaterialTheme.colorScheme.tertiary
                                            }
                                        )
                                    }
                                }
                                // Show suggested project if not recording from a specific project
                                if (uiState.projectId == null && item.suggestedProject != null) {
                                    Text(
                                        "-> ${item.suggestedProject}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.removeExtractedItem(index) }) {
                                Icon(Icons.Filled.Close, "Remove", tint = if (item.isDuplicate)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            // No items message (only if extraction was actually attempted)
            if (hasTranscript && uiState.extractedItems.isEmpty() && !uiState.isExtracting && uiState.extractionError == null && !uiState.transcriptOnly) {
                item {
                    Text(
                        "No action items detected in this recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Save hint
            if (uiState.extractedItems.isNotEmpty() && !uiState.isSaving) {
                item {
                    Text(
                        "Tap the save button to add these tasks" +
                                if (uiState.projectName != null) " to ${uiState.projectName}" else " to your Inbox",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isSaving) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Saving...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RecordingSection(
    isRecording: Boolean,
    isPaused: Boolean,
    hasAudio: Boolean,
    hasAudioPermission: Boolean,
    amplitudes: List<Float>,
    recorderState: RecorderState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onPickFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Waveform visualization (during recording)
        if (isRecording || isPaused) {
            WaveformDisplay(
                amplitudes = amplitudes,
                isActive = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Timer
            RecordingTimer(recorderState)
            Spacer(Modifier.height(16.dp))
        }

        // Main record/stop button
        if (!hasAudio) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button (visible during recording/paused)
                if (isRecording || isPaused) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, "Cancel", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }

                // Main mic/stop button
                val buttonColor by animateColorAsState(
                    if (isRecording) MaterialTheme.colorScheme.error
                    else if (isPaused) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    label = "mic_color"
                )
                FloatingActionButton(
                    onClick = {
                        when {
                            isRecording -> onStop()
                            isPaused -> onStop()
                            else -> onStart()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    containerColor = buttonColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (isRecording || isPaused) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording || isPaused) "Stop" else "Record",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Pause/resume button
                if (isRecording) {
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, "Pause", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                } else if (isPaused) {
                    OutlinedButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, "Resume", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    isRecording -> "Recording..."
                    isPaused -> "Paused"
                    else -> "Tap to start recording"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // File picker (only when idle)
            if (!isRecording && !isPaused) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onPickFile) {
                    Icon(Icons.Filled.AudioFile, "Pick file", Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick audio file")
                }
            }
        }

        // Recorder error
        if (recorderState is RecorderState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                recorderState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecordingTimer(recorderState: RecorderState) {
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(recorderState) {
        when (recorderState) {
            is RecorderState.Recording -> {
                while (true) {
                    elapsed = System.currentTimeMillis() - recorderState.startTime
                    delay(500L)
                }
            }
            is RecorderState.Paused -> {
                elapsed = recorderState.elapsedBeforePause
            }
            else -> { elapsed = 0L }
        }
    }

    val seconds = (elapsed / 1000) % 60
    val minutes = (elapsed / 1000) / 60
    Text(
        text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
        style = MaterialTheme.typography.headlineMedium,
        color = when (recorderState) {
            is RecorderState.Recording -> MaterialTheme.colorScheme.error
            is RecorderState.Paused -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
}

@Composable
private fun WaveformDisplay(
    amplitudes: List<Float>,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val barWidth = size.width / 50f
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.8f

        amplitudes.forEachIndexed { index, amp ->
            val barHeight = (amp * maxBarHeight).coerceAtLeast(4f)
            val x = index * barWidth + barWidth / 2f

            drawLine(
                color = barColor,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth * 0.6f
            )
        }
    }
}

private fun shareTranscriptFile(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share transcript"))
    } catch (_: Exception) { }
}
