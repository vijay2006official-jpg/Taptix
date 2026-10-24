package com.example.taptix.engine

import com.example.taptix.model.MapZone
import com.example.taptix.model.OfferDetails
import com.example.taptix.model.RuleEvaluationResult
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.UnparsedDataFallback
import kotlin.math.*

/**
 * Pure, decoupled offer-matching engine.
 * Separates offer detection, rule evaluation, and UI actions.
 */
object OfferRuleEngine {

    fun evaluate(
        offer: OfferDetails,
        rules: SmartAcceptRules,
        activeZones: List<MapZone> = emptyList(),
        driverLocation: Pair<Double, Double>? = null,
        includeKeywords: List<String> = emptyList(),
        excludeKeywords: List<String> = emptyList(),
        ultraMode: Boolean = false
    ): RuleEvaluationResult {

        if (ultraMode) {
            return RuleEvaluationResult(
                isAccepted = true,
                matchedRules = listOf("Ultra Mode Active"),
                evaluatedFare = offer.fare,
                evaluatedDistanceKm = offer.pickupDistanceKm,
                recognizedDestination = offer.destinationAddress,
                recognizedPickup = offer.pickupAddress
            )
        }

        val matchedRules = mutableListOf<String>()

        // 1. Exclude / Negative Keywords
        if (rules.excludeKeywordsEnabled && excludeKeywords.isNotEmpty()) {
            val upperRaw = offer.rawText.uppercase()
            for (kw in excludeKeywords) {
                val clean = kw.trim().uppercase()
                if (clean.isBlank()) continue
                val isMatched = if (clean.contains(" ")) {
                    upperRaw.contains(clean)
                } else {
                    val words = upperRaw.split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }
                    words.contains(clean)
                }
                if (isMatched) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Blocked by negative keyword: '$clean'",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm,
                        recognizedDestination = offer.destinationAddress,
                        recognizedPickup = offer.pickupAddress
                    )
                }
            }
        }

        // 2. Include / Acceptance Keywords
        if (rules.includeKeywordsEnabled && includeKeywords.isNotEmpty()) {
            val upperRaw = offer.rawText.uppercase()
            var matchedKw: String? = null
            for (kw in includeKeywords) {
                val clean = kw.trim().uppercase()
                if (clean.isBlank()) continue
                val isMatched = if (clean.contains(" ")) {
                    upperRaw.contains(clean)
                } else {
                    val words = upperRaw.split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }
                    words.contains(clean)
                }
                if (isMatched) {
                    matchedKw = clean
                    break
                }
            }

            if (matchedKw != null) {
                matchedRules.add("Keyword: $matchedKw")
            } else {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "No matching acceptance keyword found",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            }
        }

        // 3. Minimum Fare Filter
        if (rules.minFareEnabled && rules.minFareAmount > 0.0) {
            if (offer.fare == null) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Could not parse fare (Minimum required: ₹${rules.minFareAmount})",
                        evaluatedFare = null,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                }
            } else if (offer.fare < rules.minFareAmount) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Fare ₹${offer.fare} is below minimum ₹${rules.minFareAmount}",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else {
                matchedRules.add("Min Fare Met (₹${offer.fare} >= ₹${rules.minFareAmount})")
            }
        }

        // 4. Maximum Fare Filter
        if (rules.maxFareEnabled && rules.maxFareAmount > 0.0) {
            if (offer.fare != null && offer.fare > rules.maxFareAmount) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Fare ₹${offer.fare} exceeds maximum ₹${rules.maxFareAmount}",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else if (offer.fare != null) {
                matchedRules.add("Max Fare Met (₹${offer.fare} <= ₹${rules.maxFareAmount})")
            }
        }

        // 5. Maximum Pickup Distance Filter
        if (rules.maxPickupDistanceEnabled && rules.maxPickupDistanceKm > 0.0) {
            if (offer.pickupDistanceKm == null) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Could not parse pickup distance (Max allowed: ${rules.maxPickupDistanceKm} km)",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = null
                    )
                }
            } else if (offer.pickupDistanceKm > rules.maxPickupDistanceKm) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Pickup distance ${offer.pickupDistanceKm} km exceeds maximum ${rules.maxPickupDistanceKm} km",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else {
                matchedRules.add("Pickup Distance Met (${offer.pickupDistanceKm} km <= ${rules.maxPickupDistanceKm} km)")
            }
        }

        // 6. Destination Keyword Filter
        if (rules.destinationFilterEnabled && rules.destinationKeywords.isNotBlank()) {
            val destKeywords = rules.destinationKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
            val checkText = (offer.destinationAddress + " " + offer.rawText).uppercase()
            val matchedDest = destKeywords.firstOrNull { checkText.contains(it) }

            if (matchedDest != null) {
                matchedRules.add("Destination Area: $matchedDest")
            } else {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Destination does not match preferred areas (${rules.destinationKeywords})",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            }
        }

        // 7. Geographic Work Zones Filter
        if (activeZones.isNotEmpty()) {
            if (driverLocation == null) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Driver location unavailable for geofenced zone validation",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            }

            var insideZone: MapZone? = null
            var minDistance = Double.MAX_VALUE

            for (zone in activeZones) {
                val d = haversineDistanceKm(driverLocation.first, driverLocation.second, zone.lat, zone.lng)
                if (d < minDistance) minDistance = d
                if (d <= zone.radiusKm) {
                    insideZone = zone
                    break
                }
            }

            if (insideZone != null) {
                matchedRules.add("Within Work Zone: ${insideZone.name}")
            } else {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Outside all active work zones (${String.format("%.1f", minDistance)} km away)",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            }
        }

        return RuleEvaluationResult(
            isAccepted = true,
            matchedRules = matchedRules,
            evaluatedFare = offer.fare,
            evaluatedDistanceKm = offer.pickupDistanceKm,
            recognizedDestination = offer.destinationAddress,
            recognizedPickup = offer.pickupAddress
        )
    }

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
}
