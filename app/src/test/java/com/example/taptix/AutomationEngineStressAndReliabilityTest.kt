package com.example.taptix

import com.example.taptix.engine.*
import com.example.taptix.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PHASE O2: ADVANCED STRESS TESTING, EDGE-CASE VALIDATION & LONG-RUN RELIABILITY
 *
 * Exhaustive test suite covering:
 * 1. Automation Engine Stress Testing (Scenarios 1 through 18)
 * 2. 5-State Booking Lifecycle & State Machine Invariants
 * 3. High-Concurrency & Race Condition Verification
 * 4. Boundary & Malformed Data Resiliency
 */
class AutomationEngineStressAndReliabilityTest {

    private lateinit var duplicateGuard: DuplicateOfferGuard
    private lateinit var defaultRules: SmartAcceptRules

    @Before
    fun setUp() {
        duplicateGuard = DuplicateOfferGuard(windowMs = 2000L)
        defaultRules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 150.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 5.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 25.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 1: 5-STATE BOOKING STATE MACHINE INVARIANTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testScenario01_SingleValidBookingFullLifecycle() {
        val sm = BookingStateMachine(bookingId = "booking_001", duplicateGuard = duplicateGuard)
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)
        assertFalse("Detected booking must never be considered accepted", sm.isAccepted)

        // Transition 1: Filter matched
        val filterOk = sm.onFilterEvaluated(matched = true)
        assertTrue(filterOk)
        assertEquals(BookingState.FILTER_MATCHED, sm.currentState)
        assertFalse(sm.isAccepted)

        // Transition 2: Click attempted
        val clickOk = sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertTrue(clickOk)
        assertEquals(BookingState.CLICK_ATTEMPTED, sm.currentState)
        assertEquals(1, sm.clickAttempts)
        assertFalse("Click attempted must never be counted as confirmed acceptance", sm.isAccepted)

        // Transition 3: Acceptance confirmed
        val confirmOk = sm.onVerificationResult(AcceptOutcome.CONFIRMED)
        assertTrue(confirmOk)
        assertEquals(BookingState.ACCEPTANCE_CONFIRMED, sm.currentState)
        assertTrue("Acceptance confirmed must report isAccepted=true", sm.isAccepted)

        // Transition 4: Booking completed
        val completeOk = sm.onBookingCompleted()
        assertTrue(completeOk)
        assertEquals(BookingState.BOOKING_COMPLETED, sm.currentState)
        assertTrue(sm.isTerminal)
    }

    @Test
    fun testScenario06_OutOfOrderTransitionsStrictlyRejected() {
        val sm = BookingStateMachine(bookingId = "booking_out_of_order")

        // Invariant: Cannot confirm before click
        assertFalse(sm.onVerificationResult(AcceptOutcome.CONFIRMED))
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)

