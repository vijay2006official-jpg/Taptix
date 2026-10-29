package com.example.taptix



import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.app.ActivityCompat

import android.app.Activity



import com.google.android.play.core.appupdate.AppUpdateInfo

import com.google.android.play.core.appupdate.AppUpdateManager

import com.google.android.play.core.appupdate.AppUpdateManagerFactory

import com.google.android.play.core.install.model.AppUpdateType

import com.google.android.play.core.install.model.UpdateAvailability

import androidx.compose.ui.draw.alpha

import android.content.ComponentName

import android.text.TextUtils

import android.Manifest



import android.content.Context



import android.content.Intent



import android.content.pm.PackageManager



import android.net.Uri



import android.os.Build



import android.os.Bundle



import android.os.PowerManager



import android.provider.Settings



import android.widget.Toast



import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

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



import androidx.compose.ui.text.font.FontWeight



import androidx.compose.ui.unit.dp



import androidx.compose.ui.unit.sp



import androidx.core.content.ContextCompat



import androidx.lifecycle.Lifecycle



import androidx.lifecycle.LifecycleEventObserver



import androidx.lifecycle.compose.LocalLifecycleOwner



import com.example.taptix.data.ActivityLogManager



import com.example.taptix.data.BackupRestoreManager



import com.example.taptix.data.PreferencesRepository



import com.example.taptix.model.LogStatus



import com.example.taptix.model.PlatformPreset



import com.example.taptix.service.AutoClickService



import com.example.taptix.service.OverlayService



import com.example.taptix.ui.components.AccessibilityDisclosureDialog



import com.example.taptix.ui.components.BatteryGuidanceDialog



import com.example.taptix.ui.components.DailyStatsCard



import com.example.taptix.ui.components.DiagnosticItem



import com.example.taptix.ui.components.ScheduleDialog



import com.example.taptix.ui.components.SelfCheckCard



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

    private lateinit var appUpdateManager: AppUpdateManager

    private val UPDATE_REQUEST_CODE = 500



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefsRepo = PreferencesRepository(this)

        enableEdgeToEdge()



        appUpdateManager = AppUpdateManagerFactory.create(this)

        checkForAppUpdate()



        setContent { TaptixTheme { TaptixMainScreen(prefsRepo) } }

    }



    private fun checkForAppUpdate() {

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->

            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            ) {

                try {

                    appUpdateManager.startUpdateFlowForResult(

                        appUpdateInfo,

                        AppUpdateType.IMMEDIATE,

                        this,

                        UPDATE_REQUEST_CODE

                    )

                } catch (e: Exception) {

                    android.util.Log.e("MainActivity", "App update start failed: ${e.message}")

                }

            }

        }.addOnFailureListener { e ->

            android.util.Log.d("MainActivity", "In-app update check failed: ${e.message}")

        }

    }



    override fun onResume() {

        super.onResume()

        if (::appUpdateManager.isInitialized) {

            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->

                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {

                    try {

                        appUpdateManager.startUpdateFlowForResult(

                            appUpdateInfo,

                            AppUpdateType.IMMEDIATE,

                            this,

                            UPDATE_REQUEST_CODE

                        )

                    } catch (e: Exception) {

                        android.util.Log.e("MainActivity", "Resume app update failed: ${e.message}")

                    }

                }

            }

        }

    }



    @Deprecated("Deprecated in Java")

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UPDATE_REQUEST_CODE) {

            if (resultCode != RESULT_OK) {

                android.util.Log.w("MainActivity", "Update flow failed! Result code: $resultCode")

            }

        }

    }

}



/**

 * Helper function to verify if custom AccessibilityService is actively enabled for this package.

 */

