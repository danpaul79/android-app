package com.example.aicompanion.ui.record

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.speech.SpeechRecognizerManager
import com.example.aicompanion.speech.SpeechState
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
    val speechManager = remember { SpeechRecognizerManager(context) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(speechManager) {
        viewModel.initSpeechManager(speechManager)
    }

    DisposableEffect(Unit) {
        onDispose { speechManager.destroy() }
    }

    LaunchedEffect(uiState.savedNoteId) {
        uiState.savedNoteId?.let { id ->
            onNoteSaved(id)
        }
    }

    val isListening = uiState.speechState is SpeechState.Listening
    val hasResult = uiState.speechState is SpeechState.Result || uiState.transcript.isNotBlank()

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
            if (hasResult && uiState.savedNoteId == null) {
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
            // Mic button
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (speechManager.isAvailable()) {
                        val buttonColor by animateColorAsState(
                            if (isListening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            label = "mic_color"
                        )
                        FloatingActionButton(
                            onClick = {
                                if (isListening) viewModel.stopRecording()
                                else viewModel.startRecording()
                            },
                            modifier = Modifier.size(72.dp),
                            containerColor = buttonColor
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (isListening) "Stop" else "Record",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                isListening -> "Listening..."
                                hasResult -> "Tap to record again"
                                else -> "Tap to start recording"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Speech recognition not available on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Manual text input fallback
            item {
                OutlinedTextField(
                    value = if (hasResult) uiState.transcript else uiState.manualInput,
                    onValueChange = { viewModel.updateManualInput(it) },
                    label = { Text("Or type your note") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    enabled = !hasResult,
                    trailingIcon = {
                        if (!hasResult && uiState.manualInput.isNotBlank()) {
                            TextButton(onClick = { viewModel.submitManualInput() }) {
                                Text("Extract")
                            }
                        }
                    }
                )
            }

            // Error state
            if (uiState.speechState is SpeechState.Error) {
                item {
                    Text(
                        text = (uiState.speechState as SpeechState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Topic
            if (hasResult) {
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
            if (hasResult && uiState.extractedItems.isEmpty()) {
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
