package com.example.taptix

import com.example.taptix.engine.*
import com.example.taptix.model.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/**
 * Phase H & Phase J: Comprehensive Testing & Regression Verification Test Suite.
 * Validates the complete driver automation platform across automation engine lifecycle,
 * 4 distinct booking states, boundary filters, target areas, geofencing,
 * operational activity calculations, shift schedule absence, and strict financial restriction.
 */
class ComprehensiveRegressionTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. FOUR DISTINCT STATES & ACCEPTANCE CONFIRMATION TESTING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testFourDistinctStates_SeparationOfAttemptedFromConfirmed() {
        // State 1: Booking detected
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go ₹250.0 1.8 km Koramangala",
            fare = 250.0,
            pickupDistanceKm = 1.8,
            tripDistanceKm = 8.5,
            destinationAddress = "Koramangala, Bengaluru",
            detectedCategory = "CAB_PREMIUM"
        )
        assertNotNull("Booking detected must have valid details", offer)

        // State 2: Automation action attempted
        val isBoundsValid = BookingDetectionValidator.isValidButtonBounds(width = 300, height = 120)
        assertTrue("Bounding box dimensions must be physically valid", isBoundsValid)
        val actionAttempted = true
        assertTrue(actionAttempted)

        // State 3: Acceptance confirmation evaluation
        // If button is STILL visible after tap -> NOT_CONFIRMED (MUST NOT count as accepted)
        val notConfirmedOutcome = AcceptVerifier.evaluate(acceptButtonStillVisible = true)
        assertEquals(AcceptOutcome.NOT_CONFIRMED, notConfirmedOutcome)
        assertFalse(
            "An unconfirmed click attempt must NEVER be counted as confirmed acceptance",
            notConfirmedOutcome == AcceptOutcome.CONFIRMED
        )

        // Only when button DISAPPEARS after tap -> CONFIRMED
        val confirmedOutcome = AcceptVerifier.evaluate(acceptButtonStillVisible = false)
        assertEquals(AcceptOutcome.CONFIRMED, confirmedOutcome)

        // State 4: Booking completed
        val isCompleted = confirmedOutcome == AcceptOutcome.CONFIRMED
        assertTrue("Only confirmed rides can transition to completed state", isCompleted)
    }

    @Test
    fun testControlledRetryPolicy_ExactlyOneRetryPermitted() {
        // First attempt (currentAttempts = 0) with NOT_CONFIRMED -> should retry
        val allowFirstRetry = ControlledRetryPolicy.shouldRetry(
            currentAttempts = 0,
            outcome = AcceptOutcome.NOT_CONFIRMED
        )
        assertTrue("Should allow exactly 1 controlled retry on NOT_CONFIRMED", allowFirstRetry)

        // Second attempt (currentAttempts = 1) -> must NOT retry (prevent infinite click loop)
        val allowSecondRetry = ControlledRetryPolicy.shouldRetry(
            currentAttempts = 1,
            outcome = AcceptOutcome.NOT_CONFIRMED
        )
        assertFalse("Must NEVER retry more than once", allowSecondRetry)

        // If outcome is already CONFIRMED -> must NOT retry
        val noRetryOnSuccess = ControlledRetryPolicy.shouldRetry(
            currentAttempts = 0,
            outcome = AcceptOutcome.CONFIRMED
        )
        assertFalse("Must never retry when already confirmed", noRetryOnSuccess)

        // If outcome is UNKNOWN -> must NOT retry
        val noRetryOnUnknown = ControlledRetryPolicy.shouldRetry(
            currentAttempts = 0,
            outcome = AcceptOutcome.UNKNOWN
        )
        assertFalse("Must never retry on unknown outcome", noRetryOnUnknown)
    }

    @Test
    fun testDuplicateClickPrevention_DuplicateOfferGuard() {
        val guard = DuplicateOfferGuard(windowMs = 1500L)
        val now = 100000L
        val sig1 = DuplicateOfferGuard.signature("com.ubercab.driver", 250.0, 1.8, "ACCEPT")

        // First click allowed (not a duplicate)
        assertFalse("First offer should not be marked duplicate", guard.isDuplicate(sig1, now))
        guard.record(sig1, now)

        // Same booking immediately after -> is duplicate
        assertTrue("Immediate duplicate click on same booking must be blocked", guard.isDuplicate(sig1, now + 100L))
        assertTrue("Duplicate click within window must be blocked", guard.isDuplicate(sig1, now + 1499L))

        // Same booking after window elapsed -> not duplicate
        assertFalse("Click after window expiry must be permitted", guard.isDuplicate(sig1, now + 1501L))

        // Different booking ID -> not duplicate
        val sig2 = DuplicateOfferGuard.signature("com.olacabs.oladriver", 300.0, 2.5, "ACCEPT")
        assertFalse("Distinct booking signature must not be blocked by other booking guard", guard.isDuplicate(sig2, now + 200L))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. PHASE D FILTER ENGINE BOUNDARY & CONFLICT TESTING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testBookingFilters_StrictBoundaryEnforcement() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            maxFareEnabled = true,
            maxFareAmount = 800.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.0,
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 25.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 18.0,
            categoryFilterEnabled = true,
            allowedCategories = setOf("CAB_PREMIUM"),
            includeKeywordsEnabled = false
        )

        // Case A: Fare exactly at boundary
        val atMinFare = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 150",
            fare = 150.0,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 7.0,
            detectedCategory = "CAB_PREMIUM"
        )
        assertTrue("Fare exactly at minFare must be accepted", OfferRuleEngine.evaluate(atMinFare, rules).isAccepted)

        val belowMinFare = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 149.9",
            fare = 149.9,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 7.0,
            detectedCategory = "CAB_PREMIUM"
        )
        val resultBelow = OfferRuleEngine.evaluate(belowMinFare, rules)
        assertFalse("Fare below minFare must be rejected", resultBelow.isAccepted)
        assertTrue(resultBelow.rejectedReason!!.contains("fare is too low", ignoreCase = true))

        // Case B: Max Fare exceeded
        val exceedsMax = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 800.1",
            fare = 800.1,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 10.0,
            detectedCategory = "CAB_PREMIUM"
        )
        val resultMax = OfferRuleEngine.evaluate(exceedsMax, rules)
        assertFalse("Fare exceeding maxFare must be rejected", resultMax.isAccepted)
        assertTrue(resultMax.rejectedReason!!.contains("exceeds maximum", ignoreCase = true))

        // Case C: Pickup distance boundary
        val atMaxPickup = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Pickup 3.0km",
            fare = 300.0,
            pickupDistanceKm = 3.0,
            tripDistanceKm = 10.0,
            detectedCategory = "CAB_PREMIUM"
        )
        assertTrue("Pickup distance exactly at max must be accepted", OfferRuleEngine.evaluate(atMaxPickup, rules).isAccepted)

        val exceedsPickup = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Pickup 3.1km",
            fare = 300.0,
            pickupDistanceKm = 3.1,
            tripDistanceKm = 10.0,
            detectedCategory = "CAB_PREMIUM"
        )
        val resultPickup = OfferRuleEngine.evaluate(exceedsPickup, rules)
        assertFalse("Pickup distance exceeding max must be rejected", resultPickup.isAccepted)
        assertTrue(resultPickup.rejectedReason!!.contains("pickup distance is too high", ignoreCase = true))

        // Case D: Fare per KM boundary
        // ₹300 for 15 km = ₹20/km (>= ₹18/km min) -> ACCEPT
        val goodFarePerKm = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 300 dist 15",
            fare = 300.0,
            pickupDistanceKm = 1.0,
            tripDistanceKm = 15.0,
            detectedCategory = "CAB_PREMIUM"
        )
        assertTrue(OfferRuleEngine.evaluate(goodFarePerKm, rules).isAccepted)

        // ₹200 for 15 km = ₹13.33/km (< ₹18/km min) -> REJECT
        val lowFarePerKm = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 200 dist 15",
            fare = 200.0,
            pickupDistanceKm = 1.0,
            tripDistanceKm = 15.0,
            detectedCategory = "CAB_PREMIUM"
        )
        val resultFarePerKm = OfferRuleEngine.evaluate(lowFarePerKm, rules)
        assertFalse(resultFarePerKm.isAccepted)
        assertTrue(resultFarePerKm.rejectedReason!!.contains("fare per km is too low", ignoreCase = true))
    }

    @Test
    fun testBookingFilters_MissingOrInvalidInformationSafelyRejected() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 100.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )

        // Completely missing fare information
        val missingFare = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "No Fare Given",
            fare = null,
            pickupDistanceKm = 2.0
        )
        val resultMissing = OfferRuleEngine.evaluate(missingFare, rules)
        assertFalse("Offer missing fare information must be rejected safely", resultMissing.isAccepted)
        assertTrue(resultMissing.rejectedReason!!.contains("incomplete", ignoreCase = true))

        // Missing distance information when distance rule active
        val distanceRules = SmartAcceptRules(
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 2.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )
        val missingDist = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare 200 No Dist",
            fare = 200.0,
            pickupDistanceKm = null
        )
        val resultDist = OfferRuleEngine.evaluate(missingDist, distanceRules)
        assertFalse("Offer missing pickup distance must be rejected safely", resultDist.isAccepted)
        assertTrue(resultDist.rejectedReason!!.contains("incomplete", ignoreCase = true))

        // Negative or absurd values sanitized by OfferParser
        assertNull("Negative fare must be sanitized to null", OfferParser.validateFare(-50.0))
        assertNull("Zero fare must be sanitized to null", OfferParser.validateFare(0.0))
        assertNull("Absurd fare exceeding 100,000 must be sanitized to null", OfferParser.validateFare(120000.0))
        assertNull("Negative distance must be sanitized to null", OfferParser.validateDistance(-5.0))
        assertNull("Absurd distance exceeding 500km must be sanitized to null", OfferParser.validateDistance(999.0))
    }

    @Test
    fun testCategoryFiltering_AllSupportedPlatforms() {
        val rules = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("AUTO", "MOTO"),
            includeKeywordsEnabled = false
        )

        val autoOffer = OfferDetails(
            packageName = "com.olacabs.oladriver",
            rawText = "Ola Auto ₹150",
            fare = 150.0,
            pickupDistanceKm = 1.0,
            detectedCategory = "AUTO"
        )
        assertTrue("AUTO must be accepted when enabled", OfferRuleEngine.evaluate(autoOffer, rules).isAccepted)

        val motoOffer = OfferDetails(
            packageName = "com.rapido.rider",
            rawText = "Rapido Bike ₹80",
            fare = 80.0,
            pickupDistanceKm = 0.5,
            detectedCategory = "MOTO"
        )
        assertTrue("MOTO must be accepted when enabled", OfferRuleEngine.evaluate(motoOffer, rules).isAccepted)

        val cabOffer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Premier ₹350",
            fare = 350.0,
            pickupDistanceKm = 1.2,
            detectedCategory = "CAB_PREMIUM"
        )
        val resultCab = OfferRuleEngine.evaluate(cabOffer, rules)
        assertFalse("CAB_PREMIUM must be rejected when category not enabled", resultCab.isAccepted)
        assertTrue(resultCab.rejectedReason!!.contains("service category", ignoreCase = true) && resultCab.rejectedReason!!.contains("is disabled", ignoreCase = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PHASE E SMART TARGET-AREA & GEOFENCING TESTING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testGeofenceEngine_BoundaryCalculationsAndTolerance() {
        // Center: Kempegowda Bus Station (Majestic), Bengaluru
        val centerLat = 12.9781
        val centerLng = 77.5696
        val zone = MapZone(
            id = "majestic_zone",
            name = "Majestic Hub",
            lat = centerLat,
            lng = centerLng,
            radiusKm = 2.0,
            isEnabled = true
        )

        // Point inside zone (approx 0.8 km east)
        val insideLat = 12.9781
        val insideLng = 77.5770
        assertTrue("Point within 0.8km must be inside 2.0km zone", GeofenceEngine.isLocationInsideZone(insideLat, insideLng, zone))

        // Point comfortably outside zone (approx 15 km away)
        val outsideLat = 13.0827
        val outsideLng = 80.2707
        assertFalse("Point far away must be outside 2.0km zone", GeofenceEngine.isLocationInsideZone(outsideLat, outsideLng, zone))

        // Boundary tolerance test: point within 1.20x tolerance
        val distTolerance = GeofenceEngine.haversineDistanceKm(centerLat, centerLng, 12.9781, 77.5890)
        assertTrue(distTolerance in 2.0..2.3)
        assertTrue(
            "Point within 1.20x GPS drift tolerance must be accepted",
            GeofenceEngine.isLocationInsideZone(12.9781, 77.5890, zone)
        )
    }

    @Test
    fun testGeofenceEngine_AccuracyGatingAndCoordinatePrivacyMasking() {
        // GPS accuracy > 150m is unreliable and must be rejected
        assertFalse("GPS fix with accuracy > 150m must be rejected as unreliable", GeofenceEngine.validateAccuracy(220f))

        // GPS accuracy <= 150m is reliable
        assertTrue("GPS fix with accuracy 15m must be accepted", GeofenceEngine.validateAccuracy(15f))
        assertTrue("GPS fix with accuracy 150m must be accepted", GeofenceEngine.validateAccuracy(150f))

        // Coordinate privacy masking check
        val masked = GeofenceEngine.sanitizeLocationForLogs(12.9716, 77.5946)
        assertEquals("12.97**, 77.59**", masked)
        assertFalse("Masked string must not leak full precision digits", masked.contains("12.9716"))
        assertFalse("Masked string must not leak full precision digits", masked.contains("77.5946"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PHASE F / PHASE J DRIVER DASHBOARD & ACTIVITY TESTING (STRICTLY NON-FINANCIAL)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testDriverActivity_OperationalMetricsAccounting() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            acceptedCount = 8,
            rejectedCount = 2,
            completedCount = 6,
            distanceKm = 52.4
        )

        // Trip counts
        assertEquals(8, record.acceptedCount)
        assertEquals(2, record.rejectedCount)
        assertEquals(6, record.completedCount)
        assertEquals(10, record.totalRequests)

        // Acceptance rate: 8 / (8 + 2) = 80%
        assertEquals(80, record.acceptanceRate)

        // Zero division protection
        val zeroRecord = DailyAnalyticsRecord(dateString = "2026-11-01")
        assertEquals(0, zeroRecord.acceptanceRate)
    }

    @Test
    fun testDriverActivity_JsonSerializationAndSanitization() {
        val record = DailyAnalyticsRecord(
            dateString = "2026-11-01",
            acceptedCount = 4,
            rejectedCount = 1,
            completedCount = 4,
            distanceKm = 30.0
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
    fun testFinancialFeatureRestriction_Section10Compliance() {
        val fields = DailyAnalyticsRecord::class.java.declaredFields.map { it.name.lowercase() }
        val financialKeywords = listOf("earnings", "fare", "income", "profit", "expense", "revenue", "cost", "rupee", "price", "wage")

        for (kw in financialKeywords) {
            assertFalse(
                "DailyAnalyticsRecord must NOT contain financial field '$kw' (Section 10)",
                fields.any { it.contains(kw) }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PERMANENT REMOVAL OF AUTOMATIC SHIFT SCHEDULE VERIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testPermanentRemovalOfShiftScheduleAcrossAllModels() {
        // Inspect AppSettings
        val appSettingsFields = com.example.taptix.data.AppSettings::class.java.declaredFields.map { it.name.lowercase() }
        val scheduleKeywords = listOf("shiftschedule", "shift_schedule", "activeday", "shiftstart", "shiftend", "workwindow")

        for (keyword in scheduleKeywords) {
            assertFalse(
                "AppSettings must NOT contain shift schedule field: $keyword",
                appSettingsFields.any { it.contains(keyword) }
            )
        }

        // Inspect SmartAcceptRules
        val smartRulesFields = SmartAcceptRules::class.java.declaredFields.map { it.name.lowercase() }
        for (keyword in scheduleKeywords) {
            assertFalse(
                "SmartAcceptRules must NOT contain shift schedule field: $keyword",
                smartRulesFields.any { it.contains(keyword) }
            )
        }

        // Inspect OfferRuleEngine
        val ruleEngineMethods = OfferRuleEngine::class.java.declaredMethods.map { it.name.lowercase() }
        for (keyword in scheduleKeywords) {
            assertFalse(
                "OfferRuleEngine must NOT contain shift schedule method: $keyword",
                ruleEngineMethods.any { it.contains(keyword) }
            )
        }
    }
}
