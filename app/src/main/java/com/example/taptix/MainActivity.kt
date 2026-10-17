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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.taptix.model.PlatformPreset
import com.example.taptix.service.AutoClickService
import com.example.taptix.service.OverlayService
import com.example.taptix.ui.theme.TaptixTheme
import com.example.taptix.util.UpdateInfo
import com.example.taptix.util.UpdateManager

// ─── Colors ───────────────────────────────────────────────────────────────────
val BrandCyan    = Color(0xFF00E5FF)
val BrandGreen   = Color(0xFF00E676)
val BrandRed     = Color(0xFFFF4444)
val BrandAmber   = Color(0xFFFFC107)
val BrandBlue    = Color(0xFF1565C0)
val DarkBase     = Color(0xFF0D0F14)
val DarkCard     = Color(0xFF151821)
val DarkControl  = Color(0xFF1E2230)
val TextMuted    = Color(0xFF8892A4)

class MainActivity : ComponentActivity() {
    private lateinit var prefsRepo: PreferencesRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)
        enableEdgeToEdge()
        setContent { TaptixTheme { TaptixMainScreen(prefsRepo) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaptixMainScreen(prefsRepo: PreferencesRepository) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityOn  by remember { mutableStateOf(false) }
    var isOverlayOn        by remember { mutableStateOf(false) }
    var isServiceRunning   by remember { mutableStateOf(false) }
    var appSettings        by remember { mutableStateOf(prefsRepo.getSettings()) }

    val updateManager   = remember { UpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress  by remember { mutableStateOf(-1f) }
    var updateStatus    by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityOn = AutoClickService.isAccessibilityPermissionGranted(context) && AutoClickService.isServiceRunning()
                isOverlayOn       = Settings.canDrawOverlays(context)
                isServiceRunning  = OverlayService.instance != null
                appSettings       = prefsRepo.getSettings()
                updateManager.checkForUpdate("https://raw.githubusercontent.com/vijay2006official-jpg/Taptix/main/version.json") { availableUpdate = it }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = DarkBase,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF080B12)),
                title = {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Logo circle
                            Box(Modifier.size(38.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(BrandCyan, Color(0xFF0050CC)))),
                                contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("TAPTIX", fontWeight = FontWeight.Black,
                                    fontSize = 20.sp, color = Color.White, letterSpacing = 2.sp)
                                Text("Accessibility Protocol v2.0",
                                    fontSize = 10.sp, color = TextMuted)
                            }
                        }

                        Surface(shape = RoundedCornerShape(8.dp),
                            color = BrandCyan.copy(alpha = 0.12f)) {
                            Text("v1.7 PRO",
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BrandCyan)
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Update Banner
            if (availableUpdate != null) {
                SmallUpdateCard(availableUpdate!!, updateProgress, updateStatus) {
                    updateManager.downloadAndInstallApk(
                        availableUpdate!!.apkUrl,
                        onProgress = { p -> updateProgress = p / 100f },
                        onComplete = { updateStatus = "Installing..." },
                        onError    = { e -> updateStatus = "Error: $e" }
                    )
                }
            }

            // ── Permission warning
            if (!isAccessibilityOn || !isOverlayOn) {
                PermissionWarningBar(isAccessibilityOn, isOverlayOn,
                    onFixA = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onFixO = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))) }
                )
            }

            // ── 1. MASTER CONTROL CARD (Blue)
            MasterControlCard(
                isOn = appSettings.masterControlEnabled,
                isServiceRunning = isServiceRunning,
                hasPermissions   = isAccessibilityOn && isOverlayOn,
                onToggle = { wantOn ->
                    if (wantOn && (!isAccessibilityOn || !isOverlayOn)) return@MasterControlCard
                    prefsRepo.saveMasterControl(wantOn)
                    if (wantOn) {
                        val i = Intent(context, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            context.startForegroundService(i) else context.startService(i)
                        AutoClickService.instance?.setClickingActive(true)
                    } else {
                        AutoClickService.instance?.setClickingActive(false)
                    }
                    appSettings = prefsRepo.getSettings()
                }
            )

            // ── 2. ULTRA MODE CARD (Dark)
            UltraModeCard(
                isOn = appSettings.ultraModeEnabled,
                onToggle = { on ->
                    prefsRepo.saveUltraMode(on)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // ── 3. MAP ZONE CARD (Blue)
            MapZoneCard(
                isOn = appSettings.mapZoneEnabled,
                hasZone = appSettings.mapZoneLat != 0.0 || appSettings.mapZoneLng != 0.0,
                radiusKm = appSettings.mapZoneRadiusKm,
                onToggle = { on ->
                    prefsRepo.saveMapZoneEnabled(on)
                    appSettings = prefsRepo.getSettings()
                },
                onOpenMap = { context.startActivity(Intent(context, MapZoneActivity::class.java)) }
            )

            // ── 4. PLATFORM SELECTOR
            PlatformRow(appSettings.platformPreset) { preset ->
                prefsRepo.savePlatformPreset(preset)
                appSettings = prefsRepo.getSettings()
            }

            // ── 5. SPEED SELECTOR
            SpeedCard(appSettings.clickIntervalMs) { ms ->
                prefsRepo.saveClickInterval(ms)
                appSettings = prefsRepo.getSettings()
            }

            // ── 6. SOUND & VIBRATION TOGGLES
            FeedbackCard(
                ttsOn = appSettings.ttsFeedbackEnabled,
                onTts = { prefsRepo.saveTtsFeedbackEnabled(it); appSettings = prefsRepo.getSettings() }
            )

            // ── 7. LAUNCH FLOATING CONTROLS
            Button(
                onClick = {
                    if (!isAccessibilityOn || !isOverlayOn) return@Button
                    val i = Intent(context, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(i) else context.startService(i)
                    prefsRepo.saveMasterControl(true)
                    appSettings = prefsRepo.getSettings()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Color(0xFF0050CC), Color(0xFF0099FF), BrandCyan)),
                        RoundedCornerShape(16.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("LAUNCH FLOATING CONTROLS",
                            color = Color.White, fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp, letterSpacing = 1.sp)
                    }
                }
            }

            // ── HOW TO SETUP
            HowToSetupCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── MASTER CONTROL CARD ─────────────────────────────────────────────────────
@Composable
fun MasterControlCard(
    isOn: Boolean,
    isServiceRunning: Boolean,
    hasPermissions: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isOn) 1.04f else 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "masterPulse"
    )
    val cardColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF1565C0) else Color(0xFF12141C),
        animationSpec = tween(300), label = "masterCard"
    )

    Card(
        modifier = Modifier.fillMaxWidth().scale(pulse),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOn) 8.dp else 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("MASTER CONTROL", fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontSize = 18.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!hasPermissions) "⚠️ Enable permissions first"
                    else if (isOn) "✅ Auto-accept is ACTIVE"
                    else "Main switch for auto-clicker protocol",
                    color = if (isOn) BrandGreen else Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = isOn,
                onCheckedChange = { if (hasPermissions) onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = BrandGreen,
                    uncheckedThumbColor = Color(0xFF444455),
                    uncheckedTrackColor = Color(0xFF1E2230)
                )
            )
        }
    }
}

