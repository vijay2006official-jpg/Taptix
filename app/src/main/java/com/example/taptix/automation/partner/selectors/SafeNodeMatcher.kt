package com.example.taptix.automation.partner.selectors

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility for safe traversal and extraction of accessibility node trees.
 * Ensures zero crashes from null views, stale nodes, or missing boundaries.
 */
object SafeNodeMatcher {

    /**
     * Recursively traverses [rootNode] searching for nodes satisfying [predicate].
     * Traversal is bounded by [maxDepth] to prevent stack overflows on complex trees.
     */
    fun findFirstNode(
        rootNode: AccessibilityNodeInfo?,
        maxDepth: Int = 20,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (rootNode == null || maxDepth <= 0) return null
        if (predicate(rootNode)) return rootNode

        val childCount = rootNode.childCount
        for (i in 0 until childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findFirstNode(child, maxDepth - 1, predicate)
            if (found != null) return found
        }
        return null
    }

    /**
     * Finds all nodes matching [predicate] up to [maxResults].
     */
    fun findAllNodes(
        rootNode: AccessibilityNodeInfo?,
        maxDepth: Int = 20,
        maxResults: Int = 50,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (rootNode == null || maxDepth <= 0) return results

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth <= 0 || results.size >= maxResults) return
            if (predicate(node)) {
                results.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth - 1)
            }
        }

        traverse(rootNode, maxDepth)
        return results
    }

    /**
     * Safely reads text from text or contentDescription without throwing.
     */
    fun extractNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val text = node.text?.toString()?.trim() ?: ""
        if (text.isNotEmpty()) return text
        return node.contentDescription?.toString()?.trim() ?: ""
    }

    /**
     * Validates that the node bounding box is physically valid on screen.
     */
    fun getValidBounds(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return rect
    }
}
