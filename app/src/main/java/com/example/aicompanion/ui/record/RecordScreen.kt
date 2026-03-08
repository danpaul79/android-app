package com.example.aicompanion.ui.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.audio.RecorderState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onNavigateBack: () -> Unit,
    onNoteSaved: (Long) -> Unit,
    viewModel: RecordViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val uiState by viewModel.uiState.collectAsState()

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
        if (granted) {
            viewModel.startRecording()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onAudioFilePicked(it, context) }
    }

    LaunchedEffect(uiState.savedNoteId) {
        uiState.savedNoteId?.let { id ->
            onNoteSaved(id)
        }
    }

    val isRecording = uiState.recorderState is RecorderState.Recording
    val hasAudio = uiState.recorderState is RecorderState.Completed
    val hasTranscript = uiState.transcript.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Note") },
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
            if (hasTranscript && uiState.savedNoteId == null && !uiState.isSaving) {
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
            // Record button + File picker
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val buttonColor by animateColorAsState(
                        if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        label = "mic_color"
                    )
                    FloatingActionButton(
                        onClick = {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else if (hasAudioPermission) {
                                viewModel.startRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = buttonColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            isRecording -> "Recording..."
                            hasAudio -> "Recording saved"
                            else -> "Tap to start recording"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isRecording && !hasAudio) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("audio/*") }
                        ) {
                            Icon(Icons.Filled.AudioFile, "Pick file", Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick audio file")
                        }
                    }
                }
            }

            // Show audio file info
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Transcribe button
                if (!hasTranscript && !uiState.isTranscribing) {
                    item {
                        Button(
                            onClick = {
                                activity?.let { viewModel.transcribe(it) }
                            },
                            enabled = activity != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Transcribe, "Transcribe", Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transcribe with Deepgram")
                        }
                    }
                }
            }

            // Transcribing progress
            if (uiState.isTranscribing) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transcribing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Transcription error
            if (uiState.transcriptionError != null) {
                item {
                    Text(
                        text = uiState.transcriptionError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Recorder error
            if (uiState.recorderState is RecorderState.Error) {
                item {
                    Text(
                        text = (uiState.recorderState as RecorderState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Manual text input fallback
            if (!hasAudio && !isRecording) {
                item {
                    OutlinedTextField(
                        value = if (hasTranscript) uiState.transcript else uiState.manualInput,
                        onValueChange = { viewModel.updateManualInput(it) },
                        label = { Text("Or type your note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !hasTranscript,
                        trailingIcon = {
                            if (!hasTranscript && uiState.manualInput.isNotBlank()) {
                                TextButton(onClick = { viewModel.submitManualInput() }) {
                                    Text("Extract")
                                }
                            }
                        }
                    )
                }
            }

            // Transcript display
            if (hasTranscript) {
                item {
                    Text(
                        text = "Transcript",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = uiState.transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (uiState.transcriptFilePath != null) {
                    item {
                        Text(
                            text = "Transcript saved to: ${File(uiState.transcriptFilePath!!).name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Topic
            if (hasTranscript) {
                item {
                    OutlinedTextField(
                        value = uiState.topic ?: "",
                        onValueChange = { viewModel.updateTopic(it) },
                        label = { Text("Topic") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., Work, Home, Health") }
                    )
                }
            }

            // Extracted action items
            if (uiState.extractedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Extracted Action Items",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(uiState.extractedItems) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (item.dueDate != null) {
                                    val dateStr = SimpleDateFormat(
                                        "MMM d, yyyy",
                                        Locale.getDefault()
                                    ).format(Date(item.dueDate))
                                    Text(
                                        text = "Due: $dateStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.removeExtractedItem(index) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // No items extracted message
            if (hasTranscript && uiState.extractedItems.isEmpty()) {
                item {
                    Text(
                        text = "No action items detected. The note will still be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
