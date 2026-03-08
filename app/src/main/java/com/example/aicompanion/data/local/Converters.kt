package com.example.aicompanion.data.local

import androidx.room.TypeConverter
import com.example.aicompanion.data.local.entity.Priority
import com.example.aicompanion.data.local.entity.SourceType

class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.valueOf(value)
}
