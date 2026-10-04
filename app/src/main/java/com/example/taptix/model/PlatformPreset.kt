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
        title = "Universal (All Apps)",
        keywords = listOf(
            "ACCEPT", "TAP TO ACCEPT", "ACCEPT TRIP", "ACCEPT RIDE", "ACCEPT ORDER",
            "CONFIRM", "CONFIRM RIDE", "SWIPE TO ACCEPT", "MATCH", "BOOKING",
            "RIDE REQUEST", "NEW RIDE", "CAPTAIN ACCEPT", "GO TO PICKUP", "TAKE RIDE", "OFFER", "GO"
        ),
        requiresSwipe = false
    ),
    UBER(
        id = "uber",
        title = "Uber Driver",
        keywords = listOf("ACCEPT", "TAP TO ACCEPT", "RIDE REQUEST", "ACCEPT TRIP", "MATCH", "ACCEPT OFFER", "GO"),
        requiresSwipe = false
    ),
    OLA(
        id = "ola",
        title = "Ola Driver",
        keywords = listOf("ACCEPT", "CONFIRM RIDE", "NEW RIDE", "RIDE REQUEST", "ACCEPT RIDE", "BOOKING"),
        requiresSwipe = false
    ),
    RAPIDO(
        id = "rapido",
        title = "Rapido Captain",
        keywords = listOf("ACCEPT ORDER", "ACCEPT RIDE", "ACCEPT", "CAPTAIN ACCEPT", "SWIPE TO ACCEPT", "GO TO PICKUP"),
        requiresSwipe = false
    ),
    INDRIVER(
        id = "indriver",
        title = "inDrive",
        keywords = listOf("ACCEPT", "OFFER", "MAKE OFFER", "ACCEPT OFFER"),
        requiresSwipe = false
    ),
    LYFT(
        id = "lyft",
        title = "Lyft Driver",
        keywords = listOf("ACCEPT", "TAP TO ACCEPT", "ACCEPT RIDE"),
        requiresSwipe = false
    ),
    CUSTOM(
        id = "custom",
        title = "Custom App",
        keywords = listOf("ACCEPT", "CONFIRM", "YES", "OK"),
        requiresSwipe = false
    );

    companion object {
        fun fromId(id: String): PlatformPreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: UNIVERSAL
        }
    }
}
