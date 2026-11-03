package com.example.taptix.automation.partner

/**
 * Lifecycle compatibility and verification status of a Partner UI Profile.
 */
enum class PartnerProfileStatus {
    /**
     * Fully supported and verified against commercial partner layouts.
     */
    ACTIVE_SUPPORTED,

    /**
     * Community or experimental partner layout support.
     */
    EXPERIMENTAL,

    /**
     * Verified by synthetic or community test harness.
     */
    COMMUNITY_VERIFIED,

    /**
     * Deprecated layout selector profile.
     */
    DEPRECATED,

    /**
     * Fallback generic profile for user-added custom target packages.
     */
    FALLBACK_GENERIC
}
