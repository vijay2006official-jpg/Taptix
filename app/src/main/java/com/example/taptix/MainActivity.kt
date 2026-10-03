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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
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

// Driver-First Color System Tokens
val BrandCyan = Color(0xFF00E5FF)
val BrandGreen = Color(0xFF00E676)
val BrandAmber = Color(0xFFFFC107)
val DarkBase = Color(0xFF121214)
val DarkSurface = Color(0xFF1E1E24)
val DarkControl = Color(0xFF2A2B32)
val TextMuted = Color(0xFFA0AEC0)

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

    // Info Dialog Tooltip State
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

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

    // Modal Info Dialog
    if (infoDialogTitle != null && infoDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogContent = null
            },
            title = {
                Text(text = infoDialogTitle!!, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(text = infoDialogContent!!, fontSize = 14.sp, color = Color(0xFFCBD5E1), lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    infoDialogTitle = null
                    infoDialogContent = null
                }) {
                    Text("Got It", fontWeight = FontWeight.Bold, color = BrandCyan)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Taptix Logo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "TAPTIX",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 19.sp,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandCyan.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyan.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "PRO v1.2",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrandCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Glanceable Driver Assistant",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Top GPS Sync Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DarkControl,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandGreen,
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "GPS SYNC",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0E0E10)
                )
            )
        },
        containerColor = DarkBase
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

            // 2. Top-Anchored Dynamic Status Banner (Semantic Amber / Green)
            ErgonomicStatusBanner(
                isAccessibilityActive = isAccessibilityEnabled,
                isOverlayGranted = isOverlayPermissionGranted,
                onGrantAccessibility = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                onGrantOverlay = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // 3. Oversized 56dp Floating Overlay Launcher Button Card
            OversizedMasterControlCard(
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

            // 4. Glassmorphic Live Draggable Overlay Preview Canvas
            GlassmorphicPreviewCard(
                operatingMode = appSettings.operatingMode,
                preset = appSettings.platformPreset,
                dimIntensity = appSettings.nightModeDimIntensity,
                isNightModeActive = appSettings.nightModeAutoDimEnabled
            )

            // 5. Branded Driver App Package Selector (Interactive Grid Cards)
            BrandedAppPackageSelectorCard(
                targetPackages = appSettings.targetAppPackages,
                autoLaunchEnabled = appSettings.autoLaunchForTargetAppsEnabled,
                onToggleAutoLaunch = { enabled ->
                    prefsRepo.saveAutoLaunchForTargetAppsEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onPackagesUpdated = { newPackages ->
                    prefsRepo.saveTargetAppPackages(newPackages)
                    appSettings = prefsRepo.getSettings()
                },
                onShowInfo = {
                    infoDialogTitle = "Auto-Launch Driver Apps"
                    infoDialogContent = "When you open any of your enabled driver apps (Uber, Lyft, Ola, Rapido, etc.), Taptix automatically pops up the floating heads-up assistant over the screen hands-free."
                }
            )

            // 6. Detection Strategy Selection (Rich Vector Visual Cards)
            DetectionStrategyVisualCards(
                currentMode = appSettings.operatingMode,
                onModeSelected = { mode ->
                    prefsRepo.saveOperatingMode(mode)
                    appSettings = prefsRepo.getSettings()
                },
                onShowInfo = {
                    infoDialogTitle = "Detection Strategies"
                    infoDialogContent = "• Smart Accept (OCR AI): Reads the screen in real time and automatically taps or swipes incoming ride offers.\n\n• Single Target: Repeatedly taps a fixed point marked by the driver.\n\n• Multi-Target: Sequentially taps multiple marked locations."
                }
            )

            // 7. Standardized Range Slider (Touch Pulse Speed)
            StandardizedSliderCard(
                title = "SPEED & PULSE INTERVAL",
                subtitle = "Touch pulse frequency for automated taps",
                currentVal = appSettings.clickIntervalMs.toFloat(),
                minVal = 50f,
                maxVal = 1000f,
                valueFormat = "${appSettings.clickIntervalMs} ms",
                minScaleLabel = "50 ms (Fast)",
                maxScaleLabel = "1000 ms (Standard)",
                accentColor = BrandCyan,
                onValueChange = { newVal ->
                    prefsRepo.saveClickInterval(newVal.toLong())
                    appSettings = prefsRepo.getSettings()
                }
            )

            // 8. Driver Safety & Automation Card (Streamlined 1-Line Taglines)
            ErgonomicSafetyAutomationCard(
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
                },
                onShowInfo = { title, desc ->
                    infoDialogTitle = title
                    infoDialogContent = desc
                }
            )

            // 9. Night-Mode & Screen Health (With Standardized Intensity Slider)
            ErgonomicNightModeCard(
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
                },
                onShowInfo = {
                    infoDialogTitle = "Night Mode & OLED Screen Health"
                    infoDialogContent = "Auto-dimming reduces the brightness of floating widgets when idle, preventing blinding cabin glare at night. OLED Pixel-Shift subtly moves UI elements by 2 pixels to completely prevent permanent screen burn-in during long shifts."
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// 1. Ergonomic Top-Anchored Status Banner
// ==========================================
@Composable
fun ErgonomicStatusBanner(
    isAccessibilityActive: Boolean,
    isOverlayGranted: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    val isSystemFullyActive = isAccessibilityActive && isOverlayGranted

    val bannerBorder = if (isSystemFullyActive) BrandGreen else BrandAmber
    val bannerBg = DarkSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, bannerBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bannerBg)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ENGINE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSystemFullyActive) BrandGreen else BrandAmber,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSystemFullyActive) "SERVICE ARMED & ACTIVE" else "PERMISSIONS REQUIRED",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSystemFullyActive) BrandGreen else BrandAmber
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSystemFullyActive) BrandGreen.copy(alpha = 0.15f) else BrandAmber.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSystemFullyActive) BrandGreen.copy(alpha = 0.3f) else BrandAmber.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (isSystemFullyActive) "READY" else "ACTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSystemFullyActive) BrandGreen else BrandAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSystemFullyActive)
                    "Real-time ride detection and smart auto-swipe are armed."
                else
                    "Grant required system permissions to enable hands-free auto-acceptance.",
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )

            // Direct 1-Tap Action Buttons
            if (!isAccessibilityActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onGrantAccessibility,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
                ) {
                    Text(
                        text = "⚙️ Grant Accessibility Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }

            if (!isOverlayGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onGrantOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan)
                ) {
                    Text(
                        text = "🔓 Grant Display Over Other Apps",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Quick Status Pills
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PillIndicator(name = "Accessibility", active = isAccessibilityActive)
                PillIndicator(name = "Overlay Window", active = isOverlayGranted)
                PillIndicator(name = "Voice Mic", active = true)
            }
        }
    }
}

