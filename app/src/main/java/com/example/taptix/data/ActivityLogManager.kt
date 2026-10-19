package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.LogStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistent activity audit logs for captain auto-accept events.
 */
class ActivityLogManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addLog(appName: String, actionText: String, status: LogStatus, details: String = "") {
        val currentLogs = getLogs().toMutableList()
        val newItem = ActivityLogItem(
            id = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            appName = appName,
            actionText = actionText,
            status = status,
            details = details
        )
        currentLogs.add(0, newItem)
        // Keep last 50 entries
        val trimmed = if (currentLogs.size > 50) currentLogs.take(50) else currentLogs
        saveLogs(trimmed)
    }

    @Synchronized
    fun getLogs(): List<ActivityLogItem> {
        val jsonStr = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        val list = mutableListOf<ActivityLogItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ActivityLogItem(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        appName = obj.optString("appName", "Unknown"),
                        actionText = obj.optString("actionText", ""),
                        status = try {
                            LogStatus.valueOf(obj.optString("status", LogStatus.ACCEPTED.name))
                        } catch (e: Exception) {
                            LogStatus.ACCEPTED
                        },
                        details = obj.optString("details", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
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
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taptix_activity_logs"
        private const val KEY_LOGS = "logs_json"

        @Volatile
        private var INSTANCE: ActivityLogManager? = null

        fun getInstance(context: Context): ActivityLogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivityLogManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
