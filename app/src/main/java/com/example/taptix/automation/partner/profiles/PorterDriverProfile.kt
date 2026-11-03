package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for Porter Partner.
 * Supported packages: com.theporter.android.driverapp, in.porter.partner, com.theporter.partner, com.theporter
 */
class PorterDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "porter"
    override val displayName: String = "Porter Partner"

    override val supportedPackages: List<String> = listOf(
        "com.theporter.android.driverapp",
        "in.porter.partner",
        "com.theporter.partner",
        "com.theporter"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT ORDER", "ACCEPT TRIP", "ACCEPT", "TAKE TRIP",
        "NEW TRIP", "ACCEPT BOOKING", "TAKE ORDER"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ORDER ACCEPTED", "START TRIP", "NAVIGATE TO PICKUP", "ORDER DETAILS", "WAYPOINT"
    )

    override val cancellationKeywords: List<String> = listOf(
        "CANCELLED", "ORDER TIMED OUT", "EXPIRED", "MISSED"
    )
}
