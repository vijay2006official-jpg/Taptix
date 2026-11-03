package com.example.taptix.automation.partner

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Common contract for modular, version-aware partner UI detection profiles.
 * Eliminates scattered string comparisons across accessibility event loops.
 */
interface PartnerUiProfile {
    /** Unique identifier for the partner platform (e.g. "uber", "ola", "rapido"). */
    val partnerId: String

    /** Human-readable commercial partner name. */
    val displayName: String

    /** Package names belonging to this driver-partner application. */
    val supportedPackages: List<String>

    /** Semantic version of this UI profile implementation. */
    val profileVersion: String

    /** Verification and maintenance status of this profile. */
    val status: PartnerProfileStatus

    /** Whether the primary accept control is a slide-to-accept or tap-to-accept action. */
    val isSwipeToAccept: Boolean

    /** Actionable accept keywords defined for this partner. */
    val acceptKeywords: List<String>

    /** Keywords indicating positive acceptance confirmation. */
    val positiveConfirmationKeywords: List<String>

    /** Keywords indicating offer expiry or customer/platform cancellation. */
    val cancellationKeywords: List<String>

    /**
     * Determines whether [rootNode] contains indicators of an active incoming booking offer.
     */
    fun isBookingScreen(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean

    /**
     * Identifies the primary accept button or slider node and its screen bounds.
     */
    fun findAcceptControl(rootNode: AccessibilityNodeInfo?, packageName: String): PartnerDetectionResult
}
