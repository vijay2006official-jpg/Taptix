package com.example.taptix.model

/**
 * Per-app profile configuration. Each supported ride-hailing platform
 * can have its own acceptance rules, keywords, and settings.
 */
data class AppProfile(
    val packageName: String,                        // e.g., "com.ubercab.driver"
    val displayName: String,                        // e.g., "Uber Driver"
    val enabled: Boolean = true,                    // Whether auto-accept is enabled for this app
    val customKeywords: List<String> = emptyList(), // App-specific accept keywords (empty = use global)
    val negativeKeywords: List<String> = emptyList(), // App-specific reject keywords (empty = use global)
    val minFare: Double? = null,                    // Override global minimum fare (null = use global)
    val maxPickupDistanceKm: Double? = null,        // Override global max pickup distance (null = use global)
    val useGlobalZones: Boolean = true,             // Whether to use global zone settings
    val cooldownMs: Long = 1500L                    // Per-app cooldown between taps
) {
    companion object {
        /** Default profiles for all supported platforms */
        val DEFAULTS: List<AppProfile> = listOf(
            AppProfile("com.ubercab.driver", "Uber Driver"),
            AppProfile("com.olacabs.driver", "Ola Driver"),
            AppProfile("com.olacabs.partner", "Ola Partner"),
            AppProfile("com.rapido.captain", "Rapido Captain"),
            AppProfile("com.rapido.passenger", "Rapido"),
            AppProfile("com.indriver", "inDrive"),
            AppProfile("sinet.startup.inDriver", "inDrive"),
            AppProfile("in.juspay.nammayatripartner", "Namma Yatri"),
            AppProfile("com.theporter.partner", "Porter"),
            AppProfile("me.lyft.driver", "Lyft Driver"),
            AppProfile("com.grabtaxi.driver2", "Grab Driver"),
            AppProfile("com.dd.driver", "Dunzo")
        )

        fun getDisplayName(packageName: String): String {
            return DEFAULTS.firstOrNull { it.packageName == packageName }?.displayName
                ?: packageName.substringAfterLast('.')
        }
    }
}
