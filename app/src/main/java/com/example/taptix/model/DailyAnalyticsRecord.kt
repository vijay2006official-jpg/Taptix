package com.example.taptix.model

import org.json.JSONObject

/**
 * Immutable activity record capturing driver trip volume,
 * distances, and acceptance rates for a single calendar day.
 * Strictly free of financial, earnings, and expense tracking.
 */
data class DailyAnalyticsRecord(
    val dateString: String, // Format: "yyyy-MM-dd"
    val timestamp: Long = System.currentTimeMillis(),
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val completedCount: Int = 0,
    val distanceKm: Double = 0.0
) {
    val totalRequests: Int get() = acceptedCount + rejectedCount
    val acceptanceRate: Int get() = if (totalRequests > 0) ((acceptedCount * 100) / totalRequests) else 0

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("dateString", dateString)
        obj.put("timestamp", timestamp)
        obj.put("acceptedCount", acceptedCount)
        obj.put("rejectedCount", rejectedCount)
        obj.put("completedCount", completedCount)
        obj.put("distanceKm", distanceKm)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): DailyAnalyticsRecord {
            return DailyAnalyticsRecord(
                dateString = obj.optString("dateString", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                acceptedCount = obj.optInt("acceptedCount", 0),
                rejectedCount = obj.optInt("rejectedCount", 0),
                completedCount = obj.optInt("completedCount", 0),
                distanceKm = obj.optDouble("distanceKm", 0.0)
            )
        }
    }
}
