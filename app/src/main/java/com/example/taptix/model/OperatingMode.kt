package com.example.taptix.model

/**
 * Operating modes for Taptix Auto Clicker.
 */
enum class OperatingMode(val displayName: String, val description: String) {
    SINGLE_TARGET("Single Target", "Repeatedly taps a single marked coordinate on screen."),
    MULTI_TARGET("Multi Target", "Sequentially taps multiple marked coordinates in order."),
    SMART_ACCEPT("Smart Accept (OCR)", "Real-time Accessibility Tree OCR to auto-accept rides hands-free.")
}
