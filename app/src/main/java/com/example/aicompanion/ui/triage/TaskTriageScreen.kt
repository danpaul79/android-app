package com.example.aicompanion.ui.triage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.parsedTags
import com.example.aicompanion.ui.common.TagChipsRow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskTriageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit,
    viewModel: TaskTriageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Date picker dialog
    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { viewModel.dismissDatePicker() },
            confirmButton = {
                TextButton(onClick = {
                    val utc = datePickerState.selectedDateMillis
                    if (utc != null) viewModel.setDueDate(utcPickerToLocalNoon(utc))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDatePicker() }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Trash confirmation dialog
    if (uiState.showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTrashConfirm() },
            title = { Text("Trash this task?") },
            text = { Text(uiState.currentItem?.task?.text ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTrash() }) {
                    Text("Trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTrashConfirm() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.items.isNotEmpty() && !uiState.triageComplete) {
                        Text("Task Triage (${uiState.progress})")
                    } else {
                        Text("Task Triage")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.triageComplete -> {
                    CompletionCard(
                        reviewedCount = uiState.items.size,
                        onDone = onNavigateBack
                    )
                }
                uiState.items.isEmpty() -> {
                    EmptyState()
                }
                else -> {
                    val item = uiState.currentItem ?: return@Box
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }

                        // Task card
                        item(key = "card_${item.task.id}") {
                            TaskTriageCard(
                                item = item,
                                projectName = item.task.projectId?.let { uiState.projectNames[it] },
                                onClick = { onNavigateToTask(item.task.id) }
                            )
                        }

                        // Breakdown results (if any)
                        if (uiState.breakdownResult != null) {
                            item(key = "breakdown_header") {
                                BreakdownPanel(
                                    items = uiState.breakdownResult!!,
                                    selections = uiState.breakdownSelections,
                                    onToggle = viewModel::toggleBreakdownSelection,
                                    onConfirm = viewModel::confirmBreakdown,
                                    onDismiss = viewModel::dismissBreakdown
                                )
                            }
                        } else {
                            // Action buttons
                            item(key = "actions") {
                                TriageActions(
                                    item = item,
                                    isBreakingDown = uiState.isBreakingDown,
                                    onKeep = viewModel::keep,
                                    onComplete = viewModel::complete,
                                    onTrash = viewModel::requestTrash,
                                    onBreakDown = viewModel::breakDown,
                                    onSetDueDate = viewModel::showDatePicker,
                                    onSnooze = viewModel::snooze,
                                    onToggleWaiting = viewModel::toggleWaitingFor
                                )
                            }
                        }

                        // Navigation arrows
                        item(key = "nav") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { viewModel.previous() },
                                    enabled = uiState.hasPrev
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Previous")
                                }
                                TextButton(
                                    onClick = { viewModel.keep() },
                                    enabled = uiState.hasNext
                                ) {
                                    Text("Skip")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTriageCard(
    item: TriageItem,
    projectName: String?,
    onClick: () -> Unit
) {
    val task = item.task
    val tags = task.parsedTags()
    val estimate = if (task.estimatedMinutes > 0) task.estimatedMinutes else 0
    val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Reason badge
            val badgeColor = when (item.category) {
                TriageCategory.STALE -> MaterialTheme.colorScheme.errorContainer
                TriageCategory.RESCHEDULED -> MaterialTheme.colorScheme.tertiaryContainer
                TriageCategory.LARGE_UNDATED -> MaterialTheme.colorScheme.secondaryContainer
                TriageCategory.WAITING_FOR -> MaterialTheme.colorScheme.surfaceVariant
            }
            val badgeTextColor = when (item.category) {
                TriageCategory.STALE -> MaterialTheme.colorScheme.onErrorContainer
                TriageCategory.RESCHEDULED -> MaterialTheme.colorScheme.onTertiaryContainer
                TriageCategory.LARGE_UNDATED -> MaterialTheme.colorScheme.onSecondaryContainer
                TriageCategory.WAITING_FOR -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor
            ) {
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Task name
            Text(
                text = task.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Metadata row
            val metaParts = mutableListOf<String>()
            if (projectName != null) metaParts.add(projectName)
            if (estimate > 0) {
                metaParts.add(if (estimate >= 60) "${estimate / 60}h${if (estimate % 60 > 0) "${estimate % 60}m" else ""}" else "${estimate}m")
            }
            task.dueDate?.let { metaParts.add("due ${dateFmt.format(Date(it))}") }
            task.dropDeadDate?.let { metaParts.add("deadline ${dateFmt.format(Date(it))}") }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tags
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TagChipsRow(tags = tags)
            }

            // Notes preview
            if (!task.notes.isNullOrBlank()) {
                val notesPreview = task.notes.lines()
                    .filter { !it.trim().startsWith("#") || it.trim().contains(" ") }
                    .take(2)
                    .joinToString("\n")
                    .trim()
                if (notesPreview.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = notesPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriageActions(
    item: TriageItem,
    isBreakingDown: Boolean,
    onKeep: () -> Unit,
    onComplete: () -> Unit,
    onTrash: () -> Unit,
    onBreakDown: () -> Unit,
    onSetDueDate: () -> Unit,
    onSnooze: () -> Unit,
    onToggleWaiting: () -> Unit
) {
    val hasWaitingTag = item.task.parsedTags().any { it.equals("waiting-for", ignoreCase = true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do you want to do?", style = MaterialTheme.typography.titleSmall)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onComplete) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
            OutlinedButton(onClick = onKeep) {
                Text("Keep")
            }
            OutlinedButton(onClick = onSetDueDate) {
                Text("Set due date")
            }
            FilledTonalButton(
                onClick = onBreakDown,
                enabled = !isBreakingDown
            ) {
                if (isBreakingDown) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Thinking...")
                } else {
                    Text("Break it down")
                }
            }
            OutlinedButton(onClick = onSnooze) {
                Text("Snooze 2w")
            }
            OutlinedButton(onClick = onToggleWaiting) {
                Text(if (hasWaitingTag) "Unblock" else "Waiting on someone")
            }
            OutlinedButton(
                onClick = onTrash,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Trash")
            }
        }
    }
}

@Composable
private fun BreakdownPanel(
    items: List<com.example.aicompanion.network.GeminiClient.BreakdownItem>,
    selections: Set<Int>,
    onToggle: (Int) -> Unit,
    onConfirm: (trashOriginal: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var trashOriginal by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "AI suggests these subtasks:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))

            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = index in selections,
                        onCheckedChange = { onToggle(index) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val estimate = item.estimatedMinutes
                        val tagStr = item.suggestedTags.joinToString(", ") { "#$it" }
                        val meta = listOfNotNull(
                            if (estimate > 0) "${estimate}m" else null,
                            tagStr.ifBlank { null }
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = trashOriginal,
                    onCheckedChange = { trashOriginal = it }
                )
                Text("Trash original task", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(trashOriginal) },
                    enabled = selections.isNotEmpty()
                ) {
                    Text("Create ${selections.size} subtask${if (selections.size != 1) "s" else ""}")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(
    reviewedCount: Int,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "All done!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You reviewed $reviewedCount task${if (reviewedCount != 1) "s" else ""}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone) {
                    Text("Back to Dashboard")
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No tasks need review right now",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tasks will appear here when they go stale,\nget rescheduled often, or need attention.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
