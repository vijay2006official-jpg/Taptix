package com.example.taptix.engine

/**
 * Pure-Kotlin core of the Auto Acceptance flow.
 *
 * Kept free of Android framework types so the exact logic used by
 * [com.example.taptix.service.AutoClickService] can be exercised by JVM unit tests.
 */

/**
 * Driver-partner packages that the accessibility service listens to.
 * Must stay in sync with android:packageNames in res/xml/accessibility_service_config.xml
 * (enforced by a unit test).
 *
 * Verified Play Store IDs: Uber Driver (com.ubercab.driver), Ola Driver (com.olacabs.oladriver),
 * Rapido Captain (com.rapido.rider), inDrive (sinet.startup.inDriver),
 * Namma Yatri Partner (in.juspay.nammayatripartner), Porter Driver (com.theporter.android.driverapp).
 * The remaining entries are legacy/alternate IDs kept for backwards compatibility.
 */
object PartnerPackages {
    val SUPPORTED: List<String> = listOf(
        "com.ubercab.driver",
        "com.olacabs.oladriver",
        "com.olacabs.driver",
        "com.olacabs.partner",
        "com.rapido.rider",
        "com.rapido.captain",
        "com.rapido.passenger",
        "sinet.startup.inDriver",
        "com.indriver",
        "in.juspay.nammayatripartner",
        "movingtech.nammatrip.driver",
        "movingtech.nammayatri.driver",
        "in.juspay.nammayatri",
        "in.juspay.yatrisathi",
        "com.theporter.android.driverapp",
        "in.porter.partner",
        "com.theporter.partner",
        "me.lyft.driver",
        "com.grabtaxi.driver2",
        "com.dd.driver"
    )

    /**
     * Supported packages + user-configured custom target packages + Taptix itself.
     * Package names are case-sensitive on Android, so custom entries are kept as typed.
     */
    fun merged(customTargets: String, ownPackage: String): Array<String> {
        val custom = customTargets.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return (SUPPORTED + custom + ownPackage).distinct().toTypedArray()
    }
}

/** Prevents the same booking popup from being tapped more than once. */
class DuplicateOfferGuard(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private var lastSignature: String = ""
    private var lastTimestamp: Long = 0L

    @Synchronized
    fun isDuplicate(signature: String, now: Long): Boolean =
        signature == lastSignature && (now - lastTimestamp) < windowMs

    @Synchronized
    fun record(signature: String, now: Long) {
        lastSignature = signature
        lastTimestamp = now
    }

    /**
     * Atomically checks if an offer signature is duplicate within [windowMs].
     * If duplicate, returns true.
     * If unique (not duplicate), atomically records it and returns false.
     * Guarantees race-condition safety under simultaneous thread arrivals.
     */
    @Synchronized
    fun isDuplicateOrRecord(signature: String, now: Long): Boolean {
        if (signature == lastSignature && (now - lastTimestamp) < windowMs) {
            return true
        }
        lastSignature = signature
        lastTimestamp = now
        return false
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 4000L

        fun signature(pkg: String, fare: Double?, pickupKm: Double?, buttonText: String): String =
            "$pkg:${fare ?: 0.0}:${pickupKm ?: 0.0}:${buttonText.trim().uppercase()}"
    }
}

enum class ClickMethod { SWIPE, DIRECT_CLICK, GESTURE_FALLBACK, UNKNOWN_DISPATCH }

enum class DirectClickResult {
    SUCCESS,
    FAILED_CONFIRMED_NOT_DISPATCHED,
    UNKNOWN
}

/**
 * Executes exactly ONE accept action:
 * - swipe for slide-to-accept controls
 * - direct ACTION_CLICK when directClick succeeds
 * - controlled coordinate gesture fallback ONLY when direct action is confirmed not dispatched
 * - NO fallback and NO secondary tap when direct action outcome is uncertain (UNKNOWN)
 */
object AcceptClickExecutor {
    fun execute(
        isSwipe: Boolean,
        directClick: () -> DirectClickResult,
        gestureTap: () -> Unit,
        swipe: () -> Unit
    ): ClickMethod {
        if (isSwipe) {
            swipe()
            return ClickMethod.SWIPE
        }
        return when (directClick()) {
            DirectClickResult.SUCCESS -> ClickMethod.DIRECT_CLICK
            DirectClickResult.FAILED_CONFIRMED_NOT_DISPATCHED -> {
                gestureTap()
                ClickMethod.GESTURE_FALLBACK
            }
            DirectClickResult.UNKNOWN -> ClickMethod.UNKNOWN_DISPATCH
        }
    }
}

