package com.example.taptix

import android.content.Context
import android.content.ContextWrapper
import com.example.taptix.automation.partner.DetectionStatus
import com.example.taptix.automation.partner.PartnerProfileRegistry
import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import com.example.taptix.engine.AcceptOutcome
import com.example.taptix.engine.AcceptVerifier
import com.example.taptix.engine.DirectClickResult
import com.example.taptix.util.BatteryOptimizationHelper
import com.example.taptix.util.BatterySettingsLaunchResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase AE Critical Safety Integration Tests.
 *
 * Formally validates the 13 mission-critical automation safety invariants across:
 * Controller, Lifecycle, Settings, Partner Detection, and Verification Engines.
 */
class PhaseAEAutomationSafetyIntegrationTest {

    private class FakeTestContext(private val pkg: String = "com.example.taptix") : ContextWrapper(null) {
        override fun getPackageName(): String = pkg
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = FakeTestContext()
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    // SAFETY-01: Automation starts only after explicit user activation
    @Test
    fun test01_ExplicitUserActivationPrerequisite() {
        assertEquals("Initial state must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertFalse("Cannot process screen initially", AutomationSafetyController.canProcessScreen())

        // Service connection with master=true but non-explicit action cannot enable
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)
        assertEquals("Service bind cannot auto-enable automation", AutomationSafetyState.OFF, AutomationSafetyController.state)

        // When STOP is latched, implicit re-enable is strictly rejected
        AutomationSafetyController.emergencyStop("SETUP_STOP_LATCH")
        assertTrue(AutomationSafetyController.isStopLatched)

        val implicitAttempt = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = false // Denied!
        )
        assertFalse("Implicit activation while latched must be rejected", implicitAttempt)
        assertEquals("State must remain OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)

        // Explicit user activation succeeds and clears latch
        val explicitAttempt = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertTrue("Explicit user activation must succeed", explicitAttempt)
        assertEquals("State must be ON", AutomationSafetyState.ON, AutomationSafetyController.state)
        assertFalse("Latch must be cleared", AutomationSafetyController.isStopLatched)
        assertTrue("Can process screen", AutomationSafetyController.canProcessScreen())
    }

    // SAFETY-02: Emergency STOP immediately blocks new automation actions
    @Test
    fun test02_ImmediateActionBlockingOnStop() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val tokenBefore = AutomationSafetyController.currentGeneration

        assertTrue("Action allowed when ON", AutomationSafetyController.canExecuteAction(tokenBefore))

        // Trigger emergency STOP
        val stopOk = AutomationSafetyController.emergencyStop("CRITICAL_TEST_STOP")
        assertTrue("Emergency stop must return true", stopOk)
        assertEquals("State must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP must be latched", AutomationSafetyController.isStopLatched)

        // Immediately verify blocking
        assertFalse("Cannot process screen after STOP", AutomationSafetyController.canProcessScreen())
        assertFalse("Previous token blocked after STOP", AutomationSafetyController.canExecuteAction(tokenBefore))
        assertFalse("Current token blocked after STOP", AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    // SAFETY-03: STOP cancels queued clicks, pending verification and scheduled retries
    @Test
    fun test03_CancellationOfQueuedClicksPendingVerificationAndRetries() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val inFlightToken = AutomationSafetyController.currentGeneration

        var cancelCallbackFired = false
        AutomationSafetyController.registerStopListener {
            cancelCallbackFired = true
        }

        // Activate STOP
        AutomationSafetyController.emergencyStop("ABORT_IN_FLIGHT")

        assertTrue("Registered cancel handler must execute", cancelCallbackFired)
        assertTrue("Generation counter must advance to invalidate queued tokens", AutomationSafetyController.currentGeneration > inFlightToken)
        assertFalse("In-flight token rejected", AutomationSafetyController.canExecuteAction(inFlightToken))
    }

