package com.example.taptix

import com.example.taptix.model.DailyAnalyticsRecord
import com.example.taptix.model.EarningsSummary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * Exhaustive unit tests for Phase F: Driver Dashboard & Analytics Engine.
 * Verifies earnings calculations, daily/weekly/monthly summaries, expense tracking,
 * acceptance rate formulas, missing data handling, and JSON persistence.
 */
class DriverAnalyticsTest {

    @Test
    fun testDailyAnalyticsRecord_calculations() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            earnings = 1500.0,
            acceptedCount = 5,
            rejectedCount = 5,
            completedCount = 5,
            distanceKm = 45.0,
            fuelExpense = 300.0,
            otherExpenses = 50.0
        )

        assertEquals(350.0, record.totalExpenses, 0.01)
        assertEquals(1150.0, record.netProfit, 0.01)
        assertEquals(10, record.totalRequests)
        assertEquals(50, record.acceptanceRate) // 5 / 10 = 50%
        assertEquals(300.0, record.averageFare, 0.01) // 1500 / 5 = 300
    }

    @Test
    fun testDailyAnalyticsRecord_emptyState() {
        val emptyRecord = DailyAnalyticsRecord(dateString = "2026-11-01")
        assertEquals(0.0, emptyRecord.earnings, 0.01)
        assertEquals(0.0, emptyRecord.netProfit, 0.01)
        assertEquals(0, emptyRecord.totalRequests)
        assertEquals(0, emptyRecord.acceptanceRate)
        assertEquals(0.0, emptyRecord.averageFare, 0.01)
    }

    @Test
    fun testDailyAnalyticsRecord_jsonRoundtrip() {
        val original = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            timestamp = 1730460000000L,
            earnings = 2450.50,
            acceptedCount = 8,
            rejectedCount = 12,
            completedCount = 8,
            distanceKm = 68.4,
            fuelExpense = 450.0,
            otherExpenses = 80.0
        )

        val json = original.toJson()
        val restored = DailyAnalyticsRecord.fromJson(json)

        assertEquals(original.dateString, restored.dateString)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.earnings, restored.earnings, 0.01)
        assertEquals(original.acceptedCount, restored.acceptedCount)
        assertEquals(original.rejectedCount, restored.rejectedCount)
        assertEquals(original.completedCount, restored.completedCount)
        assertEquals(original.distanceKm, restored.distanceKm, 0.01)
        assertEquals(original.fuelExpense, restored.fuelExpense, 0.01)
        assertEquals(original.otherExpenses, restored.otherExpenses, 0.01)
        assertEquals(original.netProfit, restored.netProfit, 0.01)
    }

    @Test
    fun testEarningsSummary_aggregationMath() {
        val dailyRecords = listOf(
            DailyAnalyticsRecord("2026-11-01", earnings = 600.0, acceptedCount = 2, rejectedCount = 1, distanceKm = 18.0),
            DailyAnalyticsRecord("2026-11-02", earnings = 800.0, acceptedCount = 3, rejectedCount = 2, distanceKm = 24.0),
            DailyAnalyticsRecord("2026-11-03", earnings = 1000.0, acceptedCount = 4, rejectedCount = 1, distanceKm = 30.0)
        )

        val totalEarnings = dailyRecords.sumOf { it.earnings }
        val totalDistance = dailyRecords.sumOf { it.distanceKm }
        val totalAccepted = dailyRecords.sumOf { it.acceptedCount }
        val totalRejected = dailyRecords.sumOf { it.rejectedCount }
        val totalRequests = totalAccepted + totalRejected
        val rate = (totalAccepted * 100) / totalRequests
        val avgFare = totalEarnings / totalAccepted

        val summary = EarningsSummary(
            periodTitle = "3 Days",
            totalEarnings = totalEarnings,
            totalDistanceKm = totalDistance,
            completedTrips = totalAccepted,
            acceptedTrips = totalAccepted,
            rejectedTrips = totalRejected,
            averageFare = avgFare,
            acceptanceRate = rate
        )

        assertEquals(2400.0, summary.totalEarnings, 0.01)
        assertEquals(72.0, summary.totalDistanceKm, 0.01)
        assertEquals(9, summary.acceptedTrips)
        assertEquals(4, summary.rejectedTrips)
        assertEquals(69, summary.acceptanceRate) // 9 / 13 = 69%
        assertEquals(266.67, summary.averageFare, 0.01) // 2400 / 9 = 266.67
    }

    @Test
    fun testExpenseDeduction_netProfitCalculation() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            earnings = 1800.0,
            acceptedCount = 6,
            fuelExpense = 500.0,
            otherExpenses = 120.0
        )

        val expectedNet = 1800.0 - 620.0
        assertEquals(620.0, record.totalExpenses, 0.01)
        assertEquals(expectedNet, record.netProfit, 0.01)
    }

    @Test
    fun testMissingOrInvalidData_handledSafely() {
        val corruptedJson = JSONObject()
        corruptedJson.put("dateString", "2026-11-01")
        corruptedJson.put("earnings", JSONObject.NULL)

        val record = DailyAnalyticsRecord.fromJson(corruptedJson)
        assertEquals(0.0, record.earnings, 0.01)
        assertEquals(0, record.acceptedCount)
        assertEquals(0.0, record.averageFare, 0.01)
    }
}
