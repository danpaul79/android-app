package com.example.aicompanion.ui.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onNavigateToProject: (Long) -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToTask: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: ProjectsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var trashTaskId by remember { mutableStateOf<Long?>(null) }
    var showUndatedOnly by remember { mutableStateOf(false) }

    if (trashTaskId != null) {
        AlertDialog(
            onDismissRequest = { trashTaskId = null },
            title = { Text("Move task to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.trashTask(trashTaskId!!)
                    trashTaskId = null
                }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { trashTaskId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search tasks")
                    }
                    // "No date" filter toggle
                    IconButton(onClick = { showUndatedOnly = !showUndatedOnly }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = if (showUndatedOnly) "Show all tasks" else "Show undated tasks only",
                            tint = if (showUndatedOnly) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // Expand/Collapse all toggle
                    if (uiState.projects.isNotEmpty() || uiState.inboxItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (uiState.anyExpanded) viewModel.collapseAll()
                                else viewModel.expandAll()
                            }
                        ) {
                            Icon(
                                if (uiState.anyExpanded) Icons.Filled.UnfoldLess
                                else Icons.Filled.UnfoldMore,
                                contentDescription = if (uiState.anyExpanded) "Collapse all" else "Expand all",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToTrash) {
                        Icon(Icons.Filled.Delete, contentDescription = "Trash", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, "New Project")
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

            // Filter label
            if (showUndatedOnly) {
                item(key = "filter_label") {
                    AssistChip(
                        onClick = { showUndatedOnly = false },
                        label = { Text("Undated tasks only") },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "Clear filter", Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            trailingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Inbox section (virtual project)
            val filteredInbox = if (showUndatedOnly)
                uiState.inboxItems.filter { it.dueDate == null && it.dropDeadDate == null }
            else uiState.inboxItems

            if (!showUndatedOnly || filteredInbox.isNotEmpty()) {
                item(key = "inbox_header") {
                    ProjectHeader(
                        icon = { Icon(Icons.Filled.Inbox, null, tint = MaterialTheme.colorScheme.primary) },
                        name = "Inbox",
                        taskCount = filteredInbox.size,
                        isExpanded = uiState.inboxExpanded,
                        onToggleExpand = { viewModel.toggleInboxExpanded() },
                        onEdit = null
                    )
                }

                if (uiState.inboxExpanded) {
                    if (filteredInbox.isEmpty()) {
                        item(key = "inbox_empty") {
                            Text(
                                "No unassigned tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        items(filteredInbox, key = { "inbox_${it.id}" }) { item ->
                            InlineTaskCard(
                                item = item,
                                onToggle = { viewModel.toggleCompleted(item.id, !item.isCompleted) },
                                onClick = { onNavigateToTask(item.id) },
                                onTrash = { trashTaskId = item.id }
                            )
                        }
                    }
                }
            }

            // Project sections
            uiState.projects.forEach { project ->
                val allTasks = uiState.projectTasks[project.id] ?: emptyList()
                val tasks = if (showUndatedOnly)
                    allTasks.filter { it.dueDate == null && it.dropDeadDate == null }
                else allTasks
                val isExpanded = project.id in uiState.expandedProjectIds

                // Hide projects with no matching tasks when filter is active
                if (showUndatedOnly && tasks.isEmpty()) return@forEach

                item(key = "project_header_${project.id}") {
                    ProjectHeader(
                        icon = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                        name = project.name,
                        taskCount = tasks.size,
                        isExpanded = isExpanded,
                        onToggleExpand = { viewModel.toggleProjectExpanded(project.id) },
                        onEdit = { onNavigateToProject(project.id) }
                    )
                }

                if (isExpanded) {
                    if (tasks.isEmpty()) {
                        item(key = "project_empty_${project.id}") {
                            Text(
                                "No tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        items(tasks, key = { "task_${project.id}_${it.id}" }) { item ->
                            InlineTaskCard(
                                item = item,
                                onToggle = { viewModel.toggleCompleted(item.id, !item.isCompleted) },
                                onClick = { onNavigateToTask(item.id) },
                                onTrash = { trashTaskId = item.id }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createProject(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ProjectHeader(
    icon: @Composable () -> Unit,
    name: String,
    taskCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "$taskCount active task${if (taskCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit project",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InlineTaskCard(
    item: ActionItem,
    onToggle: () -> Unit,
    onClick: () -> Unit,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                isOverdue -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(priorityColor)
            )
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 0.dp, top = 4.dp, bottom = 4.dp)
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
                    tags = item.parsedTags()
                )
            }
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

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
