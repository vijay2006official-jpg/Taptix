package com.example.taptix.automation.partner

import android.view.accessibility.AccessibilityNodeInfo
import com.example.taptix.automation.partner.profiles.GenericPartnerProfile

/**
 * High-level resolver that coordinates package identification, profile lookup,
 * and structured detection result generation.
 */
object PartnerProfileResolver {

    /**
     * Resolves the appropriate profile for a package name, with optional fallback for custom targets.
     */
    fun resolve(
        packageName: String,
        customTargetPackages: List<String> = emptyList()
    ): PartnerUiProfile? {
        val cleanPkg = packageName.trim().lowercase()
        if (cleanPkg.isBlank()) return null

        // 1. Direct registry lookup
        val directMatch = PartnerProfileRegistry.getProfileForPackage(cleanPkg)
        if (directMatch != null) return directMatch

        // 2. Custom target allowlist match
        val isCustom = customTargetPackages.any { it.trim().equals(cleanPkg, ignoreCase = true) }
        if (isCustom) {
            return GenericPartnerProfile(supportedPackages = listOf(cleanPkg))
        }

        return null
    }

    /**
     * Evaluates an incoming screen node hierarchy against the resolved partner profile.
     */
    fun detectBooking(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        customTargetPackages: List<String> = emptyList()
    ): PartnerDetectionResult {
        if (rootNode == null) {
            return PartnerDetectionResult.unsupported(packageName)
        }

        val profile = resolve(packageName, customTargetPackages)
            ?: return PartnerDetectionResult.unsupported(packageName)

        if (!profile.isBookingScreen(rootNode, packageName)) {
            return PartnerDetectionResult(
                status = DetectionStatus.UNRECOGNIZED_LAYOUT,
                partnerId = profile.partnerId,
                packageName = packageName,
                diagnosticMessage = "Screen does not match booking keywords for '${profile.displayName}'"
            )
        }

        return profile.findAcceptControl(rootNode, packageName)
    }
}
