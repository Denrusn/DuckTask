package com.ducktask.app.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.UUID

const val DEFAULT_USER_ID = "local"
const val TRIGGER_TYPE_DATE = "date"
const val TRIGGER_TYPE_CALENDAR = "calendar"
const val TRIGGER_TYPE_INTERVAL = "interval"

object TaskStatus {
    const val PENDING = 0
    const val COMPLETED = 1
    const val DELETED = 2
}

@Entity(
    tableName = "reminder_tasks",
    indices = [Index(value = ["taskId"], unique = true)]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String = UUID.randomUUID().toString(),
    val userId: String = DEFAULT_USER_ID,
    val event: String,
    val description: String,
    val reminderTime: Long,
    val repeat: String? = null,
    val triggerType: String = TRIGGER_TYPE_DATE,
    val status: Int = TaskStatus.PENDING,
    val createTime: Long = System.currentTimeMillis(),
    val nextRunTime: Long = reminderTime
) {
    fun repeatRule(): RepeatRule? = repeat?.let(RepeatRule::fromJson)

    fun hasRepeat(): Boolean = repeatRule()?.isRepeating() == true
}

data class RepeatRule(
    val years: Int = 0,
    val months: Int = 0,
    val weeks: Int = 0,
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0
) {
    fun isRepeating(): Boolean =
        years > 0 || months > 0 || weeks > 0 || days > 0 || hours > 0 || minutes > 0 || seconds > 0

    fun triggerType(): String =
        if (hours > 0 || minutes > 0 || seconds > 0) TRIGGER_TYPE_INTERVAL else TRIGGER_TYPE_CALENDAR

    fun toJson(): String = JSONObject()
        .put("years", years)
        .put("months", months)
        .put("weeks", weeks)
        .put("days", days)
        .put("hours", hours)
        .put("minutes", minutes)
        .put("seconds", seconds)
        .toString()

    fun toHumanText(): String {
        return when {
            years > 0 -> if (years == 1) "每年" else "每${years}年"
            months > 0 -> if (months == 1) "每月" else "每${months}个月"
            weeks > 0 -> if (weeks == 1) "每周" else "每${weeks}周"
            days > 0 -> if (days == 1) "每天" else "每${days}天"
            hours > 0 -> if (hours == 1) "每小时" else "每${hours}小时"
            minutes > 0 -> if (minutes == 1) "每分钟" else "每${minutes}分钟"
            seconds > 0 -> if (seconds == 1) "每秒" else "每${seconds}秒"
            else -> "一次性"
        }
    }

    companion object {
        fun fromJson(json: String): RepeatRule {
            val obj = JSONObject(json)
            return RepeatRule(
                years = obj.optInt("years", 0),
                months = obj.optInt("months", 0),
                weeks = obj.optInt("weeks", 0),
                days = obj.optInt("days", 0),
                hours = obj.optInt("hours", 0),
                minutes = obj.optInt("minutes", 0),
                seconds = obj.optInt("seconds", 0)
            )
        }
    }
}
