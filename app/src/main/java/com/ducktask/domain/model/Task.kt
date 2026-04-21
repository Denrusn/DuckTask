package com.ducktask.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDateTime

@Entity(tableName = "tasks")
@TypeConverters(Converters::class)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val time: LocalDateTime,
    val event: String,
    val desc: String = "",
    val repeatYears: Int = 0,
    val repeatMonths: Int = 0,
    val repeatWeeks: Int = 0,
    val repeatDays: Int = 0,
    val repeatHours: Int = 0,
    val repeatMinutes: Int = 0,
    val deferMinutes: Int = 0,
    val isDone: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun hasRepeat(): Boolean = repeatYears > 0 || repeatMonths > 0 || repeatWeeks > 0 || repeatDays > 0 || repeatHours > 0 || repeatMinutes > 0

    fun notifyTime(): LocalDateTime = time.minusMinutes(deferMinutes.toLong())

    fun reschedule(): Boolean {
        if (!hasRepeat()) return false
        // Calculate next occurrence based on repeat rules
        return true
    }
}

class Converters {
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
}