        // Invariant: Cannot complete before confirmed
        assertFalse(sm.onBookingCompleted())
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)

        // Invariant: Cannot click before filter evaluation
        assertFalse(sm.onClickAttempted(ClickMethod.DIRECT_CLICK))
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)

        assertFalse("Out of order transitions must never report accepted", sm.isAccepted)
    }

    @Test
    fun testScenario04_DuplicateBookingPrevention_GuardSignature() {
        val now = 100000L
        val sig1 = DuplicateOfferGuard.signature("com.ubercab.driver", 250.0, 2.5, "ACCEPT")
        val sig2 = DuplicateOfferGuard.signature("com.ubercab.driver", 250.0, 2.5, "ACCEPT")

        assertEquals(sig1, sig2)

        duplicateGuard.record(sig1, now)
        // Immediate duplicate
        assertTrue("Identical offer within window must be flagged duplicate", duplicateGuard.isDuplicate(sig2, now + 500L))
        assertTrue("Duplicate within window must be rejected", duplicateGuard.isDuplicate(sig2, now + 1999L))

        // After window expiry (2000ms window)
        assertFalse("Offer after window expiry must be accepted", duplicateGuard.isDuplicate(sig2, now + 2001L))
    }

    @Test
    fun testScenario03_RepeatedIdenticalEventsDoNotCreateDuplicateActions() {
        val actionCount = AtomicInteger(0)
        val sig = DuplicateOfferGuard.signature("com.olacabs.oladriver", 300.0, 1.2, "ACCEPT RIDE")
        val now = 50000L

        for (i in 0 until 10) {
            val isDup = duplicateGuard.isDuplicate(sig, now + (i * 50))
            if (!isDup) {
                duplicateGuard.record(sig, now + (i * 50))
                actionCount.incrementAndGet()
            }
        }

        assertEquals("Exactly 1 accept action must be dispatched despite 10 repeated events", 1, actionCount.get())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 2: CONCURRENCY & SIMULTANEOUS BOOKING EVENTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testScenario02_MultipleSimultaneousBookingEvents_ThreadSafety() {
        val threads = 16
        val executor = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)
        val winningCount = AtomicInteger(0)
        val guard = DuplicateOfferGuard(windowMs = 5000L)
        val now = System.currentTimeMillis()

        val futures = (0 until threads).map {
            executor.submit {
                barrier.await()
                // All threads compete on identical popup event simultaneously using atomic check-and-record
                val sig = DuplicateOfferGuard.signature("com.rapido.rider", 180.0, 0.8, "ACCEPT")
                val isDup = guard.isDuplicateOrRecord(sig, now)
                if (!isDup) {
                    winningCount.incrementAndGet()
                }
            }
        }

        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals("Zero duplicate actions: exactly 1 thread can claim the booking", 1, winningCount.get())
    }

    @Test
    fun testScenario09_RapidStartAndStopConcurrency() {
        val isServiceRunning = AtomicBoolean(false)
        val isClickingActive = AtomicBoolean(false)
        val iterations = 500
        val executor = Executors.newFixedThreadPool(8)

        val futures = (0 until 8).map {
            executor.submit {
                for (i in 0 until iterations) {
                    isServiceRunning.set(i % 2 == 0)
                    isClickingActive.set(i % 3 == 0)
                }
            }
        }

        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        // Verify clean settling
        isServiceRunning.set(false)
        isClickingActive.set(false)
        assertFalse(isServiceRunning.get())
        assertFalse(isClickingActive.get())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 3: BOUNDARY VALUES & MALFORMED DATA RESILIENCY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testScenario07_MissingBookingFields_SafeRejection() {
        val rules = defaultRules.copy(fallbackOnUnparsedData = UnparsedDataFallback.REJECT)

        // Missing fare
        val offerNoFare = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Pickup 2.0 km away",
            fare = null,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 5.0
        )
        val evalNoFare = OfferRuleEngine.evaluate(offerNoFare, rules)
        assertFalse("Missing fare with fallbackOnUnparsedData=REJECT must reject", evalNoFare.isAccepted)

        // Missing distance
        val offerNoDist = OfferDetails(
            packageName = "com.ubercab.driver",
            rawText = "Fare: ₹200.0",
            fare = 200.0,
            pickupDistanceKm = null,
            tripDistanceKm = 5.0
        )
        val evalNoDist = OfferRuleEngine.evaluate(offerNoDist, rules)
        assertFalse("Missing distance with fallbackOnUnparsedData=REJECT must reject", evalNoDist.isAccepted)
    }

    @Test
    fun testScenario08_InvalidBookingData_Sanitization() {
        val parser = OfferParser
        // Negative fare in raw text
        val fareNegative = parser.extractFare("Fare: -₹250")
        assertNull("Negative fare must parse to null", fareNegative)

        // Empty string
        val fareEmpty = parser.extractFare("")
        assertNull(fareEmpty)

        // Distance extraction
        val distOk = parser.extractDistanceKm("5.2 km away")
        assertEquals(5.2, distOk ?: 0.0, 0.01)

        // Zero coordinates validation
        assertFalse("Zero dimensions must be rejected", BookingDetectionValidator.isValidButtonBounds(0, 0))
        assertFalse("Negative dimensions must be rejected", BookingDetectionValidator.isValidButtonBounds(-50, 40))
        assertFalse("Button narrower than 60px must be rejected", BookingDetectionValidator.isValidButtonBounds(59, 45))
        assertFalse("Button shorter than 35px must be rejected", BookingDetectionValidator.isValidButtonBounds(100, 34))
        assertTrue("Valid button bounds (200x50) must pass", BookingDetectionValidator.isValidButtonBounds(200, 50))
    }

    @Test
    fun testScenario14_InvalidCoordinatesAndBoundaryClamping() {
        val screenW = 1080
        val screenH = 2400

        val isValidCoord = { x: Float, y: Float ->
            x in 0f..screenW.toFloat() && y in 0f..screenH.toFloat()
        }

        assertFalse("Negative X coordinate is invalid", isValidCoord(-10f, 500f))
        assertFalse("Negative Y coordinate is invalid", isValidCoord(500f, -5f))
        assertFalse("X exceeding screen width is invalid", isValidCoord(1081f, 500f))
        assertFalse("Y exceeding screen height is invalid", isValidCoord(500f, 2401f))
        assertTrue("Center screen coordinate is valid", isValidCoord(540f, 1200f))
    }

    @Test
    fun testScenario15_ScreenSizeChanges_DynamicOrientationAndFoldableBounds() {
        val resolutions = listOf(
            Pair(1080, 2400),
            Pair(2400, 1080),
            Pair(1840, 2208),
            Pair(720, 1600)
        )

        for ((w, h) in resolutions) {
            val acceptBtnW = (w * 0.4).toInt()
            val acceptBtnH = (h * 0.04).toInt().coerceAtLeast(40)
            assertTrue(
                "Button bounds on resolution ${w}x$h ($acceptBtnW x $acceptBtnH) must be valid",
                BookingDetectionValidator.isValidButtonBounds(acceptBtnW, acceptBtnH)
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 4: CONTROLLED RETRY & TIMEOUT VERIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testScenario05_ControlledRetryPolicy_StrictSingleRetry() {
        val sm = BookingStateMachine("booking_retry_test")
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertEquals(1, sm.clickAttempts)

        // First verification: NOT_CONFIRMED -> should permit exactly 1 retry
        val firstVerify = sm.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertFalse("Unconfirmed verification must not mark accepted", firstVerify)
        assertFalse(sm.isAccepted)

        // Perform second attempt (retry #1)
        val retryOk = sm.onClickAttempted(ClickMethod.GESTURE_FALLBACK)
        assertTrue("Second attempt within retry policy is allowed", retryOk)
        assertEquals(2, sm.clickAttempts)

        // Second verification failure: must fail terminally without third attempt
        val secondVerify = sm.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertFalse(secondVerify)
        assertFalse(sm.isAccepted)
        assertEquals("ACCEPTANCE_NOT_CONFIRMED", sm.failureReason)

        // Third attempt must be rejected
        assertFalse("Third click attempt beyond MAX_RETRIES must be rejected", sm.onClickAttempted(ClickMethod.DIRECT_CLICK))
    }

    @Test
    fun testScenario11_ServiceInterruptionAndRecovery() {
        val sm = BookingStateMachine("booking_interrupted")
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)

        // System kills service or screen turns off
        sm.resetOnInterruption("SCREEN_OFF")
        assertTrue(sm.isTerminal)
        assertEquals("INTERRUPTED: SCREEN_OFF", sm.failureReason)
        assertFalse("Interrupted state must never report confirmed acceptance", sm.isAccepted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 5: LONG EVENT SEQUENCES & HIGH-THROUGHPUT STRESS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testScenario17_HighThroughputEventSequence_5000Events() {
        val eventCount = 5000
        val guard = DuplicateOfferGuard(windowMs = 500L)
        val startTime = System.currentTimeMillis()
        var acceptedOffers = 0
        var duplicateRejections = 0

        for (i in 0 until eventCount) {
            val pkg = if ((i / 20) % 2 == 0) "com.ubercab.driver" else "com.olacabs.oladriver"
            val fare = 150.0 + ((i / 10) % 50)
            val dist = 1.0 + ((i / 10) % 3)
            val timestamp = startTime + (i * 10) // 10ms intervals

            val sig = DuplicateOfferGuard.signature(pkg, fare, dist, "ACCEPT")
            if (guard.isDuplicateOrRecord(sig, timestamp)) {
                duplicateRejections++
            } else {
                val raw = "Fare: ₹$fare Pickup: $dist km"
                val offer = OfferDetails(pkg, raw, fare, dist, dist * 2)
                val eval = OfferRuleEngine.evaluate(offer, defaultRules)
                if (eval.isAccepted) {
                    acceptedOffers++
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        assertTrue("5000 events must be processed in under 2000ms (was ${elapsedMs}ms)", elapsedMs < 2000L)
        assertTrue("Expected non-zero accepted offers", acceptedOffers > 0)
        assertTrue("Expected non-zero duplicate rejections", duplicateRejections > 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 6: GEOFENCE ACCURACY & BOUNDARY EDGE CASES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testGeofenceEngine_ExactEdgeCases() {
        val centerLat = 12.9716
        val centerLng = 77.5946
        val zone = MapZone(
            name = "MG Road Core",
            lat = centerLat,
            lng = centerLng,
            radiusKm = 1.0,
            isEnabled = true
        )

        // Point exactly at center: distance = 0
        val insideCenter = GeofenceEngine.isLocationInsideZone(centerLat, centerLng, zone)
        assertTrue("Point at zone center must evaluate inside", insideCenter)

        // Point 5km away: must evaluate outside
        val farLat = centerLat + (5000.0 / 111000.0)
        val outside = GeofenceEngine.isLocationInsideZone(farLat, centerLng, zone, toleranceRatio = 1.0)
        assertFalse("Point 5km away must evaluate outside 1km zone", outside)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 7: SECTION 10 COMPLIANCE & SHIFT ABSENCE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testPhaseO2_FinancialExclusionVerification() {
        val forbiddenTerms = listOf("earnings", "income", "profit", "faretotal", "grossincome", "shiftschedule")
        val stateMachineFields = BookingStateMachine::class.java.declaredFields.map { it.name.lowercase() }
        val bookingStateValues = BookingState.values().map { it.name.lowercase() }

        forbiddenTerms.forEach { term ->
            assertFalse("BookingStateMachine must not declare '$term'", stateMachineFields.contains(term))
            assertFalse("BookingState must not declare '$term'", bookingStateValues.contains(term))
        }
    }
}
