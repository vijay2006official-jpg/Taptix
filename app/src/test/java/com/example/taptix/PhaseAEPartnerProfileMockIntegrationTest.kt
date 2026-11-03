package com.example.taptix

import com.example.taptix.automation.partner.DetectionStatus
import com.example.taptix.automation.partner.PartnerProfileRegistry
import com.example.taptix.engine.AcceptOutcome
import com.example.taptix.engine.AcceptVerifier
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase AE Partner Profile Integration Tests.
 *
 * Validates all 9 registered commercial partner UI profiles:
 * - Package resolution & identification.
 * - Accept & cancellation keyword validity.
 * - Strict exclusion of ambiguous navigation words.
 * - Graceful handling of malformed / null node trees.
 */
class PhaseAEPartnerProfileMockIntegrationTest {

    private val allProfiles = PartnerProfileRegistry.getAllProfiles()

    @Test
    fun test01_AllNineProfilesRegisteredAndUnique() {
        assertEquals("Exactly 9 commercial partner profiles must be registered", 9, allProfiles.size)

        val partnerIds = allProfiles.map { it.partnerId.lowercase() }
        assertEquals("All partnerIds must be unique", partnerIds.size, partnerIds.distinct().size)

        val expectedIds = listOf(
            "uber",
            "ola",
            "rapido",
            "indriver",
            "nammayatri",
            "porter",
            "lyft",
            "grab",
            "doordash"
        )
        for (expected in expectedIds) {
            assertTrue("Expected partner profile '$expected' must be registered", partnerIds.contains(expected))
        }
    }

    @Test
    fun test02_PackageIdentificationAcrossAllProfiles() {
        for (profile in allProfiles) {
            assertTrue("Profile '${profile.displayName}' must define supportedPackages", profile.supportedPackages.isNotEmpty())
            for (pkg in profile.supportedPackages) {
                assertTrue("Package '$pkg' must be marked supported in registry", PartnerProfileRegistry.isSupportedPackage(pkg))
                val resolved = PartnerProfileRegistry.getProfileForPackage(pkg)
                assertNotNull("Package '$pkg' must resolve to a profile", resolved)
                assertEquals("Resolved profile must match", profile.partnerId, resolved!!.partnerId)
            }
        }
    }

    @Test
    fun test03_AcceptKeywordsDefinedAndNonEmpty() {
        for (profile in allProfiles) {
            assertTrue("Profile '${profile.displayName}' must have acceptKeywords", profile.acceptKeywords.isNotEmpty())
            for (kw in profile.acceptKeywords) {
                assertTrue("Keyword '$kw' must not be blank", kw.isNotBlank())
            }
        }
    }

    @Test
    fun test04_PositiveConfirmationKeywordsStrictlyExcludeAmbiguousWords() {
        val prohibitedNavWords = listOf(
            "NAVIGATE", "NAVIGATION", "ARRIVING", "HEAD TO", "ON THE WAY",
            "PASSENGER", "CUSTOMER", "CHAT", "ORDER DETAILS"
        )

        for (profile in allProfiles) {
            for (prohibited in prohibitedNavWords) {
                assertFalse(
                    "Profile '${profile.displayName}' positive confirmation must NOT contain '$prohibited'",
                    profile.positiveConfirmationKeywords.any { it.equals(prohibited, ignoreCase = true) }
                )
            }
        }
    }

    @Test
    fun test05_CancellationAndDeclineKeywordsDefined() {
        for (profile in allProfiles) {
            assertTrue("Profile '${profile.displayName}' must define cancellationKeywords", profile.cancellationKeywords.isNotEmpty())
            for (kw in profile.cancellationKeywords) {
                assertTrue("Cancellation keyword must not be blank", kw.isNotBlank())
            }
        }
    }

    @Test
    fun test06_NullAndMalformedLayoutResilience() {
        for (profile in allProfiles) {
            val detection = profile.findAcceptControl(null, profile.supportedPackages.first())
            assertEquals(
                "Null node layout must produce UNRECOGNIZED_LAYOUT for '${profile.displayName}'",
                DetectionStatus.UNRECOGNIZED_LAYOUT,
                detection.status
            )
            assertNull("Matched acceptNode must be null", detection.acceptNode)
            assertFalse("Result must not be actionable", detection.isActionable)
        }
    }

    @Test
    fun test07_AcceptanceVerifierRejectsButtonDisappearanceAlone() {
        // Button disappeared, but no explicit confirmation text -> NOT_CONFIRMED (never CONFIRMED)
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals(
            "Button disappearance alone must evaluate to NOT_CONFIRMED, never CONFIRMED",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
        assertNotEquals(AcceptOutcome.CONFIRMED, outcome)

        // Null visibility evaluates to UNKNOWN
        val unknownOutcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = null,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals(
            "Unreadable/null screen must evaluate to UNKNOWN",
            AcceptOutcome.UNKNOWN,
            unknownOutcome
        )
    }

    @Test
    fun test08_AcceptanceVerifierConfirmsOnlyOnExplicitPositiveEvidence() {
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = true,
            negativeOrCancelledPresent = false
        )
        assertEquals(
            "Explicit positive confirmation evidence confirms acceptance",
            AcceptOutcome.CONFIRMED,
            outcome
        )
    }

    @Test
    fun test09_AcceptanceVerifierHandlesCancelledOrExpired() {
        val outcome = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = true
        )
        assertEquals(
            "Cancelled or expired screens evaluate to NOT_CONFIRMED",
            AcceptOutcome.NOT_CONFIRMED,
            outcome
        )
    }
}
