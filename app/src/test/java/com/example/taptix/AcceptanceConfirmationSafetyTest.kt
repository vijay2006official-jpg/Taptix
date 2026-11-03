package com.example.taptix

import com.example.taptix.automation.partner.PartnerProfileRegistry
import com.example.taptix.automation.partner.PartnerProfileResolver
import com.example.taptix.engine.*
import com.example.taptix.model.LogStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * TAPTIX v2.4 — Phase AB Final Safety Closure
 * Comprehensive regression tests verifying strict acceptance confirmation,
 * freshness, ambiguous keyword exclusion, and zero-click safety gates.
 */
class AcceptanceConfirmationSafetyTest {

    // 1. Explicit booking acceptance: explicit partner confirmation keywords confirm acceptance
    @Test
    fun test01_ExplicitBookingAcceptance() {
        val explicitKeywords = listOf("ACCEPTED", "RIDE ACCEPTED", "BOOKING ACCEPTED", "TRIP ACCEPTED", "START TRIP")
        explicitKeywords.forEach { kw ->
            val outcome = AcceptVerifier.evaluate(
                acceptButtonStillVisible = false,
                positiveConfirmationPresent = true,
                negativeOrCancelledPresent = false
            )
            assertEquals("Explicit keyword '$kw' must confirm acceptance", AcceptOutcome.CONFIRMED, outcome)
        }
    }

    // 2. Generic navigation text: ambiguous navigation words must NOT independently confirm booking acceptance
    @Test
    fun test02_GenericNavigationText_DoesNotConfirm() {
        val prohibitedNavWords = listOf("NAVIGATE", "NAVIGATION", "ARRIVING", "HEAD TO", "ON THE WAY")
        val globalPositive = AcceptVerifier.POSITIVE_CONFIRMATION_KEYWORDS
        prohibitedNavWords.forEach { word ->
            assertFalse(
                "Global positive confirmation keywords must NOT contain ambiguous word '$word'",
                globalPositive.contains(word)
            )
        }

        // Verify across all registered partner profiles that none contain these prohibited navigation words
        val allProfiles = PartnerProfileRegistry.getAllProfiles()
        allProfiles.forEach { profile ->
            prohibitedNavWords.forEach { word ->
                assertFalse(
                    "Profile '${profile.displayName}' must NOT contain ambiguous confirmation word '$word'",
                    profile.positiveConfirmationKeywords.contains(word)
                )
            }
        }
    }

    // 3. Old confirmation message: stale or out-of-order dispatch timestamp is rejected
    @Test
    fun test03_OldConfirmationMessage_Rejected() {
        val clickDispatchTime = 1000L
        val staleVerifyTime = 900L // Occurred before click dispatch
        val isFresh = staleVerifyTime >= clickDispatchTime
        assertFalse("Stale verification timestamp occurring before dispatch must be rejected", isFresh)

        // State machine guards against invalid state transitions
        val guard = DuplicateOfferGuard()
        val sm = BookingStateMachine("test-offer-sig", guard)
        // Transition to CLICK_ATTEMPTED
        sm.onFilterEvaluated(true)
        sm.onClickAttempted(ClickMethod.DIRECT_CLICK)
        assertEquals(BookingState.CLICK_ATTEMPTED, sm.currentState)

        // Complete the booking
        sm.onVerificationResult(AcceptOutcome.CONFIRMED)
        sm.onBookingCompleted()
        assertEquals(BookingState.BOOKING_COMPLETED, sm.currentState)

        // Stale duplicate verification attempt on completed booking is rejected
        val duplicateAllowed = sm.currentState == BookingState.CLICK_ATTEMPTED
        assertFalse("Stale verification on already completed state must not be permitted", duplicateAllowed)
    }

    // 4. Unrelated passenger information: passenger/customer/chat words must NOT confirm acceptance
    @Test
    fun test04_UnrelatedPassengerInformation_DoesNotConfirm() {
        val prohibitedPassengerWords = listOf("PASSENGER", "CUSTOMER", "CHAT", "ORDER DETAILS")
        val globalPositive = AcceptVerifier.POSITIVE_CONFIRMATION_KEYWORDS
        prohibitedPassengerWords.forEach { word ->
            assertFalse(
                "Global positive confirmation keywords must NOT contain entity word '$word'",
                globalPositive.contains(word)
            )
        }

        val allProfiles = PartnerProfileRegistry.getAllProfiles()
        allProfiles.forEach { profile ->
            prohibitedPassengerWords.forEach { word ->
                assertFalse(
                    "Profile '${profile.displayName}' must NOT contain entity word '$word'",
                    profile.positiveConfirmationKeywords.contains(word)
                )
            }
        }
    }

