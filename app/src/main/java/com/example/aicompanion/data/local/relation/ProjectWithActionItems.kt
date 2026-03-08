package com.example.aicompanion.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.aicompanion.data.local.entity.ActionItem
import com.example.aicompanion.data.local.entity.Project

data class ProjectWithActionItems(
    @Embedded val project: Project,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val actionItems: List<ActionItem>
)
