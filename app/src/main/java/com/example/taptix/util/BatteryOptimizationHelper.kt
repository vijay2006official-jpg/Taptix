package com.example.taptix.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Setting classification separating standard AOSP system controls from vendor proprietary UI.
 */
enum class SettingClassification(val label: String) {
    OFFICIAL_ANDROID_SETTING("[Official Android Setting]"),
    MANUFACTURER_SPECIFIC_GUIDANCE("[Manufacturer-Specific Guidance]")
}

/**
 * Granular guidance step with classification.
 */
data class GuidanceStep(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val classification: SettingClassification
)

/**
 * Manufacturer-tailored battery and background reliability profile.
 */
data class OemGuidanceInfo(
    val manufacturerName: String,
    val osFamily: String,
    val steps: List<GuidanceStep>,
    val disclaimer: String
)

/**
 * Result of attempting to launch device battery settings across the 4-tier fallback chain.
 */
enum class BatterySettingsLaunchResult {
    /** Tier 1: Successfully opened standard battery optimization app list. */
    SUCCESS_OPTIMIZATION_SETTINGS,

    /** Tier 2: Successfully opened Taptix App Info details page. */
    SUCCESS_APP_DETAILS,

    /** Tier 3: Successfully opened generic device settings. */
    SUCCESS_GENERAL_SETTINGS,

    /** Tier 4: All settings intents failed or threw ActivityNotFoundException / SecurityException. */
    FAILED_ALL
}

