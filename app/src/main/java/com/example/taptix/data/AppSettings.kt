package com.example.taptix.data

import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset

/**
 * Data class representing driver settings for Taptix.
 */
data class AppSettings(
    val clickIntervalMs: Long = 300L,
    val operatingMode: OperatingMode = OperatingMode.SMART_ACCEPT,
    val platformPreset: PlatformPreset = PlatformPreset.UBER,
    val customKeywords: String = "ACCEPT, CONFIRM, RIDE",
    val autoPauseOnAccept: Boolean = true,
    val motionSafetyLockEnabled: Boolean = true,
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
    val autoPauseOnKeyboard: Boolean = true
)
