package com.ducktask.app.util

import com.ducktask.app.domain.model.RepeatRule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val DUCK_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

fun LocalDateTime.toEpochMillis(): Long = atZone(DUCK_TIME_ZONE).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(DUCK_TIME_ZONE).toLocalDateTime()

fun RepeatRule.nextRunAfter(previousRunMillis: Long, afterMillis: Long = System.currentTimeMillis()): Long {
    var next = previousRunMillis.toLocalDateTime()
    val after = afterMillis.toLocalDateTime()

    repeat(10_000) {
        next = when {
            years > 0 -> next.plusYears(years.toLong())
            months > 0 -> next.plusMonths(months.toLong())
            weeks > 0 -> next.plusWeeks(weeks.toLong())
            days > 0 -> next.plusDays(days.toLong())
            hours > 0 -> next.plusHours(hours.toLong())
            minutes > 0 -> next.plusMinutes(minutes.toLong())
            seconds > 0 -> next.plusSeconds(seconds.toLong())
            else -> return previousRunMillis
        }
        if (next.isAfter(after)) return next.toEpochMillis()
    }

    return after.plusYears(1).toEpochMillis()
}

fun formatReminderTime(epochMillis: Long): String {
    val time = epochMillis.toLocalDateTime()
    val now = LocalDateTime.now(DUCK_TIME_ZONE)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())
    val clock = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        days == 0L -> "今天 $clock"
        days == 1L -> "明天 $clock"
        days == 2L -> "后天 $clock"
        days in 3..6 -> "${weekdayText(time.dayOfWeek.value)} $clock"
        else -> time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}

fun formatAbsoluteTime(epochMillis: Long): String =
    epochMillis.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun weekdayText(value: Int): String = when (value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
