package com.example.aicompanion.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact single-line date display: due date on the left, drop-dead date right-justified.
 * Shows nothing if both are null.
 */
@Composable
fun DateLine(
    dueDate: Long?,
    dropDeadDate: Long?,
    isOverdue: Boolean,
    modifier: Modifier = Modifier
) {
    if (dueDate == null && dropDeadDate == null) return
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (dueDate != null) {
            Text(
                text = fmt.format(Date(dueDate)),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Empty left side so deadline stays right-justified
            Text("", style = MaterialTheme.typography.bodySmall)
        }
        if (dropDeadDate != null) {
            Text(
                text = "⚠ ${fmt.format(Date(dropDeadDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
