package com.example.taptix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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

    val updateManager = remember { UpdateManager(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress by remember { mutableFloatStateOf(-1f) }
    var updateStatusText by remember { mutableStateOf("") }

    // Re-check permissions when screen becomes resumed, and check for updates
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AutoClickService.isServiceEnabled()
                isOverlayPermissionGranted = Settings.canDrawOverlays(context)
                updateManager.checkForUpdate("http://192.168.29.97:8080/version.json") { info ->
                    availableUpdate = info
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Taptix – Auto Clicker",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Ride-Hailing Driver Assistant",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // In-App Auto-Update Card (appears when a new version is detected)
            if (availableUpdate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🚀 New Update Available: v${availableUpdate?.versionName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = availableUpdate?.releaseNotes ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (updateProgress >= 0f) {
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateStatusText.ifEmpty { "Downloading update... ${updateProgress.toInt()}%" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Button(
                                onClick = {
                                    val apk = availableUpdate?.apkUrl ?: return@Button
                                    updateProgress = 0f
                                    updateStatusText = "Connecting..."
                                    updateManager.downloadAndInstallApk(
                                        apkUrl = apk,
                                        onProgress = { p -> updateProgress = p.toFloat() },
                                        onComplete = {
                                            updateStatusText = "Opening installer..."
                                        },
                                        onError = { err ->
                                            updateProgress = -1f
                                            updateStatusText = "Error: $err"
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("⚡ Update Now (1-Tap)")
                            }
                        }
                    }
                }
            }
            // Permission Dashboard Card
            PermissionDashboardCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
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

            // Service Controls Card
            OverlayControlCard(
                isReady = isAccessibilityEnabled && isOverlayPermissionGranted,
                onStartOverlay = {
                    val intent = Intent(context, OverlayService::class.java)
                    context.startForegroundService(intent)
                },
                onStopOverlay = {
                    val intent = Intent(context, OverlayService::class.java)
                    context.stopService(intent)
                }
            )

            // App-Specific Auto-Launch Card
            AutoLaunchCard(
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

            // Voice Command Card
            VoiceCommandCard(
                settings = appSettings,
                onVoiceCommandChanged = { enabled ->
                    prefsRepo.saveVoiceCommandsEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // Operating Mode Selection
            OperatingModeCard(
                currentMode = appSettings.operatingMode,
                onModeSelected = { mode ->
                    prefsRepo.saveOperatingMode(mode)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // Platform Preset Selector
            PlatformPresetCard(
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

            // Click Interval Config
            ClickIntervalCard(
                intervalMs = appSettings.clickIntervalMs,
                onIntervalChanged = { interval ->
                    prefsRepo.saveClickInterval(interval)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // Safety & Automation Features Card
            SafetyAutomationCard(
                settings = appSettings,
                onAutoPauseChanged = { enabled ->
                    prefsRepo.saveAutoPauseOnAccept(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onMotionLockChanged = { enabled ->
                    prefsRepo.saveMotionSafetyLockEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                },
                onTtsChanged = { enabled ->
                    prefsRepo.saveTtsFeedbackEnabled(enabled)
                    appSettings = prefsRepo.getSettings()
                }
            )

            // Night Mode Auto-Dimming & OLED Burn-In Card
            NightModeBurnInCard(
                settings = appSettings,
                onNightModeDimChanged = { enabled ->
                    prefsRepo.saveNightModeAutoDimEnabled(enabled)
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
        }
    }
}

@Composable
fun AutoLaunchCard(
    settings: AppSettings,
    onAutoLaunchChanged: (Boolean) -> Unit,
    onTargetPackagesChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🚀 App-Specific Auto-Launch",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ToggleRow(
                title = "Auto-Show Floating Bar",
                desc = "Automatically show Taptix floating bar when Uber, Lyft, InDrive, Ola, or Rapido opens",
                checked = settings.autoLaunchForTargetAppsEnabled,
                onCheckedChange = onAutoLaunchChanged
            )

            if (settings.autoLaunchForTargetAppsEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.targetAppPackages,
                    onValueChange = onTargetPackagesChanged,
                    label = { Text("Target App Package Names (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun VoiceCommandCard(
    settings: AppSettings,
    onVoiceCommandChanged: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🎙️ Hands-Free Voice Commands",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ToggleRow(
                title = "Driver Voice Acceptance",
                desc = "Speak \"ACCEPT\", \"DECLINE\", \"START\", or \"STOP\" to operate hands-free",
                checked = settings.voiceCommandsEnabled,
                onCheckedChange = onVoiceCommandChanged
            )
        }
    }
}

@Composable
fun PermissionDashboardCard(
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onGrantAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "System Permissions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility Status
            PermissionRow(
                title = "Accessibility Service",
                desc = "Simulate taps & analyze screen for Smart Accept",
                isGranted = isAccessibilityEnabled,
                onGrantClick = onGrantAccessibility
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Overlay Status
            PermissionRow(
                title = "Display Over Apps",
                desc = "Show floating driver toolbar on screen",
                isGranted = isOverlayGranted,
                onGrantClick = onGrantOverlay
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isGranted) "✓ Active" else "✗ Missing",
                    color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Enable", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OverlayControlCard(
    isReady: Boolean,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Floating Control Bar",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isReady) "Permissions granted. Launch driver overlay to begin." else "Please grant all missing permissions above first.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onStartOverlay,
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Start Overlay", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStopOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Stop Overlay", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OperatingModeCard(
    currentMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Operating Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OperatingMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (mode == currentMode),
                        onClick = { onModeSelected(mode) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = mode.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = mode.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformPresetCard(
    currentPreset: PlatformPreset,
    customKeywords: String,
    onPresetSelected: (PlatformPreset) -> Unit,
    onCustomKeywordsChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Driver App Preset",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = (preset == currentPreset),
                        onClick = { onPresetSelected(preset) },
                        label = { Text(preset.title.split(" ").first()) }
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

@Composable
fun ClickIntervalCard(
    intervalMs: Long,
    onIntervalChanged: (Long) -> Unit
) {
    var sliderPosition by remember(intervalMs) { mutableFloatStateOf(intervalMs.toFloat()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Click Delay Interval", fontWeight = FontWeight.Bold)
                Text(
                    text = "${sliderPosition.toInt()} ms",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onIntervalChanged(sliderPosition.toLong()) },
                valueRange = 50f..2000f,
                steps = 38
            )
        }
    }
}

@Composable
fun SafetyAutomationCard(
    settings: AppSettings,
    onAutoPauseChanged: (Boolean) -> Unit,
    onMotionLockChanged: (Boolean) -> Unit,
    onTtsChanged: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Driver Safety & Automation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
                title = "Auto-Pause on Accept",
                desc = "Pause clicking sequence immediately once ride is accepted",
                checked = settings.autoPauseOnAccept,
                onCheckedChange = onAutoPauseChanged
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ToggleRow(
                title = "Drive-Motion Safety Lock",
                desc = "Collapse UI into big safety toggle when vehicle motion detected",
                checked = settings.motionSafetyLockEnabled,
                onCheckedChange = onMotionLockChanged
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ToggleRow(
                title = "Audio Announcements (TTS)",
                desc = "Announce trip actions and state changes out loud",
                checked = settings.ttsFeedbackEnabled,
                onCheckedChange = onTtsChanged
            )
        }
    }
}

@Composable
fun NightModeBurnInCard(
    settings: AppSettings,
    onNightModeDimChanged: (Boolean) -> Unit,
    onDimDelayChanged: (Int) -> Unit,
    onPixelShiftChanged: (Boolean) -> Unit
) {
    var delaySlider by remember(settings.autoDimDelaySeconds) {
        mutableFloatStateOf(settings.autoDimDelaySeconds.toFloat())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🌙 Night-Mode Auto-Dimming & OLED Protection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
                title = "Auto-Dim UI When Idle",
                desc = "Dims floating overlay to 35% opacity and OLED black after idle delay to save eyes & screen",
                checked = settings.nightModeAutoDimEnabled,
                onCheckedChange = onNightModeDimChanged
            )

            if (settings.nightModeAutoDimEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Idle Dim Delay", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${delaySlider.toInt()} seconds",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = delaySlider,
                    onValueChange = { delaySlider = it },
                    onValueChangeFinished = { onDimDelayChanged(delaySlider.toInt()) },
                    valueRange = 3f..30f,
                    steps = 26
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ToggleRow(
                title = "OLED Pixel-Shift Burn-In Protection",
                desc = "Periodically shifts floating UI elements by +/- 2px while dimmed to prevent display burn-in during long night shifts",
                checked = settings.pixelShiftBurnInProtection,
                onCheckedChange = onPixelShiftChanged
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "⚡ Real-Time Trip Status Overlay Test Controls:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        OverlayService.instance?.updateTripStatus(
                            TripStatusInfo(
                                phase = TripPhase.OFFER_RECEIVED,
                                fare = "$22.50",
                                distance = "5.1 mi",
                                etaMinutes = 12,
                                pickupAddress = "742 Evergreen Terr",
                                dropoffAddress = "Downtown Station"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simulate Offer", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        OverlayService.instance?.updateTripStatus(
                            TripStatusInfo(
                                phase = TripPhase.TRIP_IN_PROGRESS,
                                fare = "$22.50",
                                distance = "3.2 mi",
                                etaMinutes = 7,
                                pickupAddress = "Onboard Passenger",
                                dropoffAddress = "Downtown Station"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("In Progress", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
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
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
