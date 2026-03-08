package com.example.aicompanion.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aicompanion.data.local.entity.ActionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onNavigateToTask: (Long) -> Unit,
    viewModel: InboxViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (uiState.isSelectionMode) {
                    Text("${uiState.selectedIds.size} selected")
                } else {
                    Text("Inbox")
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text("All")
                    }
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

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
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(uiState.items, key = { it.id }) { item ->
                    val isSelected = item.id in uiState.selectedIds
                    InboxItemCard(
                        item = item,
                        projects = uiState.projects,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = isSelected,
                        onAssign = { projectId -> viewModel.assignToProject(item.id, projectId) },
                        onToggle = { viewModel.toggleCompleted(item.id, !item.isCompleted) },
                        onClick = {
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                onNavigateToTask(item.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(item.id) },
                        onDelete = { viewModel.deleteTask(item.id) }
                    )
                }
                item { Spacer(Modifier.height(if (uiState.isSelectionMode) 96.dp else 80.dp)) }
            }

            // Batch action bar
            if (uiState.isSelectionMode) {
                SelectionActionBar(
                    selectedCount = uiState.selectedIds.size,
                    projects = uiState.projects,
                    onAssignToProject = { projectId -> viewModel.assignSelectedToProject(projectId) },
                    onDelete = { viewModel.deleteSelected() }
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    projects: List<com.example.aicompanion.data.local.entity.Project>,
    onAssignToProject: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var showProjectMenu by remember { mutableStateOf(false) }

    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount task${if (selectedCount != 1) "s" else ""} selected",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
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
    onDelete: () -> Unit
) {
    var showProjectMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.dueDate != null) {
                    val dateStr = SimpleDateFormat("MMM d", Locale.getDefault())
                        .format(Date(item.dueDate))
                    Text(
                        text = "Due: $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showProjectMenu = true }) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = "Assign to project",
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

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
