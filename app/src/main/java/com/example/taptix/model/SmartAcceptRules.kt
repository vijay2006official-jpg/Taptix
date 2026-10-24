package com.example.taptix.model

import org.json.JSONObject

enum class UnparsedDataFallback {
    ACCEPT,
    REJECT
}

/**
 * Structured rules model for the autonomous offer-matching engine.
 */
data class SmartAcceptRules(
    val minFareEnabled: Boolean = false,
    val minFareAmount: Double = 0.0,
    val maxFareEnabled: Boolean = false,
    val maxFareAmount: Double = 0.0,
    val maxPickupDistanceEnabled: Boolean = false,
    val maxPickupDistanceKm: Double = 5.0,
    val destinationFilterEnabled: Boolean = false,
    val destinationKeywords: String = "",
    val includeKeywordsEnabled: Boolean = true,
    val excludeKeywordsEnabled: Boolean = true,
    val fallbackOnUnparsedData: UnparsedDataFallback = UnparsedDataFallback.REJECT
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("minFareEnabled", minFareEnabled)
        obj.put("minFareAmount", minFareAmount)
        obj.put("maxFareEnabled", maxFareEnabled)
        obj.put("maxFareAmount", maxFareAmount)
        obj.put("maxPickupDistanceEnabled", maxPickupDistanceEnabled)
        obj.put("maxPickupDistanceKm", maxPickupDistanceKm)
        obj.put("destinationFilterEnabled", destinationFilterEnabled)
        obj.put("destinationKeywords", destinationKeywords)
        obj.put("includeKeywordsEnabled", includeKeywordsEnabled)
        obj.put("excludeKeywordsEnabled", excludeKeywordsEnabled)
        obj.put("fallbackOnUnparsedData", fallbackOnUnparsedData.name)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): SmartAcceptRules {
            return SmartAcceptRules(
                minFareEnabled = obj.optBoolean("minFareEnabled", false),
                minFareAmount = obj.optDouble("minFareAmount", 0.0),
                maxFareEnabled = obj.optBoolean("maxFareEnabled", false),
                maxFareAmount = obj.optDouble("maxFareAmount", 0.0),
                maxPickupDistanceEnabled = obj.optBoolean("maxPickupDistanceEnabled", false),
                maxPickupDistanceKm = obj.optDouble("maxPickupDistanceKm", 5.0),
                destinationFilterEnabled = obj.optBoolean("destinationFilterEnabled", false),
                destinationKeywords = obj.optString("destinationKeywords", ""),
                includeKeywordsEnabled = obj.optBoolean("includeKeywordsEnabled", true),
                excludeKeywordsEnabled = obj.optBoolean("excludeKeywordsEnabled", true),
                fallbackOnUnparsedData = try {
                    UnparsedDataFallback.valueOf(obj.optString("fallbackOnUnparsedData", UnparsedDataFallback.REJECT.name))
                } catch (e: Exception) {
                    UnparsedDataFallback.REJECT
                }
            )
        }
    }
}