@Composable
fun PillIndicator(name: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (active) BrandGreen else BrandAmber,
            modifier = Modifier.size(7.dp)
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$name: ${if (active) "Active" else "Missing"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Color(0xFFE2E8F0) else TextMuted
        )
    }
}

// ==========================================
// 2. Oversized 56dp Master Control Button Card
// ==========================================
@Composable
fun OversizedMasterControlCard(
    isReady: Boolean,
    isOverlayRunning: Boolean,
    onToggleOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLOATING CONTROLS DOCK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isOverlayRunning) "Heads-Up Display Active" else "Heads-Up Display Idle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOverlayRunning) BrandGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = if (isOverlayRunning) "LIVE" else "STOPPED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOverlayRunning) BrandGreen else TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Oversized 56dp Touch Target
            Button(
                onClick = onToggleOverlay,
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayRunning) Color(0xFFE53935) else BrandCyan
                )
            ) {
                Text(
                    text = if (isOverlayRunning) "🛑 STOP FLOATING OVERLAY" else "🚀 LAUNCH FLOATING CONTROLS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isOverlayRunning) Color.White else Color.Black
                )
            }
        }
    }
}

// ==========================================
// 3. Glassmorphic Live Draggable Overlay Preview
// ==========================================
@Composable
fun GlassmorphicPreviewCard(
    operatingMode: OperatingMode,
    preset: PlatformPreset,
    dimIntensity: Float,
    isNightModeActive: Boolean
) {
    var previewPlaying by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OVERLAY LIVE PREVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "On-Screen Widget Demonstration",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandAmber.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "DRIVE MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Glassmorphic Map Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161820), Color(0xFF0C0E14))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                // Background Simulated Map Roads
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.05f)))
                    Spacer(modifier = Modifier.height(45.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.05f)))
                    Spacer(modifier = Modifier.height(45.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.05f)))
                }

                // Miniature Draggable Floating Widget
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Floating Controller Pill (Horizontal status only, no vertical text)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = DarkControl.copy(alpha = if (isNightModeActive) (dimIntensity * 1.5f).coerceIn(0.4f, 1f) else 1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ":::",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = TextMuted
                            )

                            // Interactive Mini Play Button
                            Surface(
                                shape = CircleShape,
                                color = if (previewPlaying) BrandGreen else Color(0xFFFF5252),
                                modifier = Modifier
                                    .clickable { previewPlaying = !previewPlaying }
                                    .size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (previewPlaying) "▶" else "⏸",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DarkBase
                            ) {
                                Text(
                                    text = "SMART ACCEPT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BrandAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = preset.title.split(" ").first().uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "🎙️",
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Bottom Simulated Real-Time Ride Offer Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkControl.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.4f)),
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
                                    color = BrandAmber
                                ) {
                                    Text(
                                        text = "OFFER DETECTED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                                Text(
                                    text = "$24.50 • 4.2 mi",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BrandGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Terminal 3 Airport ➔ Grand Central",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BrandGreen,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (preset.requiresSwipe) "AUTO-SWIPING TO ACCEPT ➔" else "AUTO-CLICKING TO ACCEPT ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center
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
// 4. Branded Driver App Package Selector (Interactive Cards)
// ==========================================
data class DriverAppItem(val name: String, val badge: String, val packageId: String, val badgeColor: Color)

@Composable
fun BrandedAppPackageSelectorCard(
    targetPackages: String,
    autoLaunchEnabled: Boolean,
    onToggleAutoLaunch: (Boolean) -> Unit,
    onPackagesUpdated: (String) -> Unit,
    onShowInfo: () -> Unit
) {
    val predefinedApps = listOf(
        DriverAppItem("Uber Driver", "UBER", "com.ubercab.driver", Color.White),
        DriverAppItem("Lyft", "LYFT", "me.lyft.driver", Color(0xFFFF00BF)),
        DriverAppItem("Ola Driver", "OLA", "com.olacabs.driver", Color(0xFF32D74B)),
        DriverAppItem("Rapido", "RAPIDO", "com.rapido.passenger", BrandAmber),
        DriverAppItem("inDrive", "INDRIVE", "com.indriver", BrandCyan),
        DriverAppItem("Grab Driver", "GRAB", "com.grabtaxi.driver2", Color(0xFF00B14F))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTO-LAUNCH APPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Supported Taxi Platforms",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShowInfo, modifier = Modifier.size(28.dp)) {
                        Text(text = "ⓘ", color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = autoLaunchEnabled,
                        onCheckedChange = onToggleAutoLaunch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = BrandCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Branded 3x2 Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                predefinedApps.chunked(3).forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowApps.forEach { appItem ->
                            val isIncluded = targetPackages.contains(appItem.packageId, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isIncluded) BrandCyan.copy(alpha = 0.12f) else DarkControl,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isIncluded) BrandCyan else Color.White.copy(alpha = 0.06f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val currentList = targetPackages.split(",").map { it.trim() }.toMutableList()
                                        if (isIncluded) {
                                            currentList.removeAll { it.equals(appItem.packageId, ignoreCase = true) }
                                        } else {
                                            if (!currentList.contains(appItem.packageId)) {
                                                currentList.add(appItem.packageId)
                                            }
                                        }
                                        onPackagesUpdated(currentList.filter { it.isNotEmpty() }.joinToString(", "))
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black,
                                        modifier = Modifier.size(34.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = appItem.badge.take(3),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp,
                                                color = appItem.badgeColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = appItem.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncluded) Color.White else TextMuted,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (isIncluded) "● Active" else "○ Inactive",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isIncluded) BrandCyan else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. Detection Strategy Selection (Rich Vector Visual Cards)
// ==========================================
@Composable
fun DetectionStrategyVisualCards(
    currentMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit,
    onShowInfo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DETECTION STRATEGY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Acceptance Engine Mode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onShowInfo, modifier = Modifier.size(28.dp)) {
                    Text(text = "ⓘ", color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OperatingMode.entries.forEach { mode ->
                val isSelected = (mode == currentMode)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandCyan.copy(alpha = 0.12f) else DarkControl,
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isSelected) BrandCyan else Color.White.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onModeSelected(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandCyan.copy(alpha = 0.2f) else DarkBase,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (mode) {
                                        OperatingMode.SMART_ACCEPT -> "⚡"
                                        OperatingMode.SINGLE_TARGET -> "🎯"
                                        OperatingMode.MULTI_TARGET -> "🔢"
                                    },
                                    fontSize = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mode.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) BrandCyan else Color.White
                                )
                                if (mode == OperatingMode.SMART_ACCEPT) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BrandGreen.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "RECOMMENDED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrandGreen,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandCyan)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. Standardized Range Slider Card
// ==========================================
@Composable
fun StandardizedSliderCard(
    title: String,
    subtitle: String,
    currentVal: Float,
    minVal: Float,
    maxVal: Float,
    valueFormat: String,
    minScaleLabel: String,
    maxScaleLabel: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    var sliderState by remember(currentVal) { mutableFloatStateOf(currentVal) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = valueFormat,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
                value = sliderState,
                onValueChange = { sliderState = it },
                onValueChangeFinished = { onValueChange(sliderState) },
                valueRange = minVal..maxVal,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = DarkControl
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = minScaleLabel, fontSize = 11.sp, color = TextMuted)
                Text(text = maxScaleLabel, fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

// ==========================================
// 7. Driver Safety & Automation Card (Streamlined 1-Line Taglines)
// ==========================================
@Composable
fun ErgonomicSafetyAutomationCard(
    settings: AppSettings,
    onAutoPauseAcceptChanged: (Boolean) -> Unit,
    onKeyboardPauseChanged: (Boolean) -> Unit,
    onMotionLockChanged: (Boolean) -> Unit,
    onVoiceCommandsChanged: (Boolean) -> Unit,
    onTtsChanged: (Boolean) -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "DRIVER SAFETY & AUTOMATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandCyan,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Smart Highway Safety Shields",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            ErgonomicToggleRow(
                title = "Keyboard Auto-Pause",
                tagline = "Pauses clicks when soft keyboard is open for typing",
                checked = settings.autoPauseOnKeyboard,
                onCheckedChange = onKeyboardPauseChanged,
                onInfoClick = {
                    onShowInfo("Keyboard Auto-Pause", "Detects when you are typing a destination in Google Maps or a message to passenger, and automatically freezes auto-clicks so your typing is never interrupted.")
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 8.dp))

            ErgonomicToggleRow(
                title = "Auto-Pause on Accept",
                tagline = "Halts clicking sequence immediately once ride is booked",
                checked = settings.autoPauseOnAccept,
                onCheckedChange = onAutoPauseAcceptChanged,
                onInfoClick = {
                    onShowInfo("Auto-Pause on Accept", "Immediately pauses the auto-click sequence once an offer is locked in, preventing accidental cancellations or unintended button presses.")
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 8.dp))

            ErgonomicToggleRow(
                title = "Drive-Motion Safety Shield",
                tagline = "Sensor pauses gestures during high-speed driving or sharp turns",
                checked = settings.motionSafetyLockEnabled,
                onCheckedChange = onMotionLockChanged,
                onInfoClick = {
                    onShowInfo("Drive-Motion Safety Shield", "Monitors device accelerometer and gyroscope. If vehicle acceleration exceeds safe thresholds, gestures pause until the vehicle stabilizes.")
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 8.dp))

            ErgonomicToggleRow(
                title = "Hands-Free Voice Commands",
                tagline = "Speak \"ACCEPT\", \"START\", or \"STOP\" hands-free",
                checked = settings.voiceCommandsEnabled,
                onCheckedChange = onVoiceCommandsChanged,
                onInfoClick = {
                    onShowInfo("Hands-Free Voice Commands", "Allows you to operate Taptix purely by voice commands while driving without taking your eyes off the highway.")
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 8.dp))

            ErgonomicToggleRow(
                title = "TTS Speech Announcements",
                tagline = "Speaks trip fare & alerts out loud via vehicle speaker",
                checked = settings.ttsFeedbackEnabled,
                onCheckedChange = onTtsChanged,
                onInfoClick = {
                    onShowInfo("TTS Speech Announcements", "Uses Android's Speech synthesizer to announce new ride fares, distance, and booking confirmations through your car's Bluetooth audio.")
                }
            )
        }
    }
}

// ==========================================
// 8. Night-Mode & OLED Screen Health Card
// ==========================================
@Composable
fun ErgonomicNightModeCard(
    settings: AppSettings,
    onNightModeDimChanged: (Boolean) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onDimDelayChanged: (Int) -> Unit,
    onPixelShiftChanged: (Boolean) -> Unit,
    onShowInfo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NIGHT DRIVING & SCREEN HEALTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "🌙 Night-Mode & OLED Burn-In Shield",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onShowInfo, modifier = Modifier.size(28.dp)) {
                    Text(text = "ⓘ", color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ErgonomicToggleRow(
                title = "Auto-Dim Overlay When Idle",
                tagline = "Reduces floating brightness to prevent cabin glare",
                checked = settings.nightModeAutoDimEnabled,
                onCheckedChange = onNightModeDimChanged,
                onInfoClick = onShowInfo
            )

            if (settings.nightModeAutoDimEnabled) {
                Spacer(modifier = Modifier.height(14.dp))

                // Standardized Dimming Opacity Slider
                var intensityState by remember(settings.nightModeDimIntensity) {
                    mutableFloatStateOf(settings.nightModeDimIntensity)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkControl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Dimming Opacity Intensity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "${(intensityState * 100).toInt()}% Opacity", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandAmber)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = intensityState,
                            onValueChange = { intensityState = it },
                            onValueChangeFinished = { onIntensityChanged(intensityState) },
                            valueRange = 0.15f..0.85f,
                            colors = SliderDefaults.colors(thumbColor = BrandAmber, activeTrackColor = BrandAmber)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "15% (Ultra Dim)", fontSize = 10.sp, color = TextMuted)
                            Text(text = "85% (Visible)", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Standardized Idle Timeout Delay Slider
                var delayState by remember(settings.autoDimDelaySeconds) {
                    mutableFloatStateOf(settings.autoDimDelaySeconds.toFloat())
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkControl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Idle Timeout Delay", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "${delayState.toInt()} seconds", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = delayState,
                            onValueChange = { delayState = it },
                            onValueChangeFinished = { onDimDelayChanged(delayState.toInt()) },
                            valueRange = 3f..30f,
                            steps = 26,
                            colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandCyan)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "3s (Fast)", fontSize = 10.sp, color = TextMuted)
                            Text(text = "30s", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 10.dp))

            ErgonomicToggleRow(
                title = "OLED Pixel-Shift Burn-In Shield",
                tagline = "Shifts UI by 2px periodically to protect phone display",
                checked = settings.pixelShiftBurnInProtection,
                onCheckedChange = onPixelShiftChanged,
                onInfoClick = onShowInfo
            )
        }
    }
}

// ==========================================
// Helper Ergonomic Toggle Row (Oversized 52dp)
// ==========================================
@Composable
fun ErgonomicToggleRow(
    title: String,
    tagline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onInfoClick, modifier = Modifier.size(20.dp)) {
                    Text(text = "ⓘ", color = TextMuted, fontSize = 13.sp)
                }
            }
            Text(
                text = tagline,
                fontSize = 11.sp,
                color = TextMuted,
                maxLines = 1
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = BrandCyan,
                uncheckedTrackColor = DarkControl
            )
        )
    }
}

// ==========================================
// In-App Auto-Update Card
// ==========================================
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
            .border(1.5.dp, BrandCyan, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
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
                    fontSize = 15.sp,
                    color = BrandCyan
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = updateInfo.releaseNotes,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (updateProgress >= 0f) {
                LinearProgressIndicator(
                    progress = { updateProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText.ifEmpty { "Downloading update... ${updateProgress.toInt()}%" },
                    fontSize = 12.sp,
                    color = BrandCyan
                )
            } else {
                Button(
                    onClick = onUpdateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                ) {
                    Text("⚡ Update Now (1-Tap)", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}
