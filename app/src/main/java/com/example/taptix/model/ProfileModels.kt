package com.example.taptix.model

import org.json.JSONArray
import org.json.JSONObject

// Driver profile used to group rules, app targets and zone behaviour.
data class DriverProfile(
    val id: String = "default",
    val name: String = "Default",
    val platformPreset: PlatformPreset = PlatformPreset.UNIVERSAL,
    val customKeywords: String = "ACCEPT, CONFIRM, RIDE",
    val negativeKeywords: String = "CASH, LOW RATING, SHARED",
    val targetAppPackages: String = "com.ubercab.driver, com.olacabs.driver, com.rapido.captain, com.indriver",
    val ultraModeEnabled: Boolean = false,
    val mapZoneEnabled: Boolean = false,
    val mapZoneLat: Double = 0.0,
    val mapZoneLng: Double = 0.0,
    val mapZoneRadiusKm: Double = 2.0
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("platformPreset", platformPreset.id)
        put("customKeywords", customKeywords)
        put("negativeKeywords", negativeKeywords)
        put("targetAppPackages", targetAppPackages)
        put("ultraModeEnabled", ultraModeEnabled)
        put("mapZoneEnabled", mapZoneEnabled)
        put("mapZoneLat", mapZoneLat)
        put("mapZoneLng", mapZoneLng)
        put("mapZoneRadiusKm", mapZoneRadiusKm)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): DriverProfile {
            val presetId = obj.optString("platformPreset", PlatformPreset.UNIVERSAL.id)
            return DriverProfile(
                id = obj.optString("id", "default"),
                name = obj.optString("name", "Default"),
                platformPreset = PlatformPreset.fromId(presetId),
                customKeywords = obj.optString("customKeywords", "ACCEPT, CONFIRM, RIDE"),
                negativeKeywords = obj.optString("negativeKeywords", "CASH, LOW RATING, SHARED"),
                targetAppPackages = obj.optString("targetAppPackages", "com.ubercab.driver"),
                ultraModeEnabled = obj.optBoolean("ultraModeEnabled", false),
                mapZoneEnabled = obj.optBoolean("mapZoneEnabled", false),
                mapZoneLat = obj.optDouble("mapZoneLat", 0.0),
                mapZoneLng = obj.optDouble("mapZoneLng", 0.0),
                mapZoneRadiusKm = obj.optDouble("mapZoneRadiusKm", 2.0)
            )
        }

        fun fromJson(json: String): List<DriverProfile> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val items = mutableListOf<DriverProfile>()
                for (i in 0 until array.length()) {
                    items.add(fromJsonObject(array.getJSONObject(i)))
                }
                items
            } catch (_: Exception) { emptyList() }
        }
    }
}

// Saved work-zone for each driver profile or area.
data class ZoneProfile(
    val id: String = "zone_default",
    val name: String = "Default Zone",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusKm: Double = 2.0,
    val enabled: Boolean = false
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusKm", radiusKm)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: String): List<ZoneProfile> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val items = mutableListOf<ZoneProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    items.add(
                        ZoneProfile(
                            id = obj.optString("id", "zone_$i"),
                            name = obj.optString("name", "Zone $i"),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0),
                            radiusKm = obj.optDouble("radiusKm", 2.0),
                            enabled = obj.optBoolean("enabled", false)
                        )
                    )
                }
                items
            } catch (_: Exception) { emptyList() }
        }
    }
}