fun isAccessibilityServiceEnabled(context: Context): Boolean {

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





@OptIn(ExperimentalMaterial3Api::class)



@Composable



fun TaptixMainScreen(prefsRepo: PreferencesRepository) {



    val context        = LocalContext.current



    val lifecycleOwner = LocalLifecycleOwner.current



    var isAccessibilityOn  by remember { mutableStateOf(false) }



    var isOverlayOn        by remember { mutableStateOf(false) }



    var isLocationOn       by remember { mutableStateOf(false) }



    var isNotificationOn   by remember { mutableStateOf(false) }



    var isBatteryOptIgnored by remember { mutableStateOf(true) }



    var isMicOn            by remember { mutableStateOf(false) }



    var isServiceRunning   by remember { mutableStateOf(false) }



    var appSettings        by remember { mutableStateOf(prefsRepo.getSettings()) }



    var scheduleConfig by remember { mutableStateOf(prefsRepo.getScheduleConfig()) }



    var smartRules by remember { mutableStateOf(prefsRepo.getSmartAcceptRules()) }



    var showSmartRulesDialog by remember { mutableStateOf(false) }



    var showAccessibilityDisclosure by remember { mutableStateOf(false) }



    var showBatteryGuidance by remember { mutableStateOf(false) }



    var showScheduleDialog by remember { mutableStateOf(false) }

    var showTargetAreasDialog by remember { mutableStateOf(false) }
    var isTargetAreasActive by remember { mutableStateOf(prefsRepo.isTargetAreasEnabled()) }



    val updateManager   = remember { UpdateManager(context) }



    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }



    var updateProgress  by remember { mutableStateOf(-1f) }



    var updateStatus    by remember { mutableStateOf("") }



    val logManager = remember { ActivityLogManager.getInstance(context) }



    var activityLogs by remember { mutableStateOf(logManager.getLogs()) }



    fun refreshDiagnostics() {



        isAccessibilityOn = isAccessibilityServiceEnabled(context)



        isOverlayOn       = Settings.canDrawOverlays(context)



        isServiceRunning  = OverlayService.instance != null



        appSettings       = prefsRepo.getSettings()



        scheduleConfig    = prefsRepo.getScheduleConfig()



        activityLogs      = logManager.getLogs()



        isLocationOn = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||



                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED



        isNotificationOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {



            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED



        } else true



        isMicOn = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED



        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager



        isBatteryOptIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true



    }



    val locationLauncher = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.RequestMultiplePermissions()

    ) { perms ->

        val granted = perms.values.any { it } ||

            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||

            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        isLocationOn = granted

        refreshDiagnostics()

    }



    DisposableEffect(lifecycleOwner) {



        val obs = LifecycleEventObserver { _, event ->



            if (event == Lifecycle.Event.ON_RESUME) {



                refreshDiagnostics()



                updateManager.checkForUpdate("https://raw.githubusercontent.com/vijay2006official-jpg/Taptix/main/version.json") { availableUpdate = it }



            }



        }



        lifecycleOwner.lifecycle.addObserver(obs)



        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }



    }



    // Dialogs



    if (showAccessibilityDisclosure) {



        AccessibilityDisclosureDialog(



            onAgree = {



                showAccessibilityDisclosure = false



                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))



            },



            onDismiss = { showAccessibilityDisclosure = false }



        )



    }



    if (showBatteryGuidance) {



        BatteryGuidanceDialog(



            onDismiss = { showBatteryGuidance = false },



            onOpenBatterySettings = {



                showBatteryGuidance = false



                try {



                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {



                        data = Uri.parse("package:${context.packageName}")



                    }



                    context.startActivity(intent)



                } catch (e: Exception) {



                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))



                }



            }



        )



    }



    if (showSmartRulesDialog) {



        com.example.taptix.ui.components.SmartRulesDialog(



            initialRules = smartRules,



            onSave = { updated ->



                prefsRepo.saveSmartAcceptRules(updated)



                smartRules = updated



                showSmartRulesDialog = false



                Toast.makeText(context, "Smart Rules Saved", Toast.LENGTH_SHORT).show()



            },



            onDismiss = { showSmartRulesDialog = false }



        )



    }



    if (showScheduleDialog) {



        ScheduleDialog(



            initialConfig = scheduleConfig,



            onSave = { cfg ->



                prefsRepo.saveScheduleConfig(cfg)



                scheduleConfig = cfg



                showScheduleDialog = false



                Toast.makeText(context, "Schedule saved", Toast.LENGTH_SHORT).show()



            },



            onDismiss = { showScheduleDialog = false }



        )



    }



    if (showTargetAreasDialog) {

        TargetAreasDialog(

            initialAreas = appSettings.preferredAreas,

            isEnabled = isTargetAreasActive,

            onSave = { areas, enabled ->

                prefsRepo.savePreferredAreas(areas)

                prefsRepo.saveTargetAreasEnabled(enabled)

                isTargetAreasActive = enabled

                appSettings = prefsRepo.getSettings()

                showTargetAreasDialog = false

                Toast.makeText(context, "Target Areas updated", Toast.LENGTH_SHORT).show()

            },

            onDismiss = { showTargetAreasDialog = false }

        )

    }



    Scaffold(



        containerColor = DarkBase,



        topBar = {



            TopAppBar(



                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF080B12)),



                title = {



                    Row(



                        Modifier.fillMaxWidth().padding(end = 8.dp),



                        horizontalArrangement = Arrangement.SpaceBetween,



                        verticalAlignment = Alignment.CenterVertically



                    ) {



                        Row(verticalAlignment = Alignment.CenterVertically) {



                            Box(



                                Modifier.size(38.dp).clip(CircleShape)



                                    .background(Brush.radialGradient(listOf(BrandCyan, Color(0xFF0050CC)))),



                                contentAlignment = Alignment.Center



                            ) {



                                Text("⚡", fontSize = 20.sp)



                            }



                            Spacer(Modifier.width(10.dp))



                            Column {



                                Text("TAPTIX", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White, letterSpacing = 2.sp)



                                Text("Autonomous Captain Engine v2.1", fontSize = 10.sp, color = BrandCyan)



                            }



                        }



                        Surface(shape = RoundedCornerShape(8.dp), color = BrandCyan.copy(alpha = 0.12f)) {



                            Text("PRO", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BrandCyan)



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



            // ── 0. DIAGNOSTICS & PERMISSION SELF-CHECK (Phase 4 Item 27)



            SelfCheckCard(



                diagnostics = listOf(



                    DiagnosticItem("Accessibility Engine", "Required to detect and tap driver alerts", isAccessibilityOn) {

                        showAccessibilityDisclosure = true

                    },

                    DiagnosticItem("Floating Overlay", "Required for on-screen driver controls", isOverlayOn) {

                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

                    },

                    DiagnosticItem("GPS Location", "Required for geofenced map zone filtering", isLocationOn) {

                        if (!isLocationOn) {

                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            if (fine || coarse) {

                                refreshDiagnostics()

                            } else {

                                val act = context as? Activity

                                val rationale = act?.let {

                                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)

                                } ?: false

                                if (rationale) {

                                    try {

                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {

                                            data = Uri.parse("package:${context.packageName}")

                                        }

                                        context.startActivity(intent)

                                        Toast.makeText(context, "Please allow Location in App Permissions", Toast.LENGTH_LONG).show()

                                    } catch (_: Exception) {

                                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

                                    }

                                } else {

                                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

                                }

                            }

                        }

                    },

                    DiagnosticItem("Battery Exemption", "Prevents OEM systems from pausing background helper", isBatteryOptIgnored) {

                        showBatteryGuidance = true

                    }

                ),



                onOpenBatteryGuide = { showBatteryGuidance = true },

                onCardClick = {

                    if (!isAccessibilityOn) {

                        showAccessibilityDisclosure = true

                    } else if (!isOverlayOn) {

                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

                    } else if (!isLocationOn) {

                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

                    }

                }



            )



            // ── 1. MASTER CONTROL CARD



            val hasRequiredPermissions = isAccessibilityOn && isOverlayOn

            val missingPermissionsCount = (if (!isAccessibilityOn) 1 else 0) + (if (!isOverlayOn) 1 else 0)



            MasterControlCard(

                isOn = appSettings.masterControlEnabled && hasRequiredPermissions,

                isServiceRunning = isServiceRunning,

                hasPermissions = hasRequiredPermissions,

                missingPermissionsCount = missingPermissionsCount,

                onDisabledClick = {

                    if (!isAccessibilityOn) {

                        Toast.makeText(context, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()

                        showAccessibilityDisclosure = true

                    } else if (!isOverlayOn) {

                        Toast.makeText(context, "Please grant Overlay ('Display over other apps') permission", Toast.LENGTH_SHORT).show()

                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

                    }

                },

                onToggle = { wantOn ->

                    if (wantOn && !hasRequiredPermissions) {

                        Toast.makeText(context, "Please enable Accessibility & Overlay permissions first", Toast.LENGTH_SHORT).show()

                        return@MasterControlCard

                    }

                    prefsRepo.saveMasterControl(wantOn)

                    if (wantOn) {

                        val i = Intent(context, OverlayService::class.java)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

                            context.startForegroundService(i) else context.startService(i)

                        AutoClickService.instance?.setClickingActive(true)

                    } else {

                        OverlayService.stopService(context)

                        AutoClickService.instance?.setClickingActive(false)

                    }

                    appSettings = prefsRepo.getSettings()

                }

            )



            // ── 2. AUTO-ACCEPT SIMULATOR (Phase 2 Item 18)



            Card(



                modifier = Modifier.fillMaxWidth().clickable {



                    context.startActivity(Intent(context, DemoActivity::class.java))



                },



                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),



                shape = RoundedCornerShape(16.dp),



                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8))



            ) {



                Row(



                    Modifier.fillMaxWidth().padding(14.dp),



                    verticalAlignment = Alignment.CenterVertically,



                    horizontalArrangement = Arrangement.SpaceBetween



                ) {



                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {



                        Box(



                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0284C7)),



                            contentAlignment = Alignment.Center



                        ) {



                            Text("🧪", fontSize = 20.sp)



                        }



                        Column {



                            Text("TEST IN DEMO SIMULATOR", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)



                            Text("Simulate live ride offers & measure tap speed", color = Color(0xFF94A3B8), fontSize = 11.sp)



                        }



                    }



                    Text("Open ➔", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)



                }



            }



            // ── 3. SHIFT SCHEDULE & DAILY STATS (Phase 5 Item 30 & 31)



            DailyStatsCard(



                logs = activityLogs,



                onResetStats = {



                    logManager.clearLogs()



                    activityLogs = emptyList()



                    Toast.makeText(context, "Shift stats reset", Toast.LENGTH_SHORT).show()



                }



            )



            // Shift Schedule Toggle Tile



            Card(



                modifier = Modifier.fillMaxWidth().clickable { showScheduleDialog = true },



                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),



                shape = RoundedCornerShape(16.dp),



                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))



            ) {



                Row(



                    Modifier.fillMaxWidth().padding(14.dp),



                    horizontalArrangement = Arrangement.SpaceBetween,



                    verticalAlignment = Alignment.CenterVertically



                ) {



                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {



                        Text("⏰", fontSize = 18.sp)



                        Column {



                            Text("SHIFT SCHEDULE WINDOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)



                            Text(



                                if (scheduleConfig.isEnabled)



                                    "Active: ${String.format("%02d:00", scheduleConfig.startHour)} to ${String.format("%02d:00", scheduleConfig.endHour)}"



                                else "Disabled (Manual Master Toggle)",



                                color = if (scheduleConfig.isEnabled) BrandCyan else TextMuted,



                                fontSize = 11.sp



                            )



                        }



                    }



                    Text("Edit ➔", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)



                }



            }



            // ── 4. ULTRA MODE CARD



            UltraModeCard(



                isOn = appSettings.ultraModeEnabled,



                onToggle = { on ->



                    prefsRepo.saveUltraMode(on)



                    appSettings = prefsRepo.getSettings()



                }



            )



            // ── 5. MAP ZONE CARD



            MapZoneCard(



                isOn = appSettings.mapZoneEnabled,



                hasZone = appSettings.mapZoneLat != 0.0 || appSettings.mapZoneLng != 0.0 || prefsRepo.getMapZones().isNotEmpty(),



                radiusKm = appSettings.mapZoneRadiusKm,



                onToggle = { on ->



                    prefsRepo.saveMapZoneEnabled(on)



                    appSettings = prefsRepo.getSettings()



                },



                onOpenMap = { context.startActivity(Intent(context, MapZoneActivity::class.java)) }



            )



            // Target Areas active state managed at top




            TargetAreasCard(

                isOn = isTargetAreasActive,

                areasText = appSettings.preferredAreas,

                onToggle = { on ->

                    isTargetAreasActive = on

                    prefsRepo.saveTargetAreasEnabled(on)

                    appSettings = prefsRepo.getSettings()

                },

                onOpenAreas = {

                    showTargetAreasDialog = true

                }

            )



            // ── 6. PLATFORM SELECTOR



            PlatformRow(appSettings.platformPreset) { preset ->



                prefsRepo.savePlatformPreset(preset)



                appSettings = prefsRepo.getSettings()



            }



            // ── 7. SPEED SELECTOR



            SpeedCard(appSettings.clickIntervalMs) { ms ->



                prefsRepo.saveClickInterval(ms)



                appSettings = prefsRepo.getSettings()



            }



            // ── 8. SOUND & VIBRATION TOGGLES



            FeedbackCard(



                ttsOn = appSettings.ttsFeedbackEnabled,



                onTts = { prefsRepo.saveTtsFeedbackEnabled(it); appSettings = prefsRepo.getSettings() },



                vibrationOn = appSettings.vibrationOnAccept,



                onVibration = { prefsRepo.saveVibrationOnAccept(it); appSettings = prefsRepo.getSettings() }



            )



            // ── 8B. SMART MATCHING RULES (Feature 3 & 4)



            Card(



                modifier = Modifier.fillMaxWidth().clickable { showSmartRulesDialog = true },



                colors = CardDefaults.cardColors(containerColor = DarkCard),



                shape = RoundedCornerShape(16.dp),



                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))



            ) {



                Row(



                    Modifier.fillMaxWidth().padding(14.dp),



                    horizontalArrangement = Arrangement.SpaceBetween,



                    verticalAlignment = Alignment.CenterVertically



                ) {



                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {



                        Text("🎯", fontSize = 18.sp)



                        Column {



                            Text("SMART OFFER CRITERIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)



                            val fareDesc = if (smartRules.minFareEnabled) "Min ₹${smartRules.minFareAmount.toInt()}" else "Fare: Any"



                            val distDesc = if (smartRules.maxPickupDistanceEnabled) "Max ${smartRules.maxPickupDistanceKm}km" else "Dist: Any"



                            Text("$fareDesc • $distDesc", color = BrandCyan, fontSize = 11.sp)



                        }



                    }



                    Text("Configure ➔", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)



                }



            }



            // ── 9. RULE & KEYWORD FILTERS (Captain Helper Style)



            FilterRulesCard(



                customKeywords = appSettings.customKeywords,



                negativeKeywords = appSettings.negativeKeywords,



                onSaveKeywords = { custom, negative ->



                    prefsRepo.saveCustomKeywords(custom)



                    prefsRepo.saveNegativeKeywords(negative)



                    appSettings = prefsRepo.getSettings()



                }



            )



            // ── 10. ACTIVITY AUDIT LOG (With Quick-Add & Filter)



            ActivityLogCard(



                context = context,



                onQuickAddKeyword = { kw ->



                    val current = appSettings.customKeywords



                    val updated = if (current.isBlank()) kw else "$current, $kw"



                    prefsRepo.saveCustomKeywords(updated)



                    appSettings = prefsRepo.getSettings()



                    Toast.makeText(context, "Added '$kw' to accept keywords!", Toast.LENGTH_SHORT).show()



                }



            )



            // ── 11. BACKUP & RESTORE



            Card(



                modifier = Modifier.fillMaxWidth(),



                shape = RoundedCornerShape(16.dp),



                colors = CardDefaults.cardColors(containerColor = DarkCard),



                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))



            ) {



                Row(



                    Modifier.fillMaxWidth().padding(14.dp),



                    horizontalArrangement = Arrangement.SpaceBetween,



                    verticalAlignment = Alignment.CenterVertically



                ) {



                    Column {



                        Text("💾 BACKUP & RESTORE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)



                        Text("Export your rules & zones to JSON", color = TextMuted, fontSize = 11.sp)



                    }



                    Button(



                        onClick = {



                            val json = BackupRestoreManager.exportBackupJson(prefsRepo)



                            val sendIntent = Intent().apply {



                                action = Intent.ACTION_SEND



                                putExtra(Intent.EXTRA_TEXT, json)



                                type = "application/json"



                            }



                            context.startActivity(Intent.createChooser(sendIntent, "Export Taptix Backup"))



                        },



                        colors = ButtonDefaults.buttonColors(containerColor = DarkControl),



                        shape = RoundedCornerShape(8.dp),



                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)



                    ) {



                        Text("Export", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)



                    }



                }



            }



            // ── 12. LAUNCH FLOATING CONTROLS

            Button(

                onClick = {

                    if (!Settings.canDrawOverlays(context)) {

                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))

                        Toast.makeText(context, "Please grant 'Display over other apps' permission", Toast.LENGTH_SHORT).show()

                        return@Button

                    }

                    if (!isAccessibilityOn) {

                        showAccessibilityDisclosure = true

                    }



                    // 1. Save Master Control enabled FIRST in preferences

                    prefsRepo.saveMasterControl(true)

                    appSettings = prefsRepo.getSettings()



                    // 2. Start OverlayService

                    val i = Intent(context, OverlayService::class.java)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                        context.startForegroundService(i)

                    } else {

                        context.startService(i)

                    }



                    // 3. If service is already active, restore from bubble / show toolbar

                    OverlayService.instance?.expandFromBubble()



                    Toast.makeText(context, "🚀 Floating controls launched!", Toast.LENGTH_SHORT).show()

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



                        Text("LAUNCH FLOATING CONTROLS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)



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

    missingPermissionsCount: Int = 0,

    onDisabledClick: () -> Unit = {},

    onToggle: (Boolean) -> Unit

) {

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(

        initialValue = 0.98f, targetValue = 1.02f,

        animationSpec = infiniteRepeatable(

            animation = tween(900, easing = FastOutSlowInEasing),

            repeatMode = RepeatMode.Reverse

        ),

        label = "pulseScale"

    )



    val cardAlpha = if (hasPermissions) 1.0f else 0.55f



    Card(

        modifier = Modifier

            .fillMaxWidth()

            .scale(if (isOn && hasPermissions) pulseScale else 1f)

            .clickable(enabled = !hasPermissions) { onDisabledClick() },

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(

            containerColor = if (isOn && hasPermissions) Color(0xFF0D2818) else DarkCard

        ),

        border = if (isOn && hasPermissions) androidx.compose.foundation.BorderStroke(2.dp, BrandGreen)

        else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))

    ) {

        Row(

            Modifier

                .fillMaxWidth()

                .padding(20.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(

                Modifier

                    .weight(1f)

                    .alpha(cardAlpha)

            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(

                        Modifier

                            .size(10.dp)

                            .clip(CircleShape)

                            .background(

                                if (!hasPermissions) Color(0xFFEF4444)

                                else if (isOn) BrandGreen

                                else Color(0xFF555566)

                            )

                    )

                    Spacer(Modifier.width(8.dp))

                    Text(

                        if (!hasPermissions) "MASTER CONTROL: LOCKED"

                        else if (isOn) "MASTER CONTROL: ACTIVE"

                        else "MASTER CONTROL: READY",

                        fontWeight = FontWeight.ExtraBold,

                        color = if (!hasPermissions) Color(0xFFEF4444) else if (isOn) BrandGreen else Color.White,

                        fontSize = 16.sp,

                        letterSpacing = 1.sp

                    )

                }

                Spacer(Modifier.height(4.dp))

                Text(

                    if (!hasPermissions) "⚠️ Grant $missingPermissionsCount permission(s) to unlock auto-accept"

                    else if (isOn) "⚡ Auto-accepting matching requests!"

                    else "Tap switch to activate autonomous background driver",

                    color = if (!hasPermissions) Color(0xFFEF4444).copy(alpha = 0.9f)

                    else if (isOn) BrandGreen

                    else Color.White.copy(alpha = 0.7f),

                    fontSize = 13.sp

                )

            }

            Switch(

                checked = isOn && hasPermissions,

                onCheckedChange = { if (hasPermissions) onToggle(it) else onDisabledClick() },

                enabled = hasPermissions,

                colors = SwitchDefaults.colors(

                    checkedThumbColor   = Color.White,

                    checkedTrackColor   = BrandGreen,

                    uncheckedThumbColor = Color(0xFF444455),

                    uncheckedTrackColor = Color(0xFF1E2230),

                    disabledCheckedThumbColor = Color(0xFF888888),

                    disabledCheckedTrackColor = Color(0xFF444444),

                    disabledUncheckedThumbColor = Color(0xFF666666),

                    disabledUncheckedTrackColor = Color(0xFF222230)

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



        colors = CardDefaults.cardColors(containerColor = if (isOn) Color(0xFF1A1030) else Color(0xFF0F1018)),



        border = if (isOn) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C4DFF))



        else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))



    ) {



        Row(



            Modifier.fillMaxWidth().padding(20.dp),



            horizontalArrangement = Arrangement.SpaceBetween,



            verticalAlignment = Alignment.CenterVertically



        ) {



            Column(Modifier.weight(1f)) {



                Text(



                    "⚡ ULTRA MODE", fontWeight = FontWeight.ExtraBold,



                    color = if (isOn) Color(0xFFB388FF) else Color.White,



                    fontSize = 17.sp, letterSpacing = 0.5.sp



                )



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





// ─── TARGET AREAS CARD ────────────────────────────────────────────────────────



@Composable

fun TargetAreasCard(

    isOn: Boolean,

    areasText: String = "",

    onToggle: (Boolean) -> Unit,

    onOpenAreas: () -> Unit

) {

    val cleanAreas = areasText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val displaySubtitle = if (isOn) {

        if (cleanAreas.isNotEmpty()) "Filtering ON: " + cleanAreas.take(3).joinToString(", ") + (if (cleanAreas.size > 3) "..." else "")

        else "Target Areas filtering is ON (Tap 🎯 to add areas)"

    } else {

        if (cleanAreas.isNotEmpty()) "Inactive (" + cleanAreas.size.toString() + " areas saved)"

        else "Target Areas filtering is OFF"

    }



    Card(

        modifier = Modifier.fillMaxWidth().clickable { onOpenAreas() },

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(containerColor = if (isOn) Color(0xFF0A1828) else DarkCard),

        border = if (isOn) androidx.compose.foundation.BorderStroke(1.5.dp, BrandCyan)

        else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))

    ) {

        Row(

            Modifier.fillMaxWidth().padding(20.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(

                        "🎯 TARGET AREAS",

                        fontWeight = FontWeight.ExtraBold,

                        color = if (isOn) BrandCyan else Color.White,

                        fontSize = 16.sp,

                        letterSpacing = 0.5.sp

                    )

                }

                Spacer(Modifier.height(4.dp))

                Text(

                    displaySubtitle,

                    color = if (isOn) BrandCyan.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.6f),

                    fontSize = 13.sp,

                    maxLines = 2

                )

            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {

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

                        .background(Color(0xFF1E293B))

                        .clickable { onOpenAreas() },

                    contentAlignment = Alignment.Center

                ) {

                    Text("🎯", fontSize = 18.sp)

                }

            }

        }

    }

}



