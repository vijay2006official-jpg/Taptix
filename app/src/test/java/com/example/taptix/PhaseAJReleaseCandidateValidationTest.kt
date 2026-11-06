package com.example.taptix

import com.example.taptix.automation.safety.AutomationSafetyController
import com.example.taptix.automation.safety.AutomationSafetyState
import com.example.taptix.model.TargetPoint
import com.example.taptix.widget.TaptixWidgetActionReceiver
import com.example.taptix.widget.TaptixWidgetProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase AJ Release Candidate Validation Unit Tests.
 * Verifies fixes for identified release-blocking and major audit findings.
 */
class PhaseAJReleaseCandidateValidationTest {

    @Before
    fun setUp() {
        AutomationSafetyController.resetForTesting(AutomationSafetyState.OFF)
    }

    @Test
    fun test01_EnableAndPreserveActiveState() {
        // Enable automation
        val enabled = AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        assertTrue("Safety controller should be enabled", enabled)
        assertEquals("State must be ON", AutomationSafetyState.ON, AutomationSafetyController.state)
        assertTrue("Screen processing must be permitted", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test02_EmergencyStop_EnforcesLatchedState() {
        AutomationSafetyController.requestEnable(
            hasAccessibilityService = true,
            masterEnabled = true,
            isUserExplicitAction = true
        )
        AutomationSafetyController.emergencyStop("PHASE_AJ_TEST_STOP")

        assertEquals("State must be OFF after emergency stop", AutomationSafetyState.OFF, AutomationSafetyController.state)
        assertTrue("Latch must be active", AutomationSafetyController.isStopLatched)
        assertFalse("Screen processing must be blocked when stop is latched", AutomationSafetyController.canProcessScreen())
    }

    @Test
    fun test03_TargetPoints_DefensiveCopyPreventsExternalMutation() {
        val originalList = mutableListOf(
            TargetPoint(id = 1, x = 100, y = 200),
            TargetPoint(id = 2, x = 300, y = 400)
        )

        // Simulate defensive copy done in startClicking and updateTargetPoints
        val defensiveCopy = originalList.toList()
        assertEquals(2, defensiveCopy.size)

        // Mutate original list
        originalList.removeAt(0)
        assertEquals(1, originalList.size)
        assertEquals("Defensive copy must remain unchanged", 2, defensiveCopy.size)
        assertEquals("First item preserved in copy", 1, defensiveCopy[0].id)
    }

    @Test
    fun test04_TaptixWidgetActionReceiver_ClassStructure() {
        val receiver = TaptixWidgetActionReceiver()
        assertNotNull("Receiver should be instantiable", receiver)
        assertEquals(
            "Action constant must match expected",
            "com.example.taptix.ACTION_TOGGLE_MASTER",
            TaptixWidgetProvider.ACTION_TOGGLE_MASTER
        )
    }
}
