package com.example.aicompanion.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.effectivePriority
import com.example.aicompanion.data.local.entity.parsedTags
import com.example.aicompanion.reminder.MorningPlanStore
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onNavigateToTask: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPlanMyDay: () -> Unit = {},
    onNavigateToTriage: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showTrashSelectedConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun swipeTrashWithUndo(id: Long, text: String) {
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

    fun swipeCompleteWithUndo(id: Long, text: String) {
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

    // Refresh capacity and triage count when returning from PlanMyDay, triage, or other screens
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCapacity()
        viewModel.loadTodaysPlan()
        viewModel.refreshTriageCount()
        onPauseOrDispose {}
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

    if (showQuickAdd) {
        var taskText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickAdd = false },
            title = { Text("Quick add task") },
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
                        TextButton(onClick = { viewModel.selectAll() }) { Text("All") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    windowInsets = WindowInsets(0)
                )
            } else {
                var showOverflow by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text("Pocket Pilot") },
                    actions = {
                        IconButton(onClick = onNavigateToCapture) {
                            Icon(Icons.Filled.Mic, contentDescription = "Capture")
                        }
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search tasks")
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Plan My Day") },
                                    leadingIcon = { Icon(Icons.Filled.Today, contentDescription = null) },
                                    onClick = { showOverflow = false; onNavigateToPlanMyDay() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Task Triage") },
                                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                                    onClick = { showOverflow = false; onNavigateToTriage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Quick Add Task") },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = { showOverflow = false; showQuickAdd = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trash") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { showOverflow = false; onNavigateToTrash() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    windowInsets = WindowInsets(0)
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                DashboardSelectionActionBar(
                    selectedCount = uiState.selectedIds.size,
                    isSingleSelection = uiState.selectedIds.size == 1,
                    onSetDueDate = { showDatePicker = true },
                    onComplete = { viewModel.completeSelected() },
                    onRename = { showRenameDialog = true },
                    onTrash = { showTrashSelectedConfirm = true }
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (uiState.overdueItems.isEmpty() && uiState.todayItems.isEmpty() &&
            uiState.upcomingItems.isEmpty() && uiState.inboxCount == 0
        ) {
            EmptyDashboard(
                onNavigateToCapture = onNavigateToCapture,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Today's plan card (dismissible)
                val plan = uiState.todaysPlan
                if (plan != null && !uiState.isSelectionMode) {
                    item {
                        TodaysPlanCard(
                            plan = plan,
                            onDismiss = { viewModel.dismissTodaysPlan() },
                            onNavigateToTask = onNavigateToTask,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        )
                    }
                }

                // Capacity indicator
                val capacity = uiState.capacityMinutes
                if (capacity != null && !uiState.isSelectionMode) {
                    item {
                        CapacityIndicatorRow(
                            plannedMinutes = uiState.plannedMinutesToday,
                            capacityMinutes = capacity,
                            onClick = onNavigateToPlanMyDay,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        )
                    }
                }

                // Triage prompt
                if (uiState.triageCount > 0 && !uiState.isSelectionMode) {
                    item {
                        Card(
                            onClick = onNavigateToTriage,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Checklist,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${uiState.triageCount} task${if (uiState.triageCount != 1) "s" else ""} need review",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Review",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }


                if (uiState.overdueItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Overdue",
                            icon = Icons.Filled.Warning,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(uiState.overdueItems, key = { "overdue_${it.id}" }) { item ->
                        val isSelected = item.id in uiState.selectedIds
                        TaskRow(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            onToggle = {
                                if (!item.isCompleted) swipeCompleteWithUndo(item.id, item.text)
                                else viewModel.toggleCompleted(item.id, false)
                            },
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                                else onNavigateToTask(item.id)
                            },
                            onLongClick = { viewModel.toggleSelection(item.id) },
                            onSwipeComplete = { swipeCompleteWithUndo(item.id, item.text) },
                            onSwipeTrash = { swipeTrashWithUndo(item.id, item.text) },
                            isOverdue = true
                        )
                    }
                }

                if (uiState.todayItems.isNotEmpty()) {
                    item { SectionHeader(title = "Today") }
                    item {
                        ReorderableTodaySection(
                            items = uiState.todayItems,
                            isSelectionMode = uiState.isSelectionMode,
                            selectedIds = uiState.selectedIds,
                            onToggle = { item ->
                                if (!item.isCompleted) swipeCompleteWithUndo(item.id, item.text)
                                else viewModel.toggleCompleted(item.id, false)
                            },
                            onClick = { item ->
                                if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                                else onNavigateToTask(item.id)
                            },
                            onLongClick = { item -> viewModel.toggleSelection(item.id) },
                            onSwipeComplete = { item -> swipeCompleteWithUndo(item.id, item.text) },
                            onSwipeTrash = { item -> swipeTrashWithUndo(item.id, item.text) },
                            onReorder = { reordered -> viewModel.reorderTodayTasks(reordered) }
                        )
                    }
                }

                if (uiState.upcomingItems.isNotEmpty()) {
                    item { SectionHeader(title = "Upcoming (7 days)") }
                    items(uiState.upcomingItems, key = { "upcoming_${it.id}" }) { item ->
                        val isSelected = item.id in uiState.selectedIds
                        TaskRow(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            onToggle = {
                                if (!item.isCompleted) swipeCompleteWithUndo(item.id, item.text)
                                else viewModel.toggleCompleted(item.id, false)
                            },
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                                else onNavigateToTask(item.id)
                            },
                            onLongClick = { viewModel.toggleSelection(item.id) },
                            onSwipeComplete = { swipeCompleteWithUndo(item.id, item.text) },
                            onSwipeTrash = { swipeTrashWithUndo(item.id, item.text) }
                        )
                    }
                }

                if (uiState.undatedItems.isNotEmpty()) {
                    item { SectionHeader(title = "No date") }
                    items(uiState.undatedItems, key = { "undated_${it.id}" }) { item ->
                        val isSelected = item.id in uiState.selectedIds
                        TaskRow(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            onToggle = {
                                if (!item.isCompleted) swipeCompleteWithUndo(item.id, item.text)
                                else viewModel.toggleCompleted(item.id, false)
                            },
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                                else onNavigateToTask(item.id)
                            },
                            onLongClick = { viewModel.toggleSelection(item.id) },
                            onSwipeComplete = { swipeCompleteWithUndo(item.id, item.text) },
                            onSwipeTrash = { swipeTrashWithUndo(item.id, item.text) }
                        )
                    }
                }

                // Recently completed (collapsible)
                if (uiState.recentlyCompleted.isNotEmpty() && !uiState.isSelectionMode) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { viewModel.toggleShowCompleted() }
                                )
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Completed (${uiState.recentlyCompleted.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (uiState.showCompleted) "Hide" else "Show",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (uiState.showCompleted) {
                        items(uiState.recentlyCompleted, key = { "done_${it.id}" }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onNavigateToTask(item.id) }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { viewModel.toggleCompleted(item.id, false) }
                                    )
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun DashboardSelectionActionBar(
    selectedCount: Int,
    isSingleSelection: Boolean,
    onSetDueDate: () -> Unit,
    onComplete: () -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit
) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun ReorderableTodaySection(
    items: List<ActionItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggle: (ActionItem) -> Unit,
    onClick: (ActionItem) -> Unit,
    onLongClick: (ActionItem) -> Unit,
    onSwipeComplete: (ActionItem) -> Unit,
    onSwipeTrash: (ActionItem) -> Unit,
    onReorder: (List<ActionItem>) -> Unit
) {
    val localItems = remember(items) { items.toMutableStateList() }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Float>() }

    Column {
        localItems.forEachIndexed { index, item ->
            val isSelected = item.id in selectedIds
            val isDragged = draggedIndex == index

            Box(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        itemHeights[index] = coords.size.height.toFloat()
                    }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationY = dragOffset
                            scaleX = 1.02f
                            scaleY = 1.02f
                            alpha = 0.9f
                            shadowElevation = 8f
                        }
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        TaskRow(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggle = { onToggle(item) },
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) },
                            onSwipeComplete = { onSwipeComplete(item) },
                            onSwipeTrash = { onSwipeTrash(item) }
                        )
                    }
                    if (!isSelectionMode) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, offset ->
                                            change.consume()
                                            dragOffset += offset.y
                                            val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress

                                            // Calculate if we've crossed into another item's territory
                                            val avgHeight = itemHeights.values.average().toFloat()
                                            val targetIdx = (currentIdx + (dragOffset / avgHeight).toInt())
                                                .coerceIn(0, localItems.lastIndex)

                                            if (targetIdx != currentIdx) {
                                                localItems.move(currentIdx, targetIdx)
                                                draggedIndex = targetIdx
                                                dragOffset = 0f
                                            }
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffset = 0f
                                            onReorder(localItems.toList())
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffset = 0f
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val item = removeAt(fromIndex)
    add(toIndex, item)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(
    item: ActionItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeTrash: () -> Unit,
    isOverdue: Boolean = false
) {
    if (isSelectionMode) {
        TaskRowCard(item, isSelectionMode, isSelected, onToggle, onClick, onLongClick, isOverdue)
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> { onSwipeComplete(); true }
                    SwipeToDismissBoxValue.EndToStart -> { onSwipeTrash(); true }
                    SwipeToDismissBoxValue.Settled -> false
                }
            },
            positionalThreshold = { totalDistance -> totalDistance * 0.75f }
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        else -> Alignment.CenterEnd
                    }
                ) {
                    Icon(
                        imageVector = when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Delete
                        },
                        contentDescription = null,
                        tint = when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        ) {
            TaskRowCard(item, isSelectionMode, isSelected, onToggle, onClick, onLongClick, isOverdue)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRowCard(
    item: ActionItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isOverdue: Boolean
) {
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

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isOverdue -> MaterialTheme.colorScheme.errorContainer
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodaysPlanCard(
    plan: MorningPlanStore.PlanEntry,
    onDismiss: () -> Unit,
    onNavigateToTask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's plan · ${plan.capacityLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (plan.tasks.isEmpty()) {
                Text(
                    text = "No tasks scheduled — you're all clear!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                plan.tasks.forEach { task ->
                    val timeLabel = if (task.estimatedMinutes > 0) {
                        val m = task.estimatedMinutes
                        if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) "${m % 60}m" else ""}"
                    } else "~30m"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onNavigateToTask(task.id) })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = task.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapacityIndicatorRow(
    plannedMinutes: Int,
    capacityMinutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (capacityMinutes > 0) (plannedMinutes.toFloat() / capacityMinutes).coerceIn(0f, 1f) else 0f
    val overloaded = plannedMinutes > capacityMinutes

    fun minutesToLabel(m: Int): String = when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (overloaded)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today: ${minutesToLabel(plannedMinutes)} planned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overloaded) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${minutesToLabel(capacityMinutes)} capacity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overloaded) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (overloaded) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun EmptyDashboard(
    onNavigateToCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(text = "All clear!", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "No tasks due. Record a voice note to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onNavigateToCapture) {
                Icon(Icons.Filled.Mic, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Record a note")
            }
        }
    }
}
