package com.example.taptix.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-app profile configuration. Each supported ride-hailing platform
 * can have its own acceptance rules, keywords, and settings.
 */
data class AppProfile(
    val packageName: String,                        // e.g., "com.ubercab.driver"
    val displayName: String = "",                   // e.g., "Uber Driver"
    val enabled: Boolean = true,                    // Whether auto-accept is enabled for this app
    val customKeywords: List<String> = emptyList(), // App-specific accept keywords (empty = use global)
    val negativeKeywords: List<String> = emptyList(), // App-specific reject keywords (empty = use global)
    val minFare: Double? = null,                    // Override global minimum fare (null = use global)
    val maxPickupDistanceKm: Double? = null,        // Override global max pickup distance (null = use global)
    val useGlobalZones: Boolean = true,             // Whether to use global zone settings
    val cooldownMs: Long = 1500L                    // Per-app cooldown between taps
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("packageName", packageName)
        obj.put("displayName", displayName)
        obj.put("enabled", enabled)
        val ck = JSONArray()
        customKeywords.forEach { ck.put(it) }
        obj.put("customKeywords", ck)
        val nk = JSONArray()
        negativeKeywords.forEach { nk.put(it) }
        obj.put("negativeKeywords", nk)
        if (minFare != null) obj.put("minFare", minFare)
        if (maxPickupDistanceKm != null) obj.put("maxPickupDistanceKm", maxPickupDistanceKm)
        obj.put("useGlobalZones", useGlobalZones)
        obj.put("cooldownMs", cooldownMs)
        return obj
    }

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

        fun fromJson(obj: JSONObject): AppProfile {
            val pkg = obj.optString("packageName", "")
            val name = obj.optString("displayName", getDisplayName(pkg))
            val enabled = obj.optBoolean("enabled", true)

            val ckList = mutableListOf<String>()
            val ckArr = obj.optJSONArray("customKeywords")
            if (ckArr != null) {
                for (i in 0 until ckArr.length()) ckList.add(ckArr.optString(i))
            }

            val nkList = mutableListOf<String>()
            val nkArr = obj.optJSONArray("negativeKeywords")
            if (nkArr != null) {
                for (i in 0 until nkArr.length()) nkList.add(nkArr.optString(i))
            }

            val mf = if (obj.has("minFare") && !obj.isNull("minFare")) obj.optDouble("minFare") else null
            val maxDist = if (obj.has("maxPickupDistanceKm") && !obj.isNull("maxPickupDistanceKm")) obj.optDouble("maxPickupDistanceKm") else null

            return AppProfile(
                packageName = pkg,
                displayName = name,
                enabled = enabled,
                customKeywords = ckList,
                negativeKeywords = nkList,
                minFare = mf,
                maxPickupDistanceKm = maxDist,
                useGlobalZones = obj.optBoolean("useGlobalZones", true),
                cooldownMs = obj.optLong("cooldownMs", 1500L)
            )
        }
    }
}
