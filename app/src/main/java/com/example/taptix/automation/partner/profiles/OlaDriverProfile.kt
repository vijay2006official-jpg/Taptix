package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for Ola Driver / Partner.
 * Supported packages: com.olacabs.oladriver, com.olacabs.driver, com.olacabs.partner, com.olacabs
 */
class OlaDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "ola"
    override val displayName: String = "Ola Partner"

    override val supportedPackages: List<String> = listOf(
        "com.olacabs.oladriver",
        "com.olacabs.driver",
        "com.olacabs.partner",
        "com.olacabs"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "CONFIRM RIDE", "ACCEPT RIDE", "ACCEPT DUTY",
        "TAKE DUTY", "START TRIP", "CONFIRM BOOKING", "ACCEPT BOOKING"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "CONFIRMED", "RIDE ACCEPTED", "BOOKING ACCEPTED", "START TRIP"
    )

    override val cancellationKeywords: List<String> = listOf(
        "EXPIRED", "CANCELLED", "ORDER TIMED OUT", "LOST", "MISSED"
    )
}
