package com.example.taptix.model

/**
 * Summary data class aggregating driver activity metrics
 * (total offers, accepted rides, completed trips, distance driven, and daily records).
 * Strictly operational; contains no monetary or financial figures.
 */
data class ActivitySummary(
    val periodLabel: String,
    val totalOffers: Int = 0,
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val completedCount: Int = 0,
    val distanceKm: Double = 0.0,
    val dailyRecords: List<DailyAnalyticsRecord> = emptyList()
) {
    val acceptanceRate: Int get() = if (totalOffers > 0) ((acceptedCount * 100) / totalOffers) else 0
}

// Typealias for backward compatibility
typealias EarningsSummary = ActivitySummary
