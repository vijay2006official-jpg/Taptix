package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.LogStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistent activity audit logs with 7-day auto-expiry,
 * cap management, and filtering capabilities.
 */
class ActivityLogManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addLog(
        appName: String,
        actionText: String,
        status: LogStatus,
        details: String = "",
        fare: Double? = null,
        distanceKm: Double? = null,
        destination: String = "",
        rulesEvaluated: String = ""
    ) {
        val now = System.currentTimeMillis()
        val currentLogs = getLogs().toMutableList()
        val newItem = ActivityLogItem(
            id = now,
            timestamp = now,
            appName = appName,
            actionText = actionText,
            status = status,
            details = details,
            fare = fare,
            distanceKm = distanceKm,
            destination = destination,
            rulesEvaluated = rulesEvaluated
        )
        currentLogs.add(0, newItem)

        // Rule 14: Auto-expire old entries (older than 7 days) & maintain cap
        val sevenDaysAgo = now - SEVEN_DAYS_MS
        val nonExpired = currentLogs.filter { it.timestamp >= sevenDaysAgo }
        val trimmed = if (nonExpired.size > MAX_LOG_ENTRIES) nonExpired.take(MAX_LOG_ENTRIES) else nonExpired
        saveLogs(trimmed)
    }

    @Synchronized
    fun getLogs(): List<ActivityLogItem> {
        val jsonStr = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        val list = mutableListOf<ActivityLogItem>()
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - SEVEN_DAYS_MS

        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("timestamp", now)
                // Filter expired entries on read
                if (ts < sevenDaysAgo) continue

                val fareVal = if (obj.has("fare") && !obj.isNull("fare")) obj.getDouble("fare") else null
                val distVal = if (obj.has("distanceKm") && !obj.isNull("distanceKm")) obj.getDouble("distanceKm") else null

                list.add(
                    ActivityLogItem(
                        id = obj.optLong("id", ts),
                        timestamp = ts,
                        appName = obj.optString("appName", "Unknown"),
                        actionText = obj.optString("actionText", ""),
                        status = try {
                            LogStatus.valueOf(obj.optString("status", LogStatus.ACCEPTED.name))
                        } catch (e: Exception) {
                            LogStatus.ACCEPTED
                        },
                        details = obj.optString("details", ""),
                        fare = fareVal,
                        distanceKm = distVal,
                        destination = obj.optString("destination", ""),
                        rulesEvaluated = obj.optString("rulesEvaluated", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun getFilteredLogs(statusFilter: LogStatus? = null, appFilter: String? = null): List<ActivityLogItem> {
        val all = getLogs()
        return all.filter { item ->
            val matchStatus = statusFilter == null || item.status == statusFilter
            val matchApp = appFilter.isNullOrBlank() || item.appName.contains(appFilter, ignoreCase = true)
            matchStatus && matchApp
        }
    }

    @Synchronized
    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun saveLogs(logs: List<ActivityLogItem>) {
        val arr = JSONArray()
        for (item in logs) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("appName", item.appName)
                put("actionText", item.actionText)
                put("status", item.status.name)
                put("details", item.details)
                if (item.fare != null) put("fare", item.fare)
                if (item.distanceKm != null) put("distanceKm", item.distanceKm)
                if (item.destination.isNotBlank()) put("destination", item.destination)
                if (item.rulesEvaluated.isNotBlank()) put("rulesEvaluated", item.rulesEvaluated)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taptix_activity_logs"
        private const val KEY_LOGS = "logs_json"
        private const val MAX_LOG_ENTRIES = 150
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: ActivityLogManager? = null

        fun getInstance(context: Context): ActivityLogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivityLogManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
