package com.example.taptix

import com.example.taptix.model.DailyAnalyticsRecord
import com.example.taptix.model.ActivitySummary
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Phase F / Phase J driver activity analytics.
 * Verifies that driver activity, ride volume, distances, and acceptance rates are tracked,
 * and confirms that no earnings, profit, or financial calculations exist in the model.
 */
class DriverAnalyticsTest {

    @Test
    fun testDailyAnalyticsRecord_operationalMetricsCalculatedCorrectly() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            acceptedCount = 8,
            rejectedCount = 2,
            completedCount = 7,
            distanceKm = 48.5
        )

        assertEquals("2026-11-01", record.dateString)
        assertEquals(8, record.acceptedCount)
        assertEquals(2, record.rejectedCount)
        assertEquals(7, record.completedCount)
        assertEquals(10, record.totalRequests)
        assertEquals(80, record.acceptanceRate)
        assertEquals(48.5, record.distanceKm, 0.001)
    }

    @Test
    fun testDailyAnalyticsRecord_zeroRequests_handlesWithoutException() {
        val emptyRecord = DailyAnalyticsRecord(dateString = "2026-11-01")
        assertEquals(0, emptyRecord.totalRequests)
        assertEquals(0, emptyRecord.acceptanceRate)
    }

    @Test
    fun testDailyAnalyticsRecord_jsonSerialization() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            acceptedCount = 12,
            rejectedCount = 3,
            completedCount = 11,
            distanceKm = 82.0
        )

        val json = record.toJson()
        val deserialized = DailyAnalyticsRecord.fromJson(json)

        assertEquals(record.dateString, deserialized.dateString)
        assertEquals(record.acceptedCount, deserialized.acceptedCount)
        assertEquals(record.rejectedCount, deserialized.rejectedCount)
        assertEquals(record.completedCount, deserialized.completedCount)
        assertEquals(record.distanceKm, deserialized.distanceKm, 0.001)
        assertEquals(record.acceptanceRate, deserialized.acceptanceRate)
    }

    @Test
    fun testActivitySummary_aggregationCalculations() {
        val day1 = DailyAnalyticsRecord(dateString = "2026-10-31", acceptedCount = 5, rejectedCount = 1, completedCount = 5, distanceKm = 30.0)
        val day2 = DailyAnalyticsRecord(dateString = "2026-11-01", acceptedCount = 10, rejectedCount = 2, completedCount = 9, distanceKm = 60.0)

        val summary = ActivitySummary(
            periodLabel = "Test Window",
            totalOffers = 18,
            acceptedCount = 15,
            rejectedCount = 3,
            completedCount = 14,
            distanceKm = 90.0,
            dailyRecords = listOf(day1, day2)
        )

        assertEquals("Test Window", summary.periodLabel)
        assertEquals(18, summary.totalOffers)
        assertEquals(15, summary.acceptedCount)
        assertEquals(14, summary.completedCount)
        assertEquals(90.0, summary.distanceKm, 0.001)
        assertEquals(83, summary.acceptanceRate) // (15 * 100) / 18 = 83
    }

    @Test
    fun testFinancialFeatureRestriction_noFinancialFieldsInModel() {
        val fields = DailyAnalyticsRecord::class.java.declaredFields.map { it.name.lowercase() }
        val financialKeywords = listOf("earnings", "fare", "income", "profit", "expense", "revenue", "cost", "rupee", "price", "wage")

        for (kw in financialKeywords) {
            assertFalse(
                "DailyAnalyticsRecord must NOT contain financial field '$kw' (Section 10)",
                fields.any { it.contains(kw) }
            )
        }
    }
}
