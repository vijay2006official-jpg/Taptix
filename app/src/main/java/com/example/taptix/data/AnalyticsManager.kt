package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.DailyAnalyticsRecord
import com.example.taptix.model.EarningsSummary
import com.example.taptix.model.LogStatus
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * Driver Analytics Engine & Aggregator.
 * Manages daily records, earnings summaries, performance stats, and expense tracking.
 */
class AnalyticsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.US)

    fun getTodayDateString(): String = dateFormat.format(Date())

    @Synchronized
    fun getAllRecords(): List<DailyAnalyticsRecord> {
        val jsonStr = prefs.getString(KEY_DAILY_RECORDS, null) ?: return emptyList()
        val list = mutableListOf<DailyAnalyticsRecord>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(DailyAnalyticsRecord.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.dateString }
    }

    @Synchronized
    fun getRecordForDate(dateStr: String): DailyAnalyticsRecord {
        return getAllRecords().firstOrNull { it.dateString == dateStr }
            ?: DailyAnalyticsRecord(dateString = dateStr)
    }

    @Synchronized
    fun getTodayRecord(): DailyAnalyticsRecord {
        return getRecordForDate(getTodayDateString())
    }

    @Synchronized
    fun saveDailyRecord(record: DailyAnalyticsRecord) {
        val records = getAllRecords().toMutableList()
        val index = records.indexOfFirst { it.dateString == record.dateString }
        if (index >= 0) {
            records[index] = record
        } else {
            records.add(record)
        }
        // Retain up to 90 days of daily aggregates
        val trimmed = records.sortedByDescending { it.dateString }.take(90)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_DAILY_RECORDS, arr.toString()).apply()
    }

    @Synchronized
    fun recordBooking(
        fare: Double?,
        distanceKm: Double?,
        wasAccepted: Boolean,
        isCompleted: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val dateStr = dateFormat.format(Date(timestamp))
        val current = getRecordForDate(dateStr)

        val validFare = if (fare != null && fare > 0.0 && !fare.isNaN() && !fare.isInfinite()) fare else 0.0
        val validDist = if (distanceKm != null && distanceKm > 0.0 && !distanceKm.isNaN() && !distanceKm.isInfinite()) distanceKm else 0.0

        val updated = current.copy(
            earnings = if (wasAccepted) current.earnings + validFare else current.earnings,
            acceptedCount = if (wasAccepted) current.acceptedCount + 1 else current.acceptedCount,
            rejectedCount = if (!wasAccepted) current.rejectedCount + 1 else current.rejectedCount,
            completedCount = if (isCompleted || wasAccepted) current.completedCount + 1 else current.completedCount,
            distanceKm = if (wasAccepted) current.distanceKm + validDist else current.distanceKm
        )
        saveDailyRecord(updated)
    }

    @Synchronized
    fun recordExpense(fuelAmount: Double, otherAmount: Double, dateStr: String = getTodayDateString()) {
        val current = getRecordForDate(dateStr)
        val updated = current.copy(
            fuelExpense = current.fuelExpense + fuelAmount.coerceAtLeast(0.0),
            otherExpenses = current.otherExpenses + otherAmount.coerceAtLeast(0.0)
        )
        saveDailyRecord(updated)
    }

    @Synchronized
    fun getTodaySummary(): EarningsSummary {
        val today = getTodayRecord()
        return EarningsSummary(
            periodTitle = "Today",
            totalEarnings = today.earnings,
            totalDistanceKm = today.distanceKm,
            completedTrips = today.completedCount,
            acceptedTrips = today.acceptedCount,
            rejectedTrips = today.rejectedCount,
            averageFare = today.averageFare,
            totalExpenses = today.totalExpenses,
            netProfit = today.netProfit,
            acceptanceRate = today.acceptanceRate,
            dailyBreakdown = listOf(Pair("Today", today.earnings))
        )
    }

    @Synchronized
    fun getWeeklySummary(): EarningsSummary {
        val cal = Calendar.getInstance()
        val breakdown = mutableListOf<Pair<String, Double>>()
        var sumEarnings = 0.0
        var sumDistance = 0.0
        var sumAccepted = 0
        var sumRejected = 0
        var sumCompleted = 0
        var sumExpenses = 0.0

        // Last 7 days in chronological order
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val dStr = dateFormat.format(c.time)
            val dayName = dayNameFormat.format(c.time)
            val record = getRecordForDate(dStr)

            sumEarnings += record.earnings
            sumDistance += record.distanceKm
            sumAccepted += record.acceptedCount
            sumRejected += record.rejectedCount
            sumCompleted += record.completedCount
            sumExpenses += record.totalExpenses
            breakdown.add(Pair(dayName, record.earnings))
        }

        val totalRequests = sumAccepted + sumRejected
        val rate = if (totalRequests > 0) ((sumAccepted * 100) / totalRequests) else 0
        val avgFare = if (sumAccepted > 0) sumEarnings / sumAccepted else 0.0

        return EarningsSummary(
            periodTitle = "This Week (7 Days)",
            totalEarnings = sumEarnings,
            totalDistanceKm = sumDistance,
            completedTrips = sumCompleted,
            acceptedTrips = sumAccepted,
            rejectedTrips = sumRejected,
            averageFare = avgFare,
            totalExpenses = sumExpenses,
            netProfit = sumEarnings - sumExpenses,
            acceptanceRate = rate,
            dailyBreakdown = breakdown
        )
    }

    @Synchronized
    fun getMonthlySummary(): EarningsSummary {
        val currentMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val records = getAllRecords().filter { it.dateString.startsWith(currentMonthPrefix) }

        val sumEarnings = records.sumOf { it.earnings }
        val sumDistance = records.sumOf { it.distanceKm }
        val sumAccepted = records.sumOf { it.acceptedCount }
        val sumRejected = records.sumOf { it.rejectedCount }
        val sumCompleted = records.sumOf { it.completedCount }
        val sumExpenses = records.sumOf { it.totalExpenses }

        val totalRequests = sumAccepted + sumRejected
        val rate = if (totalRequests > 0) ((sumAccepted * 100) / totalRequests) else 0
        val avgFare = if (sumAccepted > 0) sumEarnings / sumAccepted else 0.0

        return EarningsSummary(
            periodTitle = "This Month",
            totalEarnings = sumEarnings,
            totalDistanceKm = sumDistance,
            completedTrips = sumCompleted,
            acceptedTrips = sumAccepted,
            rejectedTrips = sumRejected,
            averageFare = avgFare,
            totalExpenses = sumExpenses,
            netProfit = sumEarnings - sumExpenses,
            acceptanceRate = rate
        )
    }

    /**
     * Bootstraps daily records from existing activity audit logs if analytics store is empty.
     */
    @Synchronized
    fun syncFromLogs(logs: List<ActivityLogItem>) {
        if (logs.isEmpty()) return
        val currentRecords = getAllRecords()
        if (currentRecords.isNotEmpty()) return // Already populated

        val grouped = logs.groupBy { dateFormat.format(Date(it.timestamp)) }
        for ((dateStr, dayLogs) in grouped) {
            val accepted = dayLogs.filter { it.status == LogStatus.ACCEPTED }
            val rejected = dayLogs.filter { it.status != LogStatus.ACCEPTED && it.status != LogStatus.SYSTEM_EVENT }
            val earnings = accepted.sumOf { it.fare ?: 0.0 }
            val distance = accepted.sumOf { it.distanceKm ?: 0.0 }

            val record = DailyAnalyticsRecord(
                dateString = dateStr,
                timestamp = dayLogs.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                earnings = earnings,
                acceptedCount = accepted.size,
                rejectedCount = rejected.size,
                completedCount = accepted.size,
                distanceKm = distance
            )
            saveDailyRecord(record)
        }
    }

    @Synchronized
    fun clearAnalytics() {
        prefs.edit().remove(KEY_DAILY_RECORDS).apply()
    }

    companion object {
        private const val PREFS_NAME = "taptix_driver_analytics"
        private const val KEY_DAILY_RECORDS = "key_daily_records_json"

        @Volatile
        private var INSTANCE: AnalyticsManager? = null

        fun getInstance(context: Context): AnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnalyticsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
