package com.example.taptix

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import com.example.taptix.util.BatteryOptimizationHelper
import com.example.taptix.util.BatterySettingsLaunchResult
import com.example.taptix.util.SettingClassification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase AD Unit Test Suite:
 * Battery Optimization, OEM Guidance, Lifecycle Hardening, and Emergency STOP Invariants.
 */
class BatteryOptimizationHelperTest {

    private class FakeTestContext(private val pkg: String = "com.example.taptix") : ContextWrapper(null) {
        override fun getPackageName(): String = pkg
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    private lateinit var testContext: Context

    @Before
    fun setUp() {
        testContext = FakeTestContext()
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    @Test
    fun test01_OemGuidance_XiaomiHasAutostartAndDisclaimer() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("Xiaomi")
        assertEquals("Xiaomi / Redmi / POCO", guidance.manufacturerName)
        assertEquals("MIUI / HyperOS", guidance.osFamily)
        assertTrue(guidance.disclaimer.contains("Exemption does NOT guarantee uninterrupted background execution"))
        assertTrue(guidance.steps.any { it.title.contains("Autostart") && it.classification == SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE })
    }

    @Test
    fun test02_OemGuidance_SamsungHasUnrestrictedAndSleepingApps() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("SAMSUNG")
        assertEquals("Samsung", guidance.manufacturerName)
        assertEquals("One UI", guidance.osFamily)
        assertTrue(guidance.steps.any { it.title.contains("Unrestricted") && it.classification == SettingClassification.OFFICIAL_ANDROID_SETTING })
        assertTrue(guidance.steps.any { it.instruction.contains("Sleeping apps") && it.classification == SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE })
    }

    @Test
    fun test03_OemGuidance_OppoAndRealmeHaveAllowBackgroundActivity() {
        val oppo = BatteryOptimizationHelper.getManufacturerGuidance("OPPO")
        assertEquals("OPPO", oppo.manufacturerName)
        assertTrue(oppo.steps.any { it.title.contains("Allow Background Activity") })

        val realme = BatteryOptimizationHelper.getManufacturerGuidance("realme")
        assertEquals("Realme", realme.manufacturerName)
        assertTrue(realme.steps.any { it.title.contains("Allow Background Activity") })
    }

