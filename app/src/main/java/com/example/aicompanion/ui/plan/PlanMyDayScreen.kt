package com.example.aicompanion.ui.plan

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.aicompanion.data.local.entity.ActionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanMyDayScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit,
    viewModel: PlanMyDayViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan My Day") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Capacity selector
            item {
                Text("How much time do you have?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                CapacitySelector(
                    selectedMinutes = uiState.capacityMinutes,
                    onSelect = { viewModel.setCapacity(it) }
                )
            }

            // Context filter
            item {
                Text("What's your context?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ContextSelector(
                    selectedContext = uiState.selectedContext,
                    onSelect = { viewModel.setContext(it) }
                )
            }

            // Pick button
            item {
                Button(
                    onClick = { viewModel.pickTasks() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.hasPlan) "Regenerate plan" else "Pick my tasks")
                }
            }

            // Loading
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Results
            if (uiState.hasPlan && !uiState.isLoading) {
                val tasks = uiState.pickedTasks
                val totalMinutes = tasks.sumOf { if (it.estimatedMinutes > 0) it.estimatedMinutes else 30 }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tasks.isEmpty()) "No tasks matched" else "${tasks.size} tasks · ${minutesToLabel(totalMinutes)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (tasks.isEmpty()) {
                    item {
                        Text(
                            text = "Try a different context or add more tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        PlannedTaskCard(
                            task = task,
                            onClick = { onNavigateToTask(task.id) },
                            onRemove = { viewModel.removeTask(task.id) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapacitySelector(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit
) {
    var customText by remember { mutableStateOf("") }
    val isCustom = selectedMinutes !in CAPACITY_PRESETS

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CAPACITY_PRESETS.forEach { preset ->
            FilterChip(
                selected = selectedMinutes == preset,
                onClick = { onSelect(preset); customText = "" },
                label = { Text(minutesToLabel(preset)) }
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { /* handled by text field */ },
            label = { Text("Custom") }
        )
    }
    if (isCustom || customText.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = { v ->
                customText = v
                v.toIntOrNull()?.takeIf { it > 0 }?.let { onSelect(it) }
            },
            label = { Text("Minutes") },
            singleLine = true,
            modifier = Modifier.width(140.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextSelector(
    selectedContext: String?,
    onSelect: (String?) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CONTEXT_OPTIONS.forEach { (label, tag) ->
            FilterChip(
                selected = selectedContext == tag,
                onClick = { onSelect(tag) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun PlannedTaskCard(
    task: ActionItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val estimate = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = mutableListOf<String>()
                metaParts.add(minutesToLabel(estimate))
                task.dueDate?.let {
                    metaParts.add("due ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it))}")
                }
                task.dropDeadDate?.let {
                    metaParts.add("⚠ ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it))}")
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from plan",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun minutesToLabel(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}
