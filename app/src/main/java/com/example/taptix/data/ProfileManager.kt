package com.example.taptix.data

import android.content.Context
import android.content.SharedPreferences
import com.example.taptix.model.DriverProfile
import com.example.taptix.model.ZoneProfile
import org.json.JSONArray

class ProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfiles(): List<DriverProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return defaultProfiles()
        return DriverProfile.fromJson(json).ifEmpty { defaultProfiles() }
    }

    fun saveProfiles(profiles: List<DriverProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
    }

    fun getActiveProfileId(): String = prefs.getString(KEY_ACTIVE_PROFILE, "default") ?: "default"

    fun getActiveProfile(): DriverProfile {
        val id = getActiveProfileId()
        return getProfiles().firstOrNull { it.id == id } ?: getProfiles().firstOrNull() ?: defaultProfiles().first()
    }

    fun getZoneProfiles(): List<ZoneProfile> {
        val json = prefs.getString(KEY_ZONES, null) ?: return emptyList()
        return ZoneProfile.fromJson(json)
    }

    fun saveZoneProfiles(profiles: List<ZoneProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_ZONES, arr.toString()).apply()
    }

    fun defaultProfiles(): List<DriverProfile> = listOf(
        DriverProfile(
            id = "default",
            name = "Default",
            platformPreset = com.example.taptix.model.PlatformPreset.UNIVERSAL,
            customKeywords = "ACCEPT, CONFIRM, RIDE",
            negativeKeywords = "CASH, LOW RATING, SHARED",
            targetAppPackages = "com.ubercab.driver, com.olacabs.driver, com.rapido.captain, com.indriver",
            ultraModeEnabled = false,
            mapZoneEnabled = false,
            mapZoneLat = 0.0,
            mapZoneLng = 0.0,
            mapZoneRadiusKm = 2.0
        )
    )

    companion object {
        private const val PREFS_NAME = "taptix_profiles"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ZONES = "zones_json"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    }
}
