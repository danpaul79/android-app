package com.example.aicompanion.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.aicompanion.AICompanionApplication
import com.example.aicompanion.MainActivity
import com.example.aicompanion.reminder.MorningPlanStore

class TodayPlanWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val planStore = MorningPlanStore(context)
        val plan = planStore.getTodaysPlan()

        // Fetch completion status for planned task IDs from Room
        val db = (context.applicationContext as AICompanionApplication).container.taskRepository
        val taskIds = plan?.tasks?.map { it.id } ?: emptyList()
        val completedIds: Set<Long> = if (taskIds.isNotEmpty()) {
            db.getTasksByIds(taskIds).filter { it.isCompleted }.map { it.id }.toSet()
        } else emptySet()

        provideContent {
            GlanceTheme {
                WidgetContent(context, plan, completedIds)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    plan: MorningPlanStore.PlanEntry?,
    completedIds: Set<Long>
) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1C1B1F))) // dark surface
            .padding(12.dp)
            .clickable { context.startActivity(launchIntent) }
    ) {
        // Header row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's plan",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE6E1E5)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (plan != null) {
                val done = completedIds.size
                val total = plan.tasks.size
                Text(
                    text = "$done/$total · ${plan.capacityLabel}",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFCAC4D0)),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (plan == null || plan.tasks.isEmpty()) {
            Text(
                text = "No plan yet — open Pocket Pilot to plan your day",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFCAC4D0)),
                    fontSize = 12.sp
                )
            )
        } else {
            plan.tasks.take(7).forEach { task ->
                val done = task.id in completedIds
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (done) "✓" else "○",
                        style = TextStyle(
                            color = ColorProvider(
                                if (done) Color(0xFF4CAF50) else Color(0xFFCAC4D0)
                            ),
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = task.text,
                        style = TextStyle(
                            color = ColorProvider(
                                if (done) Color(0xFF938F99) else Color(0xFFE6E1E5)
                            ),
                            fontSize = 12.sp
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1
                    )
                    if (task.estimatedMinutes > 0) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        val mins = task.estimatedMinutes
                        val timeStr = if (mins < 60) "${mins}m" else "${mins / 60}h"
                        Text(
                            text = timeStr,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF938F99)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            if (plan.tasks.size > 7) {
                Text(
                    text = "+${plan.tasks.size - 7} more",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF938F99)),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

class TodayPlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayPlanWidget()
}