/**
 * Battery Optimization and OEM Background Reliability Helper.
 *
 * Guarantees:
 * 1. Non-invasive status inspection via [PowerManager.isIgnoringBatteryOptimizations].
 * 2. Safe 4-tier guarded intent resolution with zero crashes on stripped OEM ROMs.
 * 3. Never triggers spontaneous or automatic exemption prompts without explicit driver action.
 * 4. Realistic guidance: clearly states that battery exemption does NOT guarantee immunity from OEM task killers.
 * 5. Isolation: battery state queries or updates have zero authority over [com.example.taptix.automation.safety.AutomationSafetyController].
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    const val REALISTIC_DISCLAIMER =
        "Notice: Battery optimization exemption reduces Android OS background throttling, " +
        "but device manufacturers (Samsung, Xiaomi, OPPO, etc.) enforce proprietary background task managers. " +
        "Exemption does NOT guarantee uninterrupted background execution."

    /**
     * Non-invasively inspects whether Taptix is exempt from Android OS battery optimization.
     * Returns true by default on API < 23 where Doze/App Standby does not exist.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Throwable) {
            logW("Failed to query isIgnoringBatteryOptimizations: ${e.message}")
            false
        }
    }

    /**
     * Executes the 4-tier guarded fallback chain to open battery settings safely.
     * Guaranteed never to throw uncaught ActivityNotFoundException or SecurityException.
     *
     * @param context Android context
     * @param intentLauncher lambda executing the intent resolution (injectable for unit testing)
     */
    fun openBatterySettings(
        context: Context,
        intentLauncher: (action: String, dataUriString: String?) -> Boolean = { action, dataUriString ->
            val intent = Intent(action).apply {
                if (dataUriString != null) {
                    data = Uri.parse(dataUriString)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }
    ): BatterySettingsLaunchResult {
        // Tier 1: System-wide Battery Optimization app list
        try {
            val ok = intentLauncher(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null)
            if (ok) {
                logI("Opened ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS (Tier 1)")
                return BatterySettingsLaunchResult.SUCCESS_OPTIMIZATION_SETTINGS
            }
        } catch (e1: Throwable) {
            logW("Tier 1 (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) unavailable: ${e1.message}")
        }

        // Tier 2 Fallback: Taptix App Info details page
        try {
            val ok = intentLauncher(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}")
            if (ok) {
                logI("Opened ACTION_APPLICATION_DETAILS_SETTINGS (Tier 2)")
                return BatterySettingsLaunchResult.SUCCESS_APP_DETAILS
            }
        } catch (e2: Throwable) {
            logW("Tier 2 (ACTION_APPLICATION_DETAILS_SETTINGS) unavailable: ${e2.message}")
        }

        // Tier 3 Fallback: General device Settings
        try {
            val ok = intentLauncher(Settings.ACTION_SETTINGS, null)
            if (ok) {
                logI("Opened ACTION_SETTINGS (Tier 3)")
                return BatterySettingsLaunchResult.SUCCESS_GENERAL_SETTINGS
            }
        } catch (e3: Throwable) {
            logE("Tier 3 (ACTION_SETTINGS) unavailable: ${e3.message}")
        }

        // Tier 4: Complete safe termination
        logE("All battery settings intents failed. Gracefully terminating without crash.")
        return BatterySettingsLaunchResult.FAILED_ALL
    }

    /**
     * Resolves manufacturer-tailored background reliability guidance.
     * Categorizes every instruction as either [SettingClassification.OFFICIAL_ANDROID_SETTING]
     * or [SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE].
     */
    fun getManufacturerGuidance(manufacturerRaw: String = Build.MANUFACTURER): OemGuidanceInfo {
        val manufacturer = manufacturerRaw.uppercase()
        return when {
            manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO") -> {
                OemGuidanceInfo(
                    manufacturerName = "Xiaomi / Redmi / POCO",
                    osFamily = "MIUI / HyperOS",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Enable Autostart",
                            instruction = "Go to Settings > Apps > Manage Apps > Taptix > Enable 'Autostart'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Battery Saver No Restrictions",
                            instruction = "Under 'Battery Saver', choose 'No restrictions'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 3,
                            title = "Lock in Recent Apps",
                            instruction = "In Recent Apps, long-press the Taptix card and tap the Lock icon.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("SAMSUNG") -> {
                OemGuidanceInfo(
                    manufacturerName = "Samsung",
                    osFamily = "One UI",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Set Battery to Unrestricted",
                            instruction = "Go to Settings > Apps > Taptix > Battery > Select 'Unrestricted'.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Background Usage Limits",
                            instruction = "Go to Settings > Battery > Background usage limits > Ensure Taptix is not in 'Sleeping apps' or 'Deep sleeping apps'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 3,
                            title = "Lock in Recents",
                            instruction = "In Recent Apps overview, tap the Taptix app icon and select 'Keep open'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("OPPO") || manufacturer.contains("REALME") -> {
                OemGuidanceInfo(
                    manufacturerName = if (manufacturer.contains("REALME")) "Realme" else "OPPO",
                    osFamily = if (manufacturer.contains("REALME")) "Realme UI" else "ColorOS",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Allow Background Activity",
                            instruction = "Go to Settings > Battery > App Battery Management > Taptix > Turn ON 'Allow background activity'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Allow Auto-Launch",
                            instruction = "Under App Battery Management > Taptix > Turn ON 'Allow auto-launch'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 3,
                            title = "Exclude from Quick Freeze",
                            instruction = "Exclude Taptix from App Quick Freeze and lock the card in Recents.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("VIVO") || manufacturer.contains("IQOO") -> {
                OemGuidanceInfo(
                    manufacturerName = "Vivo / iQOO",
                    osFamily = "Funtouch OS / OriginOS",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "High Background Power Usage",
                            instruction = "Go to Settings > Battery > Background power consumption management > Taptix > Select 'High background power usage'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Enable Autostart",
                            instruction = "Go to Settings > Apps > Autostart > Enable Taptix.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("ONEPLUS") -> {
                OemGuidanceInfo(
                    manufacturerName = "OnePlus",
                    osFamily = "OxygenOS",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Don't Optimize Battery",
                            instruction = "Go to Settings > Battery > More settings > Optimize battery use > Taptix > Select 'Don't optimize'.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Allow Background Activity",
                            instruction = "Ensure 'Allow background activity' is enabled under Taptix battery settings.",
                            classification = SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("MOTOROLA") -> {
                OemGuidanceInfo(
                    manufacturerName = "Motorola",
                    osFamily = "Hello UI / My UX",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Set Battery to Unrestricted",
                            instruction = "Go to Settings > Apps > Taptix > App battery usage > Select 'Unrestricted'.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Disable System Battery Saver",
                            instruction = "Keep system Battery Saver disabled during active working hours.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            manufacturer.contains("GOOGLE") || manufacturer.contains("PIXEL") -> {
                OemGuidanceInfo(
                    manufacturerName = "Google Pixel",
                    osFamily = "Stock Android",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Set App Battery Usage to Unrestricted",
                            instruction = "Go to Settings > Apps > Taptix > App battery usage > Select 'Unrestricted'.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Allow Background Usage",
                            instruction = "Ensure 'Allow background usage' toggle is enabled.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        ),
                        GuidanceStep(
                            stepNumber = 3,
                            title = "Keep Battery Saver Off",
                            instruction = "Disable system Battery Saver while driving on shift.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
            else -> {
                OemGuidanceInfo(
                    manufacturerName = if (manufacturer.isNotBlank()) manufacturerRaw else "Android Device",
                    osFamily = "AOSP Standard",
                    steps = listOf(
                        GuidanceStep(
                            stepNumber = 1,
                            title = "Set Battery Usage to Unrestricted",
                            instruction = "Go to Settings > Apps > Taptix > Battery > Select 'Unrestricted'.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        ),
                        GuidanceStep(
                            stepNumber = 2,
                            title = "Disable Power Saving During Shifts",
                            instruction = "Keep system battery saver or stamina modes turned off while driving.",
                            classification = SettingClassification.OFFICIAL_ANDROID_SETTING
                        )
                    ),
                    disclaimer = REALISTIC_DISCLAIMER
                )
            }
        }
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logE(msg: String) {
        try { Log.e(TAG, msg) } catch (_: Throwable) {}
    }
}