enum class AcceptOutcome { CONFIRMED, NOT_CONFIRMED, UNKNOWN }

/**
 * Decides the result of an accept tap by looking at the screen afterwards
 * (checked asynchronously, so it never delays the tap itself).
 */
object AcceptVerifier {
    /** Delay before the post-tap screen check. The tap is dispatched immediately. */
    const val VERIFY_DELAY_MS = 650L
    /** Secondary retry timeout for slow-rendering screens. */
    const val RETRY_VERIFY_DELAY_MS = 450L

    val POSITIVE_CONFIRMATION_KEYWORDS = listOf(
        "ACCEPTED", "RIDE ACCEPTED", "BOOKING ACCEPTED", "TRIP ACCEPTED",
        "NAVIGATE", "NAVIGATION", "START TRIP", "ARRIVING", "HEAD TO",
        "PICK UP", "PICKUP", "ON THE WAY", "CALL PASSENGER", "CALL CUSTOMER",
        "CHAT", "PASSENGER", "CUSTOMER", "WAYPOINT", "ORDER DETAILS", "ACCEPTED BY YOU"
    )

    val EXPIRY_OR_CANCELLATION_KEYWORDS = listOf(
        "EXPIRED", "OFFER EXPIRED", "CANCELLED", "CANCELED", "MISSED",
        "TOO LATE", "ANOTHER DRIVER", "NO LONGER AVAILABLE", "ORDER TIMED OUT",
        "FAILED", "TRIP CANCELLED", "LOST"
    )

    /**
     * Comprehensive acceptance evaluation ensuring button disappearance alone
     * does NOT automatically establish acceptance.
     */
    fun evaluate(
        acceptButtonStillVisible: Boolean?,
        positiveConfirmationPresent: Boolean = false,
        negativeOrCancelledPresent: Boolean = false
    ): AcceptOutcome {
        if (acceptButtonStillVisible == null) return AcceptOutcome.UNKNOWN
        if (acceptButtonStillVisible) return AcceptOutcome.NOT_CONFIRMED
        if (negativeOrCancelledPresent) return AcceptOutcome.NOT_CONFIRMED
        if (positiveConfirmationPresent) return AcceptOutcome.CONFIRMED
        // Button disappearance alone without positive confirmation cannot establish successful acceptance
        return AcceptOutcome.NOT_CONFIRMED
    }

    /** Overload for direct visibility checks (fallback / basic validation) */
    fun evaluate(acceptButtonStillVisible: Boolean?): AcceptOutcome = when (acceptButtonStillVisible) {
        null -> AcceptOutcome.UNKNOWN
        true -> AcceptOutcome.NOT_CONFIRMED
        false -> AcceptOutcome.CONFIRMED
    }
}

/**
 * Policy governing controlled retries for slow or unresponsive booking controls.
 * Ensures maximum 1 retry within a safe window without looping.
 */
object ControlledRetryPolicy {
    const val MAX_RETRIES = 1

    fun shouldRetry(currentAttempts: Int, outcome: AcceptOutcome): Boolean {
        return currentAttempts < MAX_RETRIES && outcome == AcceptOutcome.NOT_CONFIRMED
    }

    /**
     * Safety gate for executing a retry tap:
     * - Must have retries remaining (< MAX_RETRIES)
     * - Button must be visible AND still enabled (not disabled or in loading state)
     * - App in foreground must still match the target partner app
     * - No negative/cancellation messages on screen
     * - No positive confirmation messages on screen
     */
    fun isRetrySafe(
        currentAttempts: Int,
        outcome: AcceptOutcome,
        isButtonStillVisible: Boolean,
        isButtonEnabled: Boolean,
        isForegroundTargetApp: Boolean,
        hasNegativeSignal: Boolean,
        hasPositiveConfirmation: Boolean
    ): Boolean {
        if (!shouldRetry(currentAttempts, outcome)) return false
        if (!isButtonStillVisible) return false
        if (!isButtonEnabled) return false // Button disabled = already processing first click!
        if (!isForegroundTargetApp) return false // Switched app = do not misclick!
        if (hasNegativeSignal) return false // Cancelled or expired = do not tap!
        if (hasPositiveConfirmation) return false // Already confirmed!
        return true
    }
}

/**
 * Validates booking UI elements before any action is attempted.
 * Filters out invalid, zero-dimension, or unreliable detection results.
 */
object BookingDetectionValidator {
    const val MIN_BUTTON_WIDTH_PX = 60
    const val MIN_BUTTON_HEIGHT_PX = 35
    const val MAX_BUTTON_HEIGHT_PX = 550

