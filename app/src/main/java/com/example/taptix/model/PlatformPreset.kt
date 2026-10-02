package com.example.taptix.model

/**
 * Driver Platform Preset configurations.
 */
enum class PlatformPreset(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val requiresSwipe: Boolean = false
) {
    UBER(
        id = "uber",
        title = "Uber Driver",
        keywords = listOf("ACCEPT", "TAP TO ACCEPT", "RIDE REQUEST", "ACCEPT TRIP", "MATCH"),
        requiresSwipe = true
    ),
    OLA(
        id = "ola",
        title = "Ola Driver",
        keywords = listOf("ACCEPT", "CONFIRM RIDE", "NEW RIDE", "RIDE REQUEST"),
        requiresSwipe = false
    ),
    RAPIDO(
        id = "rapido",
        title = "Rapido Captain",
        keywords = listOf("ACCEPT ORDER", "ACCEPT RIDE", "ACCEPT", "CAPTAIN ACCEPT"),
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
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: UBER
        }
    }
}
