package com.example.aicompanion.ui.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.effectivePriority
import com.example.aicompanion.data.local.entity.parsedTags
import com.example.aicompanion.ui.common.DateTagsRow
import java.util.Calendar
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
fun ProjectDetailScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit,
    onNavigateToCapture: (Long) -> Unit,
    viewModel: ProjectDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showTrashProjectConfirm by remember { mutableStateOf(false) }
    var showTrashSelectedConfirm by remember { mutableStateOf(false) }
    var trashSingleTaskId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun completeWithUndo(id: Long, text: String) {
        viewModel.toggleCompleted(id, true)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "\"${text.take(30)}${if (text.length > 30) "…" else ""}\" completed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.toggleCompleted(id, false)
            }
        }
    }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utc = datePickerState.selectedDateMillis
                    if (utc != null) viewModel.setDueDateForSelected(utcPickerToLocalNoon(utc))
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

    if (showRenameDialog) {
        val selectedId = uiState.selectedIds.singleOrNull()
        val currentText = viewModel.getSelectedItemText() ?: ""
        var draftText by remember(selectedId) { mutableStateOf(currentText) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename task") },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedId != null && draftText.isNotBlank()) {
                            viewModel.renameTask(selectedId, draftText)
                        }
                        showRenameDialog = false
                    },
                    enabled = draftText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTrashProjectConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashProjectConfirm = false },
            title = { Text("Move project to trash?") },
            text = { Text("This will also trash all tasks in this project.") },
            confirmButton = {
                TextButton(onClick = {
                    showTrashProjectConfirm = false
                    viewModel.trashProject()
                    onNavigateBack()
                }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showTrashProjectConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showTrashSelectedConfirm) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = { showTrashSelectedConfirm = false },
            title = { Text("Move to trash?") },
            text = { Text("$count task${if (count != 1) "s" else ""} will be moved to trash.") },
            confirmButton = {
                TextButton(onClick = {
                    showTrashSelectedConfirm = false
                    viewModel.trashSelected()
                }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showTrashSelectedConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (trashSingleTaskId != null) {
        AlertDialog(
            onDismissRequest = { trashSingleTaskId = null },
            title = { Text("Move task to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.trashTask(trashSingleTaskId!!)
                    trashSingleTaskId = null
                }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { trashSingleTaskId = null }) { Text("Cancel") }
            }
        )
    }

    if (showQuickAdd) {
        var taskText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickAdd = false },
            title = { Text("Add task") },
            text = {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What do you need to do?") },
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (taskText.isNotBlank()) viewModel.quickAddTask(taskText)
                        showQuickAdd = false
                    },
                    enabled = taskText.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAdd = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(uiState.project?.name ?: "Project") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQuickAdd = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Quick add task",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { showTrashProjectConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Move Project to Trash",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { onNavigateToCapture(projectId) }
                ) {
                    Icon(Icons.Filled.Mic, "Record voice note")
                }
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                ProjectSelectionActionBar(
                    selectedCount = uiState.selectedIds.size,
                    isSingleSelection = uiState.selectedIds.size == 1,
                    onSetDueDate = { showDatePicker = true },
                    onRename = { showRenameDialog = true },
                    onTrash = { showTrashSelectedConfirm = true }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            if (uiState.items.isEmpty() && !uiState.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No tasks yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap the mic button to add tasks via voice note",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(uiState.items, key = { it.id }) { item ->
                val isSelected = item.id in uiState.selectedIds
                ProjectTaskCard(
                    item = item,
                    isSelectionMode = uiState.isSelectionMode,
                    isSelected = isSelected,
                    onToggle = {
                        if (!item.isCompleted) completeWithUndo(item.id, item.text)
                        else viewModel.toggleCompleted(item.id, false)
                    },
                    onClick = {
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(item.id)
                        } else {
                            onNavigateToTask(item.id)
                        }
                    },
                    onLongClick = { viewModel.toggleSelection(item.id) },
                    onTrash = { trashSingleTaskId = item.id }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ProjectSelectionActionBar(
    selectedCount: Int,
    isSingleSelection: Boolean,
    onSetDueDate: () -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount task${if (selectedCount != 1) "s" else ""} selected",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onSetDueDate) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Due")
                }
                if (isSingleSelection) {
                    OutlinedButton(onClick = onRename) {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rename")
                    }
                }
                OutlinedButton(onClick = onTrash) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trash")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTaskCard(
    item: ActionItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTrash: () -> Unit
) {
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val isOverdue = item.dueDate != null && item.dueDate < dayStart && !item.isCompleted
    val effPriority = item.effectivePriority()
    val priorityColor = when (effPriority) {
        Priority.URGENT -> MaterialTheme.colorScheme.error
        Priority.HIGH   -> MaterialTheme.colorScheme.tertiary
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else            -> androidx.compose.ui.graphics.Color.Transparent
    }

    val effortLabel = if (item.estimatedMinutes > 0) {
        val m = item.estimatedMinutes
        if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) "${m % 60}m" else ""}"
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .alpha(if (item.isCompleted) 0.6f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                item.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                isOverdue -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(priorityColor)
            )
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggle() }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 0.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                )
                DateTagsRow(
                    dueDate = item.dueDate,
                    dropDeadDate = item.dropDeadDate,
                    isOverdue = isOverdue,
                    tags = item.parsedTags(),
                    isRecurring = item.recurrenceRule != null
                )
            }
            if (effortLabel != null && !isSelectionMode) {
                Text(
                    text = effortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            if (!isSelectionMode) {
                IconButton(onClick = onTrash) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Move to trash",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
