package com.example.aicompanion.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.example.aicompanion.data.sync.SyncStatus
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onViewTranscript: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Data section
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Data",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.exportData() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export Data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Save tasks and projects to Downloads/Pocket Pilot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importLauncher.launch(arrayOf("application/json")) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import Data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Restore from a backup file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // AI Enrichment section
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "AI Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                EnrichTasksCard(
                    enrichmentState = uiState.enrichment,
                    onRunEnrichment = { viewModel.runEnrichment() }
                )
            }

            // Google Tasks Sync section
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Google Tasks Sync",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                GoogleTasksSyncCard(
                    syncState = uiState.sync,
                    onSignIn = { email -> viewModel.onGoogleSignIn(email) },
                    onSignOut = { viewModel.onGoogleSignOut() },
                    onSyncNow = { viewModel.syncNow() }
                )
            }

            // Morning Check-In section
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Morning Check-In",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                MorningCheckInCard(
                    morningState = uiState.morning,
                    onEnabledChange = { viewModel.setMorningEnabled(it) },
                    onTimeChange = { h, m -> viewModel.setMorningTime(h, m) }
                )
            }

            // Voice History section
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Voice History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            if (uiState.voiceNotes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "No voice history yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.voiceNotes, key = { it.audioFile.absolutePath }) { note ->
                    VoiceNoteCard(
                        note = note,
                        onViewTranscript = {
                            note.transcriptFile?.let { onViewTranscript(it.absolutePath) }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun VoiceNoteCard(
    note: VoiceNoteFile,
    onViewTranscript: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    val sizeKb = note.audioFile.length() / 1024

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (note.isVoiceCommand) Icons.Filled.RecordVoiceOver else Icons.Filled.AudioFile,
                contentDescription = null,
                tint = if (note.isVoiceCommand) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(note.date),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (note.isVoiceCommand) "Voice command" else "${sizeKb}KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (note.transcriptFile != null) {
                IconButton(onClick = onViewTranscript) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = if (note.isVoiceCommand) "View command log" else "View transcript",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleTasksSyncCard(
    syncState: SyncUiState,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            account?.email?.let { onSignIn(it) }
        } catch (e: ApiException) {
            Log.e("GoogleTasksSync", "Sign-in failed: statusCode=${e.statusCode}, message=${e.message}", e)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (syncState.syncEnabled && syncState.accountEmail != null) {
                // Signed in state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connected", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            syncState.accountEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Sync status
                val statusText = when (val status = syncState.syncStatus) {
                    is SyncStatus.Idle -> syncState.lastSyncTime?.let {
                        "Last synced: ${formatTimeAgo(it)}"
                    } ?: "Not yet synced"
                    is SyncStatus.Syncing -> "Syncing..."
                    is SyncStatus.Success -> "Last synced: ${formatTimeAgo(status.timestamp)}"
                    is SyncStatus.Error -> "Sync error: ${status.message.take(50)}"
                }

                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (syncState.syncStatus) {
                        is SyncStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = syncState.syncStatus !is SyncStatus.Syncing
                    ) {
                        if (syncState.syncStatus is SyncStatus.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sync Now")
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                }
            } else {
                // Signed out state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Not connected", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Sync tasks with Google Tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(Scope("https://www.googleapis.com/auth/tasks"))
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        signInLauncher.launch(client.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
private fun EnrichTasksCard(
    enrichmentState: EnrichmentUiState,
    onRunEnrichment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enrich task metadata", style = MaterialTheme.typography.bodyLarge)
                    val subtitle = when {
                        enrichmentState.isDone ->
                            "Done — ${enrichmentState.enriched} tasks updated"
                        enrichmentState.isRunning ->
                            "Analyzing ${enrichmentState.progress} of ${enrichmentState.total}..."
                        enrichmentState.unenrichedCount > 0 ->
                            "${enrichmentState.unenrichedCount} tasks need effort estimates or tags"
                        else ->
                            "All tasks have effort estimates and tags"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (enrichmentState.isRunning && enrichmentState.total > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { enrichmentState.progress.toFloat() / enrichmentState.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!enrichmentState.isRunning && (enrichmentState.unenrichedCount > 0 || enrichmentState.isDone.not())) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRunEnrichment,
                    enabled = !enrichmentState.isRunning && enrichmentState.unenrichedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (enrichmentState.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Run AI enrichment")
                }
            }
        }
    }
}

@Composable
private fun MorningCheckInCard(
    morningState: MorningUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set notification time") },
            text = {
                Column {
                    Text("Morning hours:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val hours = (5..11).toList()
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(hours) { h ->
                            FilterChip(
                                selected = h == morningState.hourOfDay,
                                onClick = { onTimeChange(h, 0); showTimePicker = false },
                                label = { Text(String.format("%d:00", h)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Afternoon/evening:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val lateHours = (12..20).toList()
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(lateHours) { h ->
                            FilterChip(
                                selected = h == morningState.hourOfDay,
                                onClick = { onTimeChange(h, 0); showTimePicker = false },
                                label = { Text(String.format("%d:00", h)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) { Text("Close") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily capacity check-in", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (morningState.enabled)
                            "Fires at ${String.format("%d:%02d", morningState.hourOfDay, morningState.minute)}"
                        else
                            "Get a morning nudge to plan your tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = morningState.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (morningState.enabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change time (${String.format("%d:%02d", morningState.hourOfDay, morningState.minute)})")
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
