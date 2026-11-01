package com.example.taptix.engine

import com.example.taptix.model.MapZone
import com.example.taptix.model.OfferDetails
import com.example.taptix.model.RuleEvaluationResult
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.UnparsedDataFallback
import java.util.Locale
import kotlin.math.*

/**
 * Intelligent Decision Engine for autonomous booking filtering.
 * Evaluates incoming offers against driver-configured fare, distance,
 * service category, and destination constraints with transparent reasoning.
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

        // 1. Exclude / Negative Keywords Filter
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

        // 2. Service Category Filter
        if (rules.categoryFilterEnabled) {
            val category = offer.detectedCategory
            if (category == null) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Could not identify service category",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                } else {
                    matchedRules.add("Service Category: Unparsed (Allowed by fallback)")
                }
            } else {
                val isExcluded = rules.excludedCategories.contains(category) ||
                        (rules.allowedCategories.isNotEmpty() && !rules.allowedCategories.contains(category))

                if (isExcluded) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because service category '$category' is disabled",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                } else {
                    matchedRules.add("Service Category Allowed: $category")
                }
            }
        }

        // 3. Acceptance Keywords Filter
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

        // 4. Minimum Fare Filter
        if (rules.minFareEnabled && rules.minFareAmount > 0.0) {
            if (offer.fare == null || offer.fare <= 0.0) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Could not parse fare (Minimum required: ₹${rules.minFareAmount})",
                        evaluatedFare = null,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                }
            } else if (offer.fare < rules.minFareAmount) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Rejected because fare is too low: Fare ₹${offer.fare} is below minimum ₹${rules.minFareAmount}",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else {
                matchedRules.add("Min Fare Met (₹${offer.fare} >= ₹${rules.minFareAmount})")
            }
        }

        // 5. Maximum Fare Filter (Optional Cap)
        if (rules.maxFareEnabled && rules.maxFareAmount > 0.0) {
            if (offer.fare != null && offer.fare > rules.maxFareAmount) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Rejected because fare exceeds maximum: Fare ₹${offer.fare} exceeds maximum ₹${rules.maxFareAmount}",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else if (offer.fare != null) {
                matchedRules.add("Max Fare Met (₹${offer.fare} <= ₹${rules.maxFareAmount})")
            }
        }

        // 6. Maximum Pickup Distance Filter
        if (rules.maxPickupDistanceEnabled && rules.maxPickupDistanceKm > 0.0) {
            if (offer.pickupDistanceKm == null || offer.pickupDistanceKm <= 0.0) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Could not parse pickup distance (Max allowed: ${rules.maxPickupDistanceKm} km)",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = null
                    )
                }
            } else if (offer.pickupDistanceKm > rules.maxPickupDistanceKm) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Rejected because pickup distance is too high: Pickup distance ${offer.pickupDistanceKm} km exceeds maximum ${rules.maxPickupDistanceKm} km",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else {
                matchedRules.add("Pickup Distance Met (${offer.pickupDistanceKm} km <= ${rules.maxPickupDistanceKm} km)")
            }
        }

        // 7. Maximum Trip Distance Filter
        if (rules.maxTripDistanceEnabled && rules.maxTripDistanceKm > 0.0) {
            if (offer.tripDistanceKm == null || offer.tripDistanceKm <= 0.0) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Could not parse trip distance (Max allowed: ${rules.maxTripDistanceKm} km)",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                }
            } else if (offer.tripDistanceKm > rules.maxTripDistanceKm) {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Rejected because trip distance is too high: Trip distance ${offer.tripDistanceKm} km exceeds maximum ${rules.maxTripDistanceKm} km",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            } else {
                matchedRules.add("Trip Distance Met (${offer.tripDistanceKm} km <= ${rules.maxTripDistanceKm} km)")
            }
        }

        // 8. Minimum Fare Per Kilometre Filter
        if (rules.minFarePerKmEnabled && rules.minFarePerKmAmount > 0.0) {
            val tripDist = offer.tripDistanceKm
            val fare = offer.fare

            if (tripDist == null || tripDist <= 0.0) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Trip distance unavailable for fare/km calculation (Minimum required: ₹${rules.minFarePerKmAmount}/km)",
                        evaluatedFare = fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                } else {
                    matchedRules.add("Fare/km Skipped (Trip distance unavailable)")
                }
            } else if (fare == null || fare <= 0.0) {
                if (rules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because booking information is incomplete: Fare unavailable for fare/km calculation",
                        evaluatedFare = null,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                }
            } else {
                val farePerKm = fare / tripDist
                if (farePerKm < rules.minFarePerKmAmount) {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Rejected because fare per km is too low (₹${String.format(Locale.US, "%.2f", farePerKm)}/km < ₹${String.format(Locale.US, "%.2f", rules.minFarePerKmAmount)}/km for ${tripDist} km)",
                        evaluatedFare = fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                } else {
                    matchedRules.add("Fare/km Met (₹${String.format(Locale.US, "%.2f", farePerKm)}/km >= ₹${String.format(Locale.US, "%.2f", rules.minFarePerKmAmount)}/km)")
                }
            }
        }

        // 9. Destination Keyword Filter
        if (rules.destinationFilterEnabled && rules.destinationKeywords.isNotBlank()) {
            val destKeywords = rules.destinationKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
            val checkText = (offer.destinationAddress + " " + offer.rawText).uppercase()
            val matchedDest = destKeywords.firstOrNull { checkText.contains(it) }

            if (matchedDest != null) {
                matchedRules.add("Destination Area: $matchedDest")
            } else {
                return RuleEvaluationResult(
                    isAccepted = false,
                    rejectedReason = "Rejected because destination does not match preferred areas (${rules.destinationKeywords})",
                    evaluatedFare = offer.fare,
                    evaluatedDistanceKm = offer.pickupDistanceKm
                )
            }
        }

        // 10. Geographic Work Zones Filter
        if (activeZones.isNotEmpty()) {
            if (driverLocation == null) {
                matchedRules.add("Work Zone Active (GPS resolving, offer allowed)")
            } else {
                val geofenceStatus = GeofenceEngine.evaluateDriverPosition(
                    lat = driverLocation.first,
                    lng = driverLocation.second,
                    accuracyMeters = 25f,
                    activeZones = activeZones
                )

                if (geofenceStatus.isInsideAnyZone && geofenceStatus.matchedZone != null) {
                    matchedRules.add("Within Work Zone: ${geofenceStatus.matchedZone.name}")
                } else {
                    return RuleEvaluationResult(
                        isAccepted = false,
                        rejectedReason = "Outside all active work zones (${String.format(Locale.US, "%.1f", geofenceStatus.nearestZoneDistanceKm)} km away)",
                        evaluatedFare = offer.fare,
                        evaluatedDistanceKm = offer.pickupDistanceKm
                    )
                }
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
        return GeofenceEngine.haversineDistanceKm(lat1, lon1, lat2, lon2)
    }
}
