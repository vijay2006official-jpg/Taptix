package com.example.taptix.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.example.taptix.widget.TaptixWidgetProvider
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

    // Floating Bubble & Minimized state
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isMinimized = false

    // UI View References
    private var playPauseBtn: Button? = null
    private var modeBtn: Button? = null
    private var presetBtn: Button? = null
    private var micBtn: Button? = null
    private var statusTv: TextView? = null
    private var dimToggleBtn: Button? = null
    private var gestureToggleBtn: Button? = null

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

        if (!prefsRepo.getSettings().masterControlEnabled) {
            Log.d(TAG, "Master control disabled, stopping OverlayService immediately")
            closeOverlayCompletely()
            return
        }

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

        // Initialize default target point ONLY for manual tap modes (Single Target, Multi Target)
        if (settings.operatingMode != OperatingMode.SMART_ACCEPT) {
            addTargetMarker(x = 500, y = 800)
        }

        // Start Auto-Dim Idle Timer if enabled
        resetAutoDimTimer()

        // Automatically activate Auto-Accept engine ONLY if Master Control is active
        Handler(Looper.getMainLooper()).postDelayed({
            if (prefsRepo.getSettings().masterControlEnabled) {
                startClickingSequence()
            }
        }, 500L)
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
        val masterEnabled = prefsRepo.getSettings().masterControlEnabled

        if (intent?.action == ACTION_STOP_SERVICE || !masterEnabled) {
            closeOverlayCompletely()
            return START_NOT_STICKY
        }

        expandFromBubble()

        when (intent?.action) {
            ACTION_UPDATE_TRIP -> {
                wakeUpFromDimming()
            }
        }
        return START_NOT_STICKY
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
        removeBubbleView()
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

    // --- Floating Control Toolbar UI Setup ---

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
            x = 30
            y = 120
        }

        toolbarParams = layoutParams

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            elevation = 16f
            background = createRoundBackground("#1A1D28", 28f)
        }
        toolbarContainer = container

        // --- Header Drag Handle Row ---
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val headerTv = TextView(this).apply {
            text = "::: TAPTIX DRIVER DOCK :::"
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

        // Status Label
        statusTv = TextView(this).apply {
            text = "🟢 AUTO-ACCEPT: ACTIVE"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        container.addView(statusTv)

        // --- Action Buttons Horizontal Row ---
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Play/Pause Button
        playPauseBtn = Button(this).apply {
            text = "PAUSE"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#D84315", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                togglePlayPause()
            }
        }
        buttonRow.addView(playPauseBtn, LinearLayout.LayoutParams(160, 110).apply { rightMargin = 8 })

        // Add Target (+)
        val addTargetBtn = Button(this).apply {
            text = "+"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#2A2A3C", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                if (targetPoints.size < 5) {
                    val lastX = targetPoints.lastOrNull()?.x ?: 500
                    val lastY = targetPoints.lastOrNull()?.y ?: 800
                    addTargetMarker(lastX + 60, lastY + 60)
                }
            }
        }
        buttonRow.addView(addTargetBtn, LinearLayout.LayoutParams(95, 110).apply { rightMargin = 8 })

        // Remove Target (-)
        val removeTargetBtn = Button(this).apply {
            text = "-"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#2A2A3C", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                removeLastTargetMarker()
            }
        }
        buttonRow.addView(removeTargetBtn, LinearLayout.LayoutParams(95, 110).apply { rightMargin = 8 })

        // Gesture Mode Toggle Button (Auto-Accept vs Manual Tap)
        gestureToggleBtn = Button(this).apply {
            val isAuto = prefsRepo.getSettings().operatingMode == OperatingMode.SMART_ACCEPT
            text = if (isAuto) "⚡ SMART" else "🎯 TAP"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = createRoundBackground(if (isAuto) "#0088FF" else "#FF9800", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                toggleGestureAutomationMode()
            }
        }
        buttonRow.addView(gestureToggleBtn, LinearLayout.LayoutParams(150, 110).apply { rightMargin = 8 })

        // Voice Command Mic Button
        micBtn = Button(this).apply {
            text = "MIC"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#37474F", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                statusTv?.text = "LISTENING..."
                statusTv?.setTextColor(Color.CYAN)
                voiceManager?.startListening()
            }
        }
        buttonRow.addView(micBtn, LinearLayout.LayoutParams(110, 110).apply { rightMargin = 8 })

                // Minimize Button
        val minBtn = Button(this).apply {
            text = "➖"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#37474F", 14f)
            setOnClickListener {
                wakeUpFromDimming()
                minimizeToBubble()
            }
        }
        buttonRow.addView(minBtn, LinearLayout.LayoutParams(95, 110).apply { rightMargin = 8 })

        // Emergency Stop / Close Overlay Button
        val stopBtn = Button(this).apply {
            text = "✖"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = createRoundBackground("#C62828", 14f)
            setOnClickListener {
                closeOverlayCompletely()
            }
        }
        buttonRow.addView(stopBtn, LinearLayout.LayoutParams(95, 110))

        container.addView(buttonRow)

        toolbarView = container
        windowManager.addView(container, layoutParams)
    }

    private fun minimizeToBubble() {
        if (isMinimized) return
        toolbarView?.visibility = View.GONE
        targetViews.forEach { it.visibility = View.GONE }
        setupBubbleView()
        isMinimized = true
    }

    fun expandFromBubble() {
        if (!isMinimized) return
        removeBubbleView()
        toolbarView?.visibility = View.VISIBLE
        if (prefsRepo.getSettings().operatingMode != OperatingMode.SMART_ACCEPT) {
            targetViews.forEach { it.visibility = View.VISIBLE }
        }
        isMinimized = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleView() {
        if (bubbleView != null) return

        val params = WindowManager.LayoutParams(
            130, 130,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = toolbarParams?.x ?: 30
            y = toolbarParams?.y ?: 120
        }
        bubbleParams = params

        val bubble = TextView(this).apply {
            text = "⚡"
            textSize = 24f
            gravity = Gravity.CENTER
            background = createCircleDrawable("#00E5FF", "#FFFFFF")
            elevation = 12f
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            expandFromBubble()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubble, params)
                        return true
                    }
                }
                return false
            }
        })

        bubbleView = bubble
        windowManager.addView(bubble, params)
    }

    private fun removeBubbleView() {
        bubbleView?.let {
            try { windowManager.removeViewImmediate(it) } catch (_: Exception) {}
            bubbleView = null
        }
    }

    fun closeOverlayCompletely() {
        prefsRepo.saveMasterControl(false)
        AutoClickService.instance?.setClickingActive(false)
        stopClickingSequence()
        removeFloatingToolbar()
        removeAllTargetMarkers()
        removeBubbleView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        TaptixWidgetProvider.notifyStateChanged(this)
        stopSelf()
    }

    private fun removeFloatingToolbar() {
        toolbarView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (e: Exception) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            toolbarView = null
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

    private fun toggleGestureAutomationMode() {
        val currentMode = prefsRepo.getSettings().operatingMode
        val newMode = if (currentMode == OperatingMode.SMART_ACCEPT) OperatingMode.SINGLE_TARGET else OperatingMode.SMART_ACCEPT
        prefsRepo.saveOperatingMode(newMode)

        val isAuto = newMode == OperatingMode.SMART_ACCEPT
        gestureToggleBtn?.text = if (isAuto) "⚡ SMART" else "🎯 TAP"
        gestureToggleBtn?.background = createRoundBackground(if (isAuto) "#0088FF" else "#FF9800", 14f)

        if (isAuto) {
            targetViews.forEach { it.visibility = View.GONE }
            statusTv?.text = "🟢 AUTO-ACCEPT: ACTIVE"
            statusTv?.setTextColor(Color.parseColor("#00E676"))
        } else {
            if (targetPoints.isEmpty()) addTargetMarker(500, 800)
            targetViews.forEach { it.visibility = View.VISIBLE }
            statusTv?.text = "🎯 TARGET TAPPING"
            statusTv?.setTextColor(Color.YELLOW)
        }

        if (isPlaying) {
            stopClickingSequence()
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
        statusTv?.text = "🟢 AUTO-ACCEPT: ACTIVE"
        statusTv?.setTextColor(Color.parseColor("#00E676"))

        service.onStateChangedListener = { isRunning, _ ->
            if (!isRunning) {
                stopClickingSequence()
            }
        }

        service.startClicking(targetPoints)
    }

    fun stopClickingSequence() {
        isPlaying = false
        playPauseBtn?.text = "PLAY"
        playPauseBtn?.background = createRoundBackground("#2E7D32", 14f)
        statusTv?.text = "STATUS: PAUSED"
        statusTv?.setTextColor(Color.YELLOW)

        AutoClickService.instance?.stopClicking()
    }

    // --- Draggable Crosshair Target Markers ---

    @SuppressLint("ClickableViewAccessibility")
    private fun addTargetMarker(x: Int, y: Int) {
        val targetId = targetPoints.size + 1
        val point = TargetPoint(targetId, x, y)
        targetPoints.add(point)

        val layoutParams = WindowManager.LayoutParams(
            120, 120,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x - 60
            this.y = y - 60
        }

        val markerContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = createCircleDrawable("#CC0088FF", "#FFFFFF")
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
                windowManager.removeViewImmediate(lastView)
            } catch (_: Exception) {}
        }
    }

    private fun removeAllTargetMarkers() {
        targetViews.forEach { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {}
        }
        targetViews.clear()
        targetPoints.clear()
    }

    // --- Real-Time Trip Status Updates ---

    fun updateTripStatus(info: TripStatusInfo) {
        currentTripInfo = info
        wakeUpFromDimming()
        if (info.phase == TripPhase.OFFER_RECEIVED || info.phase == TripPhase.EN_ROUTE_PICKUP || info.phase == TripPhase.TRIP_IN_PROGRESS) {
            tripCardContainer?.visibility = View.VISIBLE
        } else {
            tripCardContainer?.visibility = View.GONE
        }

        tripBadgeTv?.text = info.phase.label
        tripBadgeTv?.background = createRoundBackground(info.phase.badgeColorHex, 14f)

        tripDetailsTv?.text = "${info.fare}  •  ${info.distance}  •  ${info.etaMinutes} min ETA  •  ${info.passengerRating}"
        tripAddressesTv?.text = "📍 Pick: ${info.pickupAddress}\n🏁 Drop: ${info.dropoffAddress}"
    }

    // --- Drive-Motion Safety Lock ---

    private fun handleMotionStateChanged(isDriving: Boolean) {
        if (isMotionLocked == isDriving) return
        isMotionLocked = isDriving

        if (isDriving) {
            statusTv?.text = "DRIVING SAFETY LOCK"
            statusTv?.setTextColor(Color.MAGENTA)
            if (prefsRepo.getSettings().ttsFeedbackEnabled) {
                ttsManager?.speak("Vehicle motion detected. Driving safety lock activated.")
            }
        } else {
            statusTv?.text = if (isPlaying) "🟢 AUTO-ACCEPT: ACTIVE" else "STATUS: PAUSED"
            statusTv?.setTextColor(if (isPlaying) Color.parseColor("#00E676") else Color.YELLOW)
        }
    }

    // --- Night Mode Auto-Dimming & Pixel Shift ---

    private fun resetAutoDimTimer() {
        dimHandler.removeCallbacks(autoDimRunnable)
        dimHandler.removeCallbacks(pixelShiftRunnable)

        val settings = prefsRepo.getSettings()
        if (settings.nightModeAutoDimEnabled) {
            dimHandler.postDelayed(autoDimRunnable, (settings.autoDimDelaySeconds * 1000L).coerceAtLeast(3000L))
        }
        if (settings.pixelShiftBurnInProtection) {
            dimHandler.postDelayed(pixelShiftRunnable, 60000L)
        }
    }

    private fun wakeUpFromDimming() {
        if (isDimmed) {
            isDimmed = false
            toolbarContainer?.alpha = 1.0f
            dimToggleBtn?.text = "☀️ NORMAL"
            dimToggleBtn?.setTextColor(Color.YELLOW)
        }
        resetAutoDimTimer()
    }

    private fun applyNightModeDimming() {
        if (!prefsRepo.getSettings().nightModeAutoDimEnabled) return
        isDimmed = true
        val alpha = prefsRepo.getSettings().nightModeDimIntensity.coerceIn(0.15f, 0.6f)
        toolbarContainer?.alpha = alpha
        dimToggleBtn?.text = "🌙 DIMMED"
        dimToggleBtn?.setTextColor(Color.CYAN)
    }

    private fun executePixelShift() {
        if (!prefsRepo.getSettings().pixelShiftBurnInProtection) return
        toolbarParams?.let { params ->
            val shiftX = Random.nextInt(-4, 5)
            val shiftY = Random.nextInt(-4, 5)
            params.x = (params.x + shiftX).coerceIn(0, 1000)
            params.y = (params.y + shiftY).coerceIn(0, 2000)
            toolbarView?.let { v ->
                try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
            }
        }
        dimHandler.postDelayed(pixelShiftRunnable, 60000L)
    }

    // --- Helper Drawables ---

    private fun createRoundBackground(colorHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(colorHex))
            this.cornerRadius = cornerRadius
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

        @Volatile
        var instance: OverlayService? = null
            private set

        fun stopService(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try { context.startService(intent) } catch (_: Exception) {}
            try { context.stopService(intent) } catch (_: Exception) {}
            instance?.closeOverlayCompletely()
        }
    }
}
