package com.example.taptix.model

/**
 * Represents current real-time trip phase and details displayed on the Floating Overlay Widget over Navigation apps.
 */
enum class TripPhase(val label: String, val badgeColorHex: String) {
    SEARCHING("SEARCHING FOR TRIPS", "#0088FF"),
    OFFER_RECEIVED("NEW TRIP OFFER!", "#FF9800"),
    EN_ROUTE_PICKUP("PICKING UP PASSENGER", "#4CAF50"),
    TRIP_IN_PROGRESS("TRIP IN PROGRESS", "#9C27B0"),
    COMPLETED("TRIP COMPLETED", "#00BCD4")
}

data class TripStatusInfo(
    val phase: TripPhase = TripPhase.SEARCHING,
    val fare: String = "$18.50",
    val distance: String = "3.8 mi",
    val etaMinutes: Int = 9,
    val pickupAddress: String = "123 Main St, Downtown",
    val dropoffAddress: String = "456 Market St, Financial Dist",
    val passengerRating: String = "4.95 ★",
    val passengerName: String = "Alex M.",
    val platformName: String = "Ride-Hailing App",
    val timestampMs: Long = System.currentTimeMillis()
)
