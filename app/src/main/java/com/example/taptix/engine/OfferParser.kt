package com.example.taptix.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.taptix.model.OfferDetails

/**
 * Parses and normalizes incoming offer data from the accessibility hierarchy.
 */
object OfferParser {

    private val FARE_REGEX = Regex("""(?:₹|RS\.?|INR|\$)\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val DISTANCE_KM_REGEX = Regex("""([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:KM|KMS)""", RegexOption.IGNORE_CASE)
    private val DISTANCE_METERS_REGEX = Regex("""([0-9]{2,4})\s*(?:M|METERS)""", RegexOption.IGNORE_CASE)

    fun parse(rootNode: AccessibilityNodeInfo, packageName: String): OfferDetails {
        val collectedText = StringBuilder()
        collectTextRecursive(rootNode, collectedText)
        val raw = collectedText.toString()

        val fare = extractFare(raw)
        val distance = extractDistanceKm(raw)

        return OfferDetails(
            packageName = packageName,
            rawText = raw,
            fare = fare,
            pickupDistanceKm = distance,
            destinationAddress = extractDestinationHint(raw),
            pickupAddress = extractPickupHint(raw)
        )
    }

    fun extractFare(text: String): Double? {
        val match = FARE_REGEX.find(text) ?: return null
        val numStr = match.groupValues[1].replace(",", ".")
        return numStr.toDoubleOrNull()
    }

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
                // child.recycle() is handled by Android framework in newer SDKs
            } catch (_: Exception) {}
        }
    }
}
