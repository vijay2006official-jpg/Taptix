package com.example.taptix

import com.example.taptix.data.AppSettings
import com.example.taptix.data.ScheduleConfig
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.AreaGroup
import com.example.taptix.model.LogStatus
import com.example.taptix.model.MapZone
import com.example.taptix.model.OfferDetails
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.TargetPoint
import com.example.taptix.model.UnparsedDataFallback
import com.example.taptix.engine.OfferParser
import com.example.taptix.engine.OfferRuleEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TaptixUnitTest {

    @Test
    fun testPlatformPreset_uberProperties() {
        val uber = PlatformPreset.UBER
        assertEquals("Uber Driver", uber.title)
        assertTrue(uber.keywords.contains("ACCEPT"))
    }

    @Test
    fun testPlatformPreset_fromId() {
        val presetOla = PlatformPreset.fromId("ola")
        assertEquals(PlatformPreset.OLA, presetOla)

        val presetRapido = PlatformPreset.fromId("rapido")
        assertEquals(PlatformPreset.RAPIDO, presetRapido)

        val presetUnknown = PlatformPreset.fromId("nonexistent")
        assertEquals(PlatformPreset.UNIVERSAL, presetUnknown)
    }

    @Test
    fun testOperatingMode_values() {
        val modes = OperatingMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(OperatingMode.SINGLE_TARGET))
        assertTrue(modes.contains(OperatingMode.MULTI_TARGET))
        assertTrue(modes.contains(OperatingMode.SMART_ACCEPT))
    }

    @Test
    fun testTargetPoint_coordinates() {
        val point = TargetPoint(id = 1, x = 300, y = 600)
        assertEquals(1, point.id)
        assertEquals(300, point.x)
        assertEquals(600, point.y)

        point.x = 450
        point.y = 850
        assertEquals(450, point.x)
        assertEquals(850, point.y)
    }

    @Test
    fun testAppSettings_defaultValues() {
        val settings = AppSettings()
        assertEquals(300L, settings.clickIntervalMs)
        assertEquals(OperatingMode.SMART_ACCEPT, settings.operatingMode)
        assertEquals(PlatformPreset.UNIVERSAL, settings.platformPreset)
        assertTrue(settings.autoPauseOnAccept)
        assertFalse(settings.motionSafetyLockEnabled)
        assertTrue(settings.ttsFeedbackEnabled)
        assertFalse(settings.masterControlEnabled)
        assertTrue(settings.vibrationOnAccept)
    }

    @Test
    fun testMapZone_serialization() {
        val zone = MapZone(
            id = "test-zone-1",
            name = "Airport Terminal",
            lat = 12.9716,
            lng = 77.5946,
            radiusKm = 3.5,
            isEnabled = true,
            isPickupZone = true
        )
        val json = zone.toJson()
        assertEquals("test-zone-1", json.getString("id"))
        assertEquals("Airport Terminal", json.getString("name"))
        assertEquals(12.9716, json.getDouble("lat"), 0.0001)
        assertEquals(77.5946, json.getDouble("lng"), 0.0001)
        assertEquals(3.5, json.getDouble("radiusKm"), 0.0001)
        assertTrue(json.getBoolean("isEnabled"))
        assertTrue(json.getBoolean("isPickupZone"))

        val restored = MapZone.fromJson(json)
        assertEquals(zone.id, restored.id)
        assertEquals(zone.name, restored.name)
        assertEquals(zone.lat, restored.lat, 0.0001)
        assertEquals(zone.lng, restored.lng, 0.0001)
        assertEquals(zone.radiusKm, restored.radiusKm, 0.0001)
        assertEquals(zone.isEnabled, restored.isEnabled)
        assertEquals(zone.isPickupZone, restored.isPickupZone)
    }

    @Test
    fun testAreaGroup_serialization() {
        val group = AreaGroup(
            id = "group-1",
            name = "Morning Commute",
            zoneIds = listOf("zone-a", "zone-b"),
            isSelected = true
        )
        val json = group.toJson()
        assertEquals("Morning Commute", json.getString("name"))
        assertTrue(json.getBoolean("isSelected"))
        assertEquals(2, json.getJSONArray("zoneIds").length())

        val restored = AreaGroup.fromJson(json)
        assertEquals(group.id, restored.id)
        assertEquals(group.name, restored.name)
        assertEquals(2, restored.zoneIds.size)
        assertTrue(restored.isSelected)
    }

    @Test
    fun testScheduleConfig_dayOfWeekAndWindowEvaluation() {
        val config = ScheduleConfig(
            isEnabled = true,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY)
        )

        // Mock calendar at 11:30 AM on Monday (Should be active)
        val calMondayActive = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(config.isWithinActiveWindow(calMondayActive))

        // Mock calendar at 8:30 PM on Monday (Outside active window)
        val calMondayEvening = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 30)
        }
        assertFalse(config.isWithinActiveWindow(calMondayEvening))

        // Mock calendar on Sunday (Day not selected)
        val calSunday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(config.isWithinActiveWindow(calSunday))
    }

    @Test
    fun testScheduleConfig_overnightShiftWindow() {
        // Night shift from 21:00 (9 PM) to 04:00 (4 AM)
        val nightConfig = ScheduleConfig(
            isEnabled = true,
            startHour = 21,
            startMinute = 0,
            endHour = 4,
            endMinute = 0,
            activeDays = setOf(Calendar.FRIDAY)
        )

        val calFridayMidnight = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 45)
        }
        assertTrue(nightConfig.isWithinActiveWindow(calFridayMidnight))

        val calFridayEarlyMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 15)
        }
        assertTrue(nightConfig.isWithinActiveWindow(calFridayEarlyMorning))

        val calFridayNoon = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(nightConfig.isWithinActiveWindow(calFridayNoon))
    }

    @Test
    fun testLogStatus_enumIntegrity() {
        val statuses = LogStatus.entries
        assertTrue(statuses.contains(LogStatus.ATTEMPTED))
        assertTrue(statuses.contains(LogStatus.ACCEPTED))
        assertTrue(statuses.contains(LogStatus.FAILED))
        assertTrue(statuses.contains(LogStatus.ERROR))
        assertTrue(statuses.contains(LogStatus.REJECTED_KEYWORD))
        assertTrue(statuses.contains(LogStatus.OUTSIDE_ZONE))
        assertTrue(statuses.contains(LogStatus.SYSTEM_EVENT))
    }

    @Test
    fun testOfferParser_fareAndDistanceExtraction() {
        val sampleText = """
            Uber Premier
            Fare: ₹ 340.50
            Pickup: 2.4 km away
            Drop: Airport Terminal 1
            ACCEPT
        """.trimIndent()

        val fare = OfferParser.extractFare(sampleText)
        assertEquals(340.50, fare ?: 0.0, 0.01)

        val distance = OfferParser.extractDistanceKm(sampleText)
        assertEquals(2.4, distance ?: 0.0, 0.01)
    }

    @Test
    fun testOfferRuleEngine_minFareCondition() {
        val rules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 250.0,
            includeKeywordsEnabled = true
        )

        val highFareOffer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "ACCEPT RIDE FARE ₹300",
            fare = 300.0
        )
        val highRes = OfferRuleEngine.evaluate(
            offer = highFareOffer,
            rules = rules,
            includeKeywords = listOf("ACCEPT")
        )
        assertTrue(highRes.isAccepted)

        val lowFareOffer = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "ACCEPT RIDE FARE ₹180",
            fare = 180.0
        )
        val lowRes = OfferRuleEngine.evaluate(
            offer = lowFareOffer,
            rules = rules,
            includeKeywords = listOf("ACCEPT")
        )
        assertFalse(lowRes.isAccepted)
        assertTrue(lowRes.rejectedReason?.contains("below minimum") == true)
    }

    @Test
    fun testOfferRuleEngine_maxPickupDistanceCondition() {
        val rules = SmartAcceptRules(
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 3.0,
            includeKeywordsEnabled = true
        )

        val nearOffer = OfferDetails(
            packageName = "com.olacabs.driver",
            rawText = "ACCEPT 1.5 KM AWAY",
            pickupDistanceKm = 1.5
        )
        val nearRes = OfferRuleEngine.evaluate(
            offer = nearOffer,
            rules = rules,
            includeKeywords = listOf("ACCEPT")
        )
        assertTrue(nearRes.isAccepted)

        val farOffer = OfferDetails(
            packageName = "com.olacabs.driver",
            rawText = "ACCEPT 5.2 KM AWAY",
            pickupDistanceKm = 5.2
        )
        val farRes = OfferRuleEngine.evaluate(
            offer = farOffer,
            rules = rules,
            includeKeywords = listOf("ACCEPT")
        )
        assertFalse(farRes.isAccepted)
        assertTrue(farRes.rejectedReason?.contains("exceeds maximum") == true)
    }

    @Test
    fun testOfferRuleEngine_haversineGeofenceZone() {
        // Zone center at Chennai Airport (12.9941, 80.1709), radius 3 km
        val airportZone = MapZone(
            id = "zone-airport",
            name = "Chennai Airport",
            lat = 12.9941,
            lng = 80.1709,
            radiusKm = 3.0,
            isEnabled = true
        )

        // Driver located 1 km away from airport
        val driverNear = Pair(12.9900, 80.1750)
        val offerNear = OfferDetails(packageName = "com.ubercab.driver", rawText = "ACCEPT RIDE")
        val resNear = OfferRuleEngine.evaluate(
            offer = offerNear,
            rules = SmartAcceptRules(includeKeywordsEnabled = false),
            activeZones = listOf(airportZone),
            driverLocation = driverNear
        )
        assertTrue(resNear.isAccepted)

        // Driver located 15 km away in Central Chennai
        val driverFar = Pair(13.0827, 80.2707)
        val offerFar = OfferDetails(packageName = "com.ubercab.driver", rawText = "ACCEPT RIDE")
        val resFar = OfferRuleEngine.evaluate(
            offer = offerFar,
            rules = SmartAcceptRules(includeKeywordsEnabled = false),
            activeZones = listOf(airportZone),
            driverLocation = driverFar
        )
        assertFalse(resFar.isAccepted)
        assertTrue(resFar.rejectedReason?.contains("Outside all active work zones") == true)
    }

    @Test
    fun testOfferRuleEngine_unparsedDataSafetyFallback() {
        val rulesStrict = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 200.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT
        )

        val unknownFareOffer = OfferDetails(
            packageName = "com.rapido.captain",
            rawText = "ACCEPT ORDER",
            fare = null // Unparsed fare
        )

        val res = OfferRuleEngine.evaluate(
            offer = unknownFareOffer,
            rules = rulesStrict,
            includeKeywords = listOf("ACCEPT")
        )
        assertFalse(res.isAccepted)
        assertTrue(res.rejectedReason?.contains("Could not parse fare") == true)
    }

    @Test
    fun testActivityLogItem_dataModel() {
        val log = ActivityLogItem(
            id = 1001L,
            timestamp = 1600000000L,
            appName = "Uber Driver",
            actionText = "ACCEPT",
            status = LogStatus.ACCEPTED,
            details = "Matched Min Fare",
            fare = 350.0,
            distanceKm = 2.1,
            destination = "Airport",
            rulesEvaluated = "Min Fare, Map Zone"
        )
        assertEquals("Uber Driver", log.appName)
        assertEquals(LogStatus.ACCEPTED, log.status)
        assertEquals(350.0, log.fare ?: 0.0, 0.01)
        assertEquals(2.1, log.distanceKm ?: 0.0, 0.01)
    }
}
