package com.example.aicompanion.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single FlowRow containing: optional due date chip, optional drop-dead date chip, then tag chips.
 * Saves vertical space vs. separate DateLine + TagChipsRow rows.
 *
 * Shows nothing if all inputs are null/empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateTagsRow(
    dueDate: Long?,
    dropDeadDate: Long?,
    isOverdue: Boolean,
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    if (dueDate == null && dropDeadDate == null && tags.isEmpty()) return

    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (dueDate != null) {
            Text(
                text = fmt.format(Date(dueDate)),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
        }
        if (dropDeadDate != null) {
            Text(
                text = "⚠ ${fmt.format(Date(dropDeadDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        tags.forEach { tag ->
            val muted = tag == "waiting-for"
            val containerColor = if (muted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.secondaryContainer
            val labelColor = if (muted)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSecondaryContainer
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = containerColor
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
