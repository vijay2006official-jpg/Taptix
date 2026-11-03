package com.example.taptix.automation.partner.selectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Matches an accessibility node by its view resource ID.
 */
data class ResourceIdSelector(
    val resourceId: String,
    val requireClickable: Boolean = false
) {
    fun matches(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (requireClickable && !node.isClickable) return false
        val viewId = node.viewIdResourceName?.trim() ?: return false
        return viewId.contains(resourceId, ignoreCase = true)
    }
}
