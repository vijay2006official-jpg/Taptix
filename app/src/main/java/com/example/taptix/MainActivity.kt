package com.example.taptix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.OperatingMode
import com.example.taptix.model.PlatformPreset
import com.example.taptix.service.AutoClickService
import com.example.taptix.service.OverlayService
import com.example.taptix.ui.theme.TaptixTheme
import com.example.taptix.util.UpdateInfo
import com.example.taptix.util.UpdateManager

// ─── Color Palette ────────────────────────────────────────────────────────────
val BrandCyan    = Color(0xFF00E5FF)
val BrandGreen   = Color(0xFF00E676)
val BrandRed     = Color(0xFFFF4444)
val BrandAmber   = Color(0xFFFFC107)
val DarkBase     = Color(0xFF0D0D0F)
val DarkSurface  = Color(0xFF1A1A22)
val DarkCard     = Color(0xFF20202C)
val DarkControl  = Color(0xFF2A2B35)
val TextMuted    = Color(0xFF8892A4)
val TextSubtle   = Color(0xFF5A6478)

// ─── Activity ─────────────────────────────────────────────────────────────────
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

// ─── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaptixDashboardScreen(prefsRepo: PreferencesRepository) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled   by remember { mutableStateOf(false) }
    var isOverlayPermGranted     by remember { mutableStateOf(false) }
    var isServiceActive          by remember { mutableStateOf(false) }
    var appSettings              by remember { mutableStateOf(prefsRepo.getSettings()) }
    var showPermDialog           by remember { mutableStateOf(false) }
    var permDialogMsg            by remember { mutableStateOf("") }

    val updateManager  = remember { UpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress  by remember { mutableFloatStateOf(-1f) }
    var updateStatus    by remember { mutableStateOf("") }

    // Re-check on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AutoClickService.isAccessibilityPermissionGranted(context)
                    && AutoClickService.isServiceRunning()
                isOverlayPermGranted   = Settings.canDrawOverlays(context)
                isServiceActive        = OverlayService.instance != null
                appSettings            = prefsRepo.getSettings()

                updateManager.checkForUpdate(
                    "https://raw.githubusercontent.com/vijay2006official-jpg/Taptix/main/version.json"
                ) { info -> availableUpdate = info }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission missing dialog
    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Permission Required", fontWeight = FontWeight.Bold,
                    color = Color.White, fontSize = 18.sp)
            },
            text = {
                Text(permDialogMsg, color = TextMuted, fontSize = 14.sp, lineHeight = 22.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermDialog = false
                        if (!isAccessibilityEnabled) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = DarkBase,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0D)),
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo + Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BrandCyan.copy(alpha = 0.8f), Color(0xFF0066FF))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚡", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "TAPTIX",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    "Auto-Accept Driver App",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        // Version badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandCyan.copy(alpha = 0.12f),
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                "v1.5 PRO",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandCyan
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Update Banner ─────────────────────────────────────────────────
            if (availableUpdate != null) {
                UpdateBannerCard(
                    info = availableUpdate!!,
                    progress = updateProgress,
                    statusText = updateStatus,
                    onUpdate = {
                        val apkUrl = availableUpdate?.apkUrl ?: return@UpdateBannerCard
                        updateManager.downloadAndInstallApk(
                            apkUrl = apkUrl,
                            onProgress = { p: Int -> updateProgress = p / 100f },
                            onComplete = { updateStatus = "Installing..." },
                            onError = { e: String -> updateStatus = "Error: $e" }
                        )
                    }
                )
            }

            // ── Status Pills Row ─────────────────────────────────────────────
            StatusPillsRow(
                accessibilityOk = isAccessibilityEnabled,
                overlayOk       = isOverlayPermGranted,
                serviceOk       = isServiceActive
            )

            // ── HERO Power Button ─────────────────────────────────────────────
            HeroPowerButton(
                isOn = isServiceActive,
                hasPermissions = isAccessibilityEnabled && isOverlayPermGranted,
                onToggle = { wantOn ->
                    if (wantOn) {
                        when {
                            !isAccessibilityEnabled -> {
                                permDialogMsg = "Accessibility Service is OFF.\n\nGo to Settings → Accessibility → Taptix → Turn ON"
                                showPermDialog = true
                            }
                            !isOverlayPermGranted -> {
                                permDialogMsg = "Display Over Apps is OFF.\n\nGo to Settings → Apps → Taptix → Display over other apps → Allow"
                                showPermDialog = true
                            }
                            else -> {
                                val intent = Intent(context, OverlayService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                isServiceActive = true
                            }
                        }
                    } else {
                        context.stopService(Intent(context, OverlayService::class.java))
                        AutoClickService.instance?.stopClicking()
                        isServiceActive = false
                    }
                }
            )

            // ── Platform Selector ─────────────────────────────────────────────
            PlatformSelectorCard(
                selected = appSettings.platformPreset,
                onSelect = { preset ->
                    prefsRepo.savePlatformPreset(preset)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // ── Settings Card ─────────────────────────────────────────────────
            SettingsCard(
                settings = appSettings,
                onModeChange = { mode ->
                    prefsRepo.saveOperatingMode(mode)
                    appSettings = prefsRepo.getSettings()
                },
                onTtsChange = { enabled ->
                    prefsRepo.saveTtsFeedbackEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onMotionLockChange = { enabled ->
                    prefsRepo.saveMotionSafetyLockEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onSpeedChange = { intervalMs ->
                    prefsRepo.saveClickInterval(intervalMs)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // ── Permission Fix Buttons ─────────────────────────────────────────
            if (!isAccessibilityEnabled || !isOverlayPermGranted) {
                PermissionFixCard(
                    accessibilityOk = isAccessibilityEnabled,
                    overlayOk       = isOverlayPermGranted,
                    onFixAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onFixOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // ── Launch Floating Controls ──────────────────────────────────────
            Button(
                onClick = {
                    when {
                        !isAccessibilityEnabled -> {
                            permDialogMsg = "Enable Accessibility first.\nSettings → Accessibility → Taptix → ON"
                            showPermDialog = true
                        }
                        !isOverlayPermGranted -> {
                            permDialogMsg = "Enable Display Over Apps.\nSettings → Apps → Taptix → Display over other apps → Allow"
                            showPermDialog = true
                        }
                        else -> {
                            val intent = Intent(context, OverlayService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isServiceActive = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00B4FF), BrandCyan, Color(0xFF00FFC8))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "LAUNCH FLOATING CONTROLS",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // ── How to Use Guide ─────────────────────────────────────────────
            HowToUseCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─── Hero Power Button ────────────────────────────────────────────────────────
@Composable
fun HeroPowerButton(
    isOn: Boolean,
    hasPermissions: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isOn) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = if (isOn) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val btnColor by animateColorAsState(
        targetValue = if (isOn) BrandGreen else Color(0xFF2A2A35),
        animationSpec = tween(400),
        label = "btnColor"
    )
    val glowColor by animateColorAsState(
        targetValue = if (isOn) BrandGreen else BrandRed.copy(alpha = 0.3f),
        animationSpec = tween(400),
        label = "glowColor"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = glowAlpha * 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(172.dp)
                    .scale(if (isOn) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.15f))
                    .border(
                        width = 3.dp,
                        color = if (isOn) BrandGreen else BrandRed.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner button
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    btnColor.copy(alpha = 0.9f),
                                    if (isOn) Color(0xFF006633) else Color(0xFF1A1A24)
                                )
                            )
                        )
                        .border(2.dp, btnColor.copy(alpha = 0.7f), CircleShape)
                        .clickable { onToggle(!isOn) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOn) "⏻" else "⏻",
                            fontSize = 36.sp,
                            color = if (isOn) Color.White else Color(0xFF6B7280)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isOn) "RUNNING" else "TAP TO\nSTART",
                            fontSize = if (isOn) 12.sp else 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOn) Color.White else TextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isOn) "✅ Auto-Accept is ACTIVE" else if (!hasPermissions) "⚠️ Permissions required" else "Tap button to start",
            fontSize = 13.sp,
            color = if (isOn) BrandGreen else if (!hasPermissions) BrandAmber else TextMuted,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Status Pills Row ─────────────────────────────────────────────────────────
@Composable
fun StatusPillsRow(
    accessibilityOk: Boolean,
    overlayOk: Boolean,
    serviceOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusPill("Accessibility", accessibilityOk, Modifier.weight(1f))
        StatusPill("Overlay",       overlayOk,       Modifier.weight(1f))
        StatusPill("Service",       serviceOk,       Modifier.weight(1f))
    }
}

@Composable
fun StatusPill(label: String, isOk: Boolean, modifier: Modifier = Modifier) {
    val color  = if (isOk) BrandGreen else BrandRed
    val status = if (isOk) "ON"        else "OFF"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text(status, fontSize = 11.sp, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─── Platform Selector ────────────────────────────────────────────────────────
@Composable
fun PlatformSelectorCard(
    selected: PlatformPreset,
    onSelect: (PlatformPreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Select Platform",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            val platforms = listOf(
                PlatformPreset.UNIVERSAL to "🌐 Universal",
                PlatformPreset.UBER      to "🚖 Uber",
                PlatformPreset.OLA       to "🟡 Ola",
                PlatformPreset.RAPIDO    to "🟠 Rapido",
                PlatformPreset.INDRIVER  to "🟣 inDrive",
            )

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                platforms.forEach { (preset, label) ->
                    val isSelected = selected == preset
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BrandCyan.copy(alpha = 0.18f) else DarkControl,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) BrandCyan else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(preset) }
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BrandCyan else TextMuted
                        )
                    }
                }
            }
        }
    }
}

// ─── Settings Card ────────────────────────────────────────────────────────────
@Composable
fun SettingsCard(
    settings: com.example.taptix.data.AppSettings,
    onModeChange: (OperatingMode) -> Unit,
    onTtsChange: (Boolean) -> Unit,
    onMotionLockChange: (Boolean) -> Unit,
    onSpeedChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚙️", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("Settings", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Smart Accept Mode
            SettingToggleRow(
                icon = "⚡",
                label = "Smart Accept Mode",
                sublabel = "Auto-finds & taps Accept button",
                isChecked = settings.operatingMode == OperatingMode.SMART_ACCEPT,
                onChanged = { on ->
                    onModeChange(if (on) OperatingMode.SMART_ACCEPT else OperatingMode.SINGLE_TARGET)
                }
            )

            HorizontalDivider(color = DarkControl, thickness = 1.dp)

            // Voice Feedback
            SettingToggleRow(
                icon = "🔊",
                label = "Voice Feedback",
                sublabel = "Speaks when ride is accepted",
                isChecked = settings.ttsFeedbackEnabled,
                onChanged = onTtsChange
            )

            HorizontalDivider(color = DarkControl, thickness = 1.dp)

            // Motion Safety Lock
            SettingToggleRow(
                icon = "🔒",
                label = "Motion Safety Lock",
                sublabel = "Pause while driving fast",
                isChecked = settings.motionSafetyLockEnabled,
                onChanged = onMotionLockChange
            )

            HorizontalDivider(color = DarkControl, thickness = 1.dp)

            // Detection Speed
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Detection Speed", color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("How fast to scan for Accept button",
                            color = TextMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val speeds = listOf(
                        Triple("🔥 Fast",   150L,  settings.clickIntervalMs <= 200L),
                        Triple("⚡ Normal", 300L,  settings.clickIntervalMs in 201L..400L),
                        Triple("🐢 Slow",   600L,  settings.clickIntervalMs > 400L)
                    )
                    speeds.forEach { (label, ms, isSelected) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandAmber.copy(alpha = 0.18f) else DarkControl,
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) BrandAmber else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSpeedChange(ms) }
                        ) {
                            Text(
                                label,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrandAmber else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: String,
    label: String,
    sublabel: String,
    isChecked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(sublabel, color = TextMuted, fontSize = 11.sp)
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = BrandGreen,
                uncheckedThumbColor = TextSubtle,
                uncheckedTrackColor = DarkControl
            )
        )
    }
}

