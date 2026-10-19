package com.example.taptix.model

/**
 * Audit log entry capturing auto-clicker decisions and actions.
 */
data class ActivityLogItem(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val actionText: String,
    val status: LogStatus,
    val details: String = ""
)

enum class LogStatus {
    ACCEPTED,
    REJECTED_KEYWORD,
    OUTSIDE_ZONE,
    SYSTEM_EVENT
}
