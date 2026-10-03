package com.example.taptix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.taptix.data.AppSettings
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.model.TripPhase
import com.example.taptix.model.TripStatusInfo
import com.example.taptix.service.AutoClickService
import com.example.taptix.service.OverlayService
import com.example.taptix.ui.theme.TaptixTheme
import com.example.taptix.util.UpdateInfo
import com.example.taptix.util.UpdateManager

class MainActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)
        enableEdgeToEdge()

        setContent {
            TaptixTheme {
                TaptixDashboardScreen(prefsRepo = prefsRepo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaptixDashboardScreen(prefsRepo: PreferencesRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayPermissionGranted by remember { mutableStateOf(false) }
    var appSettings by remember { mutableStateOf(prefsRepo.getSettings()) }

    // In-App Auto-Update State
    val updateManager = remember { UpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress by remember { mutableFloatStateOf(-1f) }
    var updateStatusText by remember { mutableStateOf("") }

    // Floating Overlay Active State
    var isOverlayRunning by remember { mutableStateOf(OverlayService.instance != null) }

    // Re-check permissions and updates on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AutoClickService.isAccessibilityPermissionGranted(context) && AutoClickService.isServiceRunning()
                isOverlayPermissionGranted = Settings.canDrawOverlays(context)
                isOverlayRunning = OverlayService.instance != null

                // Check for updates from GitHub or local server
                updateManager.checkForUpdate("https://raw.githubusercontent.com/vijay2006official-jpg/Taptix/main/version.json") { info ->
                    if (info != null) {
                        availableUpdate = info
                    } else {
                        // Fallback to local server if on same network
                        updateManager.checkForUpdate("http://192.168.29.97:8080/version.json") { localInfo ->
                            availableUpdate = localInfo
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Taptix Logo",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Taptix",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF00D2FF).copy(alpha = 0.2f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "PRO v1.1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00D2FF),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Ride-Hailing Driver Assistant",
                                fontSize = 11.sp,
                                color = Color(0xFFA0AEC0)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B101B)
                )
            )
        },
        containerColor = Color(0xFF080C14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. In-App Auto-Update Card (Conditional)
            if (availableUpdate != null) {
                InAppUpdateCard(
                    updateInfo = availableUpdate!!,
                    updateProgress = updateProgress,
                    statusText = updateStatusText,
                    onUpdateClick = {
                        val apk = availableUpdate?.apkUrl ?: return@InAppUpdateCard
                        updateProgress = 0f
                        updateStatusText = "Connecting..."
                        updateManager.downloadAndInstallApk(
                            apkUrl = apk,
                            onProgress = { p -> updateProgress = p.toFloat() },
                            onComplete = { updateStatusText = "Opening installer..." },
                            onError = { err ->
                                updateProgress = -1f
                                updateStatusText = "Error: $err"
                            }
                        )
                    }
                )
            }

            // 2. Interactive Status Card (ACTIVE / INACTIVE)
            InteractiveMasterStatusCard(
                isAccessibilityActive = isAccessibilityEnabled,
                isOverlayGranted = isOverlayPermissionGranted,
                onOpenAccessibilitySettings = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                onOpenOverlaySettings = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // 3. Floating Overlay Service Control Card
            FloatingOverlayMasterControlCard(
                isReady = isAccessibilityEnabled && isOverlayPermissionGranted,
                isOverlayRunning = isOverlayRunning,
                onToggleOverlay = {
                    if (isOverlayRunning) {
                        val intent = Intent(context, OverlayService::class.java)
                        context.stopService(intent)
                        isOverlayRunning = false
                    } else {
                        val intent = Intent(context, OverlayService::class.java)
                        context.startForegroundService(intent)
                        isOverlayRunning = true
                    }
                }
            )

            // 4. Floating Action Preview Section (Interactive Mockup)
            FloatingActionPreviewCard(
                operatingMode = appSettings.operatingMode,
                preset = appSettings.platformPreset,
                dimIntensity = appSettings.nightModeDimIntensity,
                isNightModeActive = appSettings.nightModeAutoDimEnabled
            )

            // 5. Operating Mode Card (Smart Accept OCR, Single, Multi)
            ModernOperatingModeCard(
                currentMode = appSettings.operatingMode,
                onModeSelected = { mode ->
                    prefsRepo.saveOperatingMode(mode)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 6. Platform Preset Selector (Uber, Ola, Rapido, Lyft, Custom)
            ModernPlatformPresetCard(
                currentPreset = appSettings.platformPreset,
                customKeywords = appSettings.customKeywords,
                onPresetSelected = { preset ->
                    prefsRepo.savePlatformPreset(preset)
                    appSettings = prefsRepo.getSettings()
                },
                onCustomKeywordsChanged = { keywords ->
                    prefsRepo.saveCustomKeywords(keywords)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 7. Click Speed & Timing Config
            ModernClickSpeedCard(
                intervalMs = appSettings.clickIntervalMs,
                onIntervalChanged = { interval ->
                    prefsRepo.saveClickInterval(interval)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 8. Driver Safety & Automation Card (Keyboard Auto-Pause, Motion Lock, Voice)
            ModernSafetyAutomationCard(
                settings = appSettings,
                onAutoPauseAcceptChanged = { enabled ->
                    prefsRepo.saveAutoPauseOnAccept(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onKeyboardPauseChanged = { enabled ->
                    prefsRepo.saveAutoPauseOnKeyboard(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onMotionLockChanged = { enabled ->
                    prefsRepo.saveMotionSafetyLockEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onVoiceCommandsChanged = { enabled ->
                    prefsRepo.saveVoiceCommandsEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onTtsChanged = { enabled ->
                    prefsRepo.saveTtsFeedbackEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 9. Night Mode & OLED Protection Card with Intensity Slider
            ModernNightModeBurnInCard(
                settings = appSettings,
                onNightModeDimChanged = { enabled ->
                    prefsRepo.saveNightModeAutoDimEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onIntensityChanged = { intensity ->
                    prefsRepo.saveNightModeDimIntensity(intensity)
                    appSettings = prefsRepo.getSettings()
                },
                onDimDelayChanged = { delaySec ->
                    prefsRepo.saveAutoDimDelaySeconds(delaySec)
                    appSettings = prefsRepo.getSettings()
                },
                onPixelShiftChanged = { enabled ->
                    prefsRepo.savePixelShiftBurnInProtection(enabled)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 10. App-Specific Auto-Launch Card
            ModernAutoLaunchCard(
                settings = appSettings,
                onAutoLaunchChanged = { enabled ->
                    prefsRepo.saveAutoLaunchForTargetAppsEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onTargetPackagesChanged = { packages ->
                    prefsRepo.saveTargetAppPackages(packages)
                    appSettings = prefsRepo.getSettings()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// 1. Interactive Master Status Card
// ==========================================
@Composable
fun InteractiveMasterStatusCard(
    isAccessibilityActive: Boolean,
    isOverlayGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val isSystemFullyActive = isAccessibilityActive && isOverlayGranted

    val cardBorderColor by animateColorAsState(
        if (isSystemFullyActive) Color(0xFF00E676) else if (!isAccessibilityActive) Color(0xFFFF5252) else Color(0xFFFFB800),
        label = "statusBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, cardBorderColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM ENGINE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA0AEC0),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isSystemFullyActive) "SERVICE ACTIVE" else "SERVICE INACTIVE",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSystemFullyActive) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }

                // Active Indicator Glowing Pulse Badge
                Surface(
                    shape = CircleShape,
                    color = if (isSystemFullyActive) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (isSystemFullyActive) Color(0xFF00E676) else Color(0xFFFF5252)
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSystemFullyActive) Color(0xFF00E676) else Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isSystemFullyActive)
                    "Taptix is armed and running. Auto-Accept gestures and floating controls are active."
                else if (!isAccessibilityActive)
                    "Accessibility Service is turned off. Taptix needs accessibility permission to detect ride offers and auto-accept hands-free."
                else
                    "Display Over Apps permission is missing. Needed to show floating control bar over Uber, Ola, or Maps.",
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            if (!isAccessibilityActive) {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text(
                        text = "⚙️ Enable Accessibility Service in Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            if (!isOverlayGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenOverlaySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB800))
                ) {
                    Text(
                        text = "🔓 Grant Display Over Other Apps Permission",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Quick Status Indicators
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(label = "Accessibility", active = isAccessibilityActive)
                StatusPill(label = "Overlay Window", active = isOverlayGranted)
                StatusPill(label = "Voice Engine", active = true)
            }
        }
    }
}

@Composable
fun StatusPill(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (active) Color(0xFF00E676) else Color(0xFFFF5252),
            modifier = Modifier.size(8.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: ${if (active) "Ready" else "Off"}",
            fontSize = 11.sp,
            color = if (active) Color(0xFFE2E8F0) else Color(0xFFA0AEC0)
        )
    }
}

// ==========================================
// 2. Floating Overlay Master Control Card
// ==========================================
@Composable
fun FloatingOverlayMasterControlCard(
    isReady: Boolean,
    isOverlayRunning: Boolean,
    onToggleOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLOATING OVERLAY WIDGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2FF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isOverlayRunning) "Widget is Floating on Screen" else "Widget Stopped",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOverlayRunning) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isOverlayRunning) "LIVE" else "IDLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isOverlayRunning) Color(0xFF00E676) else Color(0xFFA0AEC0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggleOverlay,
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayRunning) Color(0xFFE53935) else Color(0xFF00D2FF)
                )
            ) {
                Text(
                    text = if (isOverlayRunning) "🛑 Stop Floating Overlay" else "🚀 Start Floating Overlay",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOverlayRunning) Color.White else Color.Black
                )
            }
        }
    }
}

// ==========================================
// 3. Floating Action Preview Section (Interactive Mockup)
// ==========================================
@Composable
fun FloatingActionPreviewCard(
    operatingMode: OperatingMode,
    preset: PlatformPreset,
    dimIntensity: Float,
    isNightModeActive: Boolean
) {
    var previewPlaying by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1422))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLOATING OVERLAY PREVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2FF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Live On-Screen Widget Appearance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFB800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "INTERACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB800),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Simulated Navigation Screen Background with Floating Widget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF141923), Color(0xFF090D14))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                // Background Simulated Map Lines
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.05f)))
                    Spacer(modifier = Modifier.height(40.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.05f)))
                    Spacer(modifier = Modifier.height(40.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.05f)))
                }

                // Miniature Floating Controller Pill
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Miniature Floating Bar
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2638).copy(alpha = if (isNightModeActive) (dimIntensity * 1.5f).coerceIn(0.4f, 1f) else 1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f)),
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "::: TAPTIX",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFA0AEC0)
                            )

                            // Interactive Mini Play Button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (previewPlaying) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier
                                    .clickable { previewPlaying = !previewPlaying }
                                    .padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (previewPlaying) " ▶ ON " else " ⏸ OFF ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2D3748)
                            ) {
                                Text(
                                    text = preset.title.split(" ").first().uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB800),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2D3748)
                            ) {
                                Text(
                                    text = "🎙️ MIC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00D2FF),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Bottom Simulated Real-Time Ride Offer Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF182236).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFFB800)
                                ) {
                                    Text(
                                        text = "NEW TRIP OFFER",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "$18.50 • 3.8 mi (9 min)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF00E676)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Downtown Station ➔ Terminal 2 Airport",
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00E676),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (preset.requiresSwipe) "AUTO-SWIPING TO ACCEPT ➔" else "AUTO-CLICKING TO ACCEPT ✓",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. Operating Mode Card
// ==========================================
@Composable
fun ModernOperatingModeCard(
    currentMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "CLICKING ENGINE MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D2FF),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select Detection Strategy",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            OperatingMode.entries.forEach { mode ->
                val isSelected = (mode == currentMode)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF00D2FF).copy(alpha = 0.15f) else Color(0xFF171F30),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onModeSelected(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mode.displayName,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF00D2FF) else Color.White,
                                    fontSize = 14.sp
                                )
                                if (mode == OperatingMode.SMART_ACCEPT) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF00E676).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "RECOMMENDED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = Color(0xFFA0AEC0)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. Platform Preset Selector Card
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPlatformPresetCard(
    currentPreset: PlatformPreset,
    customKeywords: String,
    onPresetSelected: (PlatformPreset) -> Unit,
    onCustomKeywordsChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "DRIVER APP PLATFORM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D2FF),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Optimized Touch & Swipe Strategy",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformPreset.entries.forEach { preset ->
                    val isSelected = (preset == currentPreset)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(preset) },
                        label = {
                            Text(
                                text = preset.title.split(" ").first(),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00D2FF),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            if (currentPreset == PlatformPreset.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customKeywords,
                    onValueChange = onCustomKeywordsChanged,
                    label = { Text("Custom OCR Keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

// ==========================================
// 6. Click Speed & Delay Card
// ==========================================
@Composable
fun ModernClickSpeedCard(
    intervalMs: Long,
    onIntervalChanged: (Long) -> Unit
) {
    var sliderVal by remember(intervalMs) { mutableFloatStateOf(intervalMs.toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CLICK SPEED INTERVAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2FF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Touch Pulse Frequency",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00D2FF).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${sliderVal.toInt()} ms",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color(0xFF00D2FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = { onIntervalChanged(sliderVal.toLong()) },
                valueRange = 50f..2000f,
                steps = 38,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00D2FF),
                    activeTrackColor = Color(0xFF00D2FF)
                )
            )
        }
    }
}

// ==========================================
// 7. Driver Safety & Automation Card
// ==========================================
@Composable
fun ModernSafetyAutomationCard(
    settings: AppSettings,
    onAutoPauseAcceptChanged: (Boolean) -> Unit,
    onKeyboardPauseChanged: (Boolean) -> Unit,
    onMotionLockChanged: (Boolean) -> Unit,
    onVoiceCommandsChanged: (Boolean) -> Unit,
    onTtsChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "SAFETY & DRIVER AUTOMATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D2FF),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Road Safety & Distraction Prevention",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModernToggleRow(
                title = "Keyboard Auto-Pause",
                desc = "Pause auto-clicks automatically when soft keyboard is open for typing",
                checked = settings.autoPauseOnKeyboard,
                onCheckedChange = onKeyboardPauseChanged
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            ModernToggleRow(
                title = "Auto-Pause on Accept",
                desc = "Stop clicking immediately as soon as a ride offer is successfully accepted",
                checked = settings.autoPauseOnAccept,
                onCheckedChange = onAutoPauseAcceptChanged
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            ModernToggleRow(
                title = "Drive-Motion Safety Lock",
                desc = "Sensor pauses gestures when high vehicle speed or sharp turns are detected",
                checked = settings.motionSafetyLockEnabled,
                onCheckedChange = onMotionLockChanged
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            ModernToggleRow(
                title = "Hands-Free Voice Commands",
                desc = "Speak \"ACCEPT\", \"START\", or \"STOP\" to operate without touching the phone",
                checked = settings.voiceCommandsEnabled,
                onCheckedChange = onVoiceCommandsChanged
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            ModernToggleRow(
                title = "TTS Audio Speech Announcements",
                desc = "Speaks trip fare, arrival, and accept alerts out loud through car speaker",
                checked = settings.ttsFeedbackEnabled,
                onCheckedChange = onTtsChanged
            )
        }
    }
}

// ==========================================
// 8. Night-Mode & OLED Burn-In Card with Intensity Slider
// ==========================================
@Composable
fun ModernNightModeBurnInCard(
    settings: AppSettings,
    onNightModeDimChanged: (Boolean) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onDimDelayChanged: (Int) -> Unit,
    onPixelShiftChanged: (Boolean) -> Unit
) {
    var intensityVal by remember(settings.nightModeDimIntensity) {
        mutableFloatStateOf(settings.nightModeDimIntensity)
    }
    var delayVal by remember(settings.autoDimDelaySeconds) {
        mutableFloatStateOf(settings.autoDimDelaySeconds.toFloat())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "NIGHT DRIVING & SCREEN HEALTH",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D2FF),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "🌙 Night-Mode Auto-Dim & OLED Protection",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModernToggleRow(
                title = "Auto-Dim Overlay When Idle",
                desc = "Dims floating controls to save driver vision and reduce cabin glare at night",
                checked = settings.nightModeAutoDimEnabled,
                onCheckedChange = onNightModeDimChanged
            )

            if (settings.nightModeAutoDimEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Dim Intensity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimming Opacity Intensity",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "${(intensityVal * 100).toInt()}% Opacity",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB800)
                    )
                }
                Slider(
                    value = intensityVal,
                    onValueChange = { intensityVal = it },
                    onValueChangeFinished = { onIntensityChanged(intensityVal) },
                    valueRange = 0.15f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB800),
                        activeTrackColor = Color(0xFFFFB800)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dim Delay Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Idle Timeout Delay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "${delayVal.toInt()} seconds",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2FF)
                    )
                }
                Slider(
                    value = delayVal,
                    onValueChange = { delayVal = it },
                    onValueChangeFinished = { onDimDelayChanged(delayVal.toInt()) },
                    valueRange = 3f..30f,
                    steps = 26,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00D2FF),
                        activeTrackColor = Color(0xFF00D2FF)
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            ModernToggleRow(
                title = "OLED Pixel-Shift Burn-In Shield",
                desc = "Periodically shifts floating UI elements by +/- 2px to prevent permanent OLED burn-in",
                checked = settings.pixelShiftBurnInProtection,
                onCheckedChange = onPixelShiftChanged
            )
        }
    }
}

// ==========================================
// 9. App-Specific Auto-Launch Card
// ==========================================
@Composable
fun ModernAutoLaunchCard(
    settings: AppSettings,
    onAutoLaunchChanged: (Boolean) -> Unit,
    onTargetPackagesChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101624))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "AUTOMATIC APP DETECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D2FF),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Auto-Launch Over Driver Apps",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModernToggleRow(
                title = "Auto-Show Floating Bar",
                desc = "Automatically pop up Taptix overlay whenever Uber, Ola, Rapido, or Lyft is opened",
                checked = settings.autoLaunchForTargetAppsEnabled,
                onCheckedChange = onAutoLaunchChanged
            )

            if (settings.autoLaunchForTargetAppsEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.targetAppPackages,
                    onValueChange = onTargetPackagesChanged,
                    label = { Text("Target App Package IDs") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ==========================================
// Helper UI Components
// ==========================================
@Composable
fun ModernToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFFA0AEC0),
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF00D2FF)
            )
        )
    }
}

@Composable
fun InAppUpdateCard(
    updateInfo: UpdateInfo,
    updateProgress: Float,
    statusText: String,
    onUpdateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFF00D2FF), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A2E))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚀 New Update Available: v${updateInfo.versionName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF00D2FF)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = updateInfo.releaseNotes,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (updateProgress >= 0f) {
                LinearProgressIndicator(
                    progress = { updateProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00D2FF)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText.ifEmpty { "Downloading update... ${updateProgress.toInt()}%" },
                    fontSize = 12.sp,
                    color = Color(0xFF00D2FF)
                )
            } else {
                Button(
                    onClick = onUpdateClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                ) {
                    Text("⚡ Update Now (1-Tap)", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}