// ─── Permission Fix Card ──────────────────────────────────────────────────────
@Composable
fun PermissionFixCard(
    accessibilityOk: Boolean,
    overlayOk: Boolean,
    onFixAccessibility: () -> Unit,
    onFixOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1208)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("Permissions Needed",
                    color = BrandAmber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (!accessibilityOk) {
                Button(
                    onClick = onFixAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
                ) {
                    Text("🔑  Enable Accessibility Service",
                        color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            if (!overlayOk) {
                Button(
                    onClick = onFixOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                ) {
                    Text("🪟  Allow Display Over Other Apps",
                        color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── How To Use Card ─────────────────────────────────────────────────────────
@Composable
fun HowToUseCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📖 How to Use", fontWeight = FontWeight.Bold,
                color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            val steps = listOf(
                "1️⃣" to "Enable Accessibility Service (tap button above)",
                "2️⃣" to "Allow Display Over Other Apps",
                "3️⃣" to "Tap the big Power Button or Launch Floating Controls",
                "4️⃣" to "Open your ride-hailing app (Uber, Ola, Rapido...)",
                "5️⃣" to "Taptix will AUTO-ACCEPT rides instantly! 🚀"
            )
            steps.forEach { (num, text) ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(num, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

// ─── Update Banner ────────────────────────────────────────────────────────────
@Composable
fun UpdateBannerCard(
    info: UpdateInfo,
    progress: Float,
    statusText: String,
    onUpdate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🆕", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Update Available — v${info.versionName}",
                        color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(info.releaseNotes, color = TextMuted,
                        fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BrandCyan,
                    trackColor = DarkControl
                )
                Spacer(Modifier.height(6.dp))
                Text(statusText, color = TextMuted, fontSize = 11.sp)
            } else {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                ) {
                    Text("Download & Install Update",
                        color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
