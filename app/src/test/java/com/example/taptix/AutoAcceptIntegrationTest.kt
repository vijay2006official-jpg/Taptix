package com.example.taptix

import com.example.taptix.data.AppSettings
import com.example.taptix.engine.AcceptClickExecutor
import com.example.taptix.engine.AcceptOutcome
import com.example.taptix.engine.AcceptVerifier
import com.example.taptix.engine.ClickMethod
import com.example.taptix.engine.DuplicateOfferGuard
import com.example.taptix.engine.OfferParser
import com.example.taptix.engine.OfferRuleEngine
import com.example.taptix.engine.PartnerPackages
import com.example.taptix.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM tests for the Auto Acceptance core. Every test here calls the same production
 * classes that AutoClickService uses (no re-implemented copies of the logic).
 * Real device behaviour (accessibility events, real taps) is NOT covered here.
 */
class AutoAcceptIntegrationTest {

    private fun loadConfigXml(): org.w3c.dom.Element {
        val candidates = listOf(
            File("src/main/res/xml/accessibility_service_config.xml"),
            File("app/src/main/res/xml/accessibility_service_config.xml")
        )
        val file = candidates.find { it.exists() }
        assertNotNull("accessibility_service_config.xml must exist", file)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    // ── Accessibility config ──

    @Test
    fun configXml_keepsRequiredFlagsAndEvents() {
        val root = loadConfigXml()
        val flags = root.getAttribute("android:accessibilityFlags")
        listOf("flagDefault", "flagRetrieveInteractiveWindows", "flagReportViewIds", "flagIncludeNotImportantViews")
            .forEach { assertTrue("Missing $it", flags.contains(it)) }
        val events = root.getAttribute("android:accessibilityEventTypes")
        assertTrue(events.contains("typeWindowStateChanged"))
        assertTrue(events.contains("typeWindowContentChanged"))
        assertEquals("true", root.getAttribute("android:canPerformGestures"))
        assertEquals("true", root.getAttribute("android:canRetrieveWindowContent"))
    }

    @Test
    fun configXml_packageNamesMatchCodeList() {
        val xmlPkgs = loadConfigXml().getAttribute("android:packageNames").split(",").map { it.trim() }.toSet()
        val expected = (PartnerPackages.SUPPORTED + "com.example.taptix").toSet()
        assertEquals("XML packageNames and PartnerPackages.SUPPORTED are out of sync", expected, xmlPkgs)
    }

    @Test
    fun partnerPackages_containVerifiedPlayStoreIds() {
        val verified = listOf(
            "com.ubercab.driver",              // Uber Driver
            "com.olacabs.oladriver",           // Ola Driver
            "com.rapido.rider",                // Rapido Captain
            "sinet.startup.inDriver",          // inDrive (case-sensitive)
            "in.juspay.nammayatripartner",     // Namma Yatri Partner
            "com.theporter.android.driverapp"  // Porter Driver Partner
        )
        verified.forEach { assertTrue("Missing $it", PartnerPackages.SUPPORTED.contains(it)) }
    }

    @Test
    fun partnerPackages_mergedIncludesCustomTargetsAndOwnPackage() {
        val merged = PartnerPackages.merged(" com.custom.driver , ,com.ubercab.driver", "com.example.taptix")
        assertTrue(merged.contains("com.custom.driver"))
        assertTrue(merged.contains("com.example.taptix"))
        assertEquals("No duplicates", merged.size, merged.toSet().size)
    }

    // ── Booking text parsing per platform (real OfferParser / OfferRuleEngine) ──

    private fun assertParsedAndAccepted(pkg: String, text: String, fare: Double, km: Double, rules: SmartAcceptRules, kw: String) {
        val f = OfferParser.extractFare(text)
        val d = OfferParser.extractDistanceKm(text)
        assertEquals(fare, f ?: 0.0, 0.01)
        assertEquals(km, d ?: 0.0, 0.01)
        val res = OfferRuleEngine.evaluate(OfferDetails(packageName = pkg, rawText = text, fare = f, pickupDistanceKm = d), rules, includeKeywords = listOf(kw))
        assertTrue("$pkg offer should be accepted: ${res.rejectedReason}", res.isAccepted)
    }

    @Test
    fun parse_uber() = assertParsedAndAccepted("com.ubercab.driver",
        "Uber Premier\n₹ 485.50\nPickup: 1.8 km (4 mins)\nTAP TO ACCEPT", 485.50, 1.8,
        SmartAcceptRules(minFareEnabled = true, minFareAmount = 300.0), "TAP TO ACCEPT")

    @Test
    fun parse_ola() = assertParsedAndAccepted("com.olacabs.oladriver",
        "New Booking!\nRide Fare: Rs. 320\nDistance to Pickup: 2.5 KM\nACCEPT RIDE", 320.0, 2.5,
        SmartAcceptRules(maxPickupDistanceEnabled = true, maxPickupDistanceKm = 3.0), "ACCEPT RIDE")

    @Test
    fun parse_rapido() = assertParsedAndAccepted("com.rapido.rider",
        "Rapido Bike\nAmount: ₹ 145.00\nPickup: 0.8 km\nCAPTAIN ACCEPT", 145.0, 0.8, SmartAcceptRules(), "CAPTAIN ACCEPT")

    @Test
    fun parse_inDrive() = assertParsedAndAccepted("sinet.startup.inDriver",
        "City to Airport\nPassenger offer: ₹ 550\nDistance: 3.2 km\nACCEPT", 550.0, 3.2,
        SmartAcceptRules(minFareEnabled = true, minFareAmount = 400.0), "ACCEPT")

    @Test
    fun parse_nammaYatri() = assertParsedAndAccepted("in.juspay.nammayatripartner",
        "Auto Ride Offer\nFare: ₹ 180\nPickup: 1.1 km away\nACCEPT RIDE", 180.0, 1.1, SmartAcceptRules(), "ACCEPT RIDE")

    @Test
    fun parse_porter() = assertParsedAndAccepted("com.theporter.android.driverapp",
        "Goods Delivery Request\nEstimated Fare: ₹ 650.00\nPickup Location: 4.0 km\nACCEPT TRIP", 650.0, 4.0,
        SmartAcceptRules(minFareEnabled = true, minFareAmount = 500.0), "ACCEPT TRIP")

    // ── Click execution (real AcceptClickExecutor) ──

    @Test
    fun click_directClickSucceeds_noGestureDispatched() {
        var gestures = 0; var swipes = 0
        val m = AcceptClickExecutor.execute(false, directClick = { true }, gestureTap = { gestures++ }, swipe = { swipes++ })
        assertEquals(ClickMethod.DIRECT_CLICK, m)
        assertEquals("Must not double-dispatch a gesture after a successful click", 0, gestures)
        assertEquals(0, swipes)
    }

    @Test
    fun click_directClickFails_exactlyOneGestureFallback() {
        var gestures = 0
        val m = AcceptClickExecutor.execute(false, directClick = { false }, gestureTap = { gestures++ }, swipe = { fail("no swipe") })
        assertEquals(ClickMethod.GESTURE_FALLBACK, m)
        assertEquals(1, gestures)
    }

    @Test
    fun click_swipeControl_onlySwipes() {
        var directCalls = 0; var gestures = 0; var swipes = 0
        val m = AcceptClickExecutor.execute(true, directClick = { directCalls++; true }, gestureTap = { gestures++ }, swipe = { swipes++ })
        assertEquals(ClickMethod.SWIPE, m)
        assertEquals(1, swipes); assertEquals(0, directCalls); assertEquals(0, gestures)
    }

    // ── Duplicate prevention (real DuplicateOfferGuard) ──

    @Test
    fun duplicate_sameOfferWithinWindowBlocked_newOfferAllowed() {
        val guard = DuplicateOfferGuard()
        val sig1 = DuplicateOfferGuard.signature("com.ubercab.driver", 350.0, 2.1, " tap to accept ")
        val t0 = 1_000_000L
        assertFalse(guard.isDuplicate(sig1, t0))
        guard.record(sig1, t0)
        assertTrue(guard.isDuplicate(sig1, t0 + 500))
        assertTrue(guard.isDuplicate(sig1, t0 + 3999))
        assertFalse("Same popup may be retried after window", guard.isDuplicate(sig1, t0 + 4000))

        val sig2 = DuplicateOfferGuard.signature("com.ubercab.driver", 420.0, 1.5, "TAP TO ACCEPT")
        assertFalse("A different booking must not be blocked", guard.isDuplicate(sig2, t0 + 100))
    }

    @Test
    fun duplicate_signatureNormalisesButtonText() {
        assertEquals(
            DuplicateOfferGuard.signature("p", 1.0, 2.0, "accept"),
            DuplicateOfferGuard.signature("p", 1.0, 2.0, " ACCEPT ")
        )
    }

    // ── Acceptance verification (real AcceptVerifier) ──

    @Test
    fun verify_outcomeMapping() {
        assertEquals(AcceptOutcome.CONFIRMED, AcceptVerifier.evaluate(false))
        assertEquals(AcceptOutcome.NOT_CONFIRMED, AcceptVerifier.evaluate(true))
        assertEquals(AcceptOutcome.UNKNOWN, AcceptVerifier.evaluate(null))
        assertTrue("Verification must be short so it doesn't miss the next booking", AcceptVerifier.VERIFY_DELAY_MS in 300L..1500L)
    }

    // ── Shift schedule removal ──

    @Test
    fun schedule_noFieldsInSettings_andRulesEvaluateWithoutSchedule() {
        val fields = AppSettings::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { it.contains("schedule") || it.contains("shiftschedule") || it.contains("activeday") || it.contains("shiftstart") || it.contains("shiftend") })
        val res = OfferRuleEngine.evaluate(OfferDetails(packageName = "com.ubercab.driver", rawText = "ACCEPT", fare = 200.0), SmartAcceptRules())
        assertTrue(res.isAccepted)
    }

