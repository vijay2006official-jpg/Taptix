package com.example.taptix

import com.example.taptix.data.AppSettings
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaptixUnitTest {

    @Test
    fun testPlatformPreset_uberRequiresSwipe() {
        val uber = PlatformPreset.UBER
        assertEquals("Uber Driver", uber.title)
        assertTrue(uber.requiresSwipe)
        assertTrue(uber.keywords.contains("ACCEPT"))
    }

    @Test
    fun testPlatformPreset_fromId() {
        val presetOla = PlatformPreset.fromId("ola")
        assertEquals(PlatformPreset.OLA, presetOla)

        val presetRapido = PlatformPreset.fromId("rapido")
        assertEquals(PlatformPreset.RAPIDO, presetRapido)

        val presetUnknown = PlatformPreset.fromId("nonexistent")
        assertEquals(PlatformPreset.UBER, presetUnknown)
    }

    @Test
    fun testOperatingMode_values() {
        val modes = OperatingMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(OperatingMode.SINGLE_TARGET))
        assertTrue(modes.contains(OperatingMode.MULTI_TARGET))
        assertTrue(modes.contains(OperatingMode.SMART_ACCEPT))
    }

    @Test
    fun testTargetPoint_coordinates() {
        val point = TargetPoint(id = 1, x = 300, y = 600)
        assertEquals(1, point.id)
        assertEquals(300, point.x)
        assertEquals(600, point.y)

        point.x = 450
        point.y = 850
        assertEquals(450, point.x)
        assertEquals(850, point.y)
    }

    @Test
    fun testAppSettings_defaultValues() {
        val settings = AppSettings()
        assertEquals(300L, settings.clickIntervalMs)
        assertEquals(OperatingMode.SMART_ACCEPT, settings.operatingMode)
        assertEquals(PlatformPreset.UBER, settings.platformPreset)
        assertTrue(settings.autoPauseOnAccept)
        assertTrue(settings.motionSafetyLockEnabled)
        assertTrue(settings.ttsFeedbackEnabled)
    }
}