@Composable

fun TargetAreasDialog(

    initialAreas: String,

    isEnabled: Boolean,

    onSave: (String, Boolean) -> Unit,

    onDismiss: () -> Unit

) {

    var text by remember { mutableStateOf(initialAreas) }

    var enabled by remember { mutableStateOf(isEnabled) }

    val quickChips = listOf("Airport", "Downtown", "Tech Park", "Central Station", "Metro", "Expressway")



    AlertDialog(

        onDismissRequest = onDismiss,

        containerColor = Color(0xFF1E2230),

        title = {

            Text("🎯 Target Areas Filtering", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)

        },

        text = {

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text(

                    "Only auto-accept ride requests with passenger destinations in your target areas. Matches keywords in drop-off locations.",

                    color = Color(0xFF94A3B8),

                    fontSize = 12.sp,

                    lineHeight = 16.sp

                )



                Row(

                    Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Text("Filter Enabled", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Switch(

                        checked = enabled,

                        onCheckedChange = { enabled = it },

                        colors = SwitchDefaults.colors(

                            checkedThumbColor = Color.White,

                            checkedTrackColor = BrandCyan

                        )

                    )

                }



                Text("Target Areas (comma-separated):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)



                OutlinedTextField(

                    value = text,

                    onValueChange = { text = it },

                    placeholder = { Text("e.g. Airport, Downtown, Tech Park", color = Color(0xFF64748B), fontSize = 13.sp) },

                    modifier = Modifier.fillMaxWidth().height(100.dp),

                    colors = OutlinedTextFieldDefaults.colors(

                        focusedBorderColor = BrandCyan,

                        unfocusedBorderColor = Color(0xFF334155),

                        focusedTextColor = Color.White,

                        unfocusedTextColor = Color.White

                    )

                )



                Text("Quick Add Chips:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)



                Row(

                    Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(6.dp)

                ) {

                    quickChips.take(3).forEach { chip ->

                        SuggestionChip(

                            onClick = {

                                val current = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                                if (!current.any { it.equals(chip, ignoreCase = true) }) {

                                    current.add(chip)

                                    text = current.joinToString(", ")

                                }

                            },

                            label = { Text("+" + chip, fontSize = 11.sp, color = BrandCyan) }

                        )

                    }

                }

                Row(

                    Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(6.dp)

                ) {

                    quickChips.drop(3).forEach { chip ->

                        SuggestionChip(

                            onClick = {

                                val current = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

                                if (!current.any { it.equals(chip, ignoreCase = true) }) {

                                    current.add(chip)

                                    text = current.joinToString(", ")

                                }

                            },

                            label = { Text("+" + chip, fontSize = 11.sp, color = BrandCyan) }

                        )

                    }

                }

            }

        },

        confirmButton = {

            Button(

                onClick = {

                    onSave(text, enabled)

                },

                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),

                shape = RoundedCornerShape(10.dp)

            ) {

                Text("SAVE AREAS", color = Color.Black, fontWeight = FontWeight.Black)

            }

        },

        dismissButton = {

            TextButton(onClick = onDismiss) {

                Text("CANCEL", color = Color(0xFF94A3B8))

            }

        }

    )

}



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



        colors = CardDefaults.cardColors(containerColor = if (isOn) Color(0xFF0A1828) else DarkCard),



        border = if (isOn) androidx.compose.foundation.BorderStroke(1.5.dp, BrandCyan)



        else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2230))



    ) {



        Row(



            Modifier.fillMaxWidth().padding(20.dp),



            horizontalArrangement = Arrangement.SpaceBetween,



            verticalAlignment = Alignment.CenterVertically



        ) {



            Column(Modifier.weight(1f)) {



                Row(verticalAlignment = Alignment.CenterVertically) {



                    Text(



                        "🗺️ MAP WORK ZONES", fontWeight = FontWeight.ExtraBold,



                        color = if (isOn) BrandCyan else Color.White,



                        fontSize = 16.sp, letterSpacing = 0.5.sp



                    )



                }



                Spacer(Modifier.height(4.dp))



                Text(



                    if (!hasZone) "No zones configured — tap map to set"



                    else if (isOn) "✅ Geofenced auto-accept active"



                    else "Filter orders to configured circular zones",



                    color = if (isOn) BrandCyan.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),



                    fontSize = 13.sp



                )



            }



            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {



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



                PlatformPreset.UNIVERSAL  to "🌐 All Duties",



                PlatformPreset.UBER       to "🚖 Uber",



                PlatformPreset.OLA        to "🟡 Ola",



                PlatformPreset.RAPIDO     to "🟠 Rapido",



                PlatformPreset.INDRIVER   to "🟣 inDrive",



                PlatformPreset.PORTER     to "🚚 Porter",



                PlatformPreset.NAMMAYATRI to "🟢 Namma Yatri"



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



// ─── SPEED SELECTOR ───────────────────────────────────────────────────────────



@Composable



fun SpeedCard(intervalMs: Long, onSelect: (Long) -> Unit) {



    Column(



        Modifier.fillMaxWidth()



            .clip(RoundedCornerShape(18.dp))



            .background(DarkCard)



            .padding(16.dp)



    ) {



        Text("⚡ CLICK SPEED", fontWeight = FontWeight.ExtraBold,



            color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)



        Spacer(Modifier.height(12.dp))



        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {



            listOf(



                100L to "🚀 Fast\n100ms",



                250L to "⚡ Normal\n250ms",



                500L to "🛡️ Safe\n500ms"



            ).forEach { (ms, label) ->



                val sel = intervalMs == ms



                Button(



                    onClick = { onSelect(ms) },



                    modifier = Modifier.weight(1f).height(52.dp),



                    shape = RoundedCornerShape(12.dp),



                    colors = ButtonDefaults.buttonColors(



                        containerColor = if (sel) BrandCyan else DarkControl



                    ),



                    contentPadding = PaddingValues(4.dp)



                ) {



                    Text(label,



                        color = if (sel) Color.Black else TextMuted,



                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal,



                        fontSize = 12.sp,



                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,



                        lineHeight = 16.sp)



                }



            }



        }



    }



}



// ─── FEEDBACK CARD ────────────────────────────────────────────────────────────



@Composable



fun FeedbackCard(ttsOn: Boolean, onTts: (Boolean) -> Unit, vibrationOn: Boolean, onVibration: (Boolean) -> Unit) {



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



                Text("🔊 Voice Announcement", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)



                Text("Speaks on accept — silent while waiting", color = TextMuted, fontSize = 11.sp)



            }



            Switch(checked = ttsOn, onCheckedChange = onTts,



                colors = SwitchDefaults.colors(checkedTrackColor = BrandGreen))



        }



        HorizontalDivider(color = DarkControl.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))



        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {



            Column {



                Text("📳 Vibration on Accept", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)



                Text("Tactile pulse on confirmed click", color = TextMuted, fontSize = 11.sp)



            }



            Switch(checked = vibrationOn, onCheckedChange = onVibration,



                colors = SwitchDefaults.colors(checkedTrackColor = BrandGreen))



        }



    }



}



