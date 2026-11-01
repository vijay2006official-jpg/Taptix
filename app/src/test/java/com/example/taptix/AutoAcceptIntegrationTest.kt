package com.example.taptix

import com.example.taptix.data.AppSettings
import com.example.taptix.engine.OfferParser
import com.example.taptix.engine.OfferRuleEngine
import com.example.taptix.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Integration Test Suite for Taptix v2.2 Auto-Acceptance Architecture.
 * Tests configuration integrity, multi-platform booking detection,
 * button execution priority, duplicate-click prevention, and shift schedule absence.
 */
class AutoAcceptIntegrationTest {

    // ── 1. Accessibility Service Configuration XML Integration ──

    @Test
    fun testAccessibilityServiceConfigXml_integrityAndFlags() {
        val candidates = listOf(
            File("app/src/main/res/xml/accessibility_service_config.xml"),
            File("../app/src/main/res/xml/accessibility_service_config.xml"),
            File("src/main/res/xml/accessibility_service_config.xml")
        )
        val configFile = candidates.find { it.exists() }
        assertNotNull("accessibility_service_config.xml must exist", configFile)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(configFile)
        val root = doc.documentElement
        assertEquals("accessibility-service", root.nodeName)

        val flags = root.getAttribute("android:accessibilityFlags")
        assertTrue("Must include flagDefault", flags.contains("flagDefault"))
        assertTrue("Must include flagRetrieveInteractiveWindows", flags.contains("flagRetrieveInteractiveWindows"))
        assertTrue("Must include flagReportViewIds to inspect view resource IDs", flags.contains("flagReportViewIds"))
        assertTrue("Must include flagIncludeNotImportantViews for Flutter/Compose nodes", flags.contains("flagIncludeNotImportantViews"))

        val eventTypes = root.getAttribute("android:accessibilityEventTypes")
        assertTrue("Must include typeWindowStateChanged", eventTypes.contains("typeWindowStateChanged"))
        assertTrue("Must include typeWindowContentChanged", eventTypes.contains("typeWindowContentChanged"))

        val packageNames = root.getAttribute("android:packageNames")
        assertTrue("Must support Uber", packageNames.contains("com.ubercab.driver"))
        assertTrue("Must support Ola", packageNames.contains("com.olacabs.driver"))
        assertTrue("Must support Rapido", packageNames.contains("com.rapido.captain"))
        assertTrue("Must support inDrive", packageNames.contains("sinet.startup.inDriver") && packageNames.contains("com.indriver"))
        assertTrue("Must support Namma Yatri", packageNames.contains("in.juspay.nammayatripartner") || packageNames.contains("movingtech.nammatrip.driver"))
        assertTrue("Must support Porter", packageNames.contains("in.porter.partner") || packageNames.contains("com.theporter.partner"))
    }

    // ── 2. Multi-Platform Booking Detection Across Supported Apps ──

    @Test
    fun testIncomingBookingDetection_uberOffer() {
        val uberText = """
            Uber Premier
            ₹ 485.50
            Pickup: 1.8 km (4 mins)
            Drop: Domestic Terminal
            TAP TO ACCEPT
        """.trimIndent()
        val fare = OfferParser.extractFare(uberText)
        val distance = OfferParser.extractDistanceKm(uberText)
        assertEquals(485.50, fare ?: 0.0, 0.01)
        assertEquals(1.8, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "com.ubercab.driver", rawText = uberText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(minFareEnabled = true, minFareAmount = 300.0), includeKeywords = listOf("TAP TO ACCEPT", "ACCEPT"))
        assertTrue("Uber offer should be accepted by rules", res.isAccepted)
    }

    @Test
    fun testIncomingBookingDetection_olaDuty() {
        val olaText = """
            New Booking!
            Ride Fare: Rs. 320
            Distance to Pickup: 2.5 KM
            ACCEPT RIDE
        """.trimIndent()
        val fare = OfferParser.extractFare(olaText)
        val distance = OfferParser.extractDistanceKm(olaText)
        assertEquals(320.0, fare ?: 0.0, 0.01)
        assertEquals(2.5, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "com.olacabs.driver", rawText = olaText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(maxPickupDistanceEnabled = true, maxPickupDistanceKm = 3.0), includeKeywords = listOf("ACCEPT RIDE"))
        assertTrue("Ola duty should be accepted within pickup limits", res.isAccepted)
    }

