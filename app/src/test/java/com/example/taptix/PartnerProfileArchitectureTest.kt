package com.example.taptix

import com.example.taptix.automation.partner.*
import com.example.taptix.automation.partner.profiles.*
import com.example.taptix.engine.PartnerPackages
import com.example.taptix.engine.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Exhaustive unit tests for Phase AB: Dynamic Partner UI Profile Architecture.
 */
class PartnerProfileArchitectureTest {

    @Test
    fun testAllSupportedPackagesMapToAProfile() {
        val missing = PartnerProfileRegistry.getMissingSupportedPackages()
        assertTrue(
            "Every package in PartnerPackages.SUPPORTED must map to a PartnerUiProfile. Missing: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun testUberDriverProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("com.ubercab.driver")
        assertNotNull(profile)
        assertTrue(profile is UberDriverProfile)
        assertEquals("uber", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("ACCEPT"))
        assertTrue(profile.positiveConfirmationKeywords.contains("ACCEPTED"))
        assertTrue(profile.cancellationKeywords.contains("EXPIRED"))
    }

    @Test
    fun testOlaDriverProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("com.olacabs.oladriver")
        assertNotNull(profile)
        assertTrue(profile is OlaDriverProfile)
        assertEquals("ola", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("CONFIRM RIDE"))
        assertTrue(profile.positiveConfirmationKeywords.contains("CONFIRMED"))
    }

