package com.example.taptix

import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit test suite for Phase AC: Centralized Automation Safety Controller & Emergency Kill Switch.
 */
class AutomationSafetyControllerTest {

    @Before
    fun setUp() {
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    @Test
    fun test01_InitialState_DefaultsToOff_NeverSilentOn() {
        assertEquals("Initial state must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertFalse("Cannot process screen when OFF", AutomationSafetyController.canProcessScreen())
        assertFalse("Cannot execute actions when OFF", AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    @Test
    fun test02_RequestEnable_RequiresAccessibilityPrerequisite() {
        val enabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = false, // Accessibility service unavailable!
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertFalse("Enable must fail when accessibility service is missing", enabled)
        assertEquals("State must transition to BLOCKED", AutomationSafetyState.BLOCKED, AutomationSafetyController.state)
        assertFalse("Cannot process screen when BLOCKED", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test03_RequestEnable_RequiresMasterSwitch() {
        val enabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = false, // Master switch disabled in settings
            isUserExplicitAction = true
        )
        assertFalse("Enable must fail when master control is disabled", enabled)
        assertEquals("State must remain OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test04_RequestEnable_SucceedsWithAllPrerequisites() {
        val initialGen = AutomationSafetyController.currentGeneration
        val enabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertTrue("Enable must succeed when all prerequisites are met", enabled)
        assertEquals("State must be ON", AutomationSafetyState.ON, AutomationSafetyController.state)
        assertTrue("Screen processing permitted when ON", AutomationSafetyController.canProcessScreen())
        assertTrue("Generation counter must advance on enable", AutomationSafetyController.currentGeneration > initialGen)
        assertTrue("Actions permitted with current generation", AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    @Test
    fun test05_StaleGenerationToken_RejectsAction() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val validGen = AutomationSafetyController.currentGeneration
        assertTrue("Valid generation permitted", AutomationSafetyController.canExecuteAction(validGen))

        // Simulated stale action from earlier generation
        val staleGen = validGen - 1
        assertFalse("Stale action token from previous generation must be rejected", AutomationSafetyController.canExecuteAction(staleGen))
    }

    @Test
    fun test06_EmergencyStop_TransitionsToOffAndAdvancesGeneration() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val genBeforeStop = AutomationSafetyController.currentGeneration

        var listenerTriggered = false
        AutomationSafetyController.registerStopListener {
            listenerTriggered = true
        }

        val stopped = AutomationSafetyController.emergencyStop("TEST_EMERGENCY_STOP")
        assertTrue("Emergency stop call must return true", stopped)
        assertEquals("State after emergency stop must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("Stop listener must be invoked", listenerTriggered)
        assertTrue("Generation counter must advance after emergency stop", AutomationSafetyController.currentGeneration > genBeforeStop)
        assertTrue("STOP state must be latched", AutomationSafetyController.isStopLatched)

        // Actions scheduled before the stop must now be completely rejected
        assertFalse("Action using genBeforeStop must be rejected", AutomationSafetyController.canExecuteAction(genBeforeStop))
        assertFalse("Action using new gen must still be rejected because state is OFF", AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
        assertFalse("Cannot process screen after stop", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test07_EmergencyStop_IsIdempotent() {
        val initialGen = AutomationSafetyController.currentGeneration
        AutomationSafetyController.emergencyStop("FIRST_STOP")
        val genAfterFirst = AutomationSafetyController.currentGeneration

        // Second immediate stop call
        AutomationSafetyController.emergencyStop("SECOND_STOP")
        val genAfterSecond = AutomationSafetyController.currentGeneration

        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertTrue(genAfterSecond > genAfterFirst)
        assertTrue(genAfterFirst > initialGen)
    }

    @Test
    fun test08_LatchedStop_PreventsSilentAutoResumeOnServiceConnect() {
        // Trigger emergency stop to latch it
        AutomationSafetyController.emergencyStop("KILL_SWITCH")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Service reconnects with master switch on
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)

        // Must remain OFF and latched
        assertEquals("Reconnection must NOT silently enable automation when latched", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("Latch must remain intact", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test09_ExplicitUserAction_ClearsStopLatch() {
        AutomationSafetyController.emergencyStop("USER_STOP")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Explicit user action clears latch and restores ON
        val reenabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true // Explicit click by user
        )
        assertTrue("Explicit user enable must succeed", reenabled)
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)
        assertFalse("Latch must be cleared after explicit user enable", AutomationSafetyController.isStopLatched)
    }

    @Test
    fun test10_ServiceDisconnect_FailsSafelyToBlocked() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)

        // Service unbinds / disconnects unexpectedly
        AutomationSafetyController.notifyServiceDisconnected()

        assertEquals("Disconnected service must fail safely to BLOCKED", AutomationSafetyState.BLOCKED, AutomationSafetyController.state)
        assertFalse(AutomationSafetyController.canProcessScreen())
        assertFalse(AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    @Test
    fun test11_ConcurrentStopAndAction_ThreadSafety() {
        val iterations = 1000
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val rejectedCount = AtomicInteger(0)

        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val capturedGen = AutomationSafetyController.currentGeneration

        val stopThread = Thread {
            startLatch.await()
            Thread.sleep(1)
            AutomationSafetyController.emergencyStop("CONCURRENT_TEST")
            doneLatch.countDown()
        }

        val actionThread = Thread {
            startLatch.await()
            for (i in 0 until iterations) {
                if (!AutomationSafetyController.canExecuteAction(capturedGen)) {
                    rejectedCount.incrementAndGet()
                }
                Thread.yield()
            }
            doneLatch.countDown()
        }

        stopThread.start()
        actionThread.start()
        startLatch.countDown()
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS))

        // State must be consistently OFF
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        // Post-stop assertion: actions using capturedGen must be permanently rejected
        assertFalse("Actions using stale generation token must be rejected after emergency stop", AutomationSafetyController.canExecuteAction(capturedGen))
    }
}