// ─── ULTRA MODE CARD ──────────────────────────────────────────────────────────
@Composable
fun UltraModeCard(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) Color(0xFF1A1030) else Color(0xFF0F1018)
        ),
        border = if (isOn) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C4DFF))
                 else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡ ULTRA MODE", fontWeight = FontWeight.ExtraBold,
                        color = if (isOn) Color(0xFFB388FF) else Color.White,
                        fontSize = 17.sp, letterSpacing = 0.5.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isOn) "🔥 Accepting ALL bookings — no filters!"
                    else "Bypass area filtering and accept everything",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                )
            }
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = Color(0xFF7C4DFF),
                    uncheckedThumbColor = Color(0xFF444455),
                    uncheckedTrackColor = Color(0xFF1E2230)
                )
            )
        }
    }
}

// ─── MAP ZONE CARD ────────────────────────────────────────────────────────────
@Composable
fun MapZoneCard(
    isOn: Boolean,
    hasZone: Boolean,
    radiusKm: Double,
    onToggle: (Boolean) -> Unit,
    onOpenMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) Color(0xFF0D2040) else Color(0xFF111520)
        ),
        border = if (isOn) androidx.compose.foundation.BorderStroke(1.5.dp, BrandCyan.copy(alpha = 0.5f))
                 else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("🗺️ MAP ZONE", fontWeight = FontWeight.ExtraBold,
                    color = if (isOn) BrandCyan else Color.White,
                    fontSize = 17.sp, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isOn && hasZone) "✅ Active: ${String.format("%.1f", radiusKm)}km circle set"
                    else if (!hasZone) "Tap 🗺 to draw your target zone on map"
                    else "Set zone to filter bookings by location",
                    color = if (isOn) Color(0xFF80D8FF) else Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(
                    checked = isOn,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = BrandCyan,
                        uncheckedThumbColor = Color(0xFF444455),
                        uncheckedTrackColor = Color(0xFF1E2230)
                    )
                )
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(BrandCyan.copy(alpha = 0.15f))
                        .border(1.dp, BrandCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onOpenMap() },
                    contentAlignment = Alignment.Center
                ) { Text("🗺️", fontSize = 20.sp) }
            }
        }
    }
}

