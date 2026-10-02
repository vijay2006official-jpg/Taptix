package com.example.taptix.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.taptix.MainActivity
import com.example.taptix.R
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.TargetPoint
import com.example.taptix.model.TripPhase
import com.example.taptix.model.TripStatusInfo
import com.example.taptix.util.TtsManager
import com.example.taptix.util.VoiceCommand
import com.example.taptix.util.VoiceCommandManager
import kotlin.random.Random

/**
 * Foreground Service displaying:
 * 1. Floating Control Bar and Target Crosshairs over Navigation Apps.
 * 2. Real-Time Trip Status Overlay (Fare, Phase, Distance, ETA, Passenger Info).
 * 3. Night-Mode Auto-Dimming & OLED Burn-In Pixel Shift Protection for Taxi Drivers.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefsRepo: PreferencesRepository
    private var ttsManager: TtsManager? = null
    private var motionDetector: MotionSafetyDetector? = null
    private var voiceManager: VoiceCommandManager? = null

    private var toolbarView: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null

    private val targetViews = mutableListOf<View>()
    private val targetPoints = mutableListOf<TargetPoint>()

    private var isPlaying = false
    private var isMotionLocked = false
    private var isTripWidgetExpanded = true

    // Real-Time Trip Status State
    private var currentTripInfo = TripStatusInfo()

    // Night Mode Auto-Dimming & Pixel Shift
    private var isDimmed = false
    private val dimHandler = Handler(Looper.getMainLooper())
    private val autoDimRunnable = Runnable { applyNightModeDimming() }
    private val pixelShiftRunnable = Runnable { executePixelShift() }

    // UI View References
    private var playPauseBtn: Button? = null
    private var modeBtn: Button? = null
    private var presetBtn: Button? = null
    private var micBtn: Button? = null
    private var statusTv: TextView? = null
    private var dimToggleBtn: Button? = null

    // Trip Widget UI Views
    private var tripBadgeTv: TextView? = null
    private var tripDetailsTv: TextView? = null
    private var tripAddressesTv: TextView? = null
    private var tripActionBtn: Button? = null
    private var tripExpandBtn: Button? = null
    private var tripCardContainer: LinearLayout? = null
    private var toolbarContainer: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefsRepo = PreferencesRepository(this)
        ttsManager = TtsManager(this)

        startForegroundServiceNotification()
        setupFloatingToolbar()
        setupVoiceControl()

        val settings = prefsRepo.getSettings()
        if (settings.motionSafetyLockEnabled) {
            motionDetector = MotionSafetyDetector(this) { isDriving ->
                handleMotionStateChanged(isDriving)
            }
            motionDetector?.start()
        }

        // Initialize default target point
        addTargetMarker(x = 500, y = 800)

        // Start Auto-Dim Idle Timer if enabled
        resetAutoDimTimer()
    }

    private fun setupVoiceControl() {
        voiceManager = VoiceCommandManager(this) { command ->
            wakeUpFromDimming()
            handleVoiceCommand(command)
        }
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        when (command) {
            VoiceCommand.ACCEPT -> {
                statusTv?.text = "VOICE: ACCEPT"
                statusTv?.setTextColor(Color.GREEN)
                AutoClickService.instance?.triggerVoiceAccept()
                updateTripStatus(currentTripInfo.copy(phase = TripPhase.EN_ROUTE_PICKUP))
            }
            VoiceCommand.DECLINE -> {
                statusTv?.text = "VOICE: DECLINE"
                statusTv?.setTextColor(Color.RED)
                AutoClickService.instance?.triggerVoiceDecline()
            }
            VoiceCommand.START -> {
                startClickingSequence()
            }
            VoiceCommand.STOP -> {
                stopClickingSequence()
            }
        }
        micBtn?.text = "MIC"
        micBtn?.background = createRoundBackground("#37474F", 16f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_UPDATE_TRIP -> {
                wakeUpFromDimming()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        dimHandler.removeCallbacks(autoDimRunnable)
        dimHandler.removeCallbacks(pixelShiftRunnable)
        stopClickingSequence()
        motionDetector?.stop()
        voiceManager?.shutdown()
        removeFloatingToolbar()
        removeAllTargetMarkers()
        ttsManager?.shutdown()
        instance = null
        Log.d(TAG, "OverlayService destroyed")
    }

    // --- Foreground Notification Setup ---

    private fun startForegroundServiceNotification() {
        val channelId = "taptix_overlay_channel"
        val channelName = getString(R.string.notification_channel_name)

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Real-Time Floating Trip Overlay Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // --- Floating Control & Trip Overlay UI Setup ---

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingToolbar() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
            alpha = 1.0f
        }

        toolbarParams = layoutParams

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            elevation = 16f
            background = createRoundBackground("#1E1E2C", 28f)
        }
        toolbarContainer = container

        // Drag Handle / Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val headerTv = TextView(this).apply {
            text = "::: TAPTIX TRIP OVERLAY :::"
            setTextColor(Color.parseColor("#A0A0B0"))
            textSize = 10f
            gravity = Gravity.START
        }
        headerRow.addView(headerTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Dim Status Badge Button
        dimToggleBtn = Button(this).apply {
            text = "☀️ NORMAL"
            textSize = 9f
            setTextColor(Color.YELLOW)
            background = createRoundBackground("#2A2A3C", 12f)
            setOnClickListener {
                wakeUpFromDimming()
                if (isDimmed) wakeUpFromDimming() else applyNightModeDimming()
            }
        }
        headerRow.addView(dimToggleBtn, LinearLayout.LayoutParams(180, 75))

        container.addView(headerRow)

        // Touch Listener for dragging toolbar & waking from dimming
        headerRow.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                wakeUpFromDimming()
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v?.performClick()
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(container, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        // --- Real-Time Trip Status Widget Component ---
        val tripCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            background = createRoundBackground("#12121A", 20f)
        }
        tripCardContainer = tripCard

        // Trip Phase Badge Row + Expand/Collapse Button
        val tripHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tripBadgeTv = TextView(this).apply {
            text = currentTripInfo.phase.label
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(16, 6, 16, 6)
            background = createRoundBackground(currentTripInfo.phase.badgeColorHex, 14f)
        }
        tripHeaderRow.addView(tripBadgeTv)

        val spacer = View(this)
        tripHeaderRow.addView(spacer, LinearLayout.LayoutParams(20, 1, 1f))

        tripExpandBtn = Button(this).apply {
            text = if (isTripWidgetExpanded) "▲ MIN" else "▼ EXPAND"
            textSize = 9f
            setTextColor(Color.CYAN)
            background = createRoundBackground("#252538", 12f)
            setOnClickListener {
                wakeUpFromDimming()
                toggleTripWidgetExpand()
            }
        }
        tripHeaderRow.addView(tripExpandBtn, LinearLayout.LayoutParams(160, 75))

        tripCard.addView(tripHeaderRow)

        // Trip Details (Fare, Distance, ETA, Passenger)
        tripDetailsTv = TextView(this).apply {
            text = "${currentTripInfo.fare}  •  ${currentTripInfo.distance}  •  ${currentTripInfo.etaMinutes} min ETA  •  ${currentTripInfo.passengerRating}"
            setTextColor(Color.parseColor("#4DEAEA"))
            textSize = 13f
            setPadding(0, 8, 0, 4)
        }
        tripCard.addView(tripDetailsTv)

        // Pickup / Dropoff Addresses
        tripAddressesTv = TextView(this).apply {
            text = "📍 Pick: ${currentTripInfo.pickupAddress}\n🏁 Drop: ${currentTripInfo.dropoffAddress}"
            setTextColor(Color.parseColor("#BBBBCC"))
            textSize = 10f
            setPadding(0, 0, 0, 8)
        }
        tripCard.addView(tripAddressesTv)

        // Quick Trip Action Row
        val tripActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tripActionBtn = Button(this).apply {
            text = "⚡ ACCEPT OFFER (${currentTripInfo.fare})"
            textSize = 11f
            setTextColor(Color.BLACK)
            background = createRoundBackground("#FFB300", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                handleTripActionClicked()
            }
        }
        tripActionRow.addView(tripActionBtn, LinearLayout.LayoutParams(0, 95, 1f))

        val phaseCycleBtn = Button(this).apply {
            text = "NEXT ➔"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#3F51B5", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                cycleTripPhase()
            }
        }
        tripActionRow.addView(phaseCycleBtn, LinearLayout.LayoutParams(130, 95).apply { leftMargin = 10 })

        tripCard.addView(tripActionRow)
        container.addView(tripCard)

        // --- Status Label ---
        statusTv = TextView(this).apply {
            text = "STATUS: PAUSED"
            setTextColor(Color.YELLOW)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 6)
        }
        container.addView(statusTv)

        // --- Auto-Clicker Driver Buttons Row ---
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Play/Pause
        playPauseBtn = Button(this).apply {
            text = "PLAY"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#2E7D32", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                togglePlayPause()
            }
        }
        buttonRow.addView(playPauseBtn, LinearLayout.LayoutParams(160, 115).apply { rightMargin = 8 })

        // Voice Mic
        micBtn = Button(this).apply {
            text = "MIC"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#37474F", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                if (voiceManager?.isListening() == true) {
                    voiceManager?.stopListening()
                    text = "MIC"
                    background = createRoundBackground("#37474F", 14f)
                } else {
                    voiceManager?.startListening()
                    text = "REC..."
                    background = createRoundBackground("#D84315", 14f)
                }
            }
        }
        buttonRow.addView(micBtn, LinearLayout.LayoutParams(110, 115).apply { rightMargin = 8 })

        // Add Target (+)
        val addTargetBtn = Button(this).apply {
            text = "+"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#37474F", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                if (targetPoints.size < 5) {
                    val lastX = targetPoints.lastOrNull()?.x ?: 500
                    val lastY = targetPoints.lastOrNull()?.y ?: 800
                    addTargetMarker(lastX + 60, lastY + 60)
                }
            }
        }
        buttonRow.addView(addTargetBtn, LinearLayout.LayoutParams(90, 115).apply { rightMargin = 8 })

        // Remove Target (-)
        val removeTargetBtn = Button(this).apply {
            text = "-"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#37474F", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                if (targetPoints.isNotEmpty()) {
                    removeLastTargetMarker()
                }
            }
        }
        buttonRow.addView(removeTargetBtn, LinearLayout.LayoutParams(90, 115).apply { rightMargin = 8 })

        // Mode Switcher
        modeBtn = Button(this).apply {
            text = prefsRepo.getSettings().operatingMode.name.substring(0, 5)
            textSize = 10f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#455A64", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                cycleOperatingMode()
            }
        }
        buttonRow.addView(modeBtn, LinearLayout.LayoutParams(120, 115).apply { rightMargin = 8 })

        // Preset Selector
        presetBtn = Button(this).apply {
            text = prefsRepo.getSettings().platformPreset.title.split(" ").first()
            textSize = 10f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#0288D1", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                cyclePreset()
            }
        }
        buttonRow.addView(presetBtn, LinearLayout.LayoutParams(120, 115).apply { rightMargin = 8 })

        // Emergency Stop Button
        val stopBtn = Button(this).apply {
            text = "STOP"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#C62828", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                triggerEmergencyStop()
            }
        }
        buttonRow.addView(stopBtn, LinearLayout.LayoutParams(130, 115))

        container.addView(buttonRow)

        toolbarView = container
        windowManager.addView(container, layoutParams)
    }

    private fun removeFloatingToolbar() {
        toolbarView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing toolbar", e)
            }
            toolbarView = null
        }
    }

    // --- Real-Time Trip Status Updates ---

    fun updateTripStatus(info: TripStatusInfo) {
        currentTripInfo = info
        wakeUpFromDimming()

        tripBadgeTv?.text = info.phase.label
        tripBadgeTv?.background = createRoundBackground(info.phase.badgeColorHex, 14f)

        tripDetailsTv?.text = "${info.fare}  •  ${info.distance}  •  ${info.etaMinutes} min ETA  •  ${info.passengerRating}"
        tripAddressesTv?.text = "📍 Pick: ${info.pickupAddress}\n🏁 Drop: ${info.dropoffAddress}"

        when (info.phase) {
            TripPhase.SEARCHING -> {
                tripActionBtn?.text = "🔍 SEARCHING TRIPS"
                tripActionBtn?.background = createRoundBackground("#0088FF", 14f)
                tripActionBtn?.setTextColor(Color.WHITE)
            }
            TripPhase.OFFER_RECEIVED -> {
                tripActionBtn?.text = "⚡ ACCEPT OFFER (${info.fare})"
                tripActionBtn?.background = createRoundBackground("#FF9800", 14f)
                tripActionBtn?.setTextColor(Color.BLACK)
                if (prefsRepo.getSettings().ttsFeedbackEnabled) {
                    ttsManager?.speak("New trip offer received! ${info.fare} for ${info.distance}.")
                }
            }
            TripPhase.EN_ROUTE_PICKUP -> {
                tripActionBtn?.text = "📍 ARRIVED AT PICKUP"
                tripActionBtn?.background = createRoundBackground("#4CAF50", 14f)
                tripActionBtn?.setTextColor(Color.WHITE)
            }
            TripPhase.TRIP_IN_PROGRESS -> {
                tripActionBtn?.text = "🏁 COMPLETE TRIP"
                tripActionBtn?.background = createRoundBackground("#9C27B0", 14f)
                tripActionBtn?.setTextColor(Color.WHITE)
            }
            TripPhase.COMPLETED -> {
                tripActionBtn?.text = "✅ TRIP COMPLETED"
                tripActionBtn?.background = createRoundBackground("#00BCD4", 14f)
                tripActionBtn?.setTextColor(Color.BLACK)
            }
        }
    }

    private fun cycleTripPhase() {
        val nextPhase = when (currentTripInfo.phase) {
            TripPhase.SEARCHING -> TripPhase.OFFER_RECEIVED
            TripPhase.OFFER_RECEIVED -> TripPhase.EN_ROUTE_PICKUP
            TripPhase.EN_ROUTE_PICKUP -> TripPhase.TRIP_IN_PROGRESS
            TripPhase.TRIP_IN_PROGRESS -> TripPhase.COMPLETED
            TripPhase.COMPLETED -> TripPhase.SEARCHING
        }
        updateTripStatus(currentTripInfo.copy(phase = nextPhase))
    }

    private fun handleTripActionClicked() {
        when (currentTripInfo.phase) {
            TripPhase.OFFER_RECEIVED -> {
                AutoClickService.instance?.triggerVoiceAccept()
                updateTripStatus(currentTripInfo.copy(phase = TripPhase.EN_ROUTE_PICKUP))
            }
            TripPhase.EN_ROUTE_PICKUP -> {
                ttsManager?.speak("Arrived at pickup location.")
                updateTripStatus(currentTripInfo.copy(phase = TripPhase.TRIP_IN_PROGRESS))
            }
            TripPhase.TRIP_IN_PROGRESS -> {
                ttsManager?.speak("Trip completed! Earnings updated.")
                updateTripStatus(currentTripInfo.copy(phase = TripPhase.COMPLETED))
            }
            else -> {
                cycleTripPhase()
            }
        }
    }

    private fun toggleTripWidgetExpand() {
        isTripWidgetExpanded = !isTripWidgetExpanded
        if (isTripWidgetExpanded) {
            tripAddressesTv?.visibility = View.VISIBLE
            tripExpandBtn?.text = "▲ MIN"
        } else {
            tripAddressesTv?.visibility = View.GONE
            tripExpandBtn?.text = "▼ EXPAND"
        }
    }

    // --- Night Mode Auto-Dimming & OLED Pixel Shift Protection ---

    private fun resetAutoDimTimer() {
        dimHandler.removeCallbacks(autoDimRunnable)
        dimHandler.removeCallbacks(pixelShiftRunnable)

        val settings = prefsRepo.getSettings()
        if (settings.nightModeAutoDimEnabled) {
            val delayMs = settings.autoDimDelaySeconds * 1000L
            dimHandler.postDelayed(autoDimRunnable, delayMs)
        }
    }

    private fun wakeUpFromDimming() {
        if (isDimmed) {
            isDimmed = false
            dimToggleBtn?.text = "☀️ NORMAL"
            dimToggleBtn?.setTextColor(Color.YELLOW)

            toolbarContainer?.background = createRoundBackground("#1E1E2C", 28f)
            tripCardContainer?.background = createRoundBackground("#12121A", 20f)

            toolbarParams?.let { params ->
                params.alpha = 1.0f
                toolbarView?.let { windowManager.updateViewLayout(it, params) }
            }
            Log.d(TAG, "Night Mode Overlay Woken to full luminance")
        }
        resetAutoDimTimer()
    }

    private fun applyNightModeDimming() {
        isDimmed = true
        dimToggleBtn?.text = "🌙 DIMMED"
        dimToggleBtn?.setTextColor(Color.GRAY)

        // OLED Deep Black Background with 35% Alpha to save battery and prevent burn-in
        toolbarContainer?.background = createRoundBackground("#030303", 28f)
        tripCardContainer?.background = createRoundBackground("#08080C", 20f)

        toolbarParams?.let { params ->
            params.alpha = 0.35f
            toolbarView?.let { windowManager.updateViewLayout(it, params) }
        }

        Log.d(TAG, "Night Mode Auto-Dimming Applied (OLED Protection Active)")

        // Schedule Pixel Shift
        if (prefsRepo.getSettings().pixelShiftBurnInProtection) {
            dimHandler.postDelayed(pixelShiftRunnable, 30000L)
        }
    }

    private fun executePixelShift() {
        if (isDimmed && prefsRepo.getSettings().pixelShiftBurnInProtection) {
            toolbarParams?.let { params ->
                val shiftX = Random.nextInt(-3, 4)
                val shiftY = Random.nextInt(-3, 4)
                params.x += shiftX
                params.y += shiftY
                toolbarView?.let { windowManager.updateViewLayout(it, params) }
                Log.d(TAG, "OLED Pixel Shift executed: dx=$shiftX, dy=$shiftY")
            }
            // Re-schedule pixel shift every 30 seconds while dimmed
            dimHandler.postDelayed(pixelShiftRunnable, 30000L)
        }
    }

    // --- Motion Safety Listener ---

    private fun handleMotionStateChanged(isDriving: Boolean) {
        wakeUpFromDimming()
        if (isDriving) {
            isMotionLocked = true
            stopClickingSequence()
            statusTv?.text = "SAFETY LOCK: DRIVING"
            statusTv?.setTextColor(Color.RED)
            if (prefsRepo.getSettings().ttsFeedbackEnabled) {
                ttsManager?.speak("Vehicle motion detected. Controls locked for driver safety.")
            }
        } else {
            isMotionLocked = false
            statusTv?.text = "STATUS: PAUSED"
            statusTv?.setTextColor(Color.YELLOW)
        }
    }

    // --- Control Handlers ---

    private fun togglePlayPause() {
        if (isPlaying) {
            stopClickingSequence()
        } else {
            startClickingSequence()
        }
    }

    private fun startClickingSequence() {
        if (isMotionLocked) {
            statusTv?.text = "LOCKED: DRIVING"
            ttsManager?.speak("Cannot start auto clicker while driving")
            return
        }

        val service = AutoClickService.instance
        if (service == null) {
            statusTv?.text = "ERR: ACCESSIBILITY"
            statusTv?.setTextColor(Color.RED)
            ttsManager?.speak("Accessibility Service not enabled")
            return
        }

        isPlaying = true
        playPauseBtn?.text = "PAUSE"
        playPauseBtn?.background = createRoundBackground("#D84315", 14f)
        statusTv?.text = "STATUS: ACTIVE"
        statusTv?.setTextColor(Color.GREEN)

        service.onStateChangedListener = { isRunning, _ ->
            if (!isRunning) {
                stopClickingSequence()
            }
        }

        service.startClicking(targetPoints)
    }

    private fun stopClickingSequence() {
        isPlaying = false
        playPauseBtn?.text = "PLAY"
        playPauseBtn?.background = createRoundBackground("#2E7D32", 14f)
        statusTv?.text = "STATUS: PAUSED"
        statusTv?.setTextColor(Color.YELLOW)

        AutoClickService.instance?.stopClicking()
    }

    private fun triggerEmergencyStop() {
        stopClickingSequence()
        statusTv?.text = "EMERGENCY STOPPED"
        statusTv?.setTextColor(Color.RED)
        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Emergency stop triggered")
        }
    }

    private fun cycleOperatingMode() {
        val currentMode = prefsRepo.getSettings().operatingMode
        val nextMode = when (currentMode) {
            OperatingMode.SINGLE_TARGET -> OperatingMode.MULTI_TARGET
            OperatingMode.MULTI_TARGET -> OperatingMode.SMART_ACCEPT
            OperatingMode.SMART_ACCEPT -> OperatingMode.SINGLE_TARGET
        }

        prefsRepo.saveOperatingMode(nextMode)
        modeBtn?.text = nextMode.name.substring(0, 5)

        if (nextMode == OperatingMode.SMART_ACCEPT) {
            targetViews.forEach { it.visibility = View.GONE }
        } else {
            targetViews.forEach { it.visibility = View.VISIBLE }
        }

        if (isPlaying) {
            stopClickingSequence()
            startClickingSequence()
        }
    }

    private fun cyclePreset() {
        val presets = PlatformPreset.entries
        val current = prefsRepo.getSettings().platformPreset
        val nextIndex = (presets.indexOf(current) + 1) % presets.size
        val nextPreset = presets[nextIndex]

        prefsRepo.savePlatformPreset(nextPreset)
        presetBtn?.text = nextPreset.title.split(" ").first()

        if (prefsRepo.getSettings().ttsFeedbackEnabled) {
            ttsManager?.speak("Preset set to ${nextPreset.title}")
        }
    }

    // --- Draggable Crosshair Target Markers ---

    @SuppressLint("ClickableViewAccessibility")
    private fun addTargetMarker(x: Int, y: Int) {
        val targetId = targetPoints.size + 1
        val point = TargetPoint(targetId, x, y)
        targetPoints.add(point)

        val layoutParams = WindowManager.LayoutParams(
            120,
            120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x - 60
            this.y = y - 60
        }

        val markerContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = createCircleDrawable("#CCFF0000", "#FFFFFF")
        }

        val badgeTv = TextView(this).apply {
            text = targetId.toString()
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        markerContainer.addView(badgeTv)

        markerContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                wakeUpFromDimming()
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v?.performClick()
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(markerContainer, layoutParams)

                        point.x = layoutParams.x + 60
                        point.y = layoutParams.y + 60
                        return true
                    }
                }
                return false
            }
        })

        if (prefsRepo.getSettings().operatingMode == OperatingMode.SMART_ACCEPT) {
            markerContainer.visibility = View.GONE
        }

        targetViews.add(markerContainer)
        windowManager.addView(markerContainer, layoutParams)
    }

    private fun removeLastTargetMarker() {
        if (targetViews.isNotEmpty()) {
            val lastView = targetViews.removeAt(targetViews.size - 1)
            targetPoints.removeAt(targetPoints.size - 1)
            try {
                windowManager.removeView(lastView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing target marker", e)
            }
        }
    }

    private fun removeAllTargetMarkers() {
        targetViews.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing target marker", e)
            }
        }
        targetViews.clear()
        targetPoints.clear()
    }

    // --- Helper UI Drawables ---

    private fun createRoundBackground(hexColor: String, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(hexColor))
            cornerRadius = cornerRadiusDp
        }
    }

    private fun createCircleDrawable(fillHex: String, strokeHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fillHex))
            setStroke(4, Color.parseColor(strokeHex))
        }
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.taptix.ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_TRIP = "com.example.taptix.ACTION_UPDATE_TRIP"

        var instance: OverlayService? = null
    }
}
