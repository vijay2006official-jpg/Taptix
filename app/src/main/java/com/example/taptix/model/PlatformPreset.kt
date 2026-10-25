package com.example.taptix.model

/**
 * Driver Platform Preset configurations.
 * Strictly contains actionable accept keywords.
 * Excludes generic categories like "AUTO", "BIKE", "GO" to eliminate false triggers.
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
            "MATCH", "CAPTAIN ACCEPT", "TAKE RIDE", "START DUTY", "TAKE DUTY",
            "ACCEPT DELIVERY", "ACCEPT PARCEL", "ORDER REQUEST", "RIDE REQUEST", "NEW RIDE"
        ),
        requiresSwipe = false
    ),
    UBER(
        id = "uber",
        title = "Uber Driver",
        keywords = listOf(
            "ACCEPT", "TAP TO ACCEPT", "RIDE REQUEST", "ACCEPT TRIP", "MATCH", "ACCEPT OFFER",
            "ACCEPT DELIVERY", "DELIVERY REQUEST"
        ),
        requiresSwipe = false
    ),
    OLA(
        id = "ola",
        title = "Ola Partner",
        keywords = listOf(
            "ACCEPT", "CONFIRM RIDE", "NEW RIDE", "RIDE REQUEST", "ACCEPT RIDE",
            "ACCEPT DUTY", "TAKE DUTY", "START TRIP"
        ),
        requiresSwipe = false
    ),
    RAPIDO(
        id = "rapido",
        title = "Rapido Captain",
        keywords = listOf(
            "ACCEPT ORDER", "ACCEPT RIDE", "ACCEPT", "CAPTAIN ACCEPT", "SWIPE TO ACCEPT",
            "NEW ORDER", "TAKE ORDER", "CONFIRM ORDER"
        ),
        requiresSwipe = false
    ),
    INDRIVER(
        id = "indriver",
        title = "inDrive",
        keywords = listOf("ACCEPT", "OFFER", "MAKE OFFER", "ACCEPT OFFER", "RAISE FARE"),
        requiresSwipe = false
    ),
    PORTER(
        id = "porter",
        title = "Porter Partner",
        keywords = listOf("ACCEPT ORDER", "ACCEPT TRIP", "ACCEPT", "TAKE TRIP", "NEW TRIP"),
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
        keywords = listOf("ACCEPT", "CONFIRM", "YES", "OK", "TAKE"),
        requiresSwipe = false
    );

    companion object {
        fun fromId(id: String): PlatformPreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: UNIVERSAL
        }
    }
}