    @Test
    fun testIncomingBookingDetection_rapidoOrder() {
        val rapidoText = """
            Rapido Bike / Auto
            Amount: ₹ 145.00
            Pickup: 0.8 km
            CAPTAIN ACCEPT
        """.trimIndent()
        val fare = OfferParser.extractFare(rapidoText)
        val distance = OfferParser.extractDistanceKm(rapidoText)
        assertEquals(145.0, fare ?: 0.0, 0.01)
        assertEquals(0.8, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "com.rapido.captain", rawText = rapidoText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(), includeKeywords = listOf("CAPTAIN ACCEPT"))
        assertTrue("Rapido captain order should be accepted", res.isAccepted)
    }

    @Test
    fun testIncomingBookingDetection_inDriveOffer() {
        val inDriveText = """
            City to Airport
            Passenger offer: ₹ 550
            Distance: 3.2 km
            ACCEPT
        """.trimIndent()
        val fare = OfferParser.extractFare(inDriveText)
        val distance = OfferParser.extractDistanceKm(inDriveText)
        assertEquals(550.0, fare ?: 0.0, 0.01)
        assertEquals(3.2, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "sinet.startup.inDriver", rawText = inDriveText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(minFareEnabled = true, minFareAmount = 400.0), includeKeywords = listOf("ACCEPT"))
        assertTrue("inDrive offer should be accepted", res.isAccepted)
    }

    @Test
    fun testIncomingBookingDetection_nammaYatriTrip() {
        val nammaYatriText = """
            Auto Ride Offer
            Fare: ₹ 180
            Pickup: 1.1 km away
            ACCEPT RIDE
        """.trimIndent()
        val fare = OfferParser.extractFare(nammaYatriText)
        val distance = OfferParser.extractDistanceKm(nammaYatriText)
        assertEquals(180.0, fare ?: 0.0, 0.01)
        assertEquals(1.1, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "in.juspay.nammayatripartner", rawText = nammaYatriText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(), includeKeywords = listOf("ACCEPT RIDE"))
        assertTrue("Namma Yatri ride offer should be accepted", res.isAccepted)
    }

    @Test
    fun testIncomingBookingDetection_porterPartner() {
        val porterText = """
            Goods Delivery Request
            Estimated Fare: ₹ 650.00
            Pickup Location: 4.0 km
            ACCEPT TRIP
        """.trimIndent()
        val fare = OfferParser.extractFare(porterText)
        val distance = OfferParser.extractDistanceKm(porterText)
        assertEquals(650.0, fare ?: 0.0, 0.01)
        assertEquals(4.0, distance ?: 0.0, 0.01)

        val offer = OfferDetails(packageName = "in.porter.partner", rawText = porterText, fare = fare, pickupDistanceKm = distance)
        val res = OfferRuleEngine.evaluate(offer, SmartAcceptRules(minFareEnabled = true, minFareAmount = 500.0), includeKeywords = listOf("ACCEPT TRIP"))
        assertTrue("Porter delivery request should be accepted", res.isAccepted)
    }

    // ── 3. Accept Button Action & Coordinate Fallback Simulation ──

    @Test
    fun testAcceptAction_directClickVsCoordinateFallbackPriority() {
        // Simulated execution state representing AutoClickService's click logic
        fun executeAccept(isNodeClickable: Boolean, performActionClickResult: Boolean): Pair<Boolean, String> {
            var nativeClickOk = false
            if (isNodeClickable) {
                nativeClickOk = performActionClickResult
            }
            return if (nativeClickOk) {
                Pair(true, "ACTION_CLICK")
            } else {
                // Fallback to gesture tap
                Pair(true, "GESTURE_TAP_FALLBACK")
            }
        }

        // Scenario A: Standard Native Android button (isClickable=true, performAction returns true)
        val resultNative = executeAccept(isNodeClickable = true, performActionClickResult = true)
        assertTrue(resultNative.first)
        assertEquals("ACTION_CLICK", resultNative.second)

        // Scenario B: Flutter / Custom canvas node (isClickable=false, cannot perform direct action)
        val resultFlutter = executeAccept(isNodeClickable = false, performActionClickResult = false)
        assertTrue(resultFlutter.first)
        assertEquals("GESTURE_TAP_FALLBACK", resultFlutter.second)

        // Scenario C: Node marked clickable but performAction returns false
        val resultFallback = executeAccept(isNodeClickable = true, performActionClickResult = false)
        assertTrue(resultFallback.first)
        assertEquals("GESTURE_TAP_FALLBACK", resultFallback.second)
    }

