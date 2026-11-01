package com.example.taptix.engine

import android.view.accessibility.AccessibilityNodeInfo
import com.example.taptix.model.BookingServiceCategory
import com.example.taptix.model.OfferDetails

/**
 * Parses and normalizes incoming offer data from the accessibility hierarchy.
 * Extracts fare, separated pickup distance, trip distance, and service category.
 */
object OfferParser {

    private val FARE_REGEX = Regex("""(?:₹|RS\.?|INR|\$)\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
        val numStr = match.groupValues[1].replace(",", ".")
        return numStr.toDoubleOrNull()
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
     * Extracts pickup distance (distance from driver to rider).
     */
    fun extractPickupDistanceKm(text: String): Double? {
        // 1. Check direct inline pattern
        val inlineMatch = INLINE_PICKUP_REGEX.find(text)
        if (inlineMatch != null) {
            return inlineMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        // 2. Line-by-line inspection
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val upper = line.uppercase()
            if (upper.contains("PICKUP") || upper.contains("PICK UP") || upper.contains("AWAY") || upper.contains("FROM:")) {
                val d = extractDistanceKm(line)
                if (d != null) return d
            }
        }

        // 3. If multiple distances exist without explicit keywords, first is assumed pickup
        val allDistances = findAllDistancesKm(text)
        if (allDistances.size >= 2) {
            return allDistances[0]
        }

        return null
    }

    /**
     * Extracts trip / destination distance (distance from pickup to drop).
     */
    fun extractTripDistanceKm(text: String): Double? {
        // 1. Check direct inline pattern
        val inlineMatch = INLINE_TRIP_REGEX.find(text)
        if (inlineMatch != null) {
            return inlineMatch.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        // 2. Line-by-line inspection
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val upper = line.uppercase()
            if (upper.contains("TRIP") || upper.contains("DROP") || upper.contains("DESTINATION") || upper.contains("TOTAL") || upper.contains("TO:")) {
                val d = extractDistanceKm(line)
                if (d != null) return d
            }
        }

        // 3. If multiple distances exist without explicit keywords, second is assumed trip
        val allDistances = findAllDistancesKm(text)
        if (allDistances.size >= 2) {
            return allDistances[1]
        }

        return null
    }

    fun extractCategory(text: String): String? {
        return BookingServiceCategory.detectCategory(text)?.id
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
        val list = mutableListOf<Double>()
        DISTANCE_KM_REGEX.findAll(text).forEach { match ->
            match.groupValues[1].replace(",", ".").toDoubleOrNull()?.let { list.add(it) }
        }
        return list
    }

    private fun extractDestinationHint(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (i in lines.indices) {
            val l = lines[i].uppercase()
            if (l.contains("DROP") || l.contains("DESTINATION") || l.contains("TO:")) {
                if (i + 1 < lines.size) return lines[i + 1]
            }
        }
        return ""
    }

    private fun extractPickupHint(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (i in lines.indices) {
            val l = lines[i].uppercase()
            if (l.contains("PICKUP") || l.contains("FROM:")) {
                if (i + 1 < lines.size) return lines[i + 1]
            }
        }
        return ""
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val t = node.text?.toString()
        val d = node.contentDescription?.toString()
        if (!t.isNullOrBlank()) sb.append(t).append("\n")
        if (!d.isNullOrBlank() && d != t) sb.append(d).append("\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextRecursive(child, sb)
            try {
                // child.recycle() handled by framework in modern SDK
            } catch (_: Exception) {}
        }
    }
}
