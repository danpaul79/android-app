package com.example.aicompanion.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.effectivePriority
import com.example.aicompanion.data.local.entity.parsedTags
import androidx.compose.foundation.background
import com.example.aicompanion.ui.common.DateTagsRow
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
fun InboxScreen(
    onNavigateToTask: (Long) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: InboxViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTrashSelectedConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun trashWithUndo(id: Long, text: String) {
        viewModel.trashTask(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "\"${text.take(30)}${if (text.length > 30) "…" else ""}\" trashed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoTrash(id)
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text("${uiState.selectedIds.size} selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text("Inbox", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("All")
                        }
                    } else {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search tasks")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (uiState.isSelectionMode)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (uiState.isSelectionMode)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                SelectionActionBar(
                    selectedCount = uiState.selectedIds.size,
                    isSingleSelection = uiState.selectedIds.size == 1,
                    projects = uiState.projects,
                    onAssignToProject = { projectId -> viewModel.assignSelectedToProject(projectId) },
                    onSetDueDate = { showDatePicker = true },
                    onComplete = { viewModel.completeSelected() },
                    onRename = { showRenameDialog = true },
                    onTrash = { showTrashSelectedConfirm = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

        if (uiState.items.isEmpty() && !uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Inbox empty", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "New tasks from voice notes will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    val isSelected = item.id in uiState.selectedIds
                    InboxItemCard(
                        item = item,
                        projects = uiState.projects,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = isSelected,
                        onAssign = { projectId -> viewModel.assignToProject(item.id, projectId) },
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
                        onTrash = { trashWithUndo(item.id, item.text) }
                    )
                }
                item { Spacer(Modifier.height(if (uiState.isSelectionMode) 96.dp else 80.dp)) }
            }

        }
    }
}  // Scaffold
}  // InboxScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    isSingleSelection: Boolean,
    projects: List<com.example.aicompanion.data.local.entity.Project>,
    onAssignToProject: (Long) -> Unit,
    onSetDueDate: () -> Unit,
    onComplete: () -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit
) {
    var showProjectMenu by remember { mutableStateOf(false) }

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
                Box {
                    Button(onClick = { showProjectMenu = true }) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Assign")
                    }
                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false }
                    ) {
                        if (projects.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No projects yet") },
                                onClick = { showProjectMenu = false },
                                enabled = false
                            )
                        } else {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        onAssignToProject(project.id)
                                        showProjectMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onSetDueDate) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Due")
                }
                OutlinedButton(onClick = onComplete) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Done")
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
private fun InboxItemCard(
    item: ActionItem,
    projects: List<com.example.aicompanion.data.local.entity.Project>,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onAssign: (Long) -> Unit,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTrash: () -> Unit
) {
    var showProjectMenu by remember { mutableStateOf(false) }

    val effPriority = item.effectivePriority()
    val priorityColor = when (effPriority) {
        Priority.URGENT -> MaterialTheme.colorScheme.error
        Priority.HIGH   -> MaterialTheme.colorScheme.tertiary
        Priority.MEDIUM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else            -> androidx.compose.ui.graphics.Color.Transparent
    }
    val dayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val isOverdue = item.dueDate != null && item.dueDate < dayStart && !item.isCompleted

    val effortLabel = if (item.estimatedMinutes > 0) {
        val m = item.estimatedMinutes
        if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) "${m % 60}m" else ""}"
    } else null

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(bgColor)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .alpha(if (item.isCompleted) 0.55f else 1f)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(priorityColor)
            )
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else {
                Checkbox(checked = item.isCompleted, onCheckedChange = { onToggle() })
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                DateTagsRow(
                    dueDate = item.dueDate,
                    dropDeadDate = item.dropDeadDate,
                    isOverdue = isOverdue,
                    tags = item.parsedTags(),
                    isRecurring = item.recurrenceRule != null
                )
            }

            if (!isSelectionMode) {
                if (effortLabel != null) {
                    Text(
                        text = effortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showProjectMenu = true }) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = "Assign to project",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false }
                    ) {
                        if (projects.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No projects yet") },
                                onClick = { showProjectMenu = false },
                                enabled = false
                            )
                        } else {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        onAssign(project.id)
                                        showProjectMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
