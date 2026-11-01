package com.example.taptix.model

import org.json.JSONObject

/**
 * Immutable analytics record capturing driver earnings, trip volume,
 * distances, and expenses for a single calendar day.
 */
data class DailyAnalyticsRecord(
    val dateString: String, // Format: "yyyy-MM-dd"
    val timestamp: Long = System.currentTimeMillis(),
    val earnings: Double = 0.0,
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val completedCount: Int = 0,
    val distanceKm: Double = 0.0,
    val fuelExpense: Double = 0.0,
    val otherExpenses: Double = 0.0
) {
    val totalExpenses: Double get() = fuelExpense + otherExpenses
    val netProfit: Double get() = earnings - totalExpenses
    val totalRequests: Int get() = acceptedCount + rejectedCount
    val acceptanceRate: Int get() = if (totalRequests > 0) ((acceptedCount * 100) / totalRequests) else 0
    val averageFare: Double get() = if (acceptedCount > 0) earnings / acceptedCount else 0.0

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("dateString", dateString)
        obj.put("timestamp", timestamp)
        obj.put("earnings", earnings)
        obj.put("acceptedCount", acceptedCount)
        obj.put("rejectedCount", rejectedCount)
        obj.put("completedCount", completedCount)
        obj.put("distanceKm", distanceKm)
        obj.put("fuelExpense", fuelExpense)
        obj.put("otherExpenses", otherExpenses)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): DailyAnalyticsRecord {
            return DailyAnalyticsRecord(
                dateString = obj.optString("dateString", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                earnings = obj.optDouble("earnings", 0.0),
                acceptedCount = obj.optInt("acceptedCount", 0),
                rejectedCount = obj.optInt("rejectedCount", 0),
                completedCount = obj.optInt("completedCount", 0),
                distanceKm = obj.optDouble("distanceKm", 0.0),
                fuelExpense = obj.optDouble("fuelExpense", 0.0),
                otherExpenses = obj.optDouble("otherExpenses", 0.0)
            )
        }
    }
}
