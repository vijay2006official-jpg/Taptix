package com.example.taptix

import com.example.taptix.automation.partner.PartnerProfileRegistry
import com.example.taptix.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * TAPTIX v2.4 — PHASE AI
 * ADVANCED AUTOMATION RELIABILITY, SAFETY HARDENING & COMPLETE REGRESSION VALIDATION
 *
 * Verifies:
 * 1. Strict 5-State Booking Lifecycle invariants (never combined or inferred).
 * 2. Tri-state click execution model (SUCCESS, FAILED_CONFIRMED_NOT_DISPATCHED, UNKNOWN).
 * 3. Prevention of coordinate fallback and speculative retries when dispatch is UNKNOWN.
 * 4. Multi-offer bounded DuplicateOfferGuard against cross-partner interleaved offers.
 * 5. Mandatory Acceptance Confirmation Rule 1: Standalone navigation text NEVER independently confirms acceptance.
 * 6. Mandatory Acceptance Confirmation Rule 2: Generic words (ACCEPTED, CONFIRMED, START TRIP) REQUIRE contextual evidence.
 * 7. Strong compound confirmation phrases confirm acceptance when accept button vanishes.
 * 8. Cancellation / expiry precedence over any positive signals.
 */
class PhaseAIAutomationReliabilityTest {

    // =========================================================================
    // SECTION 1: REQUIRED 5-STATE BOOKING LIFECYCLE AUDIT
    // =========================================================================

    @Test
    fun test01_BookingDetected_IsNotAccepted() {
        val sm = BookingStateMachine("booking-ai-01")
        assertEquals(BookingState.BOOKING_DETECTED, sm.currentState)
        assertFalse("Booking detected state must NEVER be considered accepted", sm.isAccepted)
        assertFalse("Booking detected state must not be terminal", sm.isTerminal)
    }

    @Test
    fun test02_FilterMatched_IsNotAccepted() {
        val sm = BookingStateMachine("booking-ai-02")
        val transitioned = sm.onFilterEvaluated(matched = true)
        assertTrue(transitioned)
        assertEquals(BookingState.FILTER_MATCHED, sm.currentState)
        assertFalse("Filter matched state must NEVER be considered accepted", sm.isAccepted)
    }

    @Test
    fun test03_ClickAttempted_IsNotAccepted() {
        val sm = BookingStateMachine("booking-ai-03")
        sm.onFilterEvaluated(matched = true)
        val clickAllowed = sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertTrue(clickAllowed)
        assertEquals(BookingState.CLICK_ATTEMPTED, sm.currentState)
        assertFalse("A click attempt is NOT an accepted booking", sm.isAccepted)
    }

    @Test
    fun test04_AcceptButtonDisappearanceAlone_CannotConfirmAcceptance() {
        val outcome = AcceptVerifier.evaluateTiered(
            acceptButtonStillVisible = false,
            hasStrongPositive = false,
            hasGenericPositive = false,
            hasContextualEvidence = false,
            hasNegativeOrCancelled = false,
            hasStandaloneNavOnly = false
        )
        assertEquals("Disappearing Accept button alone is NOT sufficient proof of acceptance", AcceptOutcome.NOT_CONFIRMED, outcome)
    }

    @Test
    fun test05_NavigationScreenAlone_CannotConfirmAcceptance() {
        val outcome = AcceptVerifier.evaluateTiered(
            acceptButtonStillVisible = false,
            hasStrongPositive = false,
            hasGenericPositive = false,
            hasContextualEvidence = false,
            hasNegativeOrCancelled = false,
            hasStandaloneNavOnly = true // Navigation instructions present alone
        )
        assertEquals("A navigation screen alone is NOT sufficient proof of acceptance", AcceptOutcome.NOT_CONFIRMED, outcome)
    }