// ─── FILTER RULES CARD ─────────────────────────────────────────────────────────



@Composable



fun FilterRulesCard(



    customKeywords: String,



    negativeKeywords: String,



    onSaveKeywords: (String, String) -> Unit



) {



    var customText by remember(customKeywords) { mutableStateOf(customKeywords) }



    var negativeText by remember(negativeKeywords) { mutableStateOf(negativeKeywords) }



    var isExpanded by remember { mutableStateOf(false) }



    Column(



        Modifier.fillMaxWidth()



            .clip(RoundedCornerShape(18.dp))



            .background(DarkCard)



            .padding(16.dp),



        verticalArrangement = Arrangement.spacedBy(8.dp)



    ) {



        Row(



            Modifier.fillMaxWidth(),



            horizontalArrangement = Arrangement.SpaceBetween,



            verticalAlignment = Alignment.CenterVertically



        ) {



            Column(Modifier.weight(1f)) {



                Text("⚙️ RULE & KEYWORD FILTERS", fontWeight = FontWeight.ExtraBold,



                    color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)



                Text("Auto-accept matching words & block unwanted trips",



                    color = TextMuted, fontSize = 11.sp)



            }



            TextButton(onClick = { isExpanded = !isExpanded }) {



                Text(if (isExpanded) "Hide" else "Edit", color = BrandCyan, fontWeight = FontWeight.Bold)



            }



        }



        if (isExpanded) {



            Spacer(Modifier.height(4.dp))



            Text("✅ Include / Accept Keywords (Comma-separated):",



                color = BrandGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)



            OutlinedTextField(



                value = customText,



                onValueChange = { customText = it },



                modifier = Modifier.fillMaxWidth(),



                colors = OutlinedTextFieldDefaults.colors(



                    focusedTextColor = Color.White,



                    unfocusedTextColor = Color.White,



                    focusedBorderColor = BrandGreen,



                    unfocusedBorderColor = DarkControl



                ),



                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)



            )



            Spacer(Modifier.height(6.dp))



            Text("⛔ Exclude / Reject Keywords (Skip orders with these words):",



                color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)



            OutlinedTextField(



                value = negativeText,



                onValueChange = { negativeText = it },



                modifier = Modifier.fillMaxWidth(),



                placeholder = { Text("e.g. CASH, LOW RATING, SHARED", color = TextMuted, fontSize = 12.sp) },



                colors = OutlinedTextFieldDefaults.colors(



                    focusedTextColor = Color.White,



                    unfocusedTextColor = Color.White,



                    focusedBorderColor = BrandRed,



                    unfocusedBorderColor = DarkControl



                ),



                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)



            )



            Spacer(Modifier.height(6.dp))



            Button(



                onClick = {



                    val cleanCustom = customText.split(",")



                        .map { it.trim().uppercase() }



                        .filter { it.isNotBlank() }



                        .distinct()



                        .joinToString(", ")



                    val cleanNegative = negativeText.split(",")



                        .map { it.trim().uppercase() }



                        .filter { it.isNotBlank() }



                        .distinct()



                        .joinToString(", ")



                    onSaveKeywords(cleanCustom, cleanNegative)



                    isExpanded = false



                },



                modifier = Modifier.fillMaxWidth().height(42.dp),



                shape = RoundedCornerShape(10.dp),



                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))



            ) {



                Text("Save Keywords", color = Color.White, fontWeight = FontWeight.Bold)



            }



        }



    }



}



