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

    companion object {
        const val DEFAULT_WINDOW_MS = 4000L

        fun signature(pkg: String, fare: Double?, pickupKm: Double?, buttonText: String): String =
            "$pkg:${fare ?: 0.0}:${pickupKm ?: 0.0}:${buttonText.trim().uppercase()}"
    }
}

enum class ClickMethod { SWIPE, DIRECT_CLICK, GESTURE_FALLBACK }

/**
 * Executes exactly ONE accept action:
 * swipe for slide-to-accept controls, otherwise a direct ACTION_CLICK,
 * and a coordinate gesture tap only if the direct click was not possible.
 */
object AcceptClickExecutor {
    fun execute(
        isSwipe: Boolean,
        directClick: () -> Boolean,
        gestureTap: () -> Unit,
        swipe: () -> Unit
    ): ClickMethod {
        if (isSwipe) {
            swipe()
            return ClickMethod.SWIPE
        }
        if (directClick()) return ClickMethod.DIRECT_CLICK
        gestureTap()
        return ClickMethod.GESTURE_FALLBACK
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

    /**
     * @param acceptButtonStillVisible null = screen could not be read,
     * true = accept button still on screen, false = booking popup is gone.
     */
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
