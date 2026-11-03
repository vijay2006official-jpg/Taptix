package com.example.taptix.automation.partner.profiles

/**
 * UI detection profile for Namma Yatri / MovingTech / Yatri Sathi.
 * Supported packages: in.juspay.nammayatripartner, movingtech.nammatrip.driver,
 * movingtech.nammayatri.driver, in.juspay.nammayatri, in.juspay.yatrisathi
 */
class NammaYatriProfile : BasePartnerProfile() {
    override val partnerId: String = "nammayatri"
    override val displayName: String = "Namma Yatri Partner"

    override val supportedPackages: List<String> = listOf(
        "in.juspay.nammayatripartner",
        "movingtech.nammatrip.driver",
        "movingtech.nammayatri.driver",
        "in.juspay.nammayatri",
        "in.juspay.yatrisathi"
    )

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "ACCEPT RIDE", "NEW RIDE", "RIDE OFFER",
        "ACCEPT OFFER", "CONFIRM", "ACCEPT BOOKING"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "BOOKING ACCEPTED", "ACCEPTED BY YOU", "START TRIP"
    )

    override val cancellationKeywords: List<String> = listOf(
        "MISSED", "EXPIRED", "CANCELLED", "LOST"
    )
}
