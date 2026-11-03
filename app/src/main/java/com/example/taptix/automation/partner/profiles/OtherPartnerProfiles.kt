package com.example.taptix.automation.partner.profiles

import com.example.taptix.automation.partner.PartnerProfileStatus

/**
 * UI detection profile for Lyft Driver.
 */
class LyftDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "lyft"
    override val displayName: String = "Lyft Driver"
    override val supportedPackages: List<String> = listOf("me.lyft.driver")

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "TAP TO ACCEPT", "ACCEPT RIDE"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ACCEPTED", "NAVIGATE", "ARRIVING", "PICK UP"
    )

    override val cancellationKeywords: List<String> = listOf(
        "EXPIRED", "MISSED", "CANCELLED"
    )
}

/**
 * UI detection profile for Grab Driver.
 */
class GrabDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "grab"
    override val displayName: String = "Grab Driver"
    override val supportedPackages: List<String> = listOf("com.grabtaxi.driver2")

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "ACCEPT JOB", "TAP TO ACCEPT"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "JOB ACCEPTED", "ACCEPTED", "PICK UP", "NAVIGATE"
    )

    override val cancellationKeywords: List<String> = listOf(
        "JOB CANCELLED", "MISSED", "EXPIRED"
    )
}

/**
 * UI detection profile for DoorDash Driver.
 */
class DoorDashDriverProfile : BasePartnerProfile() {
    override val partnerId: String = "doordash"
    override val displayName: String = "DoorDash Driver"
    override val supportedPackages: List<String> = listOf("com.dd.driver")

    override val acceptKeywords: List<String> = listOf(
        "ACCEPT", "ACCEPT ORDER", "ACCEPT DELIVERY"
    )

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ORDER ACCEPTED", "HEAD TO STORE", "STORE", "PICK UP"
    )

    override val cancellationKeywords: List<String> = listOf(
        "ORDER MISSED", "EXPIRED", "CANCELLED"
    )
}

/**
 * Fallback generic profile for user-added custom target packages.
 */
class GenericPartnerProfile(
    override val supportedPackages: List<String> = emptyList(),
    override val acceptKeywords: List<String> = listOf("ACCEPT", "CONFIRM", "YES", "OK", "TAKE")
) : BasePartnerProfile() {
    override val partnerId: String = "generic"
    override val displayName: String = "Generic Partner App"
    override val status: PartnerProfileStatus = PartnerProfileStatus.FALLBACK_GENERIC

    override val positiveConfirmationKeywords: List<String> = listOf(
        "ACCEPTED", "CONFIRMED", "NAVIGATE", "PICKUP", "START TRIP", "ARRIVING"
    )

    override val cancellationKeywords: List<String> = listOf(
        "EXPIRED", "CANCELLED", "MISSED", "LOST", "FAILED"
    )
}
