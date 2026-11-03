package com.example.taptix.automation.partner.profiles

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.taptix.automation.partner.DetectionStatus
import com.example.taptix.automation.partner.PartnerDetectionResult
import com.example.taptix.automation.partner.PartnerProfileStatus
import com.example.taptix.automation.partner.PartnerUiProfile
import com.example.taptix.automation.partner.selectors.SafeNodeMatcher

/**
 * Reusable base implementation for partner UI profiles.
 * Provides deterministic node tree searching, keyword matching, and bounds extraction.
 */
abstract class BasePartnerProfile : PartnerUiProfile {

    override val profileVersion: String = "2.4.0"
    override val status: PartnerProfileStatus = PartnerProfileStatus.ACTIVE_SUPPORTED
    override val isSwipeToAccept: Boolean = false

    override fun isBookingScreen(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false
        val allText = extractAllText(rootNode).uppercase()
        return acceptKeywords.any { kw -> allText.contains(kw.uppercase()) }
    }

    override fun findAcceptControl(rootNode: AccessibilityNodeInfo?, packageName: String): PartnerDetectionResult {
        if (rootNode == null) {
            return PartnerDetectionResult.unrecognized(partnerId, packageName, "Root node is null")
        }

        // 1. Search for clickable/actionable node containing accept keywords
        var candidateNode: AccessibilityNodeInfo? = null
        var matchedKeyword: String? = null

        val nodes = SafeNodeMatcher.findAllNodes(rootNode) { node ->
            val text = SafeNodeMatcher.extractNodeText(node).uppercase()
            if (text.isBlank()) return@findAllNodes false

            acceptKeywords.any { kw ->
                val kwUpper = kw.uppercase()
                if (kwUpper.contains(" ")) {
                    text.contains(kwUpper)
                } else {
                    text.split(Regex("[^A-Z0-9]+")).contains(kwUpper)
                }
            }
        }

        for (node in nodes) {
            val text = SafeNodeMatcher.extractNodeText(node)
            // Prioritize clickable node or direct clickable parent
            val target = findClickableAncestorOrSelf(node)
            val bounds = SafeNodeMatcher.getValidBounds(target)
            if (bounds != null) {
                candidateNode = target
                matchedKeyword = text
                break
            }
        }

        if (candidateNode == null) {
            return PartnerDetectionResult(
                status = DetectionStatus.UNRECOGNIZED_LAYOUT,
                partnerId = partnerId,
                packageName = packageName,
                diagnosticMessage = "Booking keywords detected, but no actionable accept control found"
            )
        }

        val finalBounds = Rect()
        candidateNode.getBoundsInScreen(finalBounds)

        val nodeTextUpper = SafeNodeMatcher.extractNodeText(candidateNode).uppercase()
        val isSwipe = isSwipeToAccept ||
                nodeTextUpper.contains("SWIPE") ||
                nodeTextUpper.contains("SLIDE") ||
                (candidateNode.className?.toString() ?: "").contains("SeekBar", ignoreCase = true)

        return PartnerDetectionResult(
            status = DetectionStatus.ACCEPT_CONTROL_FOUND,
            partnerId = partnerId,
            packageName = packageName,
            acceptNode = candidateNode,
            targetBounds = finalBounds,
            isSwipeAction = isSwipe,
            matchedSelectorText = matchedKeyword,
            diagnosticMessage = "Accept control matched via keyword: '$matchedKeyword'"
        )
    }

    protected fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable) return node
        var curr: AccessibilityNodeInfo? = node.parent
        while (curr != null) {
            if (curr.isClickable) return curr
            curr = curr.parent
        }
        return node
    }

    protected fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(n: AccessibilityNodeInfo, depth: Int) {
            if (depth <= 0) return
            val t = SafeNodeMatcher.extractNodeText(n)
            if (t.isNotBlank()) sb.append(t).append(" ")
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                traverse(c, depth - 1)
            }
        }
        traverse(node, 15)
        return sb.toString().trim()
    }
}
