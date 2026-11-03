package com.example.taptix.service















import android.accessibilityservice.AccessibilityService



import android.accessibilityservice.AccessibilityServiceInfo















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



    private val offerGuard = com.example.taptix.engine.DuplicateOfferGuard()
    @Volatile private var currentBookingStateMachine: com.example.taptix.engine.BookingStateMachine? = null
    private var lastEventScanTime = 0L

















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
        com.example.taptix.automation.safety.AutomationSafetyController.initialize(applicationContext)
        com.example.taptix.automation.safety.AutomationSafetyController.registerStopListener {
            mainHandler.post {
                currentBookingStateMachine?.resetOnInterruption("EMERGENCY_STOP")
                currentBookingStateMachine = null
                clickRunnable?.let { mainHandler.removeCallbacks(it) }
                clickRunnable = null
                mainHandler.removeCallbacksAndMessages(null)
            }
        }















    }















    override fun onServiceConnected() {















        super.onServiceConnected()















        isServiceRunning.set(true)















        val masterEnabled = prefsRepo.getSettings().masterControlEnabled
        com.example.taptix.automation.safety.AutomationSafetyController.notifyServiceConnected(masterEnabled)
        isClickingActive.set(com.example.taptix.automation.safety.AutomationSafetyController.canProcessScreen())















        Log.d(TAG, "AutoClickService connected and activated")
        engineState = com.example.taptix.engine.AutomationEngineState.CONNECTED_IDLE
        onEngineStateChanged?.invoke(engineState)



        applyPartnerPackageFilter()







        // Start motion safety detector if enabled in settings







        if (prefsRepo.getSettings().motionSafetyLockEnabled) { startMotionDetector() }







        startLocationUpdates()















    }















    override fun onUnbind(intent: Intent?): Boolean {















        currentBookingStateMachine?.resetOnInterruption("SERVICE_DESTROYED")
        currentBookingStateMachine = null
        isServiceRunning.set(false)
        com.example.taptix.automation.safety.AutomationSafetyController.notifyServiceDisconnected()
        isClickingActive.set(false)
        engineState = com.example.taptix.engine.AutomationEngineState.DISCONNECTED
        onEngineStateChanged?.invoke(engineState)
        mainHandler.removeCallbacksAndMessages(null)















        stopClicking()















        instance = null















        return super.onUnbind(intent)















    }















    override fun onDestroy() {















        super.onDestroy()















        isServiceRunning.set(false)
        com.example.taptix.automation.safety.AutomationSafetyController.notifyServiceDisconnected()
        isClickingActive.set(false)
        engineState = com.example.taptix.engine.AutomationEngineState.DISCONNECTED
        onEngineStateChanged?.invoke(engineState)
        mainHandler.removeCallbacksAndMessages(null)















        stopClicking()















        stopMotionDetector()







        stopLocationUpdates()















        ttsManager?.shutdown()















        ttsManager = null















        instance = null















        Log.d(TAG, "AutoClickService destroyed")















    }















    override fun onAccessibilityEvent(event: AccessibilityEvent?) {















        if (!isServiceRunning.get() || event == null) return















        val settings = prefsRepo.getSettings()
        // Early exit if automation is inactive
        if (!settings.masterControlEnabled || !com.example.taptix.automation.safety.AutomationSafetyController.canProcessScreen()) return
        // Prevent accidental gesture taps when device is locked (pocket protection)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isKeyguardLocked == true) return

        val eventPkg = (event.packageName?.toString() ?: "").lowercase()
        // Discard irrelevant system or background events early for battery conservation
        if (eventPkg.isEmpty() ||
            eventPkg == "android" ||
            eventPkg.startsWith("com.android.systemui") ||
            eventPkg.startsWith("com.google.android.inputmethod") ||
            (eventPkg == packageName.lowercase() && !settings.ultraModeEnabled)) {
            return
        }

        // Re-enabled target package restriction: only evaluate events from partner apps or user target apps
        val allowedPackages = com.example.taptix.engine.PartnerPackages.merged(settings.targetAppPackages, packageName)
        val isTargetApp = allowedPackages.any { pkg ->
            val p = pkg.lowercase()
            eventPkg == p || eventPkg.startsWith("$p.")
        }
        if (!isTargetApp && !settings.ultraModeEnabled) {
            return
        }

        // Throttle high-frequency window chatter within 35ms
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastEventScanTime < 35L) return
        lastEventScanTime = nowMs















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















        if (active) {
            val masterEnabled = prefsRepo.getSettings().masterControlEnabled
            val ok = com.example.taptix.automation.safety.AutomationSafetyController.requestEnable(
                hasAccessibilityService = true,
                masterEnabled = masterEnabled,
                isUserExplicitAction = true
            )
            isClickingActive.set(ok && com.example.taptix.automation.safety.AutomationSafetyController.canProcessScreen())
            engineState = if (ok) com.example.taptix.engine.AutomationEngineState.CONNECTED_IDLE else com.example.taptix.engine.AutomationEngineState.PAUSED_BY_USER
        } else {
            stopClicking()
            engineState = com.example.taptix.engine.AutomationEngineState.PAUSED_BY_USER
        }
        onEngineStateChanged?.invoke(engineState)
        return















        if (!active) {
            mainHandler.removeCallbacksAndMessages(null)
            stopClicking()
            engineState = com.example.taptix.engine.AutomationEngineState.PAUSED_BY_USER
        } else {
            engineState = com.example.taptix.engine.AutomationEngineState.CONNECTED_IDLE
        }
        onEngineStateChanged?.invoke(engineState)















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
        currentBookingStateMachine?.resetOnInterruption("ACCESSIBILITY_SERVICE_INTERRUPTED")
        currentBookingStateMachine = null
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















        com.example.taptix.automation.safety.AutomationSafetyController.emergencyStop("STOP_CLICKING")
        isClickingActive.set(false)
        prefsRepo.saveMasterControl(false)
        mainHandler.removeCallbacksAndMessages(null)















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















    fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 100L,
        onResult: ((Boolean) -> Unit)? = null,
        actionGen: Long = com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration
    ) {
        if (!com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(actionGen)) {
            Log.w(TAG, "[SafetyController] performTap aborted: state=${com.example.taptix.automation.safety.AutomationSafetyController.state}, gen=$actionGen vs curr=${com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration}")
            onResult?.invoke(false)
            return
        }















        if (isKeyboardActive && prefsRepo.getSettings().autoPauseOnKeyboard) {















            Log.d(TAG, "Keyboard active, skipping tap at ($x, $y)")















            onResult?.invoke(false)















            return















        }















        val dm = resources.displayMetrics
        if (x <= 0f || y <= 0f || x > dm.widthPixels || y > dm.heightPixels) {
            Log.w(TAG, "Coordinates ($x, $y) out of screen bounds (${dm.widthPixels}x${dm.heightPixels}); skipping tap")
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















    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        actionGen: Long = com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration
    ) {
        if (!com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(actionGen)) {
            Log.w(TAG, "[SafetyController] performSwipe aborted: state=${com.example.taptix.automation.safety.AutomationSafetyController.state}, gen=$actionGen vs curr=${com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration}")
            return
        }















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







            foregroundPkg.contains("settings") ||







            (foregroundPkg.contains("systemui") && activePkg.contains("systemui"))) {







            return







        }















        val settings = prefsRepo.getSettings()















        // Master Control gate — if master is OFF, do nothing at all















        if (!settings.masterControlEnabled) return















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















        if (rootsToScan.isEmpty()) {
            event?.source?.let { src ->
                var top = src
                while (top.parent != null) {
                    top = top.parent ?: break
                }
                rootsToScan.add(top)
            }
        }















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















    /** Restricts accessibility events to supported partner apps plus user-configured target apps. */

    private fun applyPartnerPackageFilter() {

        try {

            val info = serviceInfo ?: return

            val custom = prefsRepo.getSettings().targetAppPackages

            info.packageNames = com.example.taptix.engine.PartnerPackages.merged(custom, packageName)

            serviceInfo = info

            Log.i(TAG, "[TaptixAutoAccept] Accessibility service listening to ${info.packageNames?.size ?: 0} packages")

        } catch (e: Exception) {

            Log.w(TAG, "[TaptixAutoAccept] Could not apply package filter: ${e.message}")

        }

    }



    /**

     * @return true if an accept button is still shown by [appPkg], false if the booking popup is gone,

     * null if the screen could not be read.

     */

    private fun isAcceptButtonStillVisible(appPkg: String, keywords: List<String>): Boolean? {

        val roots = mutableListOf<AccessibilityNodeInfo>()

        try {

            windows.forEach { w -> w.root?.let { roots.add(it) } }

        } catch (_: Exception) { }

        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }

        if (roots.isEmpty()) return null

        val appRoots = roots.filter { (it.packageName?.toString() ?: "").equals(appPkg, ignoreCase = true) }

        if (appRoots.isEmpty()) return false

        return appRoots.any { findNodeWithKeywords(it, keywords) != null }

    }



    private fun verifyAcceptOutcome(
        appPkg: String,
        keywords: List<String>,
        matchText: String,
        clickMethod: com.example.taptix.engine.ClickMethod,
        settings: com.example.taptix.data.AppSettings,
        centerX: Float = 0f,
        centerY: Float = 0f,
        retryAttempt: Int = 0,
        fare: Double = 0.0,
        distanceKm: Double = 0.0,
        destination: String = "",
        stateMachine: com.example.taptix.engine.BookingStateMachine? = null,
        profile: com.example.taptix.automation.partner.PartnerUiProfile? = null,
        dispatchTimestamp: Long = 0L
    ) {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows.forEach { w -> w.root?.let { roots.add(it) } }
        } catch (_: Exception) { }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }

        val appRoots = roots.filter { (it.packageName?.toString() ?: "").equals(appPkg, ignoreCase = true) }
        val verifyKeywords = if (matchText.isNotBlank()) listOf(matchText) else listOf("ACCEPT", "CONFIRM", "TAP TO ACCEPT", "SWIPE TO ACCEPT")
        val stillVisibleNode = if (appRoots.isNotEmpty()) {
            appRoots.mapNotNull { findNodeWithKeywords(it, verifyKeywords) }.firstOrNull()
        } else null

        val isButtonStillVisible = stillVisibleNode != null
        val isButtonEnabled = stillVisibleNode?.isEnabled ?: false

        val resolvedProfile = profile ?: com.example.taptix.automation.partner.PartnerProfileResolver.resolve(
            appPkg,
            settings.targetAppPackages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        )
        val positiveKeywords = ((resolvedProfile?.positiveConfirmationKeywords ?: emptyList()) + com.example.taptix.engine.AcceptVerifier.POSITIVE_CONFIRMATION_KEYWORDS).distinct()
        val negativeKeywords = ((resolvedProfile?.cancellationKeywords ?: emptyList()) + com.example.taptix.engine.AcceptVerifier.EXPIRY_OR_CANCELLATION_KEYWORDS).distinct()

        // Check for positive confirmation signals across the app screen tree (profile-specific + global)
        val hasPositiveConfirmation = appRoots.any { root ->
            findAnyKeywordInTree(root, positiveKeywords) != null
        }

        // Check for cancellation or expiry signals across the app screen tree (profile-specific + global)
        val hasNegativeSignal = appRoots.any { root ->
            findAnyKeywordInTree(root, negativeKeywords) != null
        }

        val outcome = if (roots.isEmpty() || appRoots.isEmpty()) {
            com.example.taptix.engine.AcceptOutcome.UNKNOWN
        } else {
            com.example.taptix.engine.AcceptVerifier.evaluate(
                acceptButtonStillVisible = isButtonStillVisible,
                positiveConfirmationPresent = hasPositiveConfirmation,
                negativeOrCancelledPresent = hasNegativeSignal
            )
        }

        val logs = com.example.taptix.data.ActivityLogManager.getInstance(applicationContext)

        when (outcome) {
            com.example.taptix.engine.AcceptOutcome.CONFIRMED -> {
                stateMachine?.onVerificationResult(outcome)
                stateMachine?.onBookingCompleted()

                Log.i(TAG, "[TaptixAutoAccept] Verified: positive acceptance confirmed after $clickMethod")

                logs.addLog(
                    appName = getReadableAppName(appPkg),
                    actionText = "Accepted: $matchText",
                    status = com.example.taptix.model.LogStatus.ACCEPTED,
                    details = "Positive acceptance confirmed ($clickMethod)" + if (fare > 0.0) " [\u20b9$fare]" else "",
                    fare = if (fare > 0.0) fare else null,
                    distanceKm = if (distanceKm > 0.0) distanceKm else null,
                    destination = destination
                )

                if (fare > 0.0 || distanceKm > 0.0) {
                    try {
                        com.example.taptix.data.AnalyticsManager.getInstance(applicationContext).recordBooking(
                            wasAccepted = true,
                            isCompleted = true,
                            distanceKm = distanceKm
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record accepted ride in AnalyticsManager", e)
                    }
                }

                notifyRideAccepted()

                if (settings.autoPauseOnAccept) {
                    setClickingActive(false)
                    OverlayService.instance?.let { ov -> ov.stopClickingSequence() }
                }
            }

            com.example.taptix.engine.AcceptOutcome.NOT_CONFIRMED -> {
                val canRetrySafely = com.example.taptix.engine.ControlledRetryPolicy.isRetrySafe(
                    currentAttempts = retryAttempt,
                    outcome = outcome,
                    isButtonStillVisible = isButtonStillVisible,
                    isButtonEnabled = isButtonEnabled,
                    isForegroundTargetApp = appRoots.isNotEmpty(),
                    hasNegativeSignal = hasNegativeSignal,
                    hasPositiveConfirmation = hasPositiveConfirmation
                )

                val currentGen = com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration
                if (canRetrySafely && centerX > 0f && centerY > 0f && com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(currentGen)) {
                    Log.i(TAG, "[TaptixAutoAccept] Button still visible and enabled; executing safe retry #1")
                    stateMachine?.onClickAttempted(com.example.taptix.engine.ClickMethod.GESTURE_FALLBACK)
                    performTap(centerX, centerY, durationMs = 60L, actionGen = currentGen)
                    mainHandler.postDelayed({
                        verifyAcceptOutcome(
                            appPkg, keywords, matchText, clickMethod, settings,
                            centerX, centerY, retryAttempt = retryAttempt + 1,
                            fare, distanceKm, destination, stateMachine, resolvedProfile,
                            dispatchTimestamp = System.currentTimeMillis()
                        )
                    }, com.example.taptix.engine.AcceptVerifier.RETRY_VERIFY_DELAY_MS)
                    return
                }

                stateMachine?.onVerificationResult(outcome)

                val unconfirmedReason = when {
                    hasNegativeSignal -> "Offer expired or cancelled by passenger/platform"
                    isButtonStillVisible && !isButtonEnabled -> "First click processing; button disabled (avoided duplicate action)"
                    isButtonStillVisible -> "Button remained visible after tap/retry — not confirmed"
                    !hasPositiveConfirmation -> "Booking popup closed without positive acceptance confirmation"
                    else -> "Acceptance could not be verified"
                }

                Log.w(TAG, "[TaptixAutoAccept] Accept NOT confirmed: $unconfirmedReason")
                logs.addLog(
                    appName = getReadableAppName(appPkg),
                    actionText = "Attempted: $matchText",
                    status = com.example.taptix.model.LogStatus.ATTEMPTED,
                    details = "$unconfirmedReason ($clickMethod)" + if (fare > 0.0) " [\u20b9$fare]" else "",
                    fare = if (fare > 0.0) fare else null,
                    distanceKm = if (distanceKm > 0.0) distanceKm else null,
                    destination = destination
                )
            }

            com.example.taptix.engine.AcceptOutcome.UNKNOWN -> {
                stateMachine?.onVerificationResult(outcome)
                Log.w(TAG, "[TaptixAutoAccept] Could not read screen to verify accept ($clickMethod)")

                logs.addLog(
                    appName = getReadableAppName(appPkg),
                    actionText = "Tap sent: $matchText",
                    status = com.example.taptix.model.LogStatus.ATTEMPTED,
                    details = "Screen could not be read to verify result ($clickMethod)"
                )
            }
        }
    }

    private fun findAndExecuteAccept(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>,
        settings: com.example.taptix.data.AppSettings = prefsRepo.getSettings()
    ): Boolean {
        // Step 0: Ensure root node and matched node NEVER belong to Taptix, System UI, or Settings
        val rootPkg = (rootNode.packageName?.toString() ?: "").trim().lowercase()
        val ownPkg = packageName.lowercase()
        if (rootPkg.contains("taptix") || rootPkg == ownPkg || rootPkg.contains("systemui") || rootPkg.contains("settings")) {
            return false
        }

        val actionGen = com.example.taptix.automation.safety.AutomationSafetyController.currentGeneration
        if (!com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(actionGen)) {
            return false
        }

        val customTargets = settings.targetAppPackages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // Step 1: Resolve partner profile candidate package
        var candidatePkg = rootPkg
        var partnerUiProfile = com.example.taptix.automation.partner.PartnerProfileResolver.resolve(candidatePkg, customTargets)

        if (partnerUiProfile == null) {
            val matchedNode = findNodeWithKeywords(rootNode, keywords) ?: return false
            val matchedPkg = (matchedNode.packageName?.toString() ?: "").trim().lowercase()
            if (matchedPkg.contains("taptix") || matchedPkg == ownPkg || matchedPkg.contains("systemui") || matchedPkg.contains("settings")) {
                return false
            }
            if (matchedPkg.isNotBlank()) {
                candidatePkg = matchedPkg
                partnerUiProfile = com.example.taptix.automation.partner.PartnerProfileResolver.resolve(candidatePkg, customTargets)
            }
        }

        // Safety Condition 3: Unknown partners must produce zero clicks
        if (partnerUiProfile == null) {
            Log.d(TAG, "[TaptixAutoAccept] Candidate package '$candidatePkg' is not a recognized partner app, producing 0 clicks")
            return false
        }

        // Safety Condition 4: Do not use GenericPartnerProfile as an unrestricted automatic-click fallback
        if (partnerUiProfile is com.example.taptix.automation.partner.profiles.GenericPartnerProfile) {
            val isExplicitlyAllowed = customTargets.any { it.equals(candidatePkg, ignoreCase = true) }
            if (!isExplicitlyAllowed) {
                Log.w(TAG, "[TaptixAutoAccept] GenericPartnerProfile rejected: '$candidatePkg' is not in explicit custom target list")
                return false
            }
        }

        val appPkg = candidatePkg
        Log.i(TAG, "[TaptixAutoAccept] Detected incoming booking screen candidate in package: $appPkg via profile: ${partnerUiProfile.displayName} (v${partnerUiProfile.profileVersion})")

        // Per-app profile: check if auto-accept is enabled for this specific app
        val appProfile = prefsRepo.getAppProfile(appPkg)
        if (!appProfile.enabled) {
            Log.d(TAG, "[TaptixAutoAccept] Per-app profile disabled for $appPkg, skipping")
            return false
        }

        // Step 2: Use Partner UI Profile to detect actionable accept control
        val detection = partnerUiProfile.findAcceptControl(rootNode, appPkg)
        if (!detection.isActionable || detection.acceptNode == null) {
            // Safety Condition 3: Unrecognized layouts must produce zero clicks
            Log.d(TAG, "[TaptixAutoAccept] Layout unrecognized or no actionable control for $appPkg: ${detection.diagnosticMessage}")
            return false
        }

        val targetNode: AccessibilityNodeInfo = detection.acceptNode!!
        val rawRect: Rect = detection.targetBounds
        val isSwipeFromProfile: Boolean = detection.isSwipeAction
        val matchText = detection.matchedSelectorText ?: (targetNode.text ?: targetNode.contentDescription ?: "Order").toString().trim()

        // Structured Offer Parsing and Rule Evaluation
        var fullTreeRoot = rootNode
        while (fullTreeRoot.parent != null) {
            fullTreeRoot = fullTreeRoot.parent ?: break
        }
        val offer = com.example.taptix.engine.OfferParser.parse(fullTreeRoot, appPkg)

        // Duplicate booking prevention
        val offerSignature = com.example.taptix.engine.DuplicateOfferGuard.signature(appPkg, offer.fare, offer.pickupDistanceKm, matchText)
        val now = System.currentTimeMillis()
        if (offerGuard.isDuplicate(offerSignature, now)) {
            Log.d(TAG, "[TaptixAutoAccept] Duplicate booking event ignored for: $offerSignature")
            return false
        }

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
            includeKeywords = partnerUiProfile.acceptKeywords.ifEmpty { keywords },
            excludeKeywords = settings.negativeKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            ultraMode = settings.ultraModeEnabled
        )

        if (!evalResult.isAccepted) {
            val reason = evalResult.rejectedReason ?: "Offer rejected by smart rules"
            Log.i(TAG, "[TaptixAutoAccept] Offer rejected: $reason")
            val status = when {
                reason.contains("negative keyword", ignoreCase = true) -> com.example.taptix.model.LogStatus.REJECTED_KEYWORD
                reason.contains("work zone", ignoreCase = true) || reason.contains("location unavailable", ignoreCase = true) -> com.example.taptix.model.LogStatus.OUTSIDE_ZONE
                else -> com.example.taptix.model.LogStatus.ERROR
            }
            com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(
                appName = getReadableAppName(appPkg),
                actionText = "Skipped offer",
                status = status,
                details = reason,
                fare = offer.fare,
                distanceKm = offer.tripDistanceKm ?: offer.pickupDistanceKm,
                destination = offer.destinationAddress
            )
            try {
                com.example.taptix.data.AnalyticsManager.getInstance(applicationContext).recordBooking(
                    wasAccepted = false,
                    distanceKm = offer.tripDistanceKm ?: offer.pickupDistanceKm
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record rejected booking", e)
            }
            return false
        }

        val stateMachine = com.example.taptix.engine.BookingStateMachine(offerSignature, offerGuard)
        stateMachine.onFilterEvaluated(matched = true)
        currentBookingStateMachine = stateMachine

        // Atomic check-or-record before executing any click actions to guarantee race-condition safety
        if (offerGuard.isDuplicateOrRecord(offerSignature, now)) {
            Log.d(TAG, "[TaptixAutoAccept] Duplicate booking race-condition detected and suppressed: $offerSignature")
            stateMachine.resetOnInterruption("DUPLICATE_CONCURRENCY_SUPPRESSED")
            return false
        }

        // Verify that target button is enabled and visible
        if (!targetNode.isEnabled) {
            Log.d(TAG, "[TaptixAutoAccept] Button is disabled, ignoring candidate")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && !targetNode.isVisibleToUser) {
            Log.d(TAG, "[TaptixAutoAccept] Button is not visible to user, ignoring candidate")
            return false
        }

        val finalRect = rawRect
        if (!com.example.taptix.engine.BookingDetectionValidator.isValidButtonBounds(finalRect.width(), finalRect.height())) {
            Log.d(TAG, "[TaptixAutoAccept] Button bounds $finalRect outside valid limits, skipping")
            return false
        }

        val centerX = finalRect.centerX().toFloat()

        val centerY = finalRect.centerY().toFloat()



        Log.i(TAG, "[TaptixAutoAccept] Found accept button: '$matchText' at $finalRect (isClickable=${targetNode.isClickable})")



        com.example.taptix.data.ActivityLogManager.getInstance(applicationContext).addLog(

            appName = getReadableAppName(appPkg),

            actionText = "Attempting accept: $matchText",

            status = com.example.taptix.model.LogStatus.ATTEMPTED,

            details = if (settings.ultraModeEnabled) "Ultra Mode Active" else "Keyword Matched"

        )



        val nodeText = (targetNode.text?.toString() ?: targetNode.contentDescription?.toString() ?: "").uppercase()

        val isSwipe = isSwipeFromProfile || nodeText.contains("SWIPE") || nodeText.contains("SLIDE") || (targetNode.className?.toString() ?: "").contains("SeekBar", ignoreCase = true)

        // Eliminate dual-dispatch conflict: prioritize immediate direct node click (ACTION_CLICK),
        // and fall back to gesture dispatch ONLY when direct action non-dispatch is reliably confirmed.
        if (!com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(actionGen)) {
            Log.w(TAG, "[SafetyController] Click execution aborted before dispatch: state=${com.example.taptix.automation.safety.AutomationSafetyController.state}")
            return false
        }

        val clickMethod = com.example.taptix.engine.AcceptClickExecutor.execute(
            isSwipe = isSwipe,
            directClick = {
                if (!com.example.taptix.automation.safety.AutomationSafetyController.canExecuteAction(actionGen)) {
                    return@execute com.example.taptix.engine.DirectClickResult.FAILED_CONFIRMED_NOT_DISPATCHED
                }
                val candidates = listOf(targetNode)
                for (node in candidates) {
                    if (node.isClickable) {
                        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (ok) return@execute com.example.taptix.engine.DirectClickResult.SUCCESS
                        // Action was dispatched to a clickable node but returned false.
                        // In Android, performAction returning false does not prove non-dispatch.
                        // Classify as UNKNOWN to prevent speculative coordinate double-tap.
                        return@execute com.example.taptix.engine.DirectClickResult.UNKNOWN
                    }
                }
                var clickableNode: AccessibilityNodeInfo? = targetNode.parent
                while (clickableNode != null) {
                    if (clickableNode.isClickable) {
                        val ok = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (ok) return@execute com.example.taptix.engine.DirectClickResult.SUCCESS
                        return@execute com.example.taptix.engine.DirectClickResult.UNKNOWN
                    }
                    clickableNode = clickableNode.parent
                }
                // No node in candidate hierarchy is clickable via accessibility action.
                // Verify whether non-dispatch is reliably established:
                // Target must be visible, enabled, in partner app foreground, with bounds on screen.
                val isTargetValid = targetNode.isVisibleToUser && targetNode.isEnabled
                val isForegroundValid = (rootNode.packageName?.toString() ?: "").equals(appPkg, ignoreCase = true)
                if (isTargetValid && isForegroundValid) {
                    com.example.taptix.engine.DirectClickResult.FAILED_CONFIRMED_NOT_DISPATCHED
                } else {
                    com.example.taptix.engine.DirectClickResult.UNKNOWN
                }
            },
            gestureTap = { performTap(centerX, centerY, durationMs = 50L, actionGen = actionGen) },
            swipe = {
                val startX = (finalRect.left + 60).toFloat()
                val endX = (finalRect.right - 60).toFloat().coerceAtLeast(startX + 180f)
                performSwipe(startX, centerY, endX, centerY, durationMs = 280L, actionGen = actionGen)
            }
        )

        stateMachine.onClickAttempted(clickMethod)
        lastAcceptTime = now
        Log.i(TAG, "[TaptixAutoAccept] Accept action dispatched via $clickMethod at ($centerX, $centerY) for $appPkg")

        mainHandler.postDelayed({
            verifyAcceptOutcome(
                appPkg, partnerUiProfile.acceptKeywords.ifEmpty { keywords }, matchText, clickMethod, settings,
                centerX, centerY, retryAttempt = 0,
                offer.fare ?: 0.0, (offer.tripDistanceKm ?: offer.pickupDistanceKm) ?: 0.0,
                offer.destinationAddress, stateMachine, partnerUiProfile,
                dispatchTimestamp = now
            )
        }, com.example.taptix.engine.AcceptVerifier.VERIFY_DELAY_MS)

        return true

    }



    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {















        val R = 6371.0















        val dLat = Math.toRadians(lat2 - lat1)















        val dLon = Math.toRadians(lon2 - lon1)















        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +















                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *















                Math.sin(dLon / 2) * Math.sin(dLon / 2)















        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))















    }















    private var locationListener: android.location.LocationListener? = null















    @Suppress("MissingPermission")







    private fun startLocationUpdates() {







        try {







            val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return







            locationListener = object : android.location.LocationListener {







                override fun onLocationChanged(loc: android.location.Location) {







                    cachedLocation = Pair(loc.latitude, loc.longitude)







                }







                @Deprecated("Deprecated in Java")







                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}







                override fun onProviderEnabled(p: String) {}







                override fun onProviderDisabled(p: String) {}







            }







            for (provider in listOf(







                android.location.LocationManager.GPS_PROVIDER,







                android.location.LocationManager.NETWORK_PROVIDER,







                android.location.LocationManager.PASSIVE_PROVIDER







            )) {







                try {







                    if (lm.isProviderEnabled(provider)) {







                        lm.requestLocationUpdates(provider, 20000L, 25f, locationListener!!, android.os.Looper.getMainLooper())







                        val last = lm.getLastKnownLocation(provider)







                        if (last != null && cachedLocation == null) {







                            cachedLocation = Pair(last.latitude, last.longitude)







                        }







                    }







                } catch (_: Exception) {}







            }







        } catch (e: Exception) {







            Log.w(TAG, "startLocationUpdates error: ${e.message}")







        }







    }















    private fun stopLocationUpdates() {







        try {







            locationListener?.let {







                val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager







                lm?.removeUpdates(it)







            }







            locationListener = null







        } catch (_: Exception) {}







    }















    @Suppress("MissingPermission")







    private fun getLastKnownLocation(): Pair<Double, Double>? {







        if (cachedLocation != null) return cachedLocation







        return try {







            val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager







            val providers = listOf(







                android.location.LocationManager.GPS_PROVIDER,







                android.location.LocationManager.NETWORK_PROVIDER,







                android.location.LocationManager.PASSIVE_PROVIDER







            )







            for (provider in providers) {







                if (lm != null && lm.isProviderEnabled(provider)) {







                    val loc = lm.getLastKnownLocation(provider)







                    if (loc != null) {







                        cachedLocation = Pair(loc.latitude, loc.longitude)







                        return cachedLocation







                    }







                }







            }







            // Fallback to active map zone coordinates if user set one







            val settings = prefsRepo.getSettings()







            if (settings.mapZoneLat != 0.0 && settings.mapZoneLng != 0.0) {







                return Pair(settings.mapZoneLat, settings.mapZoneLng)







            }







            cachedLocation







        } catch (e: Exception) {







            Log.w(TAG, "Could not get location: ${e.message}")







            val settings = prefsRepo.getSettings()







            if (settings.mapZoneLat != 0.0 && settings.mapZoneLng != 0.0) {







                Pair(settings.mapZoneLat, settings.mapZoneLng)







            } else {







                null







            }







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















            // child.recycle() deprecated in API 34+ and no longer needed















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















            // child.recycle() deprecated in API 34+ and no longer needed















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
        @Volatile
        var engineState: com.example.taptix.engine.AutomationEngineState = com.example.taptix.engine.AutomationEngineState.DISCONNECTED
            private set
        var onEngineStateChanged: ((com.example.taptix.engine.AutomationEngineState) -> Unit)? = null















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







