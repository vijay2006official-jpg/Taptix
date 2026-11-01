package com.example.taptix.model

/**
 * Aggregated earnings and performance summary for a specific time window
 * (Today, Last 7 Days / This Week, Current Month).
 */
data class EarningsSummary(
    val periodTitle: String,
    val totalEarnings: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val completedTrips: Int = 0,
    val acceptedTrips: Int = 0,
    val rejectedTrips: Int = 0,
    val averageFare: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val acceptanceRate: Int = 0,
    val dailyBreakdown: List<Pair<String, Double>> = emptyList() // "Mon", "Tue" etc. -> Earnings
)
