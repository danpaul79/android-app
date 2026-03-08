package com.example.aicompanion.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.SourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) { viewModel.loadTask(taskId) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onNavigateBack() }

    val item = uiState.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteTask() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Completed toggle + task text
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { viewModel.toggleCompleted() }
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
            }

            // Due date
            if (item.dueDate != null) {
                val dateStr = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
                    .format(Date(item.dueDate))
                val isOverdue = item.dueDate < System.currentTimeMillis() && !item.isCompleted
                Text(
                    text = "Due: $dateStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Priority
            if (item.priority != com.example.aicompanion.data.local.entity.Priority.NONE) {
                Text(
                    text = "Priority: ${item.priority.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Project assignment
            var showProjectMenu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Box {
                    TextButton(onClick = { showProjectMenu = true }) {
                        Text(uiState.currentProjectName ?: "Inbox (unassigned)")
                    }
                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Inbox (unassigned)") },
                            onClick = {
                                viewModel.assignToProject(null)
                                showProjectMenu = false
                            }
                        )
                        uiState.projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                onClick = {
                                    viewModel.assignToProject(project.id)
                                    showProjectMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Notes
            var notesText by remember(item.notes) { mutableStateOf(item.notes ?: "") }
            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    viewModel.updateNotes(it)
                },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Source info
            uiState.source?.let { source ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val typeLabel = when (source.type) {
                            SourceType.VOICE_NOTE -> "Voice Note"
                            SourceType.EMAIL -> "Email"
                            SourceType.CHAT -> "Chat"
                            SourceType.SMS -> "Text Message"
                            SourceType.MANUAL -> "Manual"
                        }
                        Text("Source: $typeLabel", style = MaterialTheme.typography.labelMedium)
                        val dateStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                            .format(Date(source.createdAt))
                        Text("Captured: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (source.rawContent.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = source.rawContent.take(200) + if (source.rawContent.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Metadata
            val createdStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                .format(Date(item.createdAt))
            Text(
                text = "Created: $createdStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.completedAt != null) {
                val completedStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                    .format(Date(item.completedAt))
                Text(
                    text = "Completed: $completedStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