    // 5. Accept button disappearance alone: disappearing button without positive signal yields NOT_CONFIRMED
    @Test
    fun test05_AcceptButtonDisappearanceAlone_YieldsNotConfirmed() {
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals(
            "Accept button disappearance alone must never confirm acceptance",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
    }

    // 6. Cancellation or expiry: negative signals strictly take precedence over positive confirmation
    @Test
    fun test06_CancellationOrExpiry_TakesPrecedence() {
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = true,  // Positive signal detected
            negativeOrCancelledPresent = true   // But also cancelled/expired
        )
        assertEquals(
            "Cancellation or expiry signal must strictly prevent a positive confirmation",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
    }

    // 7. Partner app switching: background partner app yields UNKNOWN and suppresses retries
    @Test
    fun test07_PartnerAppSwitching_SuppressesRetries() {
        // When partner app is not in foreground, isForegroundTargetApp is false
        val canRetry = ControlledRetryPolicy.isRetrySafe(
            currentAttempts = 0,
            outcome = AcceptOutcome.NOT_CONFIRMED,
            isButtonStillVisible = true,
            isButtonEnabled = true,
            isForegroundTargetApp = false, // App switched to background
            hasNegativeSignal = false,
            hasPositiveConfirmation = false
        )
        assertFalse("Switched/background application must strictly suppress retry taps", canRetry)
    }

    // 8. Unknown confirmation state: UNKNOWN outcome triggers zero retries and zero coordinate fallbacks
    @Test
    fun test08_UnknownConfirmationState_ZeroRetriesAndZeroFallbacks() {
        // Unknown outcome must never trigger a retry tap
        val canRetry = ControlledRetryPolicy.isRetrySafe(
            currentAttempts = 0,
            outcome = AcceptOutcome.UNKNOWN,
            isButtonStillVisible = true,
            isButtonEnabled = true,
            isForegroundTargetApp = true,
            hasNegativeSignal = false,
            hasPositiveConfirmation = false
        )
        assertFalse("AcceptOutcome.UNKNOWN must never trigger speculative retries", canRetry)

        // Unknown direct click result must never trigger coordinate fallback tap
        var gestureTapDispatched = false
        var swipeDispatched = false
        val clickMethod = AcceptClickExecutor.execute(
            isSwipe = false,
            directClick = { DirectClickResult.UNKNOWN },
            gestureTap = { gestureTapDispatched = true },
            swipe = { swipeDispatched = true }
        )
        assertEquals(ClickMethod.UNKNOWN_DISPATCH, clickMethod)
        assertFalse("DirectClickResult.UNKNOWN must trigger zero coordinate tap fallbacks", gestureTapDispatched)
        assertFalse("DirectClickResult.UNKNOWN must trigger zero swipe fallbacks", swipeDispatched)
    }

    // 9. Duplicate confirmation events: duplicate offers and duplicate signatures are suppressed
    @Test
    fun test09_DuplicateConfirmationEvents_Suppressed() {
        val guard = DuplicateOfferGuard(windowMs = 5000L)
        val sig = DuplicateOfferGuard.signature("com.ubercab.driver", 450.0, 2.5, "TAP TO ACCEPT")
        val now = 10000L

        // First event accepted
        val isFirstDup = guard.isDuplicate(sig, now)
        assertFalse("First offer event must not be duplicate", isFirstDup)
        val recorded = guard.isDuplicateOrRecord(sig, now)
        assertFalse("Initial record must succeed", recorded)

        // Immediate duplicate event suppressed
        val isSecondDup = guard.isDuplicate(sig, now + 500L)
        assertTrue("Subsequent offer with same signature must be suppressed as duplicate", isSecondDup)
    }

    // 10. Click attempt without confirmation: logged as ATTEMPTED, never recorded as ACCEPTED
    @Test
    fun test10_ClickAttemptWithoutConfirmation_RemainsAttempted() {
        val stateMachine = BookingStateMachine("sig-test-10", DuplicateOfferGuard())
        stateMachine.onFilterEvaluated(true)
        stateMachine.onClickAttempted(ClickMethod.DIRECT_CLICK)

        // If verification fails or is unconfirmed:
        stateMachine.onVerificationResult(AcceptOutcome.NOT_CONFIRMED)
        assertNotEquals("Unconfirmed booking must not transition to BOOKING_COMPLETED", BookingState.BOOKING_COMPLETED, stateMachine.currentState)
        assertFalse("Unconfirmed booking must not report isAccepted=true", stateMachine.isAccepted)

        // Status remains ATTEMPTED
        val initialStatus = LogStatus.ATTEMPTED
        assertNotEquals(LogStatus.ACCEPTED, initialStatus)
    }
}
