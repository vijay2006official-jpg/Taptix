package com.example.taptix.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.TargetPoint
import com.example.taptix.util.TtsManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core Accessibility Service for Taptix.
 * Handles programmatic gesture execution, real-time background ride detection,
 * direct Action-Click dispatching, and sensory driver feedback.
 */
class AutoClickService : AccessibilityService() {

    private lateinit var prefsRepo: PreferencesRepository
    private var ttsManager: TtsManager? = null

    private val isServiceRunning = AtomicBoolean(false)
    private val isClickingActive = AtomicBoolean(true) // Active by default when Accessibility is enabled

    private val mainHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null
    private var periodicScanRunnable: Runnable? = null

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
        isClickingActive.set(true)
        Log.d(TAG, "AutoClickService connected and activated")

        startPeriodicScanIfNeeded()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning.set(false)
        stopClicking()
        stopPeriodicScan()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.set(false)
        stopClicking()
        stopPeriodicScan()
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

        // Smart Accept Scan Handler: Runs on any window change or content update
        if (isClickingActive.get() && settings.operatingMode == OperatingMode.SMART_ACCEPT) {
            if (!isKeyboardActive || !settings.autoPauseOnKeyboard) {
                handleSmartAcceptScan(event, settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)
            }
        }
    }

    fun setClickingActive(active: Boolean) {
        isClickingActive.set(active)
        if (active) {
            startPeriodicScanIfNeeded()
        } else {
            stopClicking()
            stopPeriodicScan()
        }
    }

    private fun startPeriodicScanIfNeeded() {
        stopPeriodicScan()
        periodicScanRunnable = object : Runnable {
            override fun run() {
                if (isServiceRunning.get() && isClickingActive.get()) {
                    val settings = prefsRepo.getSettings()
                    if (settings.operatingMode == OperatingMode.SMART_ACCEPT && (!isKeyboardActive || !settings.autoPauseOnKeyboard)) {
                        handleSmartAcceptScan(null, settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)
                    }
                    mainHandler.postDelayed(this, 350L) // Scan every 350ms for instant reaction
                }
            }
        }
        mainHandler.post(periodicScanRunnable!!)
    }

    private fun stopPeriodicScan() {
        periodicScanRunnable?.let { mainHandler.removeCallbacks(it) }
        periodicScanRunnable = null
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
                    ttsManager?.speak("Ride hailing app detected. Taptix auto-accept ready.")
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

        handleSmartAcceptScan(null, settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)

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
        setClickingActive(true)
        startClicking(targetPoints)
    }

    fun triggerVoiceStop() {
        Log.d(TAG, "Voice Command Trigger: STOP")
        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Taptix paused.")
        }
        setClickingActive(false)
    }

    // --- Gesture Execution Engine ---

    fun startClicking(targets: List<TargetPoint>) {
        this.targetPoints = targets
        this.currentTargetIndex = 0
        setClickingActive(true)

        val settings = prefsRepo.getSettings()
        if (settings.ttsFeedbackEnabled) {
            ttsManager?.speak("Taptix auto clicker active")
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
                    handleSmartAcceptScan(null, settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)
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
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
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

    // --- Smart Accept Engine (Accessibility Tree OCR & Universal Matcher) ---

    private fun handleSmartAcceptScan(
        event: AccessibilityEvent?,
        preset: PlatformPreset,
        customKeywords: String,
        autoPause: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (now - lastAcceptTime < COOLDOWN_MS) return // Cooldown to avoid rapid repeated accepts

        // Build comprehensive keywords list combining preset + universal
        val targetKeywords = if (preset == PlatformPreset.CUSTOM) {
            customKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        } else {
            (preset.keywords + PlatformPreset.UNIVERSAL.keywords).map { it.uppercase() }.distinct()
        }

        // Collect all potential root nodes (active window, event source, and all top-level application windows)
        val rootsToScan = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { rootsToScan.add(it) }

        event?.source?.let { eventSource ->
            if (!rootsToScan.contains(eventSource)) {
                rootsToScan.add(eventSource)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (w in windows) {
                    val root = w.root
                    if (root != null && !rootsToScan.contains(root)) {
                        rootsToScan.add(root)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query windows: ${e.message}")
        }

        for (root in rootsToScan) {
            if (findAndExecuteAccept(root, targetKeywords)) {
                lastAcceptTime = now
                if (autoPause) {
                    // Maintain active scanning unless explicitly stopped by user
                }
                break
            }
        }
    }

    private fun findAndExecuteAccept(rootNode: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val matchedNode = findNodeWithKeywords(rootNode, keywords) ?: return false

        val rect = Rect()
        matchedNode.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false

        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        Log.d(TAG, "🔥 Smart Accept match found! Text: '${matchedNode.text ?: matchedNode.contentDescription}' at $rect")

        // Step 1: Direct native accessibility click on the clickable parent/ancestor
        var clickableNode: AccessibilityNodeInfo? = matchedNode
        var actionClickSuccess = false
        while (clickableNode != null) {
            if (clickableNode.isClickable) {
                actionClickSuccess = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "ACTION_CLICK executed on ${clickableNode.className}: success=$actionClickSuccess")
                if (actionClickSuccess) break
            }
            clickableNode = clickableNode.parent
        }

        // Step 2: Check if this is a swipe slider (e.g., Uber or Rapido "Swipe to accept")
        val nodeText = (matchedNode.text?.toString() ?: matchedNode.contentDescription?.toString() ?: "").uppercase()
        val isSwipe = nodeText.contains("SWIPE") || (rect.width() > 500 && rect.height() < 250)

        if (isSwipe) {
            val startX = (rect.left + 60).toFloat()
            val endX = (rect.right - 60).toFloat().coerceAtLeast(startX + 180f)
            performSwipe(startX, centerY, endX, centerY, durationMs = 280L)
        }

        // Step 3: Always also dispatch a physical gesture tap at the exact center of the button!
        performTap(centerX, centerY, durationMs = 50L)

        // Step 4: Sensory driver feedback (Vibration, Audio TTS, and Screen Toast)
        notifyRideAccepted()

        return true
    }

    private fun notifyRideAccepted() {
        try {
            // Haptic feedback (400ms pulse)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(400)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration notification failed: ${e.message}")
        }

        // Audio TTS speech feedback
        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Ride accepted by Taptix!")
        }

        // UI Toast feedback
        mainHandler.post {
            Toast.makeText(applicationContext, "🚀 Taptix: Ride Accepted!", Toast.LENGTH_SHORT).show()
        }

        // Update floating trip overlay
        OverlayService.instance?.updateTripStatus(
            com.example.taptix.model.TripStatusInfo(
                phase = com.example.taptix.model.TripPhase.EN_ROUTE_PICKUP,
                fare = "Active Ride",
                distance = "Pickup",
                etaMinutes = 5,
                pickupAddress = "En Route to Passenger Location",
                dropoffAddress = "Destination Address"
            )
        )
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
