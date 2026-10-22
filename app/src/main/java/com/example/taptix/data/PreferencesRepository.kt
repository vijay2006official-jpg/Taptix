package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.taptix.model.DriverProfile
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset

class PreferencesRepository(context: Context) {

    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                "${PREFS_NAME}_secure",
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateLegacyPreferencesIfNeeded(encryptedPrefs)
            encryptedPrefs
        } catch (e: Exception) {
            legacyPrefs
        }
    }

    private fun migrateLegacyPreferencesIfNeeded(target: SharedPreferences) {
        if (legacyPrefs.all.isEmpty() || target.all.isNotEmpty()) return
        val editor = target.edit()
        legacyPrefs.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()
    }

    fun getSettings(): AppSettings {
        val interval = prefs.getLong(KEY_INTERVAL, 300L)
        val modeStr = prefs.getString(KEY_MODE, OperatingMode.SMART_ACCEPT.name) ?: OperatingMode.SMART_ACCEPT.name
        val mode = try {
            OperatingMode.valueOf(modeStr)
        } catch (_: Exception) {
            OperatingMode.SMART_ACCEPT
        }

        val presetId = prefs.getString(KEY_PRESET, PlatformPreset.UNIVERSAL.id) ?: PlatformPreset.UNIVERSAL.id
        val preset = PlatformPreset.fromId(presetId)

        val customKeywords = prefs.getString(KEY_CUSTOM_KEYWORDS, "ACCEPT, CONFIRM, RIDE") ?: "ACCEPT, CONFIRM, RIDE"
        val autoPause = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        val motionLock = prefs.getBoolean(KEY_MOTION_LOCK, false)
        val tts = prefs.getBoolean(KEY_TTS, true)

        val autoLaunch = prefs.getBoolean(KEY_AUTO_LAUNCH, true)
        val targetPackages = prefs.getString(KEY_TARGET_PACKAGES, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES
        val autoHide = prefs.getBoolean(KEY_AUTO_HIDE, false)
        val voiceCommands = prefs.getBoolean(KEY_VOICE_COMMANDS, true)

        val nightModeDim = prefs.getBoolean(KEY_NIGHT_MODE_AUTO_DIM, true)
        val dimIntensity = prefs.getFloat(KEY_NIGHT_MODE_DIM_INTENSITY, 0.35f)
        val dimDelay = prefs.getInt(KEY_AUTO_DIM_DELAY, 8)
        val pixelShift = prefs.getBoolean(KEY_PIXEL_SHIFT, true)
        val autoPauseKeyboard = prefs.getBoolean(KEY_AUTO_PAUSE_KEYBOARD, false)

        val masterControl = prefs.getBoolean(KEY_MASTER_CONTROL, false)
        val ultraMode = prefs.getBoolean(KEY_ULTRA_MODE, false)
        val mapZoneOn = prefs.getBoolean(KEY_MAP_ZONE_ENABLED, false)
        val mapZoneLat = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_MAP_ZONE_LAT, java.lang.Double.doubleToRawLongBits(0.0))
        )
        val mapZoneLng = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_MAP_ZONE_LNG, java.lang.Double.doubleToRawLongBits(0.0))
        )
        val mapZoneRadius = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_MAP_ZONE_RADIUS, java.lang.Double.doubleToRawLongBits(2.0))
        )
        val vibrationOn = prefs.getBoolean(KEY_VIBRATION, true)
        val negativeKeywords = prefs.getString(KEY_NEGATIVE_KEYWORDS, "CASH, LOW RATING, SHARED")
            ?: "CASH, LOW RATING, SHARED"
        val preferredAreas = prefs.getString(KEY_PREFERRED_AREAS, "") ?: ""

        val activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") ?: "default"
        val profileNames = prefs.getString(KEY_PROFILE_NAMES, "default") ?: "default"
        val zoneProfilesJson = prefs.getString(KEY_ZONE_PROFILES_JSON, "[]") ?: "[]"

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
            autoPauseOnKeyboard = autoPauseKeyboard,
            masterControlEnabled = masterControl,
            ultraModeEnabled = ultraMode,
            mapZoneEnabled = mapZoneOn,
            mapZoneLat = mapZoneLat,
            mapZoneLng = mapZoneLng,
            mapZoneRadiusKm = mapZoneRadius,
            vibrationOnAccept = vibrationOn,
            negativeKeywords = negativeKeywords,
            preferredAreas = preferredAreas,
            activeProfileId = activeProfileId,
            profileNames = profileNames,
            zoneProfilesJson = zoneProfilesJson
        )
    }

    fun saveClickInterval(intervalMs: Long) = prefs.edit().putLong(KEY_INTERVAL, intervalMs).apply()
    fun saveOperatingMode(mode: OperatingMode) = prefs.edit().putString(KEY_MODE, mode.name).apply()
    fun savePlatformPreset(preset: PlatformPreset) = prefs.edit().putString(KEY_PRESET, preset.id).apply()
    fun saveCustomKeywords(keywords: String) = prefs.edit().putString(KEY_CUSTOM_KEYWORDS, keywords).apply()
    fun saveAutoPauseOnAccept(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_PAUSE, enabled).apply()
    fun saveMotionSafetyLockEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_MOTION_LOCK, enabled).apply()
    fun saveTtsFeedbackEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_TTS, enabled).apply()
    fun saveAutoLaunchForTargetAppsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, enabled).apply()
    fun saveTargetAppPackages(packages: String) = prefs.edit().putString(KEY_TARGET_PACKAGES, packages).apply()
    fun saveAutoHideOnExit(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_HIDE, enabled).apply()
    fun saveVoiceCommandsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_VOICE_COMMANDS, enabled).apply()
    fun saveNightModeAutoDimEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_NIGHT_MODE_AUTO_DIM, enabled).apply()
    fun saveAutoDimDelaySeconds(seconds: Int) = prefs.edit().putInt(KEY_AUTO_DIM_DELAY, seconds).apply()
    fun savePixelShiftBurnInProtection(enabled: Boolean) = prefs.edit().putBoolean(KEY_PIXEL_SHIFT, enabled).apply()
    fun saveNightModeDimIntensity(intensity: Float) = prefs.edit().putFloat(KEY_NIGHT_MODE_DIM_INTENSITY, intensity).apply()
    fun saveAutoPauseOnKeyboard(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_PAUSE_KEYBOARD, enabled).apply()
    fun saveMasterControl(enabled: Boolean) = prefs.edit().putBoolean(KEY_MASTER_CONTROL, enabled).apply()
    fun saveUltraMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_ULTRA_MODE, enabled).apply()
    fun saveMapZoneEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_MAP_ZONE_ENABLED, enabled).apply()
    fun saveVibrationOnAccept(enabled: Boolean) = prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    fun saveNegativeKeywords(keywords: String) = prefs.edit().putString(KEY_NEGATIVE_KEYWORDS, keywords).apply()
    fun savePreferredAreas(areas: String) = prefs.edit().putString(KEY_PREFERRED_AREAS, areas).apply()
    fun saveMapZone(lat: Double, lng: Double, radiusKm: Double) {
        prefs.edit()
            .putLong(KEY_MAP_ZONE_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_MAP_ZONE_LNG, java.lang.Double.doubleToRawLongBits(lng))
            .putLong(KEY_MAP_ZONE_RADIUS, java.lang.Double.doubleToRawLongBits(radiusKm))
            .apply()
    }

    fun saveActiveProfileId(profileId: String) = prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    fun saveProfileNames(names: String) = prefs.edit().putString(KEY_PROFILE_NAMES, names).apply()
    fun saveZoneProfilesJson(json: String) = prefs.edit().putString(KEY_ZONE_PROFILES_JSON, json).apply()

    fun saveProfile(profile: DriverProfile) {
        val current = ProfileManager(context = legacyPrefs.context ?: throw IllegalStateException("Context unavailable")).getProfiles()
        val updated = current.filterNot { it.id == profile.id } + profile
        ProfileManager(context = legacyPrefs.context ?: throw IllegalStateException("Context unavailable")).saveProfiles(updated)
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
        private const val KEY_MASTER_CONTROL = "key_master_control"
        private const val KEY_ULTRA_MODE = "key_ultra_mode"
        private const val KEY_MAP_ZONE_ENABLED = "key_map_zone_enabled"
        private const val KEY_MAP_ZONE_LAT = "key_map_zone_lat"
        private const val KEY_MAP_ZONE_LNG = "key_map_zone_lng"
        private const val KEY_MAP_ZONE_RADIUS = "key_map_zone_radius"
        private const val KEY_VIBRATION = "key_vibration_on_accept"
        private const val KEY_NEGATIVE_KEYWORDS = "key_negative_keywords"
        private const val KEY_PREFERRED_AREAS = "key_preferred_areas"
        private const val KEY_ACTIVE_PROFILE_ID = "key_active_profile_id"
        private const val KEY_PROFILE_NAMES = "key_profile_names"
        private const val KEY_ZONE_PROFILES_JSON = "key_zone_profiles_json"

        private const val DEFAULT_TARGET_PACKAGES =
            "com.ubercab.driver, com.olacabs.driver, com.olacabs.partner, com.rapido.captain, com.rapido.passenger, com.indriver, sinet.startup.inDriver, in.juspay.nammayatripartner, com.theporter.android, com.uber.partner, com.uber.driver"
    }
}