// ─── PLATFORM ROW ─────────────────────────────────────────────────────────────
@Composable
fun PlatformRow(selected: PlatformPreset, onSelect: (PlatformPreset) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text("🎯 SELECT PLATFORM", fontWeight = FontWeight.ExtraBold,
            color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PlatformPreset.UNIVERSAL to "🌐 All Apps",
                PlatformPreset.UBER      to "🚖 Uber",
                PlatformPreset.OLA       to "🟡 Ola",
                PlatformPreset.RAPIDO    to "🟠 Rapido",
                PlatformPreset.INDRIVER  to "🟣 inDrive"
            ).forEach { (preset, label) ->
                val sel = selected == preset
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) BrandBlue.copy(alpha = 0.3f) else DarkControl,
                    modifier = Modifier
                        .border(if (sel) 1.5.dp else 0.dp,
                            if (sel) BrandCyan else Color.Transparent,
                            RoundedCornerShape(10.dp))
                        .clickable { onSelect(preset) }
                ) {
                    Text(label,
                        Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) BrandCyan else TextMuted)
                }
            }
        }
    }
}

// ─── SPEED CARD ───────────────────────────────────────────────────────────────
@Composable
fun SpeedCard(intervalMs: Long, onSelect: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text("⚡ DETECTION SPEED", fontWeight = FontWeight.ExtraBold,
            color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("🔥 Fast",   150L,  intervalMs <= 200L),
                Triple("⚡ Normal", 300L,  intervalMs in 201L..400L),
                Triple("🐢 Slow",   600L,  intervalMs > 400L)
            ).forEach { (label, ms, sel) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) BrandAmber.copy(alpha = 0.18f) else DarkControl,
                    modifier = Modifier.weight(1f)
                        .border(if (sel) 1.5.dp else 0.dp,
                            if (sel) BrandAmber else Color.Transparent,
                            RoundedCornerShape(10.dp))
                        .clickable { onSelect(ms) }
                ) {
                    Text(label,
                        Modifier.padding(vertical = 11.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) BrandAmber else TextMuted)
                }
            }
        }
    }
}

// ─── FEEDBACK CARD ────────────────────────────────────────────────────────────
@Composable
fun FeedbackCard(ttsOn: Boolean, onTts: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("🔔 ALERTS & FEEDBACK", fontWeight = FontWeight.ExtraBold,
            color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("🔊 Voice: \"Ride Accepted by Taptix\"",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Plays ONCE after accepting — then silent",
                    color = TextMuted, fontSize = 11.sp)
            }
            Switch(checked = ttsOn, onCheckedChange = onTts,
                colors = SwitchDefaults.colors(checkedTrackColor = BrandGreen))
        }

        HorizontalDivider(color = DarkControl.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("📳 Vibration on Accept",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Single strong vibration pulse",
                    color = TextMuted, fontSize = 11.sp)
            }
            Text("ON", color = BrandGreen, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        HorizontalDivider(color = DarkControl.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("🤫 Silent when no booking",
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("No sound/vibration while waiting",
                    color = TextMuted, fontSize = 11.sp)
            }
            Text("AUTO", color = BrandCyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

// ─── PERMISSION WARNING ───────────────────────────────────────────────────────
@Composable
fun PermissionWarningBar(
    accessOk: Boolean, overlayOk: Boolean,
    onFixA: () -> Unit, onFixO: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1008))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("⚠️ SETUP REQUIRED", color = BrandAmber,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        if (!accessOk)
            Button(onClick = onFixA, Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)) {
                Text("Enable Accessibility Service", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        if (!overlayOk)
            Button(onClick = onFixO, Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)) {
                Text("Allow Display Over Other Apps", color = Color.Black, fontWeight = FontWeight.Bold)
            }
    }
}

// ─── HOW TO SETUP ─────────────────────────────────────────────────────────────
@Composable
fun HowToSetupCard() {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text("📖 HOW TO SETUP", fontWeight = FontWeight.ExtraBold,
            color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        listOf(
            "1️⃣" to "Enable Accessibility Service",
            "2️⃣" to "Allow Display Over Other Apps",
            "3️⃣" to "Select your platform (Uber/Ola/Rapido...)",
            "4️⃣" to "Optional: Draw Map Zone for area filtering",
            "5️⃣" to "Turn ON MASTER CONTROL → starts auto-accept",
            "6️⃣" to "Open your ride app → bookings accepted automatically! 🚀"
        ).forEach { (num, text) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(num, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(text, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

// ─── UPDATE BANNER ────────────────────────────────────────────────────────────
@Composable
fun SmallUpdateCard(
    info: UpdateInfo, progress: Float, statusText: String, onUpdate: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        CardDefaults.cardColors(Color(0xFF0D1A2E)),
        CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🆕", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Update v${info.versionName} available",
                        color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(info.releaseNotes, color = TextMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (progress >= 0f) {
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = BrandCyan, trackColor = DarkControl)
                Spacer(Modifier.height(4.dp))
                Text(statusText, color = TextMuted, fontSize = 11.sp)
            } else {
                Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)) {
                    Text("Download & Install", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
