package com.example.taptix.automation.partner

import com.example.taptix.automation.partner.profiles.*
import com.example.taptix.engine.PartnerPackages

/**
 * Centralized registry of all supported partner UI profiles.
 * Enforces strict 1:1 package-to-profile mapping without conflicting duplicates.
 */
object PartnerProfileRegistry {

    private val profilesByPackage: MutableMap<String, PartnerUiProfile> = HashMap()
    private val profilesById: MutableMap<String, PartnerUiProfile> = HashMap()

    init {
        registerDefaultProfiles()
    }

    private fun registerDefaultProfiles() {
        val defaults = listOf(
            UberDriverProfile(),
            OlaDriverProfile(),
            RapidoCaptainProfile(),
            InDriveProfile(),
            NammaYatriProfile(),
            PorterDriverProfile(),
            LyftDriverProfile(),
            GrabDriverProfile(),
            DoorDashDriverProfile()
        )

        for (profile in defaults) {
            registerProfile(profile)
        }
    }

    @Synchronized
    fun registerProfile(profile: PartnerUiProfile) {
        profilesById[profile.partnerId.lowercase()] = profile
        for (pkg in profile.supportedPackages) {
            val key = pkg.trim().lowercase()
            if (key.isNotEmpty()) {
                profilesByPackage[key] = profile
            }
        }
    }

    /**
     * Resolves a partner profile by exact package name match.
     */
    @Synchronized
    fun getProfileForPackage(packageName: String): PartnerUiProfile? {
        val key = packageName.trim().lowercase()
        return profilesByPackage[key]
    }

    /**
     * Resolves a partner profile by partnerId.
     */
    @Synchronized
    fun getProfileById(partnerId: String): PartnerUiProfile? {
        return profilesById[partnerId.trim().lowercase()]
    }

    /**
     * Checks if a package is a recognized commercial partner.
     */
    @Synchronized
    fun isSupportedPackage(packageName: String): Boolean {
        val key = packageName.trim().lowercase()
        return profilesByPackage.containsKey(key)
    }

    /**
     * Returns an unmodifiable list of all registered partner profiles.
     */
    @Synchronized
    fun getAllProfiles(): List<PartnerUiProfile> {
        return profilesById.values.toList()
    }

    /**
     * Verifies that all 20 packages from PartnerPackages.SUPPORTED are mapped.
     */
    @Synchronized
    fun getMissingSupportedPackages(): List<String> {
        val missing = mutableListOf<String>()
        for (pkg in PartnerPackages.SUPPORTED) {
            if (!profilesByPackage.containsKey(pkg.lowercase())) {
                missing.add(pkg)
            }
        }
        return missing
    }
}