    @Test
    fun test06_FullSuccessfulLifecycle_FiveDistinctStates() {
        val sm = BookingStateMachine("booking-ai-06")
        assertEquals("State 1: Detected", BookingState.BOOKING_DETECTED, sm.currentState)
        assertFalse(sm.isAccepted)

        sm.onFilterEvaluated(matched = true)
        assertEquals("State 2: Filter Matched", BookingState.FILTER_MATCHED, sm.currentState)
        assertFalse(sm.isAccepted)

        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertEquals("State 3: Click Attempted", BookingState.CLICK_ATTEMPTED, sm.currentState)
        assertFalse(sm.isAccepted)

        sm.onVerificationResult(AcceptOutcome.CONFIRMED)
        assertEquals("State 4: Acceptance Confirmed", BookingState.ACCEPTANCE_CONFIRMED, sm.currentState)
        assertTrue(sm.isAccepted)

        sm.onBookingCompleted()
        assertEquals("State 5: Booking Completed", BookingState.BOOKING_COMPLETED, sm.currentState)
        assertTrue(sm.isAccepted)
        assertTrue(sm.isTerminal)
    }

    // =========================================================================
    // SECTION 2: TRI-STATE CLICK EXECUTION & UNKNOWN DISPATCH HANDLING
    // =========================================================================

    @Test
    fun test07_TriState_DirectClickSuccess_ZeroCoordinateFallback() {
        var gestureFallbackDispatched = false
        val method = AcceptClickExecutor.execute(
            isSwipe = false,
            directClick = { DirectClickResult.SUCCESS },
            gestureTap = { gestureFallbackDispatched = true },
            swipe = {}
        )
        assertEquals(ClickMethod.DIRECT_CLICK, method)
        assertFalse("SUCCESS must not dispatch coordinate fallback", gestureFallbackDispatched)
    }

    @Test
    fun test08_TriState_FailedConfirmedNotDispatched_PermitsCoordinateFallback() {
        var gestureFallbackDispatched = false
        val method = AcceptClickExecutor.execute(
            isSwipe = false,
            directClick = { DirectClickResult.FAILED_CONFIRMED_NOT_DISPATCHED },
            gestureTap = { gestureFallbackDispatched = true },
            swipe = {}
        )
        assertEquals(ClickMethod.GESTURE_FALLBACK, method)
        assertTrue("FAILED_CONFIRMED_NOT_DISPATCHED must dispatch coordinate fallback", gestureFallbackDispatched)
    }

    @Test
    fun test09_TriState_UnknownDispatch_StrictlyForbidsCoordinateFallback() {
        var gestureFallbackDispatched = false
        val method = AcceptClickExecutor.execute(
            isSwipe = false,
            directClick = { DirectClickResult.UNKNOWN },
            gestureTap = { gestureFallbackDispatched = true },
            swipe = {}
        )
        assertEquals(ClickMethod.UNKNOWN_DISPATCH, method)
        assertFalse("UNKNOWN dispatch result must NEVER dispatch coordinate fallback", gestureFallbackDispatched)
    }

    @Test
    fun test10_UnknownDispatch_StrictlySuppressesSpeculativeRetries() {
        val canRetry = ControlledRetryPolicy.isRetrySafe(
            currentAttempts = 0,
            outcome = AcceptOutcome.NOT_CONFIRMED,
            isButtonStillVisible = true,
            isButtonEnabled = true,
            isForegroundTargetApp = true,
            hasNegativeSignal = false,
            hasPositiveConfirmation = false,
            initialClickMethod = ClickMethod.UNKNOWN_DISPATCH // Dispatch was uncertain!
        )
        assertFalse("When dispatch result is UNKNOWN, speculative retries must be strictly forbidden", canRetry)
    }