    fun isValidButtonBounds(width: Int, height: Int): Boolean {
        return width >= MIN_BUTTON_WIDTH_PX && height in MIN_BUTTON_HEIGHT_PX..MAX_BUTTON_HEIGHT_PX
    }

    fun isReliableOffer(
        rawText: String,
        fare: Double?,
        pickupKm: Double?,
        minFareEnabled: Boolean,
        minFareAmount: Double,
        maxPickupDistanceEnabled: Boolean,
        maxPickupDistanceKm: Double,
        fallbackReject: Boolean
    ): Boolean {
        if (rawText.isBlank()) return false
        if (minFareEnabled && minFareAmount > 0.0 && fare == null && fallbackReject) {
            return false
        }
        if (maxPickupDistanceEnabled && maxPickupDistanceKm > 0.0 && pickupKm == null && fallbackReject) {
            return false
        }
        return true
    }
}

/**
 * Observable lifecycle state for the Automation Engine.
 */
enum class AutomationEngineState {
    DISCONNECTED,
    CONNECTED_IDLE,
    SCANNING,
    ACTION_DISPATCHED,
    VERIFYING,
    ACCEPTED_CONFIRMED,
    PAUSED_BY_USER
}

/**
 * Formal 5-state booking lifecycle for strict accuracy and zero-duplicate guarantees.
 * Required by Phase O2 validation.
 */
enum class BookingState {
    BOOKING_DETECTED,
    FILTER_MATCHED,
    CLICK_ATTEMPTED,
    ACCEPTANCE_CONFIRMED,
    BOOKING_COMPLETED
}

/**
 * State machine managing booking lifecycle transitions with strict invariant verification:
 * - A detected booking is NOT considered accepted.
 * - A click attempt is NOT counted as confirmed acceptance until post-tap verification succeeds.
 * - Failed or timeout actions never transition to confirmed.
 * - Concurrency-safe and idempotent against duplicate / out-of-order events.
 */
class BookingStateMachine(
    val bookingId: String,
    val duplicateGuard: DuplicateOfferGuard = DuplicateOfferGuard()
) {
    var currentState: BookingState = BookingState.BOOKING_DETECTED
        private set

    var clickAttempts: Int = 0
        private set

    var failureReason: String? = null
        private set

    val isAccepted: Boolean
        get() = currentState == BookingState.ACCEPTANCE_CONFIRMED || currentState == BookingState.BOOKING_COMPLETED

    val isTerminal: Boolean
        get() = currentState == BookingState.BOOKING_COMPLETED || failureReason != null

    @Synchronized
    fun onFilterEvaluated(matched: Boolean, reason: String? = null): Boolean {
        if (currentState != BookingState.BOOKING_DETECTED) return false
        return if (matched) {
            currentState = BookingState.FILTER_MATCHED
            true
        } else {
            failureReason = reason ?: "FILTER_REJECTED"
            false
        }
    }

    @Synchronized
    fun onClickAttempted(method: ClickMethod): Boolean {
        if (failureReason != null) return false
        if (clickAttempts > ControlledRetryPolicy.MAX_RETRIES) return false
        if (currentState != BookingState.FILTER_MATCHED && currentState != BookingState.CLICK_ATTEMPTED) return false
        clickAttempts++
        currentState = BookingState.CLICK_ATTEMPTED
        return true
    }

    @Synchronized
    fun onVerificationResult(outcome: AcceptOutcome): Boolean {
        if (failureReason != null) return false
        if (currentState != BookingState.CLICK_ATTEMPTED) return false
        return when (outcome) {
            AcceptOutcome.CONFIRMED -> {
                currentState = BookingState.ACCEPTANCE_CONFIRMED
                true
            }
            AcceptOutcome.NOT_CONFIRMED -> {
                val retriesUsed = clickAttempts - 1
                if (ControlledRetryPolicy.shouldRetry(retriesUsed, outcome)) {
                    // Allowed exactly 1 retry: remain in CLICK_ATTEMPTED
                    false
                } else {
                    failureReason = "ACCEPTANCE_NOT_CONFIRMED"
                    false
                }
            }
            AcceptOutcome.UNKNOWN -> {
                failureReason = "VERIFICATION_TIMEOUT_OR_UNKNOWN"
                false
            }
        }
    }

    @Synchronized
    fun onBookingCompleted(): Boolean {
        if (failureReason != null) return false
        if (currentState != BookingState.ACCEPTANCE_CONFIRMED) return false
        currentState = BookingState.BOOKING_COMPLETED
        return true
    }

    @Synchronized
    fun resetOnInterruption(reason: String) {
        if (!isAccepted) {
            failureReason = "INTERRUPTED: $reason"
        }
    }
}

