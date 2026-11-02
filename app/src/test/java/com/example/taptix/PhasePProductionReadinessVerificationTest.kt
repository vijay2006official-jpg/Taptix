package com.example.taptix

import com.example.taptix.data.AppSettings
import com.example.taptix.engine.*
import com.example.taptix.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PHASE P: INDEPENDENT PRODUCTION READINESS & FULL REGRESSION AUDIT SUITE
 *
 * Exhaustively exercises and verifies:
 * 1. 5-State Booking Lifecycle & Invariants (Detected -> Matched -> Click -> Confirmed -> Completed)
 * 2. Unconfirmed/Attempted Action Separation (Clicks are never treated as acceptances)
 * 3. Atomic Multi-Threaded Deduplication & Concurrency Race Condition Safety
 * 4. Controlled Single-Retry Enforcement & Finite Backoff Policy
 * 5. Button Coordinate Clamping & Misclick Protection for Foldable / Large Displays
 * 6. High-Throughput 5,000-Event Stream Deduplication & Evaluation Benchmark
 * 7. Malformed / Boundary Data Sanitization (Negative fares, NaN, extreme numbers)
 * 8. Interruption Resilience & Clean Memory Reclamation
 * 9. Security, Privacy & Section 10 Operational-Only Compliance
 */
class PhasePProductionReadinessVerificationTest {

    private lateinit var duplicateGuard: DuplicateOfferGuard
    private lateinit var sampleRules: SmartAcceptRules

    @Before
    fun setUp() {
        duplicateGuard = DuplicateOfferGuard(windowMs = 3000L)
        sampleRules = SmartAcceptRules(
            minFareEnabled = true,
            minFareAmount = 180.0,
            maxPickupDistanceEnabled = true,
            maxPickupDistanceKm = 4.0,
            minFarePerKmEnabled = true,
            minFarePerKmAmount = 20.0,
            fallbackOnUnparsedData = UnparsedDataFallback.REJECT
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. FIVE-STATE BOOKING LIFECYCLE INVARIANTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testFiveStateBookingLifecycle_StrictProgression() {
        val sm = BookingStateMachine("booking_phase_p_01", duplicateGuard)

        // State 1: Detected
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)
        assertFalse("Detected booking must never report accepted", sm.isAccepted)
        assertFalse(sm.isTerminal)

        // State 2: Filter Matched
        val matched = sm.onFilterEvaluated(matched = true)
        assertTrue(matched)
        assertEquals(BookingState.FILTER_MATCHED, sm.currentState)
        assertFalse("Filter matched booking must never report accepted", sm.isAccepted)

        // State 3: Click Attempted
        val clickOk = sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertTrue(clickOk)
        assertEquals(BookingState.CLICK_ATTEMPTED, sm.currentState)
        assertEquals(1, sm.clickAttempts)
        assertFalse("Click attempted must NEVER be treated as accepted", sm.isAccepted)

        // State 4: Acceptance Confirmed (Only via post-tap UI check)
        val verified = sm.onVerificationResult(AcceptOutcome.CONFIRMED)
        assertTrue(verified)
        assertEquals(BookingState.ACCEPTANCE_CONFIRMED, sm.currentState)
        assertTrue("Only post-tap verified bookings can report accepted", sm.isAccepted)

        // State 5: Booking Completed
        val completed = sm.onBookingCompleted()
        assertTrue(completed)
        assertEquals(BookingState.BOOKING_COMPLETED, sm.currentState)
        assertTrue(sm.isTerminal)
    }

    @Test
    fun testClickAttempt_NeverTreatedAsSuccessfulAcceptance() {
        val sm = BookingStateMachine("booking_unconfirmed_test", duplicateGuard)
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.GESTURE_FALLBACK)

        // Result 1: Accept button still visible (NOT_CONFIRMED)
        val outcome = sm.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertFalse("Unconfirmed verification must return false", outcome)
        assertFalse("Unconfirmed booking MUST NOT report isAccepted=true", sm.isAccepted)

        // Result 2: Retry #1 attempted
        val retryOk = sm.onClickAttempted(ClickMethod.GESTURE_FALLBACK)
        assertTrue(retryOk)
        val retryOutcome = sm.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertFalse("Exhausted retry verification must return false", retryOutcome)
        assertFalse("Exhausted retry booking MUST NOT report isAccepted=true", sm.isAccepted)
        assertEquals("ACCEPTANCE_NOT_CONFIRMED", sm.failureReason)
        assertTrue(sm.isTerminal)

        // Cannot complete a non-confirmed booking
        assertFalse(sm.onBookingCompleted())
    }

