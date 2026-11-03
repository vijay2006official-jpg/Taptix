package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for Uber Driver.
 * Supported packages: com.ubercab.driver, com.ubercab
 */
class UberDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "uber"
    override val displayName: String = "Uber Driver"

    override val supportedPackages: List<String> = listOf(
        "com.ubercab.driver",
        "com.ubercab"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "TAP TO ACCEPT", "ACCEPT TRIP", "ACCEPT RIDE",
        "ACCEPT OFFER", "ACCEPT DELIVERY", "ACCEPT ORDER"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ACCEPTED", "RIDE ACCEPTED", "TRIP ACCEPTED", "START TRIP"
    )

    override val cancellationKeywords: List<String> = listOf(
        "EXPIRED", "OFFER EXPIRED", "CANCELLED", "CANCELED", "MISSED",
        "TOO LATE", "NO LONGER AVAILABLE", "LOST"
    )
}
