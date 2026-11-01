package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.ActivitySummary
import com.example.taptix.model.DailyAnalyticsRecord
import com.example.taptix.model.LogStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages persistent driver activity records, acceptance rate trends,
 * and 7-day operational performance without financial or earnings tracking.
 */
class AnalyticsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayDateString(): String = dateFormat.format(Date())

    @Synchronized
    fun getAllRecords(): List<DailyAnalyticsRecord> {
        val jsonStr = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        val list = mutableListOf<DailyAnalyticsRecord>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(DailyAnalyticsRecord.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun getRecordForDate(dateStr: String): DailyAnalyticsRecord {
        val all = getAllRecords()
        return all.firstOrNull { it.dateString == dateStr } ?: DailyAnalyticsRecord(dateString = dateStr)
    }

    @Synchronized
    fun getTodayRecord(): DailyAnalyticsRecord {
        return getRecordForDate(getTodayDateString())
    }

    @Synchronized
    fun saveDailyRecord(record: DailyAnalyticsRecord) {
        val all = getAllRecords().toMutableList()
        val index = all.indexOfFirst { it.dateString == record.dateString }
        if (index != -1) {
            all[index] = record
        } else {
            all.add(0, record)
        }

        // Keep up to 60 days of historical activity records
        val trimmed = if (all.size > 60) all.take(60) else all
        val arr = JSONArray()
        for (item in trimmed) {
            arr.put(item.toJson())
        }
        prefs.edit().putString(KEY_RECORDS_JSON, arr.toString()).apply()
    }

    @Synchronized
    fun recordBooking(
        wasAccepted: Boolean,
        isCompleted: Boolean = false,
        distanceKm: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val dateStr = dateFormat.format(Date(timestamp))
        val current = getRecordForDate(dateStr)

        val validDist = if (distanceKm != null && distanceKm > 0.0 && !distanceKm.isNaN() && !distanceKm.isInfinite()) {
            distanceKm
        } else 0.0

        val updated = current.copy(
            timestamp = timestamp,
            acceptedCount = current.acceptedCount + if (wasAccepted) 1 else 0,
            rejectedCount = current.rejectedCount + if (!wasAccepted) 1 else 0,
            completedCount = current.completedCount + if (isCompleted) 1 else 0,
            distanceKm = current.distanceKm + validDist
        )
        saveDailyRecord(updated)
    }

    @Synchronized
    fun getTodaySummary(): ActivitySummary {
        val today = getTodayRecord()
        return ActivitySummary(
            periodLabel = "Today",
            totalOffers = today.totalRequests,
            acceptedCount = today.acceptedCount,
            rejectedCount = today.rejectedCount,
            completedCount = today.completedCount,
            distanceKm = today.distanceKm,
            dailyRecords = listOf(today)
        )
    }

    @Synchronized
    fun getWeeklySummary(): ActivitySummary {
        val all = getAllRecords()
        val calendar = Calendar.getInstance()
        val records7Days = mutableListOf<DailyAnalyticsRecord>()

        for (i in 0 until 7) {
            val d = dateFormat.format(calendar.time)
            val rec = all.firstOrNull { it.dateString == d } ?: DailyAnalyticsRecord(dateString = d)
            records7Days.add(0, rec)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        var totalAccepted = 0
        var totalRejected = 0
        var totalCompleted = 0
        var totalDist = 0.0

        for (r in records7Days) {
            totalAccepted += r.acceptedCount
            totalRejected += r.rejectedCount
            totalCompleted += r.completedCount
            totalDist += r.distanceKm
        }

        return ActivitySummary(
            periodLabel = "This Week",
            totalOffers = totalAccepted + totalRejected,
            acceptedCount = totalAccepted,
            rejectedCount = totalRejected,
            completedCount = totalCompleted,
            distanceKm = totalDist,
            dailyRecords = records7Days
        )
    }

    @Synchronized
    fun getMonthlySummary(): ActivitySummary {
        val all = getAllRecords()
        val currentMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthRecords = all.filter { it.dateString.startsWith(currentMonthPrefix) }

        var totalAccepted = 0
        var totalRejected = 0
        var totalCompleted = 0
        var totalDist = 0.0

        for (r in monthRecords) {
            totalAccepted += r.acceptedCount
            totalRejected += r.rejectedCount
            totalCompleted += r.completedCount
            totalDist += r.distanceKm
        }

        return ActivitySummary(
            periodLabel = "This Month",
            totalOffers = totalAccepted + totalRejected,
            acceptedCount = totalAccepted,
            rejectedCount = totalRejected,
            completedCount = totalCompleted,
            distanceKm = totalDist,
            dailyRecords = monthRecords
        )
    }

    @Synchronized
    fun syncFromLogs(logs: List<ActivityLogItem>) {
        if (logs.isEmpty()) return
        val dailyMap = mutableMapOf<String, MutableList<ActivityLogItem>>()

        for (log in logs) {
            val dateStr = dateFormat.format(Date(log.timestamp))
            dailyMap.getOrPut(dateStr) { mutableListOf() }.add(log)
        }

        for ((dateStr, dayLogs) in dailyMap) {
            var accepted = 0
            var rejected = 0
            var completed = 0
            var distance = 0.0

            for (item in dayLogs) {
                if (item.status == LogStatus.ACCEPTED) {
                    accepted++
                    completed++
                    if (item.distanceKm != null && item.distanceKm > 0.0) {
                        distance += item.distanceKm
                    }
                } else {
                    rejected++
                }
            }

            val existing = getRecordForDate(dateStr)
            val merged = existing.copy(
                acceptedCount = maxOf(existing.acceptedCount, accepted),
                rejectedCount = maxOf(existing.rejectedCount, rejected),
                completedCount = maxOf(existing.completedCount, completed),
                distanceKm = maxOf(existing.distanceKm, distance)
            )
            saveDailyRecord(merged)
        }
    }

    @Synchronized
    fun clearAnalytics() {
        prefs.edit().remove(KEY_RECORDS_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "taptix_driver_analytics"
        private const val KEY_RECORDS_JSON = "key_analytics_records_json"

        @Volatile
        private var INSTANCE: AnalyticsManager? = null

        fun getInstance(context: Context): AnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
