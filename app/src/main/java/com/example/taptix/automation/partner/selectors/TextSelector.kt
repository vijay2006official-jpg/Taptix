package com.example.taptix.automation.partner.selectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Matches an accessibility node by its visible text.
 */
data class TextSelector(
    val expectedText: String,
    val ignoreCase: Boolean = true,
    val exactMatch: Boolean = false,
    val requireClickable: Boolean = false
) {
    fun matches(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (requireClickable && !node.isClickable) return false
        val text = node.text?.toString()?.trim() ?: return false
        if (text.isEmpty()) return false

        return if (exactMatch) {
            text.equals(expectedText, ignoreCase = ignoreCase)
        } else {
            text.contains(expectedText, ignoreCase = ignoreCase)
        }
    }
}
