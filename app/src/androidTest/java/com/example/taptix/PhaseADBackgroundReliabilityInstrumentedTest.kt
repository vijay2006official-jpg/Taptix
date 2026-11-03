package com.example.taptix

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import com.example.taptix.util.BatteryOptimizationHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase AD Android 15 (API 35) Connected Integration Tests on Pixel 8 AVD.
 *
 * Verifies live background reliability, battery optimization intent resolution,
 * and emergency STOP latch invariant preservation on the real Android OS runtime.
 */
@RunWith(AndroidJUnit4::class)
class PhaseADBackgroundReliabilityInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutomationSafetyController.initialize(context)
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    @Test
    fun test01_BatteryOptimizationStatusInspection_LiveSystem() {
        val isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        assertNotNull("Battery exemption status must be non-null boolean on live system", isExempt)
    }

    @Test
    fun test02_BatterySettingsIntentResolution_Tier1_LiveSystem() {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertNotNull("ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS must resolve on Android 15 AVD", resolveInfo)
    }

    @Test
    fun test03_BatterySettingsIntentResolution_Tier2_AppDetails_LiveSystem() {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertNotNull("ACTION_APPLICATION_DETAILS_SETTINGS must resolve on Android 15 AVD", resolveInfo)
    }

    @Test
    fun test04_StopLatched_BatteryStateInspection_PreservesLatchOnDevice() {
        // Enable automation
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)

        // Trigger emergency STOP
        AutomationSafetyController.emergencyStop("EMERGENCY_STOP_DEVICE_TEST")
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP must be latched", AutomationSafetyController.isStopLatched)

        // Query battery status on real device
        val isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        assertNotNull(isExempt)

        // Verify latch invariant
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch must remain active on device after battery check", AutomationSafetyController.isStopLatched)
        assertFalse("Cannot process screen", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test05_StopLatched_SimulatedDisconnectAndReconnect_PreservesLatchOnDevice() {
        // Trigger STOP
        AutomationSafetyController.emergencyStop("STOP_BEFORE_DISCONNECT_DEVICE")
        assertTrue(AutomationSafetyController.isStopLatched)

        // OS unbinds accessibility service
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals(AutomationSafetyState.BLOCKED, AutomationSafetyController.state)

        // OS rebinds accessibility service
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)

        // Invariant: Must remain strictly OFF because STOP is latched
        assertEquals("State must be OFF after rebind when latched", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch must remain true", AutomationSafetyController.isStopLatched)
        assertFalse("Cannot process screen", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test06_ActionExecutionBlocked_WhenStopLatched() {
        AutomationSafetyController.emergencyStop("STOP_LATCH_BLOCK_ACTION")
        val gen = AutomationSafetyController.currentGeneration

        val canExecute = AutomationSafetyController.canExecuteAction(gen)
        assertFalse("Action execution must be blocked when STOP is latched", canExecute)
    }

    @Test
    fun test07_ConcurrentStopActivation_ThreadSafeOnLiveDevice() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val initialGen = AutomationSafetyController.currentGeneration

        val threads = 10
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val stopSuccessCount = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    val ok = AutomationSafetyController.emergencyStop("DEVICE_CONCURRENT_STOP_$i")
                    if (ok) stopSuccessCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All threads must finish within timeout", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("State must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP must be latched", AutomationSafetyController.isStopLatched)
        assertEquals("All threads succeeded", threads, stopSuccessCount.get())
        assertTrue("Generation counter must advance", AutomationSafetyController.currentGeneration > initialGen)
    }
}
