package com.ducktask.app.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val editableFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatEditableDateTime(epochMillis: Long): String = editableFormatter.format(epochMillis.toLocalDateTime())

fun parseEditableDateTime(text: String): LocalDateTime? {
    return try {
        LocalDateTime.parse(text.trim(), editableFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