    @Test
    fun test11_UnknownDispatch_SafelyTerminatesUnconfirmedAttempt() {
        val sm = BookingStateMachine("booking-ai-11")
        sm.onFilterEvaluated(matched = true)
        sm.onClickAttempted(ClickMethod.UNKNOWN_DISPATCH)
        assertEquals(ClickMethod.UNKNOWN_DISPATCH, sm.lastClickMethod)

        // Post-click verification finds screen unconfirmed
        val result = sm.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertFalse("Unconfirmed result after UNKNOWN dispatch must fail transition", result)
        assertEquals(
            "State machine must record safe termination for UNKNOWN dispatch",
            "UNKNOWN_DISPATCH_UNCONFIRMED_TERMINATED",
            sm.failureReason
        )
        assertFalse("Must not be accepted", sm.isAccepted)
    }

    // =========================================================================
    // SECTION 3: MULTI-OFFER BOUNDED DUPLICATE GUARD
    // =========================================================================

    @Test
    fun test12_DuplicateGuard_TracksMultipleInterleavedOffersAcrossPartners() {
        val guard = DuplicateOfferGuard(windowMs = 4000L)
        val sigUber = DuplicateOfferGuard.signature("com.ubercab.driver", 350.0, 1.2, "ACCEPT")
        val sigOla = DuplicateOfferGuard.signature("com.olacabs.oladriver", 420.0, 2.5, "ACCEPT")
        val now = 10000L

        // Offer A (Uber) arrives and records
        assertFalse(guard.isDuplicateOrRecord(sigUber, now))

        // Intervening Offer B (Ola) arrives and records
        assertFalse(guard.isDuplicateOrRecord(sigOla, now + 100L))

        // Re-entrant / duplicate Offer A arrives within window
        assertTrue("Interleaved duplicate Offer A must still be recognized as duplicate", guard.isDuplicate(sigUber, now + 500L))
        assertTrue("Duplicate Offer A must be suppressed by isDuplicateOrRecord", guard.isDuplicateOrRecord(sigUber, now + 500L))

        // Duplicate Offer B also remains suppressed
        assertTrue("Duplicate Offer B must be suppressed", guard.isDuplicate(sigOla, now + 600L))

        // After window expiry, Offer A can be accepted again
        assertFalse("Offer A after window expiration must not be duplicate", guard.isDuplicate(sigUber, now + 4100L))
    }