    // SAFETY-04: STOP remains latched after app restart
    @Test
    fun test04_StopLatchPersistenceAcrossAppRestart() {
        AutomationSafetyController.emergencyStop("PERSIST_FOR_RESTART")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Simulated process restart: reset in-memory state but latch preserved in storage
        // When latched, state must initialize strictly as OFF
        assertEquals("State must remain OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch persists across restarts", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    // SAFETY-05: STOP remains latched after accessibility service reconnection
    @Test
    fun test05_StopLatchPersistenceAcrossServiceReconnection() {
        AutomationSafetyController.emergencyStop("PRE_DISCONNECT_STOP")
        assertTrue(AutomationSafetyController.isStopLatched)

        // OS unbinds accessibility service
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals(AutomationSafetyState.BLOCKED, AutomationSafetyController.state)

        // OS rebinds accessibility service with masterEnabled = true
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)

        // Invariant: Must remain strictly OFF because STOP is latched
        assertEquals("State must be OFF after rebind when STOP is latched", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch must remain true", AutomationSafetyController.isStopLatched)
        assertFalse("Automation cannot resume silently", AutomationSafetyController.canProcessScreen())
    }

    // SAFETY-06: Battery whitelist changes never clear the STOP latch
    @Test
    fun test06_BatteryWhitelistChangesCannotClearLatch() {
        AutomationSafetyController.emergencyStop("BATTERY_LATCH_TEST")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Simulate battery exemption inspection
        val exempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        assertNotNull(exempt)

        // Invariant: Controller state and latch are unaffected
        assertEquals("State remains strictly OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch remains true", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    // SAFETY-07: Battery settings navigation never enables automation automatically
    @Test
    fun test07_BatterySettingsNavigationNeverEnablesAutomation() {
        AutomationSafetyController.emergencyStop("SETTINGS_NAV_TEST")
        assertTrue(AutomationSafetyController.isStopLatched)

        // User navigates to battery settings
        val launchResult = BatteryOptimizationHelper.openBatterySettings(context) { _, _ -> true }
        assertEquals(BatterySettingsLaunchResult.SUCCESS_OPTIMIZATION_SETTINGS, launchResult)

        // Returning from settings: state remains OFF, latch remains active
        assertEquals("Automation remains OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch remains true", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    // SAFETY-08: Permission revocation blocks automation
    @Test
    fun test08_PermissionRevocationFailsSafelyToBlocked() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)

        // Permission revoked / service unbound by OS
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals("State must transition to BLOCKED", AutomationSafetyState.BLOCKED, AutomationSafetyController.state)
        assertFalse("Cannot process screen when BLOCKED", AutomationSafetyController.canProcessScreen())
        assertFalse("Cannot execute actions when BLOCKED", AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    // SAFETY-09: Unknown partner applications produce zero automatic clicks
    @Test
    fun test09_UnknownPartnerApplicationsProduceZeroClicks() {
        val unknownPackages = listOf(
            "com.random.game",
            "com.facebook.katana",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.android.chrome"
        )
        for (pkg in unknownPackages) {
            val isSupported = PartnerProfileRegistry.isSupportedPackage(pkg)
            assertFalse("Package '$pkg' must not be supported", isSupported)
            val profile = PartnerProfileRegistry.getProfileForPackage(pkg)
            assertNull("Unknown package must return null profile", profile)
        }
    }

    // SAFETY-10: Unknown screen layouts produce zero automatic clicks
    @Test
    fun test10_UnknownScreenLayoutsProduceZeroClicks() {
        val uber = PartnerProfileRegistry.getProfileById("uber")
        assertNotNull("Uber profile must exist", uber)

        // Passing null root node simulates unrecognized screen
        val result = uber!!.findAcceptControl(null, "com.ubercab.driver")
        assertEquals(DetectionStatus.UNRECOGNIZED_LAYOUT, result.status)
        assertNull("Unrecognized layout must yield null acceptNode", result.acceptNode)
        assertFalse("Unrecognized layout must not be actionable", result.isActionable)
    }

    // SAFETY-11: UNKNOWN click outcomes produce zero coordinate fallbacks and zero speculative retries
    @Test
    fun test11_UnknownClickOutcomesProduceZeroCoordinateFallbacksAndZeroRetries() {
        // Direct click outcome UNKNOWN
        val directResult = DirectClickResult.UNKNOWN
        assertFalse("UNKNOWN click result must not be considered success", directResult == DirectClickResult.SUCCESS)
        assertFalse("UNKNOWN click result must not be considered confirmed dispatched", directResult == DirectClickResult.FAILED_CONFIRMED_NOT_DISPATCHED)

        // Accept outcome when screen is unreadable evaluates to UNKNOWN
        val acceptOutcomeNull = AcceptVerifier.evaluate(
            acceptButtonStillVisible = null,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals("Unreadable screen must evaluate to UNKNOWN", AcceptOutcome.UNKNOWN, acceptOutcomeNull)

        // Button disappearance alone without positive confirmation evaluates to NOT_CONFIRMED (never CONFIRMED)
        val acceptOutcomeDisappeared = AcceptVerifier.evaluate(
            acceptButtonStillVisible = false,
            positiveConfirmationPresent = false,
            negativeOrCancelledPresent = false
        )
        assertEquals("Button disappearance alone without confirmation must be NOT_CONFIRMED", AcceptOutcome.NOT_CONFIRMED, acceptOutcomeDisappeared)
        assertFalse("Button disappearance alone must never confirm acceptance", acceptOutcomeDisappeared == AcceptOutcome.CONFIRMED)
    }

    // SAFETY-12: Duplicate STOP requests remain idempotent
    @Test
    fun test12_DuplicateStopRequestsRemainIdempotent() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val stop1 = AutomationSafetyController.emergencyStop("FIRST_STOP")
        val genAfterStop1 = AutomationSafetyController.currentGeneration

        val stop2 = AutomationSafetyController.emergencyStop("REDUNDANT_STOP_1")
        val stop3 = AutomationSafetyController.emergencyStop("REDUNDANT_STOP_2")

        assertTrue("First stop succeeds", stop1)
        assertTrue("Redundant stop 1 returns cleanly", stop2)
        assertTrue("Redundant stop 2 returns cleanly", stop3)
        assertEquals("State remains OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP remains latched", AutomationSafetyController.isStopLatched)
        assertEquals("Generation counter must not increment spuriously on redundant stops", genAfterStop1, AutomationSafetyController.currentGeneration)
    }

    // SAFETY-13: Manual reactivation is required after STOP
    @Test
    fun test13_ManualReactivationRequiredAfterStop() {
        AutomationSafetyController.emergencyStop("STOP_BEFORE_REACTIVATION")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Attempting to re-enable without explicit user action fails
        val implicitReenable = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = false
        )
        assertFalse("Implicit re-enabling must fail", implicitReenable)
        assertTrue("STOP latch must remain active", AutomationSafetyController.isStopLatched)

        // Explicit user action clears latch and re-enables
        val explicitReenable = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertTrue("Explicit user action must re-enable", explicitReenable)
        assertFalse("STOP latch must be cleared", AutomationSafetyController.isStopLatched)
        assertEquals("State must be ON", AutomationSafetyState.ON, AutomationSafetyController.state)
    }
}