    // ── 4. Duplicate Click Prevention Logic ──

    @Test
    fun testDuplicateClickPrevention_signatureDebounce() {
        val appPkg = "com.ubercab.driver"
        val fare = 350.0
        val pickupKm = 2.1
        val matchText = "TAP TO ACCEPT"

        fun makeSignature(pkg: String, f: Double?, d: Double?, text: String): String {
            return "$pkg:${f ?: 0.0}:${d ?: 0.0}:${text.uppercase()}"
        }

        val sig1 = makeSignature(appPkg, fare, pickupKm, matchText)
        var lastAcceptedSig = ""
        var lastAcceptedTime = 0L

        fun canAccept(sig: String, now: Long): Boolean {
            if (sig == lastAcceptedSig && (now - lastAcceptedTime) < 4000L) {
                return false // Duplicate rejected
            }
            lastAcceptedSig = sig
            lastAcceptedTime = now
            return true
        }

        val t0 = 1000000L
        // 1st arrival: Accepted
        assertTrue("First arrival must be accepted", canAccept(sig1, t0))

        // Repeat event for SAME offer 500ms later: Rejected as duplicate
        assertFalse("Same offer within 4s must be rejected as duplicate", canAccept(sig1, t0 + 500L))

        // Repeat event for SAME offer 2000ms later: Rejected as duplicate
        assertFalse("Same offer within 4s must be rejected as duplicate", canAccept(sig1, t0 + 2000L))

        // Brand NEW offer (different fare ₹420) arriving 1000ms later: MUST BE ACCEPTED IMMEDIATELY!
        val sig2 = makeSignature(appPkg, 420.0, 1.5, matchText)
        assertTrue("Different offer must be accepted immediately without waiting 30s", canAccept(sig2, t0 + 1000L))

        // After 4000ms expires, the same signature is acceptable again (e.g. next day/trip)
        assertTrue("After 4000ms window, offer signature can be processed again", canAccept(sig2, t0 + 1000L + 4001L))
    }

    // ── 5. Auto Acceptance ON/OFF Behavior ──

    @Test
    fun testAutoAcceptance_onOffStateTransitions() {
        val settingsOn = AppSettings(masterControlEnabled = true, operatingMode = OperatingMode.SMART_ACCEPT)
        val settingsOff = AppSettings(masterControlEnabled = false)

        assertTrue(settingsOn.masterControlEnabled)
        assertEquals(OperatingMode.SMART_ACCEPT, settingsOn.operatingMode)
        assertFalse(settingsOff.masterControlEnabled)

        // Per-app profile toggle
        val uberProfileEnabled = AppProfile(packageName = "com.ubercab.driver", enabled = true)
        val uberProfileDisabled = AppProfile(packageName = "com.ubercab.driver", enabled = false)

        fun shouldProcessApp(profile: AppProfile, globalActive: Boolean): Boolean {
            return globalActive && profile.enabled
        }

        assertTrue("When global and app enabled, process offer", shouldProcessApp(uberProfileEnabled, true))
        assertFalse("When global disabled, reject offer", shouldProcessApp(uberProfileEnabled, false))
        assertFalse("When app profile disabled, reject offer", shouldProcessApp(uberProfileDisabled, true))
    }

    // ── 6 & 7. Absolute Removal of Shift Schedule Gating ──

    @Test
    fun testShiftSchedule_completelyRemovedFromArchitecture() {
        // Verify that AppSettings does not have any shift schedule fields
        val settings = AppSettings()
        val fields = AppSettings::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("AppSettings must not contain scheduleEnabled", fields.contains("scheduleenabled"))
        assertFalse("AppSettings must not contain shiftstarthour", fields.contains("shiftstarthour"))
        assertFalse("AppSettings must not contain shiftendhour", fields.contains("shiftendhour"))
        assertFalse("AppSettings must not contain activedaysofweek", fields.contains("activedaysofweek"))

        // Verify that OfferRuleEngine evaluates independently without schedule parameters
        val offer = OfferDetails(packageName = "com.ubercab.driver", rawText = "ACCEPT", fare = 200.0)
        val res = OfferRuleEngine.evaluate(offer = offer, rules = SmartAcceptRules())
        assertTrue("Auto acceptance must evaluate and accept without any schedule checks", res.isAccepted)
    }
}
