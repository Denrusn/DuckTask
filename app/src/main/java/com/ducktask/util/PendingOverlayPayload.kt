package com.ducktask.app.util

data class PendingOverlayPayload(
    val taskId: String,
    val event: String,
    val description: String,
    val logId: Long,
    val notificationId: Int
)
