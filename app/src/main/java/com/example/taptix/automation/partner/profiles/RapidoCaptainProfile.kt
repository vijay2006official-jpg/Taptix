package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for Rapido Captain.
 * Supported packages: com.rapido.rider, com.rapido.captain, com.rapido.passenger
 */
class RapidoCaptainProfile : BasePartnerProfile() {
    override val partnerId: String = "rapido"
    override val displayName: String = "Rapido Captain"

    override val supportedPackages: List<String> = listOf(
        "com.rapido.rider",
        "com.rapido.captain",
        "com.rapido.passenger"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT ORDER", "ACCEPT RIDE", "ACCEPT", "CAPTAIN ACCEPT",
        "SWIPE TO ACCEPT", "TAKE ORDER", "CONFIRM ORDER", "ACCEPT BOOKING", "ACCEPT DUTY"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ACCEPTED", "START TRIP", "PICKUP", "CALL CUSTOMER", "CUSTOMER DETAILS", "PASSENGER"
    )

    override val cancellationKeywords: List<String> = listOf(
        "EXPIRED", "MISSED", "ORDER TIMED OUT", "CANCELLED", "CANCELED"
    )
}
