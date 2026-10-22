package com.example.taptix.model

/**
 * Driver Platform Preset configurations.
 * Contains targeted keyword matching rules and gesture behaviors.
 */
enum class PlatformPreset(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val requiresSwipe: Boolean = false
) {
    UNIVERSAL(
        id = "universal",
        title = "Universal (All Duties)",
        keywords = listOf(
            "ACCEPT", "TAP TO ACCEPT", "ACCEPT TRIP", "ACCEPT RIDE", "ACCEPT ORDER",
            "ACCEPT BOOKING", "ACCEPT DUTY", "CONFIRM", "CONFIRM RIDE", "SWIPE TO ACCEPT",
            "MATCH", "BOOKING", "RIDE REQUEST", "NEW RIDE", "CAPTAIN ACCEPT",
            "GO TO PICKUP", "TAKE RIDE", "OFFER", "START DUTY", "TAKE DUTY",
            "NEW DUTY", "OUTSTATION", "RENTAL", "HOURLY", "PACKAGE", "PARCEL",
            "DELIVERY", "ACCEPT DELIVERY", "ACCEPT PARCEL", "ORDER REQUEST"
        ),
        requiresSwipe = false
    ),
    UBER(
        id = "uber",
        title = "Uber Driver",
        keywords = listOf(
            "ACCEPT", "TAP TO ACCEPT", "RIDE REQUEST", "ACCEPT TRIP", "MATCH", "ACCEPT OFFER",
            "UBER GO", "UBER AUTO", "UBER MOTO", "PACKAGE", "CONNECT", "RESERVE",
            "ACCEPT DELIVERY", "DELIVERY REQUEST"
        ),
        requiresSwipe = false
    ),
    OLA(
        id = "ola",
        title = "Ola Partner",
        keywords = listOf(
            "ACCEPT", "CONFIRM RIDE", "NEW RIDE", "RIDE REQUEST", "ACCEPT RIDE",
            "BOOKING", "DUTY", "ACCEPT DUTY", "OUTSTATION", "RENTALS", "RENTAL",
            "MINI", "PRIME", "AUTO", "BIKE", "TAKE DUTY", "START TRIP"
        ),
        requiresSwipe = false
    ),
    RAPIDO(
        id = "rapido",
        title = "Rapido Captain",
        keywords = listOf(
            "ACCEPT ORDER", "ACCEPT RIDE", "ACCEPT", "CAPTAIN ACCEPT", "SWIPE TO ACCEPT",
            "GO TO PICKUP", "PARCEL", "FOOD", "AUTO", "BIKE", "DELIVERY", "RAPIDO AUTO",
            "RAPIDO BIKE", "NEW ORDER", "TAKE ORDER"
        ),
        requiresSwipe = false
    ),
    INDRIVER(
        id = "indriver",
        title = "inDrive",
        keywords = listOf("ACCEPT", "OFFER", "MAKE OFFER", "ACCEPT OFFER", "RAISE FARE", "CITY", "COURIER", "FREIGHT"),
        requiresSwipe = false
    ),
    PORTER(
        id = "porter",
        title = "Porter Partner",
        keywords = listOf("ACCEPT ORDER", "ACCEPT TRIP", "ACCEPT", "TAKE TRIP", "LOAD", "UNLOAD", "NEW TRIP"),
        requiresSwipe = false
    ),
    NAMMAYATRI(
        id = "nammayatri",
        title = "Namma Yatri Partner",
        keywords = listOf("ACCEPT", "ACCEPT RIDE", "NEW RIDE", "RIDE OFFER", "ACCEPT OFFER"),
        requiresSwipe = false
    ),
    CUSTOM(
        id = "custom",
        title = "Custom App",
        keywords = listOf("ACCEPT", "CONFIRM"),
        requiresSwipe = false
    );

    companion object {
        fun fromId(id: String): PlatformPreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: UNIVERSAL
        }
    }
}
