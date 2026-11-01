package com.example.taptix.model

import org.json.JSONArray
import org.json.JSONObject

enum class UnparsedDataFallback {
    ACCEPT,
    REJECT
}

/**
 * Structured rules model for the autonomous offer-matching and filtering engine.
 */
data class SmartAcceptRules(
    val minFareEnabled: Boolean = false,
    val minFareAmount: Double = 0.0,
    val maxFareEnabled: Boolean = false,
    val maxFareAmount: Double = 0.0,
    val minFarePerKmEnabled: Boolean = false,
    val minFarePerKmAmount: Double = 0.0,
    val maxPickupDistanceEnabled: Boolean = false,
    val maxPickupDistanceKm: Double = 5.0,
    val maxTripDistanceEnabled: Boolean = false,
    val maxTripDistanceKm: Double = 25.0,
    val destinationFilterEnabled: Boolean = false,
    val destinationKeywords: String = "",
    val includeKeywordsEnabled: Boolean = true,
    val excludeKeywordsEnabled: Boolean = true,
    val categoryFilterEnabled: Boolean = false,
    val allowedCategories: Set<String> = BookingServiceCategory.ALL_IDS,
    val excludedCategories: Set<String> = emptySet(),
    val fallbackOnUnparsedData: UnparsedDataFallback = UnparsedDataFallback.REJECT
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("minFareEnabled", minFareEnabled)
        obj.put("minFareAmount", minFareAmount)
        obj.put("maxFareEnabled", maxFareEnabled)
        obj.put("maxFareAmount", maxFareAmount)
        obj.put("minFarePerKmEnabled", minFarePerKmEnabled)
        obj.put("minFarePerKmAmount", minFarePerKmAmount)
        obj.put("maxPickupDistanceEnabled", maxPickupDistanceEnabled)
        obj.put("maxPickupDistanceKm", maxPickupDistanceKm)
        obj.put("maxTripDistanceEnabled", maxTripDistanceEnabled)
        obj.put("maxTripDistanceKm", maxTripDistanceKm)
        obj.put("destinationFilterEnabled", destinationFilterEnabled)
        obj.put("destinationKeywords", destinationKeywords)
        obj.put("includeKeywordsEnabled", includeKeywordsEnabled)
        obj.put("excludeKeywordsEnabled", excludeKeywordsEnabled)
        obj.put("categoryFilterEnabled", categoryFilterEnabled)

        val allowedArr = JSONArray()
        allowedCategories.forEach { allowedArr.put(it) }
        obj.put("allowedCategories", allowedArr)

        val excludedArr = JSONArray()
        excludedCategories.forEach { excludedArr.put(it) }
        obj.put("excludedCategories", excludedArr)

        obj.put("fallbackOnUnparsedData", fallbackOnUnparsedData.name)
        return obj
    }

    companion object {
        fun defaultRules(): SmartAcceptRules {
            return SmartAcceptRules(
                minFareEnabled = true,
                minFareAmount = 150.0,
                maxFareEnabled = false,
                maxFareAmount = 0.0,
                minFarePerKmEnabled = false,
                minFarePerKmAmount = 18.0,
                maxPickupDistanceEnabled = true,
                maxPickupDistanceKm = 4.0,
                maxTripDistanceEnabled = false,
                maxTripDistanceKm = 30.0,
                categoryFilterEnabled = false,
                allowedCategories = BookingServiceCategory.ALL_IDS,
                excludedCategories = emptySet(),
                fallbackOnUnparsedData = UnparsedDataFallback.REJECT
            )
        }

        fun fromJson(obj: JSONObject): SmartAcceptRules {
            val allowedSet = mutableSetOf<String>()
            val allowedArr = obj.optJSONArray("allowedCategories")
            if (allowedArr != null && allowedArr.length() > 0) {
                for (i in 0 until allowedArr.length()) {
                    allowedSet.add(allowedArr.optString(i))
                }
            } else {
                allowedSet.addAll(BookingServiceCategory.ALL_IDS)
            }

            val excludedSet = mutableSetOf<String>()
            val excludedArr = obj.optJSONArray("excludedCategories")
            if (excludedArr != null) {
                for (i in 0 until excludedArr.length()) {
                    excludedSet.add(excludedArr.optString(i))
                }
            }

            return SmartAcceptRules(
                minFareEnabled = obj.optBoolean("minFareEnabled", false),
                minFareAmount = obj.optDouble("minFareAmount", 0.0),
                maxFareEnabled = obj.optBoolean("maxFareEnabled", false),
                maxFareAmount = obj.optDouble("maxFareAmount", 0.0),
                minFarePerKmEnabled = obj.optBoolean("minFarePerKmEnabled", false),
                minFarePerKmAmount = obj.optDouble("minFarePerKmAmount", 0.0),
                maxPickupDistanceEnabled = obj.optBoolean("maxPickupDistanceEnabled", false),
                maxPickupDistanceKm = obj.optDouble("maxPickupDistanceKm", 5.0),
                maxTripDistanceEnabled = obj.optBoolean("maxTripDistanceEnabled", false),
                maxTripDistanceKm = obj.optDouble("maxTripDistanceKm", 25.0),
                destinationFilterEnabled = obj.optBoolean("destinationFilterEnabled", false),
                destinationKeywords = obj.optString("destinationKeywords", ""),
                includeKeywordsEnabled = obj.optBoolean("includeKeywordsEnabled", true),
                excludeKeywordsEnabled = obj.optBoolean("excludeKeywordsEnabled", true),
                categoryFilterEnabled = obj.optBoolean("categoryFilterEnabled", false),
                allowedCategories = allowedSet,
                excludedCategories = excludedSet,
                fallbackOnUnparsedData = try {
                    UnparsedDataFallback.valueOf(obj.optString("fallbackOnUnparsedData", UnparsedDataFallback.REJECT.name))
                } catch (_: Exception) {
                    UnparsedDataFallback.REJECT
                }
            )
        }
    }
}
