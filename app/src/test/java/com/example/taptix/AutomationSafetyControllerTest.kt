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
        // Start from ON to establish a baseline active state
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val initialGen = AutomationSafetyController.currentGeneration
        var listenerCount = 0
        AutomationSafetyController.registerStopListener { listenerCount++ }

        // First Stop
        val firstResult = AutomationSafetyController.emergencyStop("FIRST_STOP")
        val genAfterFirst = AutomationSafetyController.currentGeneration
        assertTrue("First stop must succeed", firstResult)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertTrue("Generation must advance on initial stop transition", genAfterFirst > initialGen)
        assertEquals("Stop listener must be invoked exactly once", 1, listenerCount)

        // Second duplicate/redundant stop call
        val secondResult = AutomationSafetyController.emergencyStop("SECOND_STOP")
        val genAfterSecond = AutomationSafetyController.currentGeneration
        assertTrue("Second stop call must return true safely", secondResult)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        // Idempotency: Redundant stop must NOT advance generation or re-notify listeners
        assertEquals("Generation must remain unchanged on redundant stop", genAfterFirst, genAfterSecond)
        assertEquals("Stop listener must NOT be re-invoked on redundant stop", 1, listenerCount)
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

    @Test
    fun test12_RepeatedDuplicateStopRequests_CannotTriggerClicksOrResume() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val initialGen = AutomationSafetyController.currentGeneration
        assertTrue("Controller must be ON", AutomationSafetyController.canProcessScreen())

        // Execute initial STOP
        AutomationSafetyController.emergencyStop("PRIMARY_STOP")
        val stopGen = AutomationSafetyController.currentGeneration
        assertTrue(stopGen > initialGen)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)

        // Fire 10 consecutive duplicate STOP requests
        for (i in 1..10) {
            val res = AutomationSafetyController.emergencyStop("REDUNDANT_STOP_$i")
            assertTrue("Duplicate stop must succeed safely", res)
            assertEquals("State must remain OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
            assertTrue("Stop latch must remain active", AutomationSafetyController.isStopLatched)
            assertEquals("Generation must not drift on duplicate stops", stopGen, AutomationSafetyController.currentGeneration)
            assertFalse("Screen processing must remain blocked", AutomationSafetyController.canProcessScreen())
            assertFalse("Previous tokens must be rejected", AutomationSafetyController.canExecuteAction(initialGen))
            assertFalse("Current tokens must be rejected while OFF", AutomationSafetyController.canExecuteAction(stopGen))
        }
    }

    @Test
    fun test13_ConcurrentStopsAndActions_NeverResumesOrDispatches() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val initialGen = AutomationSafetyController.currentGeneration
        val threadCount = 8
        val latch = CountDownLatch(threadCount)
        val actionsDispatched = AtomicInteger(0)

        // Half the threads repeatedly spam emergencyStop; other half try to execute actions
        val threads = (1..threadCount).map { idx ->
            Thread {
                try {
                    if (idx % 2 == 0) {
                        for (j in 1..20) {
                            AutomationSafetyController.emergencyStop("THREAD_${idx}_STOP_$j")
                        }
                    } else {
                        for (j in 1..20) {
                            if (AutomationSafetyController.canExecuteAction(initialGen)) {
                                actionsDispatched.incrementAndGet()
                            }
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        assertTrue("Concurrent operations must finish within timeout", latch.await(4, TimeUnit.SECONDS))

        // System must be strictly OFF and latched
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
        assertFalse(AutomationSafetyController.canExecuteAction(initialGen))
        assertFalse(AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }
}
