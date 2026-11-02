package com.example.taptix.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.example.taptix.model.BookingServiceCategory
import com.example.taptix.model.OfferDetails

/**
 * Parses and normalizes incoming offer data from the accessibility hierarchy.
 * Extracts fare, separated pickup distance, trip distance, and service category.
 */
object OfferParser {

    private val FARE_REGEX = Regex(
        """(?:₹|RS\.?|INR|\$)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val DISTANCE_KM_REGEX = Regex("""([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:KM|KMS)""", RegexOption.IGNORE_CASE)
    private val DISTANCE_METERS_REGEX = Regex("""([0-9]{2,4})\s*(?:M|METERS)""", RegexOption.IGNORE_CASE)

    // Inline contextual distance extractors
    private val INLINE_PICKUP_REGEX = Regex(
        """(?:pickup|pick\s*up|away|from)[:\s]*([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:KM|KMS)""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_TRIP_REGEX = Regex(
        """(?:trip|drop|destination|total|to)[:\s]*([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:KM|KMS)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rootNode: AccessibilityNodeInfo, packageName: String): OfferDetails {
        val collectedText = StringBuilder()
        collectTextRecursive(rootNode, collectedText)
        val raw = collectedText.toString()

        val fare = validateFare(extractFare(raw))
        val pickupDistance = validateDistance(extractPickupDistanceKm(raw) ?: extractDistanceKm(raw))
        val tripDistance = validateDistance(extractTripDistanceKm(raw))
        val category = extractCategory(raw)

        return OfferDetails(
            packageName = packageName,
            rawText = raw,
            fare = fare,
            pickupDistanceKm = pickupDistance,
            tripDistanceKm = tripDistance,
            pickupAddress = extractPickupHint(raw),
            destinationAddress = extractDestinationHint(raw),
            detectedCategory = category
        )
    }

    fun extractFare(text: String): Double? {
        val match = FARE_REGEX.find(text) ?: return null
        if (match.range.first > 0 && text[match.range.first - 1] == '-') return null
        val raw = match.groupValues[1].trim()
        val cleanStr = when {
            raw.contains(",") && raw.contains(".") -> raw.replace(",", "")
            raw.contains(",") -> {
                val parts = raw.split(",")
                if (parts.last().length == 3 || parts.size > 2) {
                    raw.replace(",", "")
                } else {
                    raw.replace(",", ".")
                }
            }
            else -> raw
        }
        return validateFare(cleanStr.toDoubleOrNull())
    }

    /**
     * Primary distance extractor for backward compatibility.
     * Returns first recognized distance in text.
     */
    fun extractDistanceKm(text: String): Double? {
        val kmMatch = DISTANCE_KM_REGEX.find(text)
        if (kmMatch != null) {
            val numStr = kmMatch.groupValues[1].replace(",", ".")
            return numStr.toDoubleOrNull()
        }

        val mMatch = DISTANCE_METERS_REGEX.find(text)
        if (mMatch != null) {
            val meters = mMatch.groupValues[1].toDoubleOrNull() ?: return null
            return meters / 1000.0
        }

        return null
    }

    /**
     * Extracts pickup-specific distance from text using contextual hints.
     */
    fun extractPickupDistanceKm(text: String): Double? {
        val match = INLINE_PICKUP_REGEX.find(text)
        if (match != null) {
            val numStr = match.groupValues[1].replace(",", ".")
            return numStr.toDoubleOrNull()
        }
        return null
    }

    /**
     * Extracts trip / drop-off distance from text using contextual hints.
     */
    fun extractTripDistanceKm(text: String): Double? {
        val match = INLINE_TRIP_REGEX.find(text)
        if (match != null) {
            val numStr = match.groupValues[1].replace(",", ".")
            return numStr.toDoubleOrNull()
        }

        // If no explicit trip prefix found, check if there are 2 distances in text
        val allDistances = findAllDistancesKm(text)
        if (allDistances.size >= 2) {
            // First is typically pickup, second is trip
            return allDistances[1]
        }
        return null
    }

    /**
     * Identifies service category from screen text using multi-tier matching.
     */
    fun extractCategory(text: String): String? {
        val category = BookingServiceCategory.detectCategory(text)
        return category?.id
    }

    fun validateFare(fare: Double?): Double? {
        if (fare == null) return null
        if (fare <= 0.0 || fare.isNaN() || fare.isInfinite()) return null
        if (fare > 100000.0) return null
        return fare
    }

    fun validateDistance(distanceKm: Double?): Double? {
        if (distanceKm == null) return null
        if (distanceKm <= 0.0 || distanceKm.isNaN() || distanceKm.isInfinite()) return null
        if (distanceKm > 500.0) return null
        return distanceKm
    }

    private fun findAllDistancesKm(text: String): List<Double> {
        val results = mutableListOf<Double>()
        val matches = DISTANCE_KM_REGEX.findAll(text)
        for (m in matches) {
            val numStr = m.groupValues[1].replace(",", ".")
            val d = numStr.toDoubleOrNull()
            if (d != null) results.add(d)
        }
        return results
    }

    private fun extractDestinationHint(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val l = lines[i]
            if (l.contains("drop", ignoreCase = true) || l.contains("to", ignoreCase = true) || l.contains("destination", ignoreCase = true)) {
                if (i + 1 < lines.size) return lines[i + 1]
            }
        }
        return ""
    }

    private fun extractPickupHint(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val l = lines[i]
            if (l.contains("pickup", ignoreCase = true) || l.contains("from", ignoreCase = true)) {
                if (i + 1 < lines.size) return lines[i + 1]
            }
        }
        return ""
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            sb.append(text).append("\n")
        }

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) {
            sb.append(desc).append("\n")
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            collectTextRecursive(child, sb)
        }
    }
}
