package com.example.taptix.automation.partner.selectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Matches an accessibility node by its contentDescription.
 */
data class ContentDescriptionSelector(
    val expectedDescription: String,
    val ignoreCase: Boolean = true,
    val exactMatch: Boolean = false,
    val requireClickable: Boolean = false
) {
    fun matches(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (requireClickable && !node.isClickable) return false
        val desc = node.contentDescription?.toString()?.trim() ?: return false
        if (desc.isEmpty()) return false

        return if (exactMatch) {
            desc.equals(expectedDescription, ignoreCase = ignoreCase)
        } else {
            desc.contains(expectedDescription, ignoreCase = ignoreCase)
        }
    }
}
