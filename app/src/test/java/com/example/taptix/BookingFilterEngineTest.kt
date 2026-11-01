package com.example.taptix

import com.example.taptix.engine.OfferParser
import com.example.taptix.engine.OfferRuleEngine
import com.example.taptix.model.BookingServiceCategory
import com.example.taptix.model.OfferDetails
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.UnparsedDataFallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Exhaustive unit test suite for Phase D: Intelligent Booking Filtering Engine.
 * Verifies fare boundaries, fare-per-km rate math, pickup & trip distance isolation,
 * service category detection and exclusion, safety fallbacks, and JSON backward compatibility.
 */
class BookingFilterEngineTest {

    // ── 1. FARE FILTERING ────────────────────────────────────────────────────────

    @Test
    fun testFareFiltering_minFareMet_accepted() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 200.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Premier\n₹ 250\nPickup: 1.5 km",
            fare = 250.0,
            pickupDistanceKm = 1.5
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Min Fare Met") })
    }

    @Test
    fun testFareFiltering_minFareNotMet_rejected() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 300.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 180\nPickup: 1.0 km",
            fare = 180.0,
            pickupDistanceKm = 1.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("fare is too low") == true)
        assertTrue(result.rejectedReason?.contains("below minimum") == true)
    }

    @Test
    fun testFareFiltering_maxFareExceeded_rejected() {
        val rules = SmartAcceptRules(
            maxFareEnabled = true,
            maxFareAmount = 800.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.olacabs.oladriver",
            rawText = "Ola Outstation\n₹ 1200\nPickup: 2.0 km",
            fare = 1200.0,
            pickupDistanceKm = 2.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("fare exceeds maximum") == true)
    }

    @Test
    fun testFareFiltering_maxFareWithinLimit_accepted() {
        val rules = SmartAcceptRules(
            maxFareEnabled = true,
            maxFareAmount = 1000.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.olacabs.oladriver",
            rawText = "Ola Prime\n₹ 650\nPickup: 2.0 km",
            fare = 650.0,
            pickupDistanceKm = 2.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Max Fare Met") })
    }

    @Test
    fun testFareFiltering_missingFare_strictReject() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.rapido.captain",
            rawText = "Rapido Bike\nUnknown Fare\nPickup: 1.0 km",
            fare = null,
            pickupDistanceKm = 1.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("Could not parse fare") == true)
    }

    @Test
    fun testFareFiltering_missingFare_permissiveAccept() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            fallbackOnUnparsedData = UnparsedDataFallback.ACCEPT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.rapido.captain",
            rawText = "Rapido Bike\nPickup: 1.0 km",
            fare = null,
            pickupDistanceKm = 1.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
    }

    // ── 2. DISTANCE FILTERING ────────────────────────────────────────────────────

    @Test
    fun testDistanceFiltering_pickupDistanceMet_accepted() {
        val rules = SmartAcceptRules(
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.rapido.captain",
            rawText = "Pickup: 1.8 km",
            fare = 120.0,
            pickupDistanceKm = 1.8
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Pickup Distance Met") })
    }

    @Test
    fun testDistanceFiltering_pickupDistanceExceeded_rejected() {
        val rules = SmartAcceptRules(
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 2.5,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.rapido.captain",
            rawText = "Pickup: 4.2 km",
            fare = 120.0,
            pickupDistanceKm = 4.2
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("pickup distance is too high") == true)
        assertTrue(result.rejectedReason?.contains("exceeds maximum") == true)
    }

    @Test
    fun testDistanceFiltering_tripDistanceMet_accepted() {
        val rules = SmartAcceptRules(
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 25.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Drop: 18.0 km",
            fare = 350.0,
            tripDistanceKm = 18.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Trip Distance Met") })
    }

    @Test
    fun testDistanceFiltering_tripDistanceExceeded_rejected() {
        val rules = SmartAcceptRules(
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 20.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Trip: 32.5 km",
            fare = 500.0,
            tripDistanceKm = 32.5
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("trip distance is too high") == true)
        assertTrue(result.rejectedReason?.contains("exceeds maximum") == true)
    }

    @Test
    fun testDistanceFiltering_missingTrip_strictReject() {
        val rules = SmartAcceptRules(
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 25.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "No drop distance",
            fare = 400.0,
            tripDistanceKm = null
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("Could not parse trip distance") == true)
    }

    // ── 3. FARE PER KILOMETRE FILTERING ──────────────────────────────────────────

    @Test
    fun testFarePerKm_aboveMinimum_accepted() {
        val rules = SmartAcceptRules(
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 20.0,
            includeKeywordsEnabled = false
        )
        // ₹300 for 12 km trip = ₹25.00/km >= ₹20.00/km
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 300\nTrip: 12.0 km",
            fare = 300.0,
            tripDistanceKm = 12.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Fare/km Met") })
    }

    @Test
    fun testFarePerKm_belowMinimum_rejectedWithExactMath() {
        val rules = SmartAcceptRules(
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 18.0,
            includeKeywordsEnabled = false
        )
        // ₹150 for 10 km trip = ₹15.00/km < ₹18.00/km
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 150\nTrip: 10.0 km",
            fare = 150.0,
            tripDistanceKm = 10.0
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertEquals("Rejected because fare per km is too low (₹15.00/km < ₹18.00/km for 10.0 km)", result.rejectedReason)
    }

    @Test
    fun testFarePerKm_tripDistanceMissing_strictReject() {
        val rules = SmartAcceptRules(
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 20.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 200",
            fare = 200.0,
            tripDistanceKm = null
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("Trip distance unavailable for fare/km calculation") == true)
    }

    @Test
    fun testFarePerKm_tripDistanceMissing_permissiveAccept() {
        val rules = SmartAcceptRules(
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 20.0,
            fallbackOnUnparsedData = UnparsedDataFallback.ACCEPT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 200",
            fare = 200.0,
            tripDistanceKm = null
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Fare/km Skipped") })
    }

    // ── 4. SERVICE CATEGORY FILTERING ────────────────────────────────────────────

    @Test
    fun testServiceCategory_uberGoDetected_acceptedWhenAllowed() {
        val rules = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("CAB_ECONOMY", "CAB_PREMIUM"),
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\n₹ 220\nPickup: 1.5 km",
            fare = 220.0,
            detectedCategory = "CAB_ECONOMY"
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Service Category Allowed: CAB_ECONOMY") })
    }

    @Test
    fun testServiceCategory_rapidoBikeDetected_rejectedWhenMotoExcluded() {
        val rules = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("AUTO", "CAB_ECONOMY"),
            excludedCategories = setOf("MOTO"),
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.rapido.rider",
            rawText = "Rapido Bike\n₹ 65\nPickup: 0.5 km",
            fare = 65.0,
            detectedCategory = "MOTO"
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertEquals("Rejected because service category 'MOTO' is disabled", result.rejectedReason)
    }

    @Test
    fun testServiceCategory_olaAutoDetected_acceptedWhenAutoAllowed() {
        val rules = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("AUTO"),
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.olacabs.oladriver",
            rawText = "Ola Auto\n₹ 140\nPickup: 1.0 km",
            fare = 140.0,
            detectedCategory = "AUTO"
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertTrue(result.matchedRules.any { it.contains("Service Category Allowed: AUTO") })
    }

    @Test
    fun testServiceCategory_porterDeliveryDetected_allowedAndRejected() {
        val rulesAllowed = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("DELIVERY"),
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.theporter.android.driverapp",
            rawText = "Goods Delivery\n₹ 450",
            fare = 450.0,
            detectedCategory = "DELIVERY"
        )
        val res1 = OfferRuleEngine.evaluate(offer, rulesAllowed)
        assertTrue(res1.isAccepted)

        val rulesDisallowed = SmartAcceptRules(
            categoryFilterEnabled = true,
            allowedCategories = setOf("CAB_ECONOMY"),
            includeKeywordsEnabled = false
        )
        val res2 = OfferRuleEngine.evaluate(offer, rulesDisallowed)
        assertFalse(res2.isAccepted)
        assertEquals("Rejected because service category 'DELIVERY' is disabled", res2.rejectedReason)
    }

    @Test
    fun testServiceCategory_unsupportedFormat_strictFallbackRejection() {
        val rules = SmartAcceptRules(
            categoryFilterEnabled = true,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "custom.unknown.taxi",
            rawText = "Custom Ride Request\n₹ 200",
            fare = 200.0,
            detectedCategory = null
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertEquals("Rejected because booking information is incomplete: Could not identify service category", result.rejectedReason)
    }

    // ── 5. MULTIPLE FILTER COMBINATIONS ──────────────────────────────────────────

    @Test
    fun testMultipleFilters_farePasses_distanceFails() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 200.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 2.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare: ₹ 350\nPickup: 4.5 km",
            fare = 350.0, // Passes
            pickupDistanceKm = 4.5 // Fails
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("pickup distance is too high") == true)
        assertTrue(result.rejectedReason?.contains("exceeds maximum") == true)
    }

    @Test
    fun testMultipleFilters_fareAndDistancePass_farePerKmFails() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 25.0,
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare: ₹ 200\nPickup: 1.0 km\nTrip: 12.0 km",
            fare = 200.0, // Passes min fare
            pickupDistanceKm = 1.0, // Passes pickup distance
            tripDistanceKm = 12.0 // Fails fare per km (₹16.67/km < ₹25.00/km)
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertFalse(result.isAccepted)
        assertTrue(result.rejectedReason?.contains("Rejected because fare per km is too low") == true)
    }

    @Test
    fun testMultipleFilters_allPass_accepted() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            maxFareEnabled = true,
            maxFareAmount = 600.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.0,
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 20.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 20.0,
            categoryFilterEnabled = true,
            allowedCategories = setOf("AUTO", "CAB_ECONOMY"),
            includeKeywordsEnabled = false
        )
        val offer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Uber Go\nFare: ₹ 280\nPickup: 1.2 km\nTrip: 10.0 km",
            fare = 280.0,
            pickupDistanceKm = 1.2,
            tripDistanceKm = 10.0,
            detectedCategory = "CAB_ECONOMY"
        )
        val result = OfferRuleEngine.evaluate(offer, rules)
        assertTrue(result.isAccepted)
        assertEquals(6, result.matchedRules.size) // Category, MinFare, MaxFare, PickupDist, TripDist, Fare/km
    }

    // ── 6. OFFER PARSER DISTANCE SEPARATION ──────────────────────────────────────

    @Test
    fun testOfferParser_separatePickupAndTripDistance() {
        val text = "Uber Premier\nFare: ₹ 450.00\nPickup: 1.5 km (3 min)\nDrop: 14.2 km (28 min)\nTAP TO ACCEPT"
        val pickup = OfferParser.extractPickupDistanceKm(text)
        val trip = OfferParser.extractTripDistanceKm(text)
        val fare = OfferParser.extractFare(text)
        val category = OfferParser.extractCategory(text)

        assertEquals(1.5, pickup ?: 0.0, 0.01)
        assertEquals(14.2, trip ?: 0.0, 0.01)
        assertEquals(450.0, fare ?: 0.0, 0.01)
        assertEquals("CAB_PREMIUM", category)
    }

    @Test
    fun testOfferParser_singleDistanceWithAwayLabel() {
        val text = "Auto Ride Offer\nFare: ₹ 180\nPickup: 2.1 km away\nACCEPT RIDE"
        val pickup = OfferParser.extractPickupDistanceKm(text)
        val trip = OfferParser.extractTripDistanceKm(text)
        val category = OfferParser.extractCategory(text)

        assertEquals(2.1, pickup ?: 0.0, 0.01)
        assertNull(trip)
        assertEquals("AUTO", category)
    }

    @Test
    fun testOfferParser_validationDiscardsOutliers() {
        assertNull(OfferParser.validateFare(-50.0))
        assertNull(OfferParser.validateFare(0.0))
        assertNull(OfferParser.validateFare(200000.0)) // Unrealistic outlier
        assertEquals(350.0, OfferParser.validateFare(350.0) ?: 0.0, 0.01)

        assertNull(OfferParser.validateDistance(-5.0))
        assertNull(OfferParser.validateDistance(0.0))
        assertNull(OfferParser.validateDistance(9999.0)) // Unrealistic outlier
        assertEquals(8.5, OfferParser.validateDistance(8.5) ?: 0.0, 0.01)
    }

    // ── 7. JSON PERSISTENCE & BACKWARD COMPATIBILITY ─────────────────────────────

    @Test
    fun testSmartAcceptRules_jsonRoundtrip() {
        val original = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 180.0,
            maxFareEnabled = true,
            maxFareAmount = 900.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 22.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.5,
            maxTripDistanceEnabled = true,
            maxTripDistanceKm = 30.0,
            categoryFilterEnabled = true,
            allowedCategories = setOf("AUTO", "MOTO"),
            excludedCategories = setOf("XL"),
            destinationFilterEnabled = true,
            destinationKeywords = "Airport, Station",
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT
        )

        val json = original.toJson()
        val restored = SmartAcceptRules.fromJson(json)

        assertEquals(original.minFareEnabled, restored.minFareEnabled)
        assertEquals(original.minFareAmount, restored.minFareAmount, 0.01)
        assertEquals(original.maxFareEnabled, restored.maxFareEnabled)
        assertEquals(original.maxFareAmount, restored.maxFareAmount, 0.01)
        assertEquals(original.minFarePerKmEnabled, restored.minFarePerKmEnabled)
        assertEquals(original.minFarePerKmAmount, restored.minFarePerKmAmount, 0.01)
        assertEquals(original.maxPickupDistanceKm, restored.maxPickupDistanceKm, 0.01)
        assertEquals(original.maxTripDistanceKm, restored.maxTripDistanceKm, 0.01)
        assertEquals(original.categoryFilterEnabled, restored.categoryFilterEnabled)
        assertEquals(original.allowedCategories, restored.allowedCategories)
        assertEquals(original.excludedCategories, restored.excludedCategories)
        assertEquals(original.fallbackOnUnparsedData, restored.fallbackOnUnparsedData)
    }

    @Test
    fun testSmartAcceptRules_legacyJsonMissingNewFields_defaultsGracefully() {
        // Simulates old v2.2 JSON without Phase D fields
        val legacyJson = JSONObject()
        legacyJson.put("minFareEnabled", true)
        legacyJson.put("minFareAmount", 120.0)
        legacyJson.put("maxPickupDistanceEnabled", true)
        legacyJson.put("maxPickupDistanceKm", 4.0)

        val rules = SmartAcceptRules.fromJson(legacyJson)

        assertEquals(true, rules.minFareEnabled)
        assertEquals(120.0, rules.minFareAmount, 0.01)
        assertEquals(false, rules.minFarePerKmEnabled)
        assertEquals(0.0, rules.minFarePerKmAmount, 0.01)
        assertEquals(false, rules.maxTripDistanceEnabled)
        assertEquals(25.0, rules.maxTripDistanceKm, 0.01)
        assertEquals(false, rules.categoryFilterEnabled)
        assertEquals(BookingServiceCategory.ALL_IDS, rules.allowedCategories)
        assertEquals(emptySet<String>(), rules.excludedCategories)
    }
}