    @Test
    fun schedule_noSourceReferencesRemain() {
        val srcRoot = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { f ->
                val t = f.readText()
                listOf("ScheduleConfig", "ScheduleDialog", "isWithinActiveWindow", "KEY_SCHEDULE", "Shift Schedule")
                    .any { t.contains(it, ignoreCase = true) }
            }
            .map { it.name }.toList()
        assertTrue("Schedule references found in: $offenders", offenders.isEmpty())
    }

    // ── Phase C Advanced Automation Engine Tests ──

    @Test
    fun phaseC_controlledRetryPolicy_singleAttemptBoundary() {
        // Attempt 0 + NOT_CONFIRMED -> Retry allowed
        assertTrue(com.example.taptix.engine.ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.NOT_CONFIRMED))

        // Attempt 1 (already retried once) -> Retry NOT allowed to prevent loops
        assertFalse(com.example.taptix.engine.ControlledRetryPolicy.shouldRetry(1, AcceptOutcome.NOT_CONFIRMED))

        // Outcome CONFIRMED -> No retry needed
        assertFalse(com.example.taptix.engine.ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.CONFIRMED))

        // Outcome UNKNOWN -> No retry
        assertFalse(com.example.taptix.engine.ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.UNKNOWN))
    }

    @Test
    fun phaseC_bookingDetectionValidator_buttonBounds() {
        // Valid buttons: normal ride partner accept buttons
        assertTrue(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(240, 80))
        assertTrue(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(60, 35))
        assertTrue(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(800, 150))

        // Invalid buttons: too small (micro icons or noise)
        assertFalse(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(50, 30))
        assertFalse(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(10, 10))

        // Invalid buttons: full-screen container (false parent node)
        assertFalse(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(1080, 2400))
        assertFalse(com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(400, 600))
    }

    @Test
    fun phaseC_bookingDetectionValidator_unreliableOfferRejection() {
        // Blank text is always unreliable
        assertFalse(
            com.example.taptix.engine.BookingDetectionValidator.isReliableOffer(
                rawText = "",
                fare = 150.0,
                pickupKm = 2.0,
                minFareEnabled = false,
                minFareAmount = 0.0,
                maxPickupDistanceEnabled = false,
                maxPickupDistanceKm = 0.0,
                fallbackReject = true
            )
        )

        // Min fare enabled with unparsed fare under reject policy -> Unreliable
        assertFalse(
            com.example.taptix.engine.BookingDetectionValidator.isReliableOffer(
                rawText = "ACCEPT RIDE",
                fare = null,
                pickupKm = 2.0,
                minFareEnabled = true,
                minFareAmount = 200.0,
                maxPickupDistanceEnabled = false,
                maxPickupDistanceKm = 0.0,
                fallbackReject = true
            )
        )

        // Valid complete offer -> Reliable
        assertTrue(
            com.example.taptix.engine.BookingDetectionValidator.isReliableOffer(
                rawText = "ACCEPT RIDE FARE 250",
                fare = 250.0,
                pickupKm = 1.5,
                minFareEnabled = true,
                minFareAmount = 200.0,
                maxPickupDistanceEnabled = true,
                maxPickupDistanceKm = 5.0,
                fallbackReject = true
            )
        )
    }

    @Test
    fun phaseC_automationEngineState_lifecycleTransitions() {
        val states = com.example.taptix.engine.AutomationEngineState.entries
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.DISCONNECTED))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.CONNECTED_IDLE))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.SCANNING))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.ACTION_DISPATCHED))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.VERIFYING))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.ACCEPTED_CONFIRMED))
        assertTrue(states.contains(com.example.taptix.engine.AutomationEngineState.PAUSED_BY_USER))
    }
}