// ─── ACTIVITY AUDIT LOG (With Quick-Add Keyword & Filtering) ──────────────────────────────



@Composable



fun ActivityLogCard(context: Context, onQuickAddKeyword: (String) -> Unit) {



    val logManager = remember { ActivityLogManager.getInstance(context) }



    var logs by remember { mutableStateOf(logManager.getLogs()) }



    var isExpanded by remember { mutableStateOf(false) }



    var showConfirmClear by remember { mutableStateOf(false) }



    var selectedFilter by remember { mutableStateOf<LogStatus?>(null) }



    if (showConfirmClear) {



        AlertDialog(



            onDismissRequest = { showConfirmClear = false },



            title = { Text("Clear Activity History?", color = Color.White, fontWeight = FontWeight.Bold) },



            text = { Text("Are you sure you want to permanently clear all activity logs?", color = Color(0xFF94A3B8)) },



            confirmButton = {



                Button(



                    onClick = {



                        logManager.clearLogs()



                        logs = emptyList()



                        showConfirmClear = false



                    },



                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)



                ) {



                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)



                }



            },



            dismissButton = {



                TextButton(onClick = { showConfirmClear = false }) {



                    Text("Cancel", color = Color.White)



                }



            },



            containerColor = Color(0xFF1E293B)



        )



    }



    Column(



        Modifier.fillMaxWidth()



            .clip(RoundedCornerShape(18.dp))



            .background(DarkCard)



            .padding(16.dp),



        verticalArrangement = Arrangement.spacedBy(8.dp)



    ) {



        Row(



            Modifier.fillMaxWidth(),



            horizontalArrangement = Arrangement.SpaceBetween,



            verticalAlignment = Alignment.CenterVertically



        ) {



            Column(Modifier.weight(1f)) {



                Text("📋 ACTIVITY AUDIT LOG", fontWeight = FontWeight.ExtraBold,



                    color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)



                Text(



                    if (logs.isEmpty()) "No trips detected yet" else "${logs.size} recent actions recorded (7-day retention)",



                    color = TextMuted, fontSize = 11.sp



                )



            }



            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {



                if (logs.isNotEmpty()) {



                    TextButton(onClick = { showConfirmClear = true }) {



                        Text("Clear", color = TextMuted, fontSize = 11.sp)



                    }



                }



                TextButton(onClick = {



                    logs = logManager.getLogs()



                    isExpanded = !isExpanded



                }) {



                    Text(if (isExpanded) "Collapse" else "View", color = BrandCyan, fontWeight = FontWeight.Bold)



                }



            }



        }



        if (isExpanded) {



            // Filter Pills



            Row(



                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),



                horizontalArrangement = Arrangement.spacedBy(6.dp)



            ) {



                listOf(



                    null to "All",



                    LogStatus.ACCEPTED to "Accepted",



                    LogStatus.OUTSIDE_ZONE to "Zone Skipped",



                    LogStatus.REJECTED_KEYWORD to "Keyword Blocked",



                    LogStatus.FAILED to "Failed"



                ).forEach { (st, label) ->



                    val isSel = selectedFilter == st



                    Surface(



                        shape = RoundedCornerShape(8.dp),



                        color = if (isSel) BrandCyan.copy(alpha = 0.2f) else DarkControl,



                        modifier = Modifier



                            .clickable { selectedFilter = st }



                            .border(if (isSel) 1.dp else 0.dp, if (isSel) BrandCyan else Color.Transparent, RoundedCornerShape(8.dp))



                    ) {



                        Text(



                            label,



                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),



                            color = if (isSel) BrandCyan else TextMuted,



                            fontSize = 11.sp,



                            fontWeight = FontWeight.Bold



                        )



                    }



                }



            }



            val displayedLogs = if (selectedFilter == null) logs else logs.filter { it.status == selectedFilter }



            if (displayedLogs.isEmpty()) {



                Text("No activity items matching selected filter.",



                    color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))



            } else {



                val timeFormat = remember { java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()) }



                Column(



                    Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),



                    verticalArrangement = Arrangement.spacedBy(6.dp)



                ) {



                    displayedLogs.take(25).forEach { item ->



                        val (statusColor, statusIcon) = when (item.status) {



                            LogStatus.ACCEPTED -> BrandGreen to "✅"



                            LogStatus.ATTEMPTED -> BrandCyan to "⏳"



                            LogStatus.FAILED -> BrandRed to "❌"



                            LogStatus.ERROR -> BrandRed to "⚠️"



                            LogStatus.REJECTED_KEYWORD -> BrandRed to "⛔"



                            LogStatus.OUTSIDE_ZONE -> BrandAmber to "🗺️"



                            LogStatus.SYSTEM_EVENT -> BrandCyan to "ℹ️"



                        }



                        Surface(



                            shape = RoundedCornerShape(8.dp),



                            color = DarkControl,



                            modifier = Modifier.fillMaxWidth()



                        ) {



                            Row(



                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),



                                horizontalArrangement = Arrangement.SpaceBetween,



                                verticalAlignment = Alignment.CenterVertically



                            ) {



                                Column(Modifier.weight(1f)) {



                                    Row(verticalAlignment = Alignment.CenterVertically) {



                                        Text(statusIcon, fontSize = 12.sp)



                                        Spacer(Modifier.width(6.dp))



                                        Text(item.appName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)



                                        if (item.fare != null) {



                                            Spacer(Modifier.width(6.dp))



                                            Text("₹${item.fare.toInt()}", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)



                                        }



                                        if (item.distanceKm != null) {



                                            Spacer(Modifier.width(4.dp))



                                            Text("• ${String.format("%.1f", item.distanceKm)}km", color = BrandCyan, fontSize = 11.sp)



                                        }



                                    }



                                    Text(item.actionText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)



                                    if (item.details.isNotBlank()) {



                                        Text(item.details, color = TextMuted, fontSize = 10.sp)



                                    }



                                }



                                Column(horizontalAlignment = Alignment.End) {



                                    Text(



                                        timeFormat.format(java.util.Date(item.timestamp)),



                                        color = TextMuted, fontSize = 10.sp



                                    )



                                    if (item.status == LogStatus.ACCEPTED || item.status == LogStatus.ATTEMPTED) {



                                        Text(



                                            "+ Quick Add",



                                            color = BrandCyan,



                                            fontSize = 10.sp,



                                            fontWeight = FontWeight.Bold,



                                            modifier = Modifier.clickable {



                                                val kw = item.actionText.substringAfter(":").trim().ifBlank { "ACCEPT" }



                                                onQuickAddKeyword(kw)



                                            }



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



            "1️⃣" to "Enable Accessibility Service & Display Over Other Apps",



            "2️⃣" to "Select your platform (Uber/Ola/Rapido/Porter...)",



            "3️⃣" to "Optional: Draw Map Zones or Set Active Shift Schedule",



            "4️⃣" to "Test using the built-in Auto-Accept Simulator",



            "5️⃣" to "Turn ON MASTER CONTROL → starts auto-accept",



            "6️⃣" to "Open your driver app → bookings accepted automatically! 🚀"



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

