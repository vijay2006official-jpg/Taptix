package com.example.taptix.engine

import com.example.taptix.model.MapZone
import java.util.Locale
import kotlin.math.*

enum class GeofenceTransition {
    ENTER,
    EXIT,
    INSIDE,
    OUTSIDE
}

data class GeofenceStatus(
    val isInsideAnyZone: Boolean,
    val matchedZone: MapZone? = null,
    val nearestZoneDistanceKm: Double = Double.MAX_VALUE,
    val transition: GeofenceTransition = GeofenceTransition.OUTSIDE,
    val accuracyMeters: Float = 0f,
    val isAccuracyAcceptable: Boolean = true
)

/**
 * Intelligent Geofencing & Location Engine for Taptix v2.3.
 * Handles boundary evaluation, entry/exit state transitions, accuracy gating,
 * and diagnostic privacy sanitization.
 */
object GeofenceEngine {

    const val MAX_ACCEPTABLE_ACCURACY_METERS = 150f
    const val GEOFENCE_TOLERANCE_RATIO = 1.20 // 20% tolerance for GPS drift / cell tower inaccuracy

    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return haversineDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    fun validateAccuracy(accuracyMeters: Float): Boolean {
        return accuracyMeters in 0.01f..MAX_ACCEPTABLE_ACCURACY_METERS
    }

    fun isLocationInsideZone(
        lat: Double,
        lng: Double,
        zone: MapZone,
        toleranceRatio: Double = GEOFENCE_TOLERANCE_RATIO
    ): Boolean {
        if (!zone.isEnabled) return false
        val distKm = haversineDistanceKm(lat, lng, zone.lat, zone.lng)
        return distKm <= (zone.radiusKm * toleranceRatio)
    }

    fun evaluateDriverPosition(
        lat: Double,
        lng: Double,
        accuracyMeters: Float,
        activeZones: List<MapZone>,
        previousStatus: GeofenceStatus? = null
    ): GeofenceStatus {
        val isAccuracyAcceptable = validateAccuracy(accuracyMeters)

        if (activeZones.isEmpty()) {
            return GeofenceStatus(
                isInsideAnyZone = true,
                matchedZone = null,
                nearestZoneDistanceKm = 0.0,
                transition = GeofenceTransition.INSIDE,
                accuracyMeters = accuracyMeters,
                isAccuracyAcceptable = isAccuracyAcceptable
            )
        }

        var matchedZone: MapZone? = null
        var minDistanceKm = Double.MAX_VALUE

        for (zone in activeZones) {
            val dist = haversineDistanceKm(lat, lng, zone.lat, zone.lng)
            if (dist < minDistanceKm) {
                minDistanceKm = dist
            }
            if (matchedZone == null && dist <= (zone.radiusKm * GEOFENCE_TOLERANCE_RATIO)) {
                matchedZone = zone
            }
        }

        val isInside = matchedZone != null
        val wasInside = previousStatus?.isInsideAnyZone ?: false

        val transition = when {
            !wasInside && isInside -> GeofenceTransition.ENTER
            wasInside && !isInside -> GeofenceTransition.EXIT
            isInside -> GeofenceTransition.INSIDE
            else -> GeofenceTransition.OUTSIDE
        }

        return GeofenceStatus(
            isInsideAnyZone = isInside,
            matchedZone = matchedZone,
            nearestZoneDistanceKm = minDistanceKm,
            transition = transition,
            accuracyMeters = accuracyMeters,
            isAccuracyAcceptable = isAccuracyAcceptable
        )
    }

    /**
     * Sanitizes precise geographic coordinates for diagnostic logs,
     * protecting driver privacy while maintaining support troubleshooting fidelity.
     */
    fun sanitizeLocationForLogs(lat: Double, lng: Double): String {
        return String.format(Locale.US, "%.2f**, %.2f**", lat, lng)
    }
}
