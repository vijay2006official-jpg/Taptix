package com.example.taptix.model

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Normalized representation of a detected incoming ride offer on the screen.
 * Decouples screen detection from rule evaluation.
 */
data class OfferDetails(
    val packageName: String,
    val rawText: String,
    val fare: Double? = null,
    val pickupDistanceKm: Double? = null,
    val tripDistanceKm: Double? = null,
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val acceptNode: AccessibilityNodeInfo? = null,
    val isSwipeAction: Boolean = false
)
