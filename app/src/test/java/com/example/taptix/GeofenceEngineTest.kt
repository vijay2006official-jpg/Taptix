package com.example.taptix

import com.example.taptix.engine.GeofenceEngine
import com.example.taptix.engine.GeofenceTransition
import com.example.taptix.model.MapZone
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Phase E: Geofencing Engine, Accuracy Validation,
 * Transition Detection, and Privacy Sanitization.
 */
class GeofenceEngineTest {

    private val airportZone = MapZone(
        id = "zone-airport",
        name = "Airport Duty Zone",
        lat = 12.9941,
        lng = 80.1709,
        radiusKm = 3.0,
        isEnabled = true
    )

    private val techParkZone = MapZone(
        id = "zone-techpark",
        name = "OMR Tech Corridor",
        lat = 12.9150,
        lng = 80.2280,
        radiusKm = 5.0,
        isEnabled = true
    )

    @Test
    fun testHaversineDistance_exactKnownCoordinates() {
        // Distance between Airport (12.9941, 80.1709) and Central (13.0827, 80.2707)
        val dist = GeofenceEngine.haversineDistanceKm(12.9941, 80.1709, 13.0827, 80.2707)
        assertTrue("Distance should be approximately 14.5 - 16.0 km: $dist", dist in 14.5..16.0)
    }

    @Test
    fun testHaversineDistance_samePoint_isZero() {
        val dist = GeofenceEngine.haversineDistanceKm(12.9941, 80.1709, 12.9941, 80.1709)
        assertEquals(0.0, dist, 0.0001)
    }

    @Test
    fun testValidateAccuracy_acceptableThreshold() {
        assertTrue(GeofenceEngine.validateAccuracy(15f))
        assertTrue(GeofenceEngine.validateAccuracy(50f))
        assertTrue(GeofenceEngine.validateAccuracy(150f))
        assertFalse(GeofenceEngine.validateAccuracy(151f))
        assertFalse(GeofenceEngine.validateAccuracy(350f))
    }

    @Test
    fun testValidateAccuracy_negativeOrZero_isInvalid() {
        assertFalse(GeofenceEngine.validateAccuracy(0f))
        assertFalse(GeofenceEngine.validateAccuracy(-10f))
    }

    @Test
    fun testIsLocationInsideZone_withinRadius_returnsTrue() {
        // Driver 1.2 km away from airport center (radius is 3.0 km)
        val driverLat = 12.9990
        val driverLng = 80.1750
        val inside = GeofenceEngine.isLocationInsideZone(driverLat, driverLng, airportZone)
        assertTrue(inside)
    }

    @Test
    fun testIsLocationInsideZone_withinTolerance_returnsTrue() {
        // Airport center is (12.9941, 80.1709), radius 3.0 km
        // Tolerance is 3.0 * 1.20 = 3.6 km.
        // A point ~3.2 km away is within tolerance
        val dist32 = GeofenceEngine.haversineDistanceKm(12.9941, 80.1709, 13.0220, 80.1709)
        assertTrue(dist32 in 3.0..3.5)
        assertTrue(GeofenceEngine.isLocationInsideZone(13.0220, 80.1709, airportZone))
    }

    @Test
    fun testIsLocationInsideZone_outsideTolerance_returnsFalse() {
        // Point in Central Chennai (~15 km away)
        val inside = GeofenceEngine.isLocationInsideZone(13.0827, 80.2707, airportZone)
        assertFalse(inside)
    }

    @Test
    fun testIsLocationInsideZone_disabledZone_returnsFalse() {
        val disabledZone = airportZone.copy(isEnabled = false)
        val inside = GeofenceEngine.isLocationInsideZone(12.9941, 80.1709, disabledZone)
        assertFalse("Disabled zone must always reject location checks", inside)
    }

    @Test
    fun testEvaluateDriverPosition_enterTransition() {
        // Previous status was OUTSIDE
        val prev = GeofenceEngine.evaluateDriverPosition(
            lat = 13.0827,
            lng = 80.2707,
            accuracyMeters = 20f,
            activeZones = listOf(airportZone)
        )
        assertFalse(prev.isInsideAnyZone)
        assertEquals(GeofenceTransition.OUTSIDE, prev.transition)

        // Driver enters airport zone
        val current = GeofenceEngine.evaluateDriverPosition(
            lat = 12.9941,
            lng = 80.1709,
            accuracyMeters = 15f,
            activeZones = listOf(airportZone),
            previousStatus = prev
        )
        assertTrue(current.isInsideAnyZone)
        assertEquals(GeofenceTransition.ENTER, current.transition)
        assertEquals("zone-airport", current.matchedZone?.id)
    }

    @Test
    fun testEvaluateDriverPosition_exitTransition() {
        // Previous status was INSIDE
        val prev = GeofenceEngine.evaluateDriverPosition(
            lat = 12.9941,
            lng = 80.1709,
            accuracyMeters = 15f,
            activeZones = listOf(airportZone)
        )
        assertTrue(prev.isInsideAnyZone)

        // Driver drives away to 15 km away
        val current = GeofenceEngine.evaluateDriverPosition(
            lat = 13.0827,
            lng = 80.2707,
            accuracyMeters = 18f,
            activeZones = listOf(airportZone),
            previousStatus = prev
        )
        assertFalse(current.isInsideAnyZone)
        assertEquals(GeofenceTransition.EXIT, current.transition)
    }

    @Test
    fun testEvaluateDriverPosition_emptyZones_allowsEverything() {
        val status = GeofenceEngine.evaluateDriverPosition(
            lat = 13.0827,
            lng = 80.2707,
            accuracyMeters = 20f,
            activeZones = emptyList()
        )
        assertTrue("When no zones configured, driver is allowed everywhere", status.isInsideAnyZone)
        assertEquals(GeofenceTransition.INSIDE, status.transition)
        assertNull(status.matchedZone)
    }

    @Test
    fun testSanitizeLocationForLogs_masksPreciseDigits() {
        val sanitized = GeofenceEngine.sanitizeLocationForLogs(12.994123, 80.170987)
        assertEquals("12.99**, 80.17**", sanitized)
        assertFalse("Must not contain precise 4th+ decimal digits", sanitized.contains("12.994"))
        assertFalse("Must not contain precise 4th+ decimal digits", sanitized.contains("80.1709"))
    }

    @Test
    fun testMultiZone_matchesCorrectZone() {
        val zones = listOf(airportZone, techParkZone)

        // Point near Tech Park center
        val statusTechPark = GeofenceEngine.evaluateDriverPosition(
            lat = 12.9160,
            lng = 80.2290,
            accuracyMeters = 10f,
            activeZones = zones
        )
        assertTrue(statusTechPark.isInsideAnyZone)
        assertEquals("zone-techpark", statusTechPark.matchedZone?.id)

        // Point near Airport center
        val statusAirport = GeofenceEngine.evaluateDriverPosition(
            lat = 12.9945,
            lng = 80.1712,
            accuracyMeters = 10f,
            activeZones = zones
        )
        assertTrue(statusAirport.isInsideAnyZone)
        assertEquals("zone-airport", statusAirport.matchedZone?.id)
    }
}
