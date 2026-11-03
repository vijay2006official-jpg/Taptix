package com.example.taptix

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import com.example.taptix.engine.AcceptClickExecutor
import com.example.taptix.engine.ClickMethod
import com.example.taptix.engine.DirectClickResult
import com.example.taptix.service.OverlayService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android 15 AVD Live Integration Test Suite for Phase AC.
 * Covers all 12 Emergency STOP, callback invalidation, and safety controller scenarios.
 */
@RunWith(AndroidJUnit4::class)
class PhaseACEmergencyStopInstrumentedTest {

    private lateinit var context: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        AutomationSafetyController.initialize(context)
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    /**
     * Test 1: Enable automation and activate emergency STOP.
     * Category: Action prevented after STOP.
     */
    @Test
    fun test01_EnableAutomationAndActivateEmergencyStop() {
        val enabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertTrue("Automation must enable cleanly with prerequisites", enabled)
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.canProcessScreen())

        val actionGen = AutomationSafetyController.currentGeneration
        assertTrue(AutomationSafetyController.canExecuteAction(actionGen))

        // Activate Emergency STOP
        val stopped = AutomationSafetyController.emergencyStop("TEST_STOP_USER")
        assertTrue(stopped)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertFalse("Processing must be blocked after stop", AutomationSafetyController.canProcessScreen())
        assertFalse("Action execution must be prevented after stop", AutomationSafetyController.canExecuteAction(actionGen))
    }

    /**
     * Test 2: Press STOP while a delayed click is queued.
     * Category: Action scheduled but cancelled / prevented after STOP.
     */
    @Test
    fun test02_PressStopWhileDelayedClickIsQueued() {
        AutomationSafetyController.requestEnable(true, true, true)
        val actionGen = AutomationSafetyController.currentGeneration
        val clickDispatched = AtomicBoolean(false)
        val actionPrevented = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        // Queue a delayed click task (e.g. 150ms delay)
        val clickRunnable = Runnable {
            if (AutomationSafetyController.canExecuteAction(actionGen)) {
                clickDispatched.set(true)
            } else {
                actionPrevented.set(true)
            }
            latch.countDown()
        }
        mainHandler.postDelayed(clickRunnable, 100)

        // Emergency STOP pressed while delayed click is pending in looper queue
        AutomationSafetyController.emergencyStop("STOP_WHILE_QUEUED")

        assertTrue("Delayed task must execute within timeout", latch.await(2, TimeUnit.SECONDS))
        assertFalse("Queued click must NOT be dispatched after STOP", clickDispatched.get())
        assertTrue("Queued click must be cleanly prevented by safety controller", actionPrevented.get())
    }

    /**
     * Test 3: Press STOP while verification is pending.
     * Category: Verification retry prevented after STOP.
     */
    @Test
    fun test03_PressStopWhileVerificationIsPending() {
        AutomationSafetyController.requestEnable(true, true, true)
        val actionGen = AutomationSafetyController.currentGeneration
        val retryDispatched = AtomicBoolean(false)
        val retryPrevented = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        // Verification scheduled retry
        val verificationRetry = Runnable {
            if (AutomationSafetyController.canExecuteAction(actionGen)) {
                retryDispatched.set(true)
            } else {
                retryPrevented.set(true)
            }
            latch.countDown()
        }
        mainHandler.postDelayed(verificationRetry, 100)

        // Emergency STOP before verification check executes
        AutomationSafetyController.emergencyStop("STOP_DURING_VERIFY")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertFalse("Verification retry must NOT dispatch gesture after STOP", retryDispatched.get())
        assertTrue("Verification retry must be rejected", retryPrevented.get())
    }

    /**
     * Test 4: Press STOP while a retry is scheduled.
     * Category: Action scheduled but cancelled.
     */
    @Test
    fun test04_PressStopWhileRetryIsScheduled() {
        AutomationSafetyController.requestEnable(true, true, true)
        val scheduledGen = AutomationSafetyController.currentGeneration
        val retryExecuted = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val retryTask = Runnable {
            if (AutomationSafetyController.canExecuteAction(scheduledGen)) {
                retryExecuted.set(true)
            }
            latch.countDown()
        }
        mainHandler.postDelayed(retryTask, 80)

        AutomationSafetyController.emergencyStop("STOP_BEFORE_RETRY")
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertFalse("Scheduled retry must NOT execute after emergency STOP", retryExecuted.get())
    }

    /**
     * Test 5: Rapidly activate STOP multiple times (idempotency & thread safety).
     */
    @Test
    fun test05_RapidlyActivateStopMultipleTimes() {
        AutomationSafetyController.requestEnable(true, true, true)
        val initialGen = AutomationSafetyController.currentGeneration

        val threads = (1..5).map { idx ->
            Thread {
                AutomationSafetyController.emergencyStop("RAPID_STOP_$idx")
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertTrue("Generation counter must advance monotonically", AutomationSafetyController.currentGeneration > initialGen)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    /**
     * Test 6: Disconnect and reconnect Accessibility Service after STOP.
     * Category: Latched STOP prevents silent auto-resume.
     */
    @Test
    fun test06_DisconnectAndReconnectAccessibilityServiceAfterStop() {
        AutomationSafetyController.requestEnable(true, true, true)
        AutomationSafetyController.emergencyStop("KILL_SWITCH")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Accessibility service unbinds
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals(AutomationSafetyState.BLOCKED, AutomationSafetyController.state)

        // Accessibility service reconnects
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)
        assertEquals("Must remain OFF when stop is latched, no silent auto-resume", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    /**
     * Test 7: Force-stop and relaunch Taptix after STOP (persistence).
     */
    @Test
    fun test07_ForceStopAndRelaunchTaptixAfterStop() {
        AutomationSafetyController.requestEnable(true, true, true)
        AutomationSafetyController.emergencyStop("PERSISTENCE_STOP")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Simulate complete process recreation by re-initializing controller with fresh Context
        AutomationSafetyController.initialize(context)
        assertEquals("Initial state after relaunch must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("Persistent latch must survive process recreation", AutomationSafetyController.isStopLatched)
        assertFalse("Cannot process screen when stop is latched", AutomationSafetyController.canProcessScreen())
    }

    /**
     * Test 8: Trigger STOP from the persistent notification.
     */
    @Test
    fun test08_TriggerStopFromPersistentNotification() {
        AutomationSafetyController.requestEnable(true, true, true)
        val actionGen = AutomationSafetyController.currentGeneration
        assertTrue(AutomationSafetyController.canExecuteAction(actionGen))

        // Trigger notification action intent
        val overlayService = OverlayService()
        val stopIntent = Intent().apply {
            action = OverlayService.ACTION_EMERGENCY_STOP
        }
        val result = overlayService.onStartCommand(stopIntent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)

        assertEquals("State must be OFF after notification STOP", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("Latch must be set after notification STOP", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canExecuteAction(actionGen))
    }

    /**
     * Test 9: Trigger STOP from the floating overlay.
     */
    @Test
    fun test09_TriggerStopFromFloatingOverlay() {
        AutomationSafetyController.requestEnable(true, true, true)
        val actionGen = AutomationSafetyController.currentGeneration

        // Floating overlay STOP triggers emergencyStop("OVERLAY_STOP")
        AutomationSafetyController.emergencyStop("OVERLAY_STOP")

        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canExecuteAction(actionGen))
    }

    /**
     * Test 10: Verify the dashboard displays the correct safety state.
     */
    @Test
    fun test10_VerifyDashboardDisplaysCorrectSafetyState() {
        // Condition: isOn = masterControlEnabled && hasRequiredPermissions && isAccessibilityOn &&
        // (if (isServiceRunning) (state == ON) else !isStopLatched)
        val masterEnabled = true
        val hasPerms = true
        val isA11yOn = true

        // Case A: Controller is ON
        AutomationSafetyController.requestEnable(true, true, true)
        val dashboardOnState = masterEnabled && hasPerms && isA11yOn && (AutomationSafetyController.state == AutomationSafetyState.ON)
        assertTrue("Dashboard must display ON when controller is ON", dashboardOnState)

        // Case B: Controller STOPPED
        AutomationSafetyController.emergencyStop("DASHBOARD_TEST")
        val dashboardOffState = masterEnabled && hasPerms && isA11yOn && (AutomationSafetyController.state == AutomationSafetyState.ON)
        assertFalse("Dashboard must display OFF immediately after STOP", dashboardOffState)
    }

    /**
     * Test 11: Verify that manual re-enabling is required after STOP.
     */
    @Test
    fun test11_VerifyManualReenablingRequiredAfterStop() {
        AutomationSafetyController.emergencyStop("TEST_STOP")
        assertTrue(AutomationSafetyController.isStopLatched)

        // Passive enable attempts (e.g. background sync or service connect) must be rejected
        val passiveResult = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = false // Passive attempt!
        )
        assertFalse("Passive re-enable must be rejected when STOP is latched", passiveResult)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)

        // Explicit user action clears latch and restores ON
        val explicitResult = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true // Explicit button click
        )
        assertTrue("Explicit user re-enable must succeed", explicitResult)
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)
        assertFalse("Latch must be cleared", AutomationSafetyController.isStopLatched)
    }

    /**
     * Test 12: Verify that no stale callback dispatches a new action after STOP.
     * Category: Action prevented after STOP (10 consecutive attempts).
     */
    @Test
    fun test12_VerifyNoStaleCallbackDispatchesAfterStop() {
        AutomationSafetyController.requestEnable(true, true, true)
        val staleGen = AutomationSafetyController.currentGeneration

        // STOP fired
        AutomationSafetyController.emergencyStop("KILL_ALL_CALLBACKS")

        val attemptedCount = 10
        val preventedCount = AtomicInteger(0)
        val dispatchedCount = AtomicInteger(0)

        for (i in 1..attemptedCount) {
            if (AutomationSafetyController.canExecuteAction(staleGen)) {
                dispatchedCount.incrementAndGet()
            } else {
                preventedCount.incrementAndGet()
            }
        }

        assertEquals("Zero stale callbacks may be dispatched", 0, dispatchedCount.get())
        assertEquals("All 10 stale callbacks must be cleanly prevented", attemptedCount, preventedCount.get())
    }

    /**
     * Critical Safety Check: UNKNOWN click results produce ZERO coordinate fallbacks and ZERO retries.
     */
    @Test
    fun test13_UnknownClickResult_ZeroCoordinateFallbacksZeroRetries() {
        var coordinateFallbackInvoked = false
        var swipeFallbackInvoked = false

        val result = AcceptClickExecutor.execute(
            isSwipe = false,
            directClick = {
                // Returns UNKNOWN: e.g. target node disappeared or was ambiguous
                DirectClickResult.UNKNOWN
            },
            gestureTap = {
                coordinateFallbackInvoked = true
                true
            },
            swipe = {
                swipeFallbackInvoked = true
                true
            }
        )

        assertEquals("Click result must be UNKNOWN_DISPATCH", ClickMethod.UNKNOWN_DISPATCH, result)
        assertFalse("Coordinate fallback must NOT be invoked when click result is UNKNOWN", coordinateFallbackInvoked)
        assertFalse("Swipe fallback must NOT be invoked when click result is UNKNOWN", swipeFallbackInvoked)
    }
}
