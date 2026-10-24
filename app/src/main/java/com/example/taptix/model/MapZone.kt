package com.example.taptix.model

import org.json.JSONObject
import java.util.UUID

/**
 * Represents a circular geofenced work zone configured by the driver.
 */
data class MapZone(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Double = 2.0,
    val isEnabled: Boolean = true,
    val isPickupZone: Boolean = true
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("lat", lat)
        obj.put("lng", lng)
        obj.put("radiusKm", radiusKm)
        obj.put("isEnabled", isEnabled)
        obj.put("isPickupZone", isPickupZone)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): MapZone {
            return MapZone(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Zone"),
                lat = obj.optDouble("lat", 0.0),
                lng = obj.optDouble("lng", 0.0),
                radiusKm = obj.optDouble("radiusKm", 2.0),
                isEnabled = obj.optBoolean("isEnabled", true),
                isPickupZone = obj.optBoolean("isPickupZone", true)
            )
        }
    }
}
