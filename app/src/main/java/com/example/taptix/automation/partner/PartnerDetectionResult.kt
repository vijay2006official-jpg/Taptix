package com.example.taptix.automation.partner

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Structured outcome states for partner booking screen and accept-control detection.
 */
enum class DetectionStatus {
    /** Target booking screen identified and verified for the partner app. */
    BOOKING_SCREEN_DETECTED,

    /** Actionable accept button/control located with valid bounding coordinates. */
    ACCEPT_CONTROL_FOUND,

    /** Booking screen detected, but essential offer data could not be parsed. */
    BOOKING_DATA_INCOMPLETE,

    /** Partner application is active, but view hierarchy does not match known layouts. */
    UNRECOGNIZED_LAYOUT,

    /** Package is not a recognized or allowlisted partner application. */
    UNSUPPORTED_PARTNER,

    /** View hierarchy became stale or was recycled during evaluation. */
    STALE_ACCESSIBILITY_TREE
}

/**
 * Structured detection result returned by partner UI profiles.
 */
data class PartnerDetectionResult(
    val status: DetectionStatus,
    val partnerId: String,
    val packageName: String,
    val acceptNode: AccessibilityNodeInfo? = null,
    val targetBounds: Rect = Rect(),
    val isSwipeAction: Boolean = false,
    val matchedSelectorText: String? = null,
    val diagnosticMessage: String = ""
) {
    val isActionable: Boolean
        get() = status == DetectionStatus.ACCEPT_CONTROL_FOUND &&
                acceptNode != null &&
                targetBounds.width() > 0 &&
                targetBounds.height() > 0

    companion object {
        fun unsupported(packageName: String): PartnerDetectionResult =
            PartnerDetectionResult(
                status = DetectionStatus.UNSUPPORTED_PARTNER,
                partnerId = "unsupported",
                packageName = packageName,
                diagnosticMessage = "Package '$packageName' is not supported"
            )

        fun unrecognized(partnerId: String, packageName: String, message: String): PartnerDetectionResult =
            PartnerDetectionResult(
                status = DetectionStatus.UNRECOGNIZED_LAYOUT,
                partnerId = partnerId,
                packageName = packageName,
                diagnosticMessage = message
            )
    }
}
