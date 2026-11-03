package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for inDrive.
 * Supported packages: sinet.startup.inDriver, com.indriver, indriver
 */
class InDriveProfile : BasePartnerProfile() {
    override val partnerId: String = "indriver"
    override val displayName: String = "inDrive"

    override val supportedPackages: List<String> = listOf(
        "sinet.startup.inDriver",
        "sinet.startup.indriver",
        "com.indriver",
        "indriver"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "MAKE OFFER", "ACCEPT OFFER", "RAISE FARE", "ACCEPT REQUEST"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "RIDE ACCEPTED", "HEAD TO", "CALL PASSENGER", "CHAT", "PASSENGER", "ACCEPTED"
    )

    override val cancellationKeywords: List<String> = listOf(
        "ANOTHER DRIVER", "NO LONGER AVAILABLE", "CANCELLED", "EXPIRED"
    )
}
