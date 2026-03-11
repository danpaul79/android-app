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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun utcPickerToLocalNoon(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.timeInMillis = utcMillis
    val localCal = Calendar.getInstance()
    localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    localCal.set(Calendar.MILLISECOND, 0)
    return localCal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showDropDeadDatePicker by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { viewModel.loadTask(taskId) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onNavigateBack() }

    val item = uiState.item

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utc = datePickerState.selectedDateMillis
                    if (utc != null) viewModel.setDueDate(utcPickerToLocalNoon(utc))
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDropDeadDatePicker) {
        val dropDeadPickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDropDeadDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utc = dropDeadPickerState.selectedDateMillis
                    if (utc != null) viewModel.setDropDeadDate(utcPickerToLocalNoon(utc))
                    showDropDeadDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDropDeadDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dropDeadPickerState)
        }
    }

    if (showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            title = { Text("Move task to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    showTrashConfirm = false
                    viewModel.trashTask()
                }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirm = false }) { Text("Cancel") }
            }
        )
    }

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
                    IconButton(onClick = { showTrashConfirm = true }) {
                        Icon(Icons.Filled.Delete, "Move to trash", tint = MaterialTheme.colorScheme.error)
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

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Completed toggle + editable task name
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { viewModel.toggleCompleted() },
                    modifier = Modifier.padding(top = 4.dp)
                )
                var taskText by remember(item.text) { mutableStateOf(item.text) }
                OutlinedTextField(
                    value = taskText,
                    onValueChange = {
                        taskText = it
                        if (it.isNotBlank()) viewModel.updateText(it)
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                    ),
                    minLines = 2
                )
            }

            // Due date row with picker and clear
            val isOverdue = item.dueDate != null && item.dueDate < dayStart && !item.isCompleted
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = when {
                        isOverdue -> MaterialTheme.colorScheme.error
                        item.dueDate != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    val label = if (item.dueDate != null) {
                        SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(item.dueDate))
                    } else {
                        "Set due date"
                    }
                    Text(
                        text = label,
                        color = when {
                            isOverdue -> MaterialTheme.colorScheme.error
                            item.dueDate != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (item.dueDate != null) {
                    IconButton(onClick = { viewModel.setDueDate(null) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear due date",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Drop-dead date row
            val isDropDeadOverdue = item.dropDeadDate != null && item.dropDeadDate < dayStart && !item.isCompleted
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = when {
                        isDropDeadOverdue -> MaterialTheme.colorScheme.error
                        item.dropDeadDate != null -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showDropDeadDatePicker = true }) {
                    val label = if (item.dropDeadDate != null) {
                        "Deadline: " + SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(item.dropDeadDate))
                    } else {
                        "Set drop-dead date"
                    }
                    Text(
                        text = label,
                        color = when {
                            isDropDeadOverdue -> MaterialTheme.colorScheme.error
                            item.dropDeadDate != null -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (item.dropDeadDate != null) {
                    IconButton(onClick = { viewModel.setDropDeadDate(null) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear deadline",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Effort estimate
            val effortOptions = listOf(0 to "?", 10 to "10m", 20 to "20m", 30 to "30m", 60 to "1h", 90 to "90m", 120 to "2h+")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    effortOptions.forEach { (minutes, label) ->
                        FilterChip(
                            selected = item.estimatedMinutes == minutes,
                            onClick = { viewModel.setEstimatedMinutes(minutes) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
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
