package com.example.taptix.data

import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset

/**
 * Data class representing driver settings for Taptix.
 */
data class AppSettings(
    val clickIntervalMs: Long = 300L,
    val operatingMode: OperatingMode = OperatingMode.SMART_ACCEPT,
    val platformPreset: PlatformPreset = PlatformPreset.UNIVERSAL,
    val customKeywords: String = "ACCEPT, CONFIRM, RIDE",
    val autoPauseOnAccept: Boolean = true,
    val motionSafetyLockEnabled: Boolean = false,
    val ttsFeedbackEnabled: Boolean = true,
    val swipeDurationMs: Long = 350L,
    val autoLaunchForTargetAppsEnabled: Boolean = true,
    val targetAppPackages: String = "com.ubercab.driver, me.lyft.driver, com.indriver, com.olacabs.driver, com.rapido.passenger, com.grabtaxi.driver2, com.dd.driver",
    val autoHideOnExit: Boolean = false,
    val voiceCommandsEnabled: Boolean = true,
    val nightModeAutoDimEnabled: Boolean = true,
    val nightModeDimIntensity: Float = 0.35f,
    val autoDimDelaySeconds: Int = 8,
    val pixelShiftBurnInProtection: Boolean = true,
    val autoPauseOnKeyboard: Boolean = false,

    // New v1.7 Settings
    val masterControlEnabled: Boolean = false,   // Main ON/OFF master switch
    val ultraModeEnabled: Boolean = false,        // Accept ALL bookings, bypass filters
    val mapZoneEnabled: Boolean = false,          // Only accept bookings in drawn map zone
    val mapZoneLat: Double = 0.0,                 // Center latitude of zone circle
    val mapZoneLng: Double = 0.0,                 // Center longitude of zone circle
    val mapZoneRadiusKm: Double = 2.0,            // Radius in kilometers
    val vibrationOnAccept: Boolean = true,        // Vibrate on accept (can be toggled OFF)
    val negativeKeywords: String = "CASH, LOW RATING, SHARED", // Exclude orders containing these terms
    val preferredAreas: String = ""              // Comma-separated preferred area names
)
