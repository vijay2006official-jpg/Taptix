package com.example.taptix.data

import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset

// Phase 2 profile-aware settings
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
    val targetAppPackages: String = "com.ubercab.driver, com.olacabs.driver, com.olacabs.partner, com.rapido.captain, com.rapido.passenger, com.indriver, sinet.startup.inDriver, in.juspay.nammayatripartner, com.theporter.android, com.uber.partner, com.uber.driver",
    val autoHideOnExit: Boolean = false,
    val voiceCommandsEnabled: Boolean = true,
    val nightModeAutoDimEnabled: Boolean = true,
    val nightModeDimIntensity: Float = 0.35f,
    val autoDimDelaySeconds: Int = 8,
    val pixelShiftBurnInProtection: Boolean = true,
    val autoPauseOnKeyboard: Boolean = false,
    val masterControlEnabled: Boolean = false,
    val ultraModeEnabled: Boolean = false,
    val mapZoneEnabled: Boolean = false,
    val mapZoneLat: Double = 0.0,
    val mapZoneLng: Double = 0.0,
    val mapZoneRadiusKm: Double = 2.0,
    val vibrationOnAccept: Boolean = true,
    val negativeKeywords: String = "CASH, LOW RATING, SHARED",
    val preferredAreas: String = "",
    val activeProfileId: String = "default",
    val profileNames: String = "default",
    val zoneProfilesJson: String = "[]"
)
