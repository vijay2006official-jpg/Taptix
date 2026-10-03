package com.example.taptix.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.TargetPoint
import com.example.taptix.util.TtsManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core Accessibility Service for Taptix.
 * Handles programmatic gesture execution (taps/swipes), App-Specific Auto-Launch, and Smart Accept.
 */
class AutoClickService : AccessibilityService() {

    private lateinit var prefsRepo: PreferencesRepository
    private var ttsManager: TtsManager? = null

    private val isServiceRunning = AtomicBoolean(false)
    private val isClickingActive = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null

    private var targetPoints: List<TargetPoint> = emptyList()
    private var currentTargetIndex = 0

    private var lastAcceptTime = 0L
    private var lastAutoLaunchedPackage: String = ""
    private var isKeyboardActive = false

    var onStateChangedListener: ((isRunning: Boolean, mode: OperatingMode) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsRepo = PreferencesRepository(this)
        ttsManager = TtsManager(this)
        Log.d(TAG, "AutoClickService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning.set(true)
        Log.d(TAG, "AutoClickService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning.set(false)
        stopClicking()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.set(false)
        stopClicking()
        ttsManager?.shutdown()
        ttsManager = null
        instance = null
        Log.d(TAG, "AutoClickService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceRunning.get() || event == null) return

        val settings = prefsRepo.getSettings()

        // Check if on-screen soft keyboard is currently open
        checkKeyboardActive(event)

        // App-Specific Auto-Launch Handler
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            handleAppSpecificAutoLaunch(packageName, settings.autoLaunchForTargetAppsEnabled, settings.targetAppPackages)
        }

        // Smart Accept Scan Handler
        if (isClickingActive.get() && settings.operatingMode == OperatingMode.SMART_ACCEPT) {
            if (!isKeyboardActive || !settings.autoPauseOnKeyboard) {
                handleSmartAcceptScan(settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)
            }
        }
    }

    private fun checkKeyboardActive(event: AccessibilityEvent) {
        if (!prefsRepo.getSettings().autoPauseOnKeyboard) {
            isKeyboardActive = false
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val inputMethodWindow = windows.find { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                isKeyboardActive = inputMethodWindow != null
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun handleAppSpecificAutoLaunch(packageName: String, autoLaunchEnabled: Boolean, targetPackagesStr: String) {
        if (!autoLaunchEnabled || packageName.isEmpty()) return

        val targets = targetPackagesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val isTargetApp = targets.any { packageName.equals(it, ignoreCase = true) || packageName.contains(it, ignoreCase = true) }

        if (isTargetApp && packageName != lastAutoLaunchedPackage) {
            lastAutoLaunchedPackage = packageName
            Log.d(TAG, "Detected target ride-hailing app: $packageName. Auto-launching Taptix Overlay...")

            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, OverlayService::class.java)
                startForegroundService(intent)

                if (prefsRepo.getSettings().ttsFeedbackEnabled) {
                    ttsManager?.speak("Ride hailing app detected. Taptix controls ready.")
                }
            }
        }
    }

    override fun onInterrupt() {
        stopClicking()
    }

    // --- Voice Command Controls ---

    fun triggerVoiceAccept() {
        Log.d(TAG, "Voice Command Trigger: ACCEPT")
        val settings = prefsRepo.getSettings()

        if (settings.ttsFeedbackEnabled) {
            ttsManager?.speak("Voice command: Accepting ride!")
        }

        handleSmartAcceptScan(settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)

        if (targetPoints.isNotEmpty()) {
            val point = targetPoints.first()
            performTap(point.x.toFloat(), point.y.toFloat())
        }
    }

    fun triggerVoiceDecline() {
        Log.d(TAG, "Voice Command Trigger: DECLINE")
        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Voice command: Declining ride.")
        }
        stopClicking()
    }

    fun triggerVoiceStart() {
        Log.d(TAG, "Voice Command Trigger: START")
        startClicking(targetPoints)
    }

    fun triggerVoiceStop() {
        Log.d(TAG, "Voice Command Trigger: STOP")
        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Taptix auto clicker paused.")
        }
        stopClicking()
    }

    // --- Gesture Execution Engine ---

    fun startClicking(targets: List<TargetPoint>) {
        if (targets.isEmpty() && prefsRepo.getSettings().operatingMode != OperatingMode.SMART_ACCEPT) {
            Log.w(TAG, "No targets defined for click loop")
            return
        }

        this.targetPoints = targets
        this.currentTargetIndex = 0
        isClickingActive.set(true)

        val settings = prefsRepo.getSettings()
        if (settings.ttsFeedbackEnabled) {
            ttsManager?.speak("Auto clicker activated")
        }

        scheduleNextClick()
    }

    fun stopClicking() {
        isClickingActive.set(false)
        clickRunnable?.let { mainHandler.removeCallbacks(it) }
        clickRunnable = null
    }

    fun isClicking(): Boolean = isClickingActive.get()

    private fun scheduleNextClick() {
        if (!isClickingActive.get()) return

        val settings = prefsRepo.getSettings()

        clickRunnable = Runnable {
            if (!isClickingActive.get()) return@Runnable

            when (settings.operatingMode) {
                OperatingMode.SINGLE_TARGET -> {
                    if (targetPoints.isNotEmpty()) {
                        val point = targetPoints.first()
                        performTap(point.x.toFloat(), point.y.toFloat())
                    }
                }

                OperatingMode.MULTI_TARGET -> {
                    if (targetPoints.isNotEmpty()) {
                        val point = targetPoints[currentTargetIndex]
                        performTap(point.x.toFloat(), point.y.toFloat())
                        currentTargetIndex = (currentTargetIndex + 1) % targetPoints.size
                    }
                }

                OperatingMode.SMART_ACCEPT -> {
                    // Periodic scan trigger in case window state didn't emit accessibility event
                    handleSmartAcceptScan(settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)
                }
            }

            if (isClickingActive.get()) {
                mainHandler.postDelayed(clickRunnable!!, settings.clickIntervalMs.coerceAtLeast(50L))
            }
        }

        mainHandler.post(clickRunnable!!)
    }

    /**
     * Programmatically performs a single tap at (x, y) coordinates.
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 50L) {
        if (isKeyboardActive && prefsRepo.getSettings().autoPauseOnKeyboard) {
            Log.d(TAG, "Keyboard active, skipping tap at ($x, $y)")
            return
        }
        if (x < 0 || y < 0) return

        try {
            val path = Path().apply {
                moveTo(x, y)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Tap completed at ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Tap cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "performTap error: ${e.message}")
        }
    }

    /**
     * Programmatically performs a swipe gesture from (startX, startY) to (endX, endY).
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350L) {
        if (isKeyboardActive && prefsRepo.getSettings().autoPauseOnKeyboard) {
            Log.d(TAG, "Keyboard active, skipping swipe")
            return
        }
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) return

        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe completed from ($startX, $startY) to ($endX, $endY)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Swipe cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "performSwipe error: ${e.message}")
        }
    }

    // --- Smart Accept Engine (Accessibility Tree OCR) ---

    private fun handleSmartAcceptScan(preset: PlatformPreset, customKeywords: String, autoPause: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastAcceptTime < COOLDOWN_MS) return // Cooldown to prevent spamming

        val rootNode = rootInActiveWindow ?: return

        val targetKeywords = if (preset == PlatformPreset.CUSTOM) {
            customKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        } else {
            preset.keywords.map { it.uppercase() }
        }

        val matchedNode = findNodeWithKeywords(rootNode, targetKeywords)
        if (matchedNode != null) {
            val rect = Rect()
            matchedNode.getBoundsInScreen(rect)

            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()

            if (rect.width() > 0 && rect.height() > 0) {
                lastAcceptTime = now
                Log.d(TAG, "Smart Accept matched! Performing action at ($centerX, $centerY)")

                if (preset.requiresSwipe) {
                    // Uber style horizontal swipe to accept
                    val startX = rect.left.toFloat() + 20f
                    val endX = rect.right.toFloat() - 20f
                    performSwipe(startX, centerY, endX, centerY)
                } else {
                    performTap(centerX, centerY)
                }

                val settings = prefsRepo.getSettings()
                if (settings.ttsFeedbackEnabled) {
                    ttsManager?.speak("Ride request accepted!")
                }

                // Update real-time floating trip overlay
                OverlayService.instance?.updateTripStatus(
                    com.example.taptix.model.TripStatusInfo(
                        phase = com.example.taptix.model.TripPhase.EN_ROUTE_PICKUP,
                        fare = "$18.50",
                        distance = "3.8 mi",
                        etaMinutes = 9,
                        pickupAddress = "En Route to Passenger Location",
                        dropoffAddress = "Destination Address"
                    )
                )

                if (autoPause) {
                    stopClicking()
                    onStateChangedListener?.invoke(false, settings.operatingMode)
                }
            }
        }
    }

    private fun findNodeWithKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val nodeText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").uppercase()

        if (nodeText.isNotEmpty()) {
            for (kw in keywords) {
                if (nodeText.contains(kw)) {
                    return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeWithKeywords(child, keywords)
            if (result != null) {
                return result
            }
        }

        return null
    }

    companion object {
        private const val TAG = "AutoClickService"
        private const val COOLDOWN_MS = 2500L

        @Volatile
        var instance: AutoClickService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        fun isAccessibilityPermissionGranted(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AutoClickService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }

        fun isServiceEnabled(): Boolean = instance != null
    }
}
