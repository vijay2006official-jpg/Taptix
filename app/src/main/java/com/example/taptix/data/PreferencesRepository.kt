package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset

/**
 * Lightweight repository for saving and loading driver preferences without external dependencies.
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        val interval = prefs.getLong(KEY_INTERVAL, 300L)
        val modeStr = prefs.getString(KEY_MODE, OperatingMode.SMART_ACCEPT.name) ?: OperatingMode.SMART_ACCEPT.name
        val mode = try {
            OperatingMode.valueOf(modeStr)
        } catch (e: Exception) {
            OperatingMode.SMART_ACCEPT
        }

        val presetId = prefs.getString(KEY_PRESET, PlatformPreset.UBER.id) ?: PlatformPreset.UBER.id
        val preset = PlatformPreset.fromId(presetId)

        val customKeywords = prefs.getString(KEY_CUSTOM_KEYWORDS, "ACCEPT, CONFIRM, RIDE") ?: "ACCEPT, CONFIRM, RIDE"
        val autoPause = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        val motionLock = prefs.getBoolean(KEY_MOTION_LOCK, true)
        val tts = prefs.getBoolean(KEY_TTS, true)

        val autoLaunch = prefs.getBoolean(KEY_AUTO_LAUNCH, true)
        val targetPackages = prefs.getString(KEY_TARGET_PACKAGES, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES
        val autoHide = prefs.getBoolean(KEY_AUTO_HIDE, false)
        val voiceCommands = prefs.getBoolean(KEY_VOICE_COMMANDS, true)

        val nightModeDim = prefs.getBoolean(KEY_NIGHT_MODE_AUTO_DIM, true)
        val dimIntensity = prefs.getFloat(KEY_NIGHT_MODE_DIM_INTENSITY, 0.35f)
        val dimDelay = prefs.getInt(KEY_AUTO_DIM_DELAY, 8)
        val pixelShift = prefs.getBoolean(KEY_PIXEL_SHIFT, true)
        val autoPauseKeyboard = prefs.getBoolean(KEY_AUTO_PAUSE_KEYBOARD, true)

        return AppSettings(
            clickIntervalMs = interval,
            operatingMode = mode,
            platformPreset = preset,
            customKeywords = customKeywords,
            autoPauseOnAccept = autoPause,
            motionSafetyLockEnabled = motionLock,
            ttsFeedbackEnabled = tts,
            autoLaunchForTargetAppsEnabled = autoLaunch,
            targetAppPackages = targetPackages,
            autoHideOnExit = autoHide,
            voiceCommandsEnabled = voiceCommands,
            nightModeAutoDimEnabled = nightModeDim,
            nightModeDimIntensity = dimIntensity,
            autoDimDelaySeconds = dimDelay,
            pixelShiftBurnInProtection = pixelShift,
            autoPauseOnKeyboard = autoPauseKeyboard
        )
    }

    fun saveClickInterval(intervalMs: Long) {
        prefs.edit().putLong(KEY_INTERVAL, intervalMs).apply()
    }

    fun saveOperatingMode(mode: OperatingMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun savePlatformPreset(preset: PlatformPreset) {
        prefs.edit().putString(KEY_PRESET, preset.id).apply()
    }

    fun saveCustomKeywords(keywords: String) {
        prefs.edit().putString(KEY_CUSTOM_KEYWORDS, keywords).apply()
    }

    fun saveAutoPauseOnAccept(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE, enabled).apply()
    }

    fun saveMotionSafetyLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOTION_LOCK, enabled).apply()
    }

    fun saveTtsFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS, enabled).apply()
    }

    fun saveAutoLaunchForTargetAppsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH, enabled).apply()
    }

    fun saveTargetAppPackages(packages: String) {
        prefs.edit().putString(KEY_TARGET_PACKAGES, packages).apply()
    }

    fun saveAutoHideOnExit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE, enabled).apply()
    }

    fun saveVoiceCommandsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_COMMANDS, enabled).apply()
    }

    fun saveNightModeAutoDimEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE_AUTO_DIM, enabled).apply()
    }

    fun saveAutoDimDelaySeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_DIM_DELAY, seconds).apply()
    }

    fun savePixelShiftBurnInProtection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIXEL_SHIFT, enabled).apply()
    }

    fun saveNightModeDimIntensity(intensity: Float) {
        prefs.edit().putFloat(KEY_NIGHT_MODE_DIM_INTENSITY, intensity).apply()
    }

    fun saveAutoPauseOnKeyboard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE_KEYBOARD, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "taptix_driver_prefs"
        private const val KEY_INTERVAL = "key_interval"
        private const val KEY_MODE = "key_mode"
        private const val KEY_PRESET = "key_preset"
        private const val KEY_CUSTOM_KEYWORDS = "key_custom_keywords"
        private const val KEY_AUTO_PAUSE = "key_auto_pause"
        private const val KEY_MOTION_LOCK = "key_motion_lock"
        private const val KEY_TTS = "key_tts"
        private const val KEY_AUTO_LAUNCH = "key_auto_launch"
        private const val KEY_TARGET_PACKAGES = "key_target_packages"
        private const val KEY_AUTO_HIDE = "key_auto_hide"
        private const val KEY_VOICE_COMMANDS = "key_voice_commands"
        private const val KEY_NIGHT_MODE_AUTO_DIM = "key_night_mode_auto_dim"
        private const val KEY_NIGHT_MODE_DIM_INTENSITY = "key_night_mode_dim_intensity"
        private const val KEY_AUTO_DIM_DELAY = "key_auto_dim_delay"
        private const val KEY_PIXEL_SHIFT = "key_pixel_shift"
        private const val KEY_AUTO_PAUSE_KEYBOARD = "key_auto_pause_keyboard"

        private const val DEFAULT_TARGET_PACKAGES =
            "com.ubercab.driver, me.lyft.driver, com.indriver, com.olacabs.driver, com.rapido.passenger, com.grabtaxi.driver2, com.dd.driver"
    }
}
