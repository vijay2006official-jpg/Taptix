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

    private var targetPoints: List<TargetPoint> = emptyList()

    private var currentTargetIndex = 0

    private var lastAcceptTime = 0L

    private var hasSoundPlayed = false
    private var cachedLocation: Pair<Double, Double>? = null  // Sound plays ONCE per accept, then stays silent

    private var lastAutoLaunchedPackage: String = ""

    private var isKeyboardActive = false

    var onStateChangedListener: ((isRunning: Boolean, mode: OperatingMode) -> Unit)? = null

    private var motionDetector: MotionSafetyDetector? = null
    private val isDrivingDetected = java.util.concurrent.atomic.AtomicBoolean(false)

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
        // Start motion safety detector if enabled in settings
        if (prefsRepo.getSettings().motionSafetyLockEnabled) { startMotionDetector() }

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

        stopMotionDetector()

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

        if (isClickingActive.get()) {

            if (!isKeyboardActive || !settings.autoPauseOnKeyboard) {

                handleSmartAcceptScan(event, settings.platformPreset, settings.customKeywords, settings.autoPauseOnAccept)

            }

        }

    }

    fun setClickingActive(active: Boolean) {

        isClickingActive.set(active)

        if (!active) {

            stopClicking()

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

                if (inputMethodWindow != null) {

                    val rect = Rect()

                    inputMethodWindow.getBoundsInScreen(rect)

                    isKeyboardActive = inputMethodWindow.isFocused && rect.height() > 300

                } else {

                    isKeyboardActive = false

                }

            } else {

                isKeyboardActive = false

            }

        } catch (e: Exception) {

            isKeyboardActive = false

        }

    }

    private fun handleAppSpecificAutoLaunch(packageName: String, autoLaunchEnabled: Boolean, targetPackagesStr: String) {

        if (!autoLaunchEnabled || packageName.isEmpty()) return

        val targets = targetPackagesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val isTargetApp = targets.any { packageName.equals(it, ignoreCase = true) || packageName.contains(it, ignoreCase = true) }

        if (isTargetApp && packageName != lastAutoLaunchedPackage) {

            lastAutoLaunchedPackage = packageName

            val currentSettings = prefsRepo.getSettings()
            if (currentSettings.floatingDockEnabled && currentSettings.masterControlEnabled && Settings.canDrawOverlays(this)) {

                Log.d(TAG, "Detected target ride-hailing app: $packageName. Showing enabled Taptix Floating Dock...")

                OverlayService.showService(this)

                if (currentSettings.ttsFeedbackEnabled) {

                    ttsManager?.speak("Ride hailing app detected. Taptix auto-accept ready.")

                }

            }

        }

    }

    override fun onInterrupt() {

        stopClicking()

    }

    private fun startMotionDetector() {

        if (motionDetector == null) {

            motionDetector = MotionSafetyDetector(this) { isDriving ->

                isDrivingDetected.set(isDriving)

                val msg = if (isDriving) "Vehicle motion detected. Safety lock activated." else "Vehicle stopped. Auto-accept ready."

                Log.d(TAG, msg)

                if (prefsRepo.getSettings().ttsFeedbackEnabled) { ttsManager?.speak(msg) }

            }

            motionDetector?.start()

        }

    }

    private fun stopMotionDetector() {

        motionDetector?.stop()

        motionDetector = null

        isDrivingDetected.set(false)

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

    fun updateTargetPoints(targets: List<TargetPoint>) {

        this.targetPoints = targets

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

                    val point = targetPoints.firstOrNull()

                    if (point != null) {

                        performTap(point.x.toFloat(), point.y.toFloat())

                    } else {

                        val dm = resources.displayMetrics

                        performTap(dm.widthPixels / 2f, dm.heightPixels * 0.78f)

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

                    // Smart Accept: ONLY scan accessibility tree + dispatch tap on the found node.

                    // Do NOT also tap the fixed overlay marker — that causes double-firing.

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

    fun performTap(x: Float, y: Float, durationMs: Long = 100L, onResult: ((Boolean) -> Unit)? = null) {

        if (isKeyboardActive && prefsRepo.getSettings().autoPauseOnKeyboard) {

            Log.d(TAG, "Keyboard active, skipping tap at ($x, $y)")

            onResult?.invoke(false)

            return

        }

        if (x < 0 || y < 0) {

            onResult?.invoke(false)

            return

        }

        try {

            val path = Path().apply {

                moveTo(x, y)

                lineTo(x, y + 1f)

            }

            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(20L, 200L))

            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {

                override fun onCompleted(gestureDescription: GestureDescription?) {

                    Log.d(TAG, "Tap completed at ($x, $y)")

                    mainHandler.post { onResult?.invoke(true) }

                }

                override fun onCancelled(gestureDescription: GestureDescription?) {

                    Log.w(TAG, "Tap cancelled at ($x, $y)")

                    mainHandler.post { onResult?.invoke(false) }

                }

            }, null)

            if (!dispatched) {

                Log.w(TAG, "dispatchGesture returned false for tap at ($x, $y)")

                mainHandler.post { onResult?.invoke(false) }

            }

        } catch (e: Exception) {

            Log.e(TAG, "performTap error: ${e.message}")

            mainHandler.post { onResult?.invoke(false) }

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

        // Never scan while foreground or active window belongs to Taptix or system settings
        val foregroundPkg = (event?.packageName?.toString() ?: "").lowercase()
        val activePkg = (rootInActiveWindow?.packageName?.toString() ?: "").lowercase()
        val ownPkg = packageName.lowercase()

        if (foregroundPkg.contains("taptix") || foregroundPkg == ownPkg ||
            activePkg.contains("taptix") || activePkg == ownPkg ||
            foregroundPkg.contains("settings") || activePkg.contains("settings") ||
            foregroundPkg.contains("systemui") || activePkg.contains("systemui")) {
            return
        }

        val settings = prefsRepo.getSettings()

        // Master Control gate — if master is OFF, do nothing at all

        if (!settings.masterControlEnabled) return

        // Shift Schedule gate (Phase 5 Item 30) — if outside scheduled shift, do nothing

        val schedule = prefsRepo.getScheduleConfig()

        if (!schedule.isWithinActiveWindow()) return

        // Motion Safety Lock gate — skip auto-accept while vehicle is moving

        if (settings.motionSafetyLockEnabled && isDrivingDetected.get()) return

        val now = System.currentTimeMillis()

        if (now - lastAcceptTime < COOLDOWN_MS) return

        // Build keyword list — in Ultra Mode accept EVERYTHING, bypass keywords

        val targetKeywords = if (settings.ultraModeEnabled) {

            // Ultra Mode: match any button-like node

            listOf("ACCEPT", "CONFIRM", "TAKE RIDE", "START DUTY", "CAPTAIN ACCEPT", "TAP TO ACCEPT", "SWIPE TO ACCEPT", "ACCEPT ORDER", "ACCEPT TRIP", "ACCEPT BOOKING", "ACCEPT OFFER")

        } else if (preset == PlatformPreset.CUSTOM) {

            customKeywords.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }

        } else {

            (preset.keywords + PlatformPreset.UNIVERSAL.keywords).map { it.uppercase() }.distinct()

        }

        // Collect all root nodes to scan

        val rootsToScan = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { rootsToScan.add(it) }

        event?.source?.let { src -> if (!rootsToScan.contains(src)) rootsToScan.add(src) }

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                for (w in windows) {

                    val root = w.root

                    if (root != null && !rootsToScan.contains(root)) rootsToScan.add(root)

                }

            }

        } catch (e: Exception) {

            Log.w(TAG, "Failed to query windows: ${e.message}")

        }

        for (root in rootsToScan) {

            if (findAndExecuteAccept(root, targetKeywords, settings)) {

                lastAcceptTime = now

                break

            }

        }

    }

    private fun findAndExecuteAccept(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>,
        settings: com.example.taptix.data.AppSettings = prefsRepo.getSettings()
    ): Boolean {
        // Step 0: Ensure root node and matched node NEVER belong to Taptix, System UI, or Settings
        val rootPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        val ownPkg = packageName.lowercase()
        if (rootPkg.contains("taptix") || rootPkg == ownPkg || rootPkg.contains("systemui") || rootPkg.contains("settings")) {
            return false
        }

        val matchedNode = findNodeWithKeywords(rootNode, keywords) ?: return false

        val matchedPkg = (matchedNode.packageName?.toString() ?: "").lowercase()
        if (matchedPkg.contains("taptix") || matchedPkg == ownPkg || matchedPkg.contains("systemui") || matchedPkg.contains("settings")) {
            return false
        }

        // Strict Target App Whitelist: ONLY accept genuine driver partner apps
        val defaultRideApps = listOf(
            "com.ubercab.driver",
            "com.olacabs.driver",
            "com.rapido.captain",
            "com.rapido.passenger",
            "com.indriver",
            "com.moveinsync",
            "com.nammayatri",
            "in.porter",
            "com.theporter",
            "me.lyft.driver",
            "com.grabtaxi.driver2",
            "com.dd.driver"
        )
        val customTargets = settings.targetAppPackages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val allTargets = (defaultRideApps + customTargets).distinct()

        val candidatePkg = if (matchedPkg.isNotBlank()) matchedPkg else rootPkg
        val isPartnerApp = allTargets.any { target ->
            candidatePkg.contains(target) || rootPkg.contains(target) || matchedPkg.contains(target)
        }

        if (!isPartnerApp) {
            // Not a supported ride partner app, ignore
            return false
        }

        val appPkg = if (candidatePkg.isNotBlank()) candidatePkg else "ride_partner"

        // Per-app profile: check if auto-accept is enabled for this specific app
        val profile = prefsRepo.getAppProfile(appPkg)
        if (profile != null && !profile.enabled) {
            Log.d(TAG, "Per-app profile disabled for $appPkg, skipping")
            return false
        }

        val rect = Rect()

        matchedNode.getBoundsInScreen(rect)

        if (rect.width() <= 0 || rect.height() <= 0) return false

        // Feature 3 & 4: Structured Offer Parsing and Rule Evaluation

        val offer = com.example.taptix.engine.OfferParser.parse(rootNode, appPkg)

        val smartRules = prefsRepo.getSmartAcceptRules()

        val targetAreasOn = prefsRepo.isTargetAreasEnabled()
        val preferredAreas = prefsRepo.getSettings().preferredAreas
        val effectiveRules = if (targetAreasOn && preferredAreas.isNotBlank()) {
            smartRules.copy(
                destinationFilterEnabled = true,
                destinationKeywords = preferredAreas
            )
        } else {
            smartRules
        }

        val activeZones = if (settings.mapZoneEnabled) prefsRepo.getActiveZones() else emptyList()

        val driverLoc = getLastKnownLocation()

        val evalResult = com.example.taptix.engine.OfferRuleEngine.evaluate(

            offer = offer,

            rules = effectiveRules,

            activeZones = activeZones,

            driverLocation = driverLoc,

            includeKeywords = keywords,

            excludeKeywords = settings.negativeKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },

            ultraMode = settings.ultraModeEnabled

        )

        if (!evalResult.isAccepted) {

            val reason = evalResult.rejectedReason ?: "Offer rejected by smart rules"

            Log.d(TAG, "Offer rejected: $reason")

            val status = when {

                reason.contains("negative keyword", ignoreCase = true) -> com.example.taptix.model.LogStatus.REJECTED_KEYWORD

                reason.contains("work zone", ignoreCase = true) || reason.contains("location unavailable", ignoreCase = true) -> com.example.taptix.model.LogStatus.OUTSIDE_ZONE

                else -> com.example.taptix.model.LogStatus.ERROR

            }

            com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(

                appName = getReadableAppName(appPkg),

                actionText = "Skipped offer",

                status = status,

                details = reason

            )

            return false

        }

        val centerX = rect.centerX().toFloat()

        val centerY = rect.centerY().toFloat()

        val matchText = (matchedNode.text ?: matchedNode.contentDescription ?: "Order").toString()

        Log.d(TAG, "🔥 Accept candidate matched: '$matchText' at $rect")

        // Log ATTEMPTED first (Rule 1)

        com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(

            appName = getReadableAppName(appPkg),

            actionText = "Attempting accept: $matchText",

            status = com.example.taptix.model.LogStatus.ATTEMPTED,

            details = if (settings.ultraModeEnabled) "Ultra Mode Active" else "Keyword Matched"

        )

        // Single tap path (Rule 2): Swipe if slider, else ACTION_CLICK, gesture tap only as fallback

        val nodeText = (matchedNode.text?.toString() ?: matchedNode.contentDescription?.toString() ?: "").uppercase()

        val isSwipe = nodeText.contains("SWIPE") || (rect.width() > 500 && rect.height() < 250)

        var clickSucceeded = false

        if (isSwipe) {

            val startX = (rect.left + 60).toFloat()

            val endX = (rect.right - 60).toFloat().coerceAtLeast(startX + 180f)

            performSwipe(startX, centerY, endX, centerY, durationMs = 280L)

            clickSucceeded = true

        } else {

            // Priority 1: Native accessibility click

            var clickableNode: AccessibilityNodeInfo? = matchedNode

            while (clickableNode != null) {

                if (clickableNode.isClickable) {

                    val ok = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    if (ok) {

                        clickSucceeded = true

                        break

                    }

                }

                clickableNode = clickableNode.parent

            }

            // Priority 2: Gesture tap fallback ONLY if ACTION_CLICK failed or wasn't clickable

            if (!clickSucceeded) {

                performTap(centerX, centerY, durationMs = 100L)

                clickSucceeded = true

            }

        }

        if (clickSucceeded) {

            // Rule 1: Log ACCEPTED only after click path executed

            com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(

                appName = getReadableAppName(appPkg),

                actionText = "Accepted: $matchText",

                status = com.example.taptix.model.LogStatus.ACCEPTED,

                details = "Action confirmed"

            )

            notifyRideAccepted()

            if (settings.autoPauseOnAccept) {
                setClickingActive(false)
                OverlayService.instance?.let { ov ->
                    mainHandler.post { ov.stopClickingSequence() }
                }
            }

            // Force 30-second pause after a successful accept to prevent rapid re-triggering

            lastAcceptTime = System.currentTimeMillis() + 28500L

            return true

        } else {

            com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(

                appName = getReadableAppName(appPkg),

                actionText = "Failed accept: $matchText",

                status = com.example.taptix.model.LogStatus.FAILED,

                details = "Node could not be clicked"

            )

            return false

        }

    }

    // Haversine formula — calculate distance in km between two GPS coordinates

    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {

        val R = 6371.0

        val dLat = Math.toRadians(lat2 - lat1)

        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +

                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *

                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    }

    @Suppress("MissingPermission")

    private fun getLastKnownLocation(): Pair<Double, Double>? {

        return try {

            val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager

            val providers = listOf(

                android.location.LocationManager.GPS_PROVIDER,

                android.location.LocationManager.NETWORK_PROVIDER,

                android.location.LocationManager.PASSIVE_PROVIDER

            )

            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    cachedLocation = Pair(loc.latitude, loc.longitude)
                    return cachedLocation
                }
            }
            cachedLocation

        } catch (e: Exception) {

            Log.w(TAG, "Could not get location: ${e.message}")

            null

        }

    }

    private fun notifyRideAccepted() {

        val settings = prefsRepo.getSettings()

        try {

            // Haptic feedback — only if vibrationOnAccept is ON

            if (settings.vibrationOnAccept) {

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

            }

        } catch (e: Exception) {

            Log.e(TAG, "Vibration notification failed: ${e.message}")

        }

        // Audio TTS — plays ONCE per accept, then SILENT until next new booking

        if (!hasSoundPlayed && prefsRepo.getSettings().ttsFeedbackEnabled) {

            hasSoundPlayed = true

            ttsManager?.speak("Ride accepted by Taptix!")

            // Reset flag after cooldown so it can speak again for next booking

            mainHandler.postDelayed({ hasSoundPlayed = false }, 30000L) // 30s silence after each TTS

        }

        // UI Toast feedback

        mainHandler.post {

            Toast.makeText(applicationContext, "🚀 Taptix: Ride Accepted!", Toast.LENGTH_SHORT).show()

        }

        // Real-time accept toast and vibration delivered

    }

    private fun findNodeWithKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {

        val nodeText = (node.text?.toString() ?: "").uppercase().trim()

        val nodeDesc = (node.contentDescription?.toString() ?: "").uppercase().trim()

        if (nodeText.isNotBlank() || nodeDesc.isNotBlank()) {

            val words = "$nodeText $nodeDesc".split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }

            for (kw in keywords) {

                val cleanKw = kw.trim().uppercase()

                if (cleanKw.isBlank()) continue

                val isMatch = if (cleanKw.contains(" ")) {

                    // Multi-word exact phrase match (e.g. "TAP TO ACCEPT", "ACCEPT RIDE")

                    nodeText.contains(cleanKw) || nodeDesc.contains(cleanKw)

                } else {

                    // Single word: MUST match exact whole word to prevent false hits like "GO" in "GOOGLE" or "OK" in "BOOKING"

                    words.contains(cleanKw)

                }

                if (isMatch) {

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

            // Recycle non-matching child to prevent memory leaks

            try { child.recycle() } catch (_: Exception) {}

        }

        return null

    }

    private fun findAnyKeywordInTree(node: AccessibilityNodeInfo, keywords: List<String>): String? {

        val nodeText = (node.text?.toString() ?: "").uppercase().trim()

        val nodeDesc = (node.contentDescription?.toString() ?: "").uppercase().trim()

        if (nodeText.isNotBlank() || nodeDesc.isNotBlank()) {

            val words = "$nodeText $nodeDesc".split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }

            for (kw in keywords) {

                val cleanKw = kw.trim().uppercase()

                if (cleanKw.isBlank()) continue

                val isMatch = if (cleanKw.contains(" ")) {

                    nodeText.contains(cleanKw) || nodeDesc.contains(cleanKw)

                } else {

                    words.contains(cleanKw)

                }

                if (isMatch) return cleanKw

            }

        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i) ?: continue

            val found = findAnyKeywordInTree(child, keywords)

            if (found != null) return found

            try { child.recycle() } catch (_: Exception) {}

        }

        return null

    }

    private fun getReadableAppName(packageName: String): String {

        return when {

            packageName.contains("uber", ignoreCase = true) -> "Uber Driver"

            packageName.contains("ola", ignoreCase = true) -> "Ola Driver"

            packageName.contains("rapido", ignoreCase = true) -> "Rapido Captain"

            packageName.contains("indriver", ignoreCase = true) -> "inDrive"

            packageName.contains("nammayatri", ignoreCase = true) -> "Namma Yatri"

            packageName.contains("porter", ignoreCase = true) -> "Porter"

            packageName.contains("swiggy", ignoreCase = true) -> "Swiggy"

            packageName.contains("zomato", ignoreCase = true) -> "Zomato"

            packageName.contains("shadowfax", ignoreCase = true) -> "Shadowfax"

            packageName.isNotBlank() -> packageName.substringAfterLast('.')

            else -> "Ride App"

        }

    }

    companion object {

        private const val TAG = "AutoClickService"

        private const val COOLDOWN_MS = 1500L

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