    @Test
    fun test04_OemGuidance_VivoHasHighBackgroundPowerUsage() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("vivo")
        assertEquals("Vivo / iQOO", guidance.manufacturerName)
        assertTrue(guidance.steps.any { it.title.contains("High Background Power") })
    }

    @Test
    fun test05_OemGuidance_OnePlusHasDontOptimize() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("OnePlus")
        assertEquals("OnePlus", guidance.manufacturerName)
        assertTrue(guidance.steps.any { it.title.contains("Don't Optimize") })
    }

    @Test
    fun test06_OemGuidance_MotorolaHasUnrestrictedAospSetting() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("motorola")
        assertEquals("Motorola", guidance.manufacturerName)
        assertTrue(guidance.steps.any { it.title.contains("Unrestricted") && it.classification == SettingClassification.OFFICIAL_ANDROID_SETTING })
    }

    @Test
    fun test07_OemGuidance_GooglePixelHasStockAndroidSettings() {
        val guidance = BatteryOptimizationHelper.getManufacturerGuidance("Google")
        assertEquals("Google Pixel", guidance.manufacturerName)
        assertTrue(guidance.steps.all { it.classification == SettingClassification.OFFICIAL_ANDROID_SETTING })
    }

    @Test
    fun test08_OpenBatterySettings_Tier1Success() {
        var launchedAction: String? = null
        val launcher: (String, String?) -> Boolean = { action, uri ->
            launchedAction = action
            true
        }

        val result = BatteryOptimizationHelper.openBatterySettings(testContext, launcher)
        assertEquals(BatterySettingsLaunchResult.SUCCESS_OPTIMIZATION_SETTINGS, result)
        assertEquals(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, launchedAction)
    }

    @Test
    fun test09_OpenBatterySettings_Tier1Fails_Tier2AppDetailsSuccess() {
        var launchedAction: String? = null
        var attempts = 0
        val launcher: (String, String?) -> Boolean = { action, uri ->
            attempts++
            if (attempts == 1) {
                throw ActivityNotFoundException("Tier 1 not available on custom ROM")
            }
            launchedAction = action
            true
        }

        val result = BatteryOptimizationHelper.openBatterySettings(testContext, launcher)
        assertEquals(BatterySettingsLaunchResult.SUCCESS_APP_DETAILS, result)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launchedAction)
    }

    @Test
    fun test10_OpenBatterySettings_Tier1And2Fail_Tier3GeneralSettingsSuccess() {
        var launchedAction: String? = null
        var attempts = 0
        val launcher: (String, String?) -> Boolean = { action, uri ->
            attempts++
            if (attempts == 1) {
                throw ActivityNotFoundException("Tier 1 missing")
            }
            if (attempts == 2) {
                throw SecurityException("Tier 2 blocked")
            }
            launchedAction = action
            true
        }

        val result = BatteryOptimizationHelper.openBatterySettings(testContext, launcher)
        assertEquals(BatterySettingsLaunchResult.SUCCESS_GENERAL_SETTINGS, result)
        assertEquals(android.provider.Settings.ACTION_SETTINGS, launchedAction)
    }

    @Test
    fun test11_OpenBatterySettings_AllFail_ReturnsFailedAllWithoutCrashing() {
        val launcher: (String, String?) -> Boolean = { _, _ ->
            throw ActivityNotFoundException("All settings missing")
        }

        val result = BatteryOptimizationHelper.openBatterySettings(testContext, launcher)
        assertEquals(BatterySettingsLaunchResult.FAILED_ALL, result)
    }

    @Test
    fun test12_StopFollowedByBatteryChanges_LatchRemainsStrictlyActive() {
        // 1. Enable automation
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertEquals(AutomationSafetyState.ON, AutomationSafetyController.state)

        // 2. Emergency STOP
        AutomationSafetyController.emergencyStop("EMERGENCY_STOP_TEST")
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue(AutomationSafetyController.isStopLatched)

        // 3. Simulate battery exemption inspection
        val isExempt1 = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(testContext)
        assertNotNull(isExempt1)
        // Verify controller state did not mutate
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch must remain active after battery state inspection", AutomationSafetyController.isStopLatched)
        assertFalse(AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test13_StopFollowedByServiceDisconnectAndReconnect_RemainsStrictlyOff() {
        // 1. Enable automation
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )

        // 2. Trigger STOP
        AutomationSafetyController.emergencyStop("STOP_BEFORE_DISCONNECT")
        assertTrue(AutomationSafetyController.isStopLatched)

        // 3. OS unbinds accessibility service
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals(AutomationSafetyState.BLOCKED, AutomationSafetyController.state)

        // 4. OS rebinds accessibility service with masterEnabled = true
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)

        // 5. Must remain strictly OFF because STOP is latched
        assertEquals("State must be OFF after rebind when STOP is latched", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP latch must remain true", AutomationSafetyController.isStopLatched)
        assertFalse("Cannot process screen", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test14_StopFollowedByPermissionRevocationAndRestoration_CannotResumeSilently() {
        // 1. Trigger STOP
        AutomationSafetyController.emergencyStop("STOP_FOR_PERMISSIONS")
        assertTrue(AutomationSafetyController.isStopLatched)

        // 2. Permission revocation blocks service
        AutomationSafetyController.notifyServiceDisconnected()
        assertEquals(AutomationSafetyState.BLOCKED, AutomationSafetyController.state)

        // 3. Permission restored and service reconnected
        AutomationSafetyController.notifyServiceConnected(masterEnabled = true)
        assertEquals(AutomationSafetyState.OFF, AutomationSafetyController.state)

        // 4. Automation cannot run without explicit user request
        assertFalse(AutomationSafetyController.canProcessScreen())
        assertFalse(AutomationSafetyController.canExecuteAction(AutomationSafetyController.currentGeneration))
    }

    @Test
    fun test15_ConcurrentStopRequests_IdempotentAndThreadSafe() {
        // Enable automation
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        val initialGen = AutomationSafetyController.currentGeneration

        val threads = 20
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val stopSuccessCount = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    val ok = AutomationSafetyController.emergencyStop("CONCURRENT_STOP_$i")
                    if (ok) stopSuccessCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All threads must finish", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("State must be OFF", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("STOP must be latched", AutomationSafetyController.isStopLatched)
        assertEquals("All 20 threads returned successfully", threads, stopSuccessCount.get())
        // Generation counter should advance cleanly without corrupting state
        assertTrue("Generation counter must advance beyond initial", AutomationSafetyController.currentGeneration > initialGen)
        assertFalse("Cannot process screen after concurrent stop", AutomationSafetyController.canProcessScreen())
    }
}