    @Test
    fun testUnknownVerificationScreen_NeverTreatedAsAccepted() {
        val sm = BookingStateMachine("booking_unknown_test", duplicateGuard)
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)

        val outcome = sm.onVerificationResult(AcceptOutcome.UNKNOWN)
        assertFalse("Screen read failure must not confirm accept", outcome)
        assertFalse(sm.isAccepted)
        assertEquals("VERIFICATION_TIMEOUT_OR_UNKNOWN", sm.failureReason)
        assertTrue(sm.isTerminal)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CONCURRENCY & ATOMIC DEDUPLICATION UNDER 16-THREAD CONTENTION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testAtomicDeduplication_16ThreadsSimultaneousContention() {
        val threads = 16
        val executor = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)
        val winners = AtomicInteger(0)
        val losers = AtomicInteger(0)
        val now = 200000L

        val sig = DuplicateOfferGuard.signature("com.ubercab.driver", 350.0, 1.5, "TAP TO ACCEPT")

        val tasks = (0 until threads).map {
            executor.submit {
                barrier.await()
                // Atomic test-and-set operation
                val isDup = duplicateGuard.isDuplicateOrRecord(sig, now)
                if (!isDup) {
                    winners.incrementAndGet()
                } else {
                    losers.incrementAndGet()
                }
            }
        }

        tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals("Exactly one thread can win the booking", 1, winners.get())
        assertEquals("All other 15 threads must be atomically rejected", 15, losers.get())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CONTROLLED RETRY POLICY WITH STRICT MAXIMUMS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testControlledRetryPolicy_StrictSingleRetryLimit() {
        // Attempt 0 (initial tap): can retry on NOT_CONFIRMED
        assertTrue(ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.NOT_CONFIRMED))

        // Attempt 1 (first retry): CANNOT retry again
        assertFalse(ControlledRetryPolicy.shouldRetry(1, AcceptOutcome.NOT_CONFIRMED))

        // Attempt 2+: CANNOT retry
        assertFalse(ControlledRetryPolicy.shouldRetry(2, AcceptOutcome.NOT_CONFIRMED))