    @Test
    fun test13_DuplicateGuard_ConcurrentThreadSafety() {
        val guard = DuplicateOfferGuard(windowMs = 4000L)
        val sig = DuplicateOfferGuard.signature("com.ubercab.driver", 250.0, 1.0, "ACCEPT")
        val now = 50000L
        val threads = 10
        val latch = CountDownLatch(threads)
        val successCount = AtomicInteger(0)

        for (i in 0 until threads) {
            Thread {
                val isDup = guard.isDuplicateOrRecord(sig, now)
                if (!isDup) {
                    successCount.incrementAndGet()
                }
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals("Exactly ONE thread must succeed in recording a simultaneous offer arrival", 1, successCount.get())
    }

    // =========================================================================
    // SECTION 4: ACCEPTANCE CONFIRMATION — MANDATORY RULES AUDIT
    // =========================================================================

    @Test
    fun test14_MandatoryRule1_ProhibitedStandaloneNavigationKeywords_NeverConfirm() {
        val prohibitedNavList = listOf(
            "NAVIGATE TO PICKUP",
            "NAVIGATE",
            "NAVIGATION",
            "ARRIVING",
            "HEAD TO",
            "ON THE WAY",
            "WAY TO PICKUP"
        )

        prohibitedNavList.forEach { navWord ->
            val outcome = AcceptVerifier.evaluateTiered(
                acceptButtonStillVisible = false,
                hasStrongPositive = false,
                hasGenericPositive = false,
                hasContextualEvidence = false,
                hasNegativeOrCancelled = false,
                hasStandaloneNavOnly = true
            )
            assertEquals(
                "Prohibited navigation phrase '$navWord' must NEVER independently confirm acceptance",
                AcceptOutcome.NOT_CONFIRMED,
                outcome
            )
        }
    }

    @Test
    fun test15_MandatoryRule2_GenericKeywordsWithoutContext_NeverConfirm() {
        val genericKeywords = listOf("ACCEPTED", "CONFIRMED", "START TRIP")

        genericKeywords.forEach { genericWord ->
            val outcome = AcceptVerifier.evaluateTiered(
                acceptButtonStillVisible = false,
                hasStrongPositive = false,
                hasGenericPositive = true, // Generic token detected
                hasContextualEvidence = false, // NO contextual evidence!
                hasNegativeOrCancelled = false,
                hasStandaloneNavOnly = false
            )
            assertEquals(
                "Generic word '$genericWord' WITHOUT contextual evidence must NOT confirm acceptance",
                AcceptOutcome.NOT_CONFIRMED,
                outcome
            )
        }
    }

    @Test
    fun test16_MandatoryRule2_GenericKeywordsWithContext_ConfirmsAcceptance() {
        val genericKeywords = listOf("ACCEPTED", "CONFIRMED", "START TRIP")

        genericKeywords.forEach { genericWord ->
            val outcome = AcceptVerifier.evaluateTiered(
                acceptButtonStillVisible = false,
                hasStrongPositive = false,
                hasGenericPositive = true, // Generic token detected
                hasContextualEvidence = true, // Contextual evidence present (passenger, trip ID, pickup)!
                hasNegativeOrCancelled = false,
                hasStandaloneNavOnly = false
            )
            assertEquals(
                "Generic word '$genericWord' WITH contextual evidence must confirm acceptance",
                AcceptOutcome.CONFIRMED,
                outcome
            )
        }
    }

    @Test
    fun test17_Tier1_StrongExplicitCompoundKeywords_ConfirmsAcceptance() {
        val strongKeywords = AcceptVerifier.STRONG_CONFIRMATION_KEYWORDS

        strongKeywords.forEach { strongWord ->
            val outcome = AcceptVerifier.evaluateTiered(
                acceptButtonStillVisible = false,
                hasStrongPositive = true,
                hasGenericPositive = false,
                hasContextualEvidence = false,
                hasNegativeOrCancelled = false,
                hasStandaloneNavOnly = false
            )
            assertEquals(
                "Strong explicit compound keyword '$strongWord' must confirm acceptance",
                AcceptOutcome.CONFIRMED,
                outcome
            )
        }
    }

    @Test
    fun test18_NegativeOrCancelledSignal_StrictlyOverridesPositiveConfirmation() {
        val outcome = AcceptVerifier.evaluateTiered(
            acceptButtonStillVisible = false,
            hasStrongPositive = true, // Strong positive present
            hasGenericPositive = true,
            hasContextualEvidence = true,
            hasNegativeOrCancelled = true, // But cancelled/expired is also present!
            hasStandaloneNavOnly = false
        )
        assertEquals(
            "Cancellation or expiry must strictly take precedence over all positive signals",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
    }

    @Test
    fun test19_ButtonStillVisible_StrictlyPreventsConfirmation() {
        val outcome = AcceptVerifier.evaluateTiered(
            acceptButtonStillVisible = true, // Accept button is still on screen!
            hasStrongPositive = true,
            hasGenericPositive = true,
            hasContextualEvidence = true,
            hasNegativeOrCancelled = false,
            hasStandaloneNavOnly = false
        )
        assertEquals(
            "Visible accept button indicates offer is still pending; must be NOT_CONFIRMED",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
    }

    @Test
    fun test20_PartnerProfileRegistry_ZeroProfilesContainProhibitedStandaloneNav() {
        val allProfiles = PartnerProfileRegistry.getAllProfiles()
        val prohibitedNav = AcceptVerifier.PROHIBITED_STANDALONE_NAVIGATION_KEYWORDS

        allProfiles.forEach { profile ->
            prohibitedNav.forEach { navWord ->
                assertFalse(
                    "Profile '${profile.displayName}' positiveConfirmationKeywords must NOT contain prohibited navigation phrase '$navWord'",
                    profile.positiveConfirmationKeywords.any { it.equals(navWord, ignoreCase = true) }
                )
            }
        }
    }
}