    @Test
    fun testRapidoCaptainProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("com.rapido.captain")
        assertNotNull(profile)
        assertTrue(profile is RapidoCaptainProfile)
        assertEquals("rapido", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("SWIPE TO ACCEPT"))
        assertTrue(profile.positiveConfirmationKeywords.contains("START TRIP"))
    }

    @Test
    fun testInDriveProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("sinet.startup.indriver")
        assertNotNull(profile)
        assertTrue(profile is InDriveProfile)
        assertEquals("indriver", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("MAKE OFFER"))
        assertTrue(profile.positiveConfirmationKeywords.contains("RIDE ACCEPTED"))
    }

    @Test
    fun testNammaYatriProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("in.juspay.nammayatripartner")
        assertNotNull(profile)
        assertTrue(profile is NammaYatriProfile)
        assertEquals("nammayatri", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("NEW RIDE"))
        assertTrue(profile.positiveConfirmationKeywords.contains("BOOKING ACCEPTED"))
    }

    @Test
    fun testPorterDriverProfile_ResolutionAndKeywords() {
        val profile = PartnerProfileRegistry.getProfileForPackage("com.theporter.android.driverapp")
        assertNotNull(profile)
        assertTrue(profile is PorterDriverProfile)
        assertEquals("porter", profile?.partnerId)
        assertTrue(profile!!.acceptKeywords.contains("ACCEPT ORDER"))
        assertTrue(profile.positiveConfirmationKeywords.contains("ORDER ACCEPTED"))
    }

    @Test
    fun testOtherPartnerProfiles_LyftGrabDoorDash() {
        val lyft = PartnerProfileRegistry.getProfileForPackage("me.lyft.driver")
        assertNotNull(lyft)
        assertTrue(lyft is LyftDriverProfile)

        val grab = PartnerProfileRegistry.getProfileForPackage("com.grabtaxi.driver2")
        assertNotNull(grab)
        assertTrue(grab is GrabDriverProfile)

        val dd = PartnerProfileRegistry.getProfileForPackage("com.dd.driver")
        assertNotNull(dd)
        assertTrue(dd is DoorDashDriverProfile)
    }

    @Test
    fun testUnknownPackage_ReturnsNullAndUnsupportedResult() {
        val profile = PartnerProfileRegistry.getProfileForPackage("com.unknown.rideshare")
        assertNull("Unknown package must not match any profile", profile)

        val result = PartnerProfileResolver.detectBooking(
            rootNode = null,
            packageName = "com.unknown.rideshare"
        )
        assertEquals(DetectionStatus.UNSUPPORTED_PARTNER, result.status)
        assertFalse(result.isActionable)
    }

    @Test
    fun testCustomTargetPackage_ResolvesToGenericProfile() {
        val customPkg = "com.custom.delivery.app"
        val profile = PartnerProfileResolver.resolve(
            packageName = customPkg,
            customTargetPackages = listOf(customPkg, "com.another.app")
        )
        assertNotNull(profile)
        assertTrue(profile is GenericPartnerProfile)
        assertEquals("generic", profile?.partnerId)
        assertEquals(PartnerProfileStatus.FALLBACK_GENERIC, profile?.status)
    }

    @Test
    fun testProfileRegistry_DeterministicAndUnique() {
        val allProfiles = PartnerProfileRegistry.getAllProfiles()
        assertTrue("At least 9 distinct partner profiles registered", allProfiles.size >= 9)

        val partnerIds = allProfiles.map { it.partnerId }
        assertEquals("Every profile must have a unique partnerId", partnerIds.distinct().size, partnerIds.size)
    }

    @Test
    fun testAcceptanceConfirmation_ButtonDisappearanceAloneCannotConfirm() {
        // Disappearance alone without positive confirmation MUST yield NOT_CONFIRMED
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals(AcceptOutcome.NOT_CONFIRMED, outcome)
    }

    @Test
    fun testAcceptanceConfirmation_NegativeSignalPreventsPositiveConfirmation() {
        // Even if a positive keyword was matched, negative/cancellation signal MUST take strict priority
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = true,
            negativeOrCancelledPresent = true
        )
        assertEquals(AcceptOutcome.NOT_CONFIRMED, outcome)
    }

    @Test
    fun testAcceptanceConfirmation_AmbiguousKeywordsExcludedFromGlobalDefaults() {
        val positive = AcceptVerifier.POSITIVE_CONFIRMATION_KEYWORDS
        assertFalse("Ambiguous keyword 'CHAT' must not be in positive confirmation list", positive.contains("CHAT"))
        assertFalse("Ambiguous keyword 'PASSENGER' must not be in positive confirmation list", positive.contains("PASSENGER"))
        assertFalse("Ambiguous keyword 'CUSTOMER' must not be in positive confirmation list", positive.contains("CUSTOMER"))
        assertFalse("Ambiguous keyword 'ORDER DETAILS' must not be in positive confirmation list", positive.contains("ORDER DETAILS"))
    }

    @Test
    fun testAcceptanceConfirmation_NullVisibilityYieldsUnknown() {
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = null,
            positiveConfirmationPresent = true,
            negativeOrCancelledPresent = false
        )
        assertEquals(AcceptOutcome.UNKNOWN, outcome)
    }

    @Test
    fun testClickSafety_UnknownClickResultTriggersZeroRetries() {
        // ControlledRetryPolicy must reject retries when outcome is UNKNOWN
        val canRetry = ControlledRetryPolicy.isRetrySafe(
            currentAttempts = 0,
            outcome = AcceptOutcome.UNKNOWN,
            isButtonStillVisible = true,
            isButtonEnabled = true,
            isForegroundTargetApp = true,
            hasNegativeSignal = false,
            hasPositiveConfirmation = false
        )
        assertFalse("UNKNOWN outcome must never trigger speculative retries", canRetry)
    }

    @Test
    fun testClickSafety_BackgroundAppDisablesRetries() {
        val canRetry = ControlledRetryPolicy.isRetrySafe(
            currentAttempts = 0,
            outcome = AcceptOutcome.NOT_CONFIRMED,
            isButtonStillVisible = true,
            isButtonEnabled = true,
            isForegroundTargetApp = false, // App is in background
            hasNegativeSignal = false,
            hasPositiveConfirmation = false
        )
        assertFalse("Background application must never trigger retries", canRetry)
    }

    @Test
    fun testProfileRegistry_DoesNotImplyRealDeviceTested() {
        // Explicitly confirm that registration does not imply the real partner app or current layout has been tested
        val allProfiles = PartnerProfileRegistry.getAllProfiles()
        allProfiles.forEach { profile ->
            assertNotNull(profile.partnerId)
            assertTrue(profile.supportedPackages.isNotEmpty())
            // Status tracks maintenance readiness, not physical hardware verification
            assertNotNull(profile.status)
        }
    }
}