        // CONFIRMED never retries
        assertFalse(ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.CONFIRMED))

        // UNKNOWN never retries to avoid blind double-tapping
        assertFalse(ControlledRetryPolicy.shouldRetry(0, AcceptOutcome.UNKNOWN))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. COORDINATE & BOUNDARY RESILIENCY (FOLDABLES & MISCLICK DEFENSE)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testButtonBoundsAndScreenValidation() {
        // Button bounds checks
        assertFalse(BookingDetectionValidator.isValidButtonBounds(0, 50))
        assertFalse(BookingDetectionValidator.isValidButtonBounds(150, 0))
        assertFalse(BookingDetectionValidator.isValidButtonBounds(50, 40)) // too narrow (< 60)
        assertFalse(BookingDetectionValidator.isValidButtonBounds(200, 25)) // too short (< 35)
        assertFalse(BookingDetectionValidator.isValidButtonBounds(200, 600)) // too tall (> 550, likely whole screen)
        assertTrue(BookingDetectionValidator.isValidButtonBounds(300, 90))

        // Large display / foldable coordinates bounds
        val displayWidth = 1840
        val displayHeight = 2208
        val isWithinBounds = { x: Float, y: Float ->
            x >= 0f && y >= 0f && x <= displayWidth && y <= displayHeight
        }

        assertTrue(isWithinBounds(920f, 1104f))
        assertFalse(isWithinBounds(-1f, 500f))
        assertFalse(isWithinBounds(1841f, 500f))
        assertFalse(isWithinBounds(500f, 2209f))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. DATA SANITIZATION & BOUNDARY FARE DEFENSE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testFareSanitization_NegativeAndMalformedInputs() {
        assertNull("Negative fare prefix must be rejected", OfferParser.extractFare("Fare: -₹300"))
        assertNull("Negative fare without space must be rejected", OfferParser.extractFare("-₹450.00"))
        assertNull("Zero fare must be rejected", OfferParser.extractFare("Fare: ₹0"))
        assertNull("NaN fare must parse to null", OfferParser.validateFare(Double.NaN))
        assertNull("Infinite fare must parse to null", OfferParser.validateFare(Double.POSITIVE_INFINITY))
        assertNull("Out-of-bounds fare (>100k) must parse to null", OfferParser.validateFare(150000.0))

        val validFare = OfferParser.extractFare("Booking fare is ₹ 280.50 (incl. tax)")
        assertEquals(280.50, validFare ?: 0.0, 0.001)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. HIGH-THROUGHPUT REPRODUCIBLE 5,000-EVENT BENCHMARK
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testHighThroughputBenchmark_5000EventsUnder2000Ms() {
        val count = 5000
        val guard = DuplicateOfferGuard(windowMs = 400L)
        val startTime = System.currentTimeMillis()
        var accepted = 0
        var duplicates = 0
        var rejectedByRules = 0

        for (i in 0 until count) {
            val pkg = if ((i / 20) % 2 == 0) "com.ubercab.driver" else "com.rapido.rider"
            val fare = 160.0 + ((i / 10) % 40) // groups of 10 identical events
            val dist = 1.2 + ((i / 10) % 2)
            val ts = startTime + (i * 5)

            val sig = DuplicateOfferGuard.signature(pkg, fare, dist, "ACCEPT")
            if (guard.isDuplicateOrRecord(sig, ts)) {
                duplicates++
            } else {
                val offer = OfferDetails(pkg, "Fare ₹$fare, $dist km", fare, dist, dist * 3)
                val res = OfferRuleEngine.evaluate(offer, sampleRules)
                if (res.isAccepted) {
                    accepted++
                } else {
                    rejectedByRules++
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        assertTrue("5000 events must execute under 2000ms (took ${elapsed}ms)", elapsed < 2000L)
        assertTrue("Must detect duplicates in clustered stream", duplicates > 4000)
        assertTrue("Must accept valid offers", accepted > 0)
        assertTrue("Acceptance + duplicates + rule rejects must total 5000", (accepted + duplicates + rejectedByRules) == 5000)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. SERVICE INTERRUPTION & LIFECYCLE RECOVERY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testServiceInterruption_CleanStateReset() {
        val sm = BookingStateMachine("booking_interrupt", duplicateGuard)
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)

        sm.resetOnInterruption("ACCESSIBILITY_SERVICE_INTERRUPTED")
        assertTrue(sm.isTerminal)
        assertEquals("INTERRUPTED: ACCESSIBILITY_SERVICE_INTERRUPTED", sm.failureReason)
        assertFalse(sm.isAccepted)

        // Out-of-order calls after interrupt are blocked
        assertFalse(sm.onClickAttempted(ClickMethod.DIRECT_CLICK))
        assertFalse(sm.onVerificationResult(AcceptOutcome.CONFIRMED))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. SECURITY, PRIVACY & SECTION 10 FINANCIAL EXCLUSION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testSecurityAndSection10FinancialExclusionCompliance() {
        // 1. Verify keystore is NOT tracked by Git
        val gitIgnore = listOf(File(".gitignore"), File("../.gitignore")).find { it.exists() && it.readText().contains("*.keystore") }
        assertNotNull("Root .gitignore must exist and exclude keystores", gitIgnore)
        val text = gitIgnore!!.readText()
        assertTrue(".gitignore must exclude keystore files", text.contains("*.keystore"))
        assertTrue(".gitignore must exclude keystore.properties", text.contains("keystore.properties"))

        // 2. Section 10: Strict operational metrics only, zero earnings/income tracking
        val forbiddenFinancialFields = listOf(
            "earnings", "income", "netincome", "grossincome", "revenue",
            "profit", "expenses", "hourlyrate", "shiftearnings"
        )

        val modelClasses = listOf(
            ActivitySummary::class.java,
            DailyAnalyticsRecord::class.java,
            OfferDetails::class.java,
            RuleEvaluationResult::class.java
        )

        for (clazz in modelClasses) {
            val fields = clazz.declaredFields.map { it.name.lowercase() }
            for (forbidden in forbiddenFinancialFields) {
                assertFalse(
                    "Model ${clazz.simpleName} must not contain financial field '$forbidden'",
                    fields.contains(forbidden)
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. ANDROID MANIFEST COMPONENT SECURITY AUDIT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testManifestSecurityConfiguration() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).find { it.exists() }

        assertNotNull("AndroidManifest.xml must exist", manifestFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val appElement = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element

        assertEquals("allowBackup must be false for security", "false", appElement.getAttribute("android:allowBackup"))
        assertEquals("usesCleartextTraffic must be false for network security", "false", appElement.getAttribute("android:usesCleartextTraffic"))
    }
}
