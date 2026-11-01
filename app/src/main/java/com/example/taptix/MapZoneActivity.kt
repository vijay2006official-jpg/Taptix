package com.example.taptix

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.engine.GeofenceEngine
import com.example.taptix.model.MapZone
import com.example.taptix.ui.theme.TaptixTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class MapZoneActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository
    private var leafletCss: String = ""
    private var leafletJs: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)

        try {
            leafletCss = assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
            leafletJs = assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            leafletCss = ""
            leafletJs = ""
        }

        setContent {
            TaptixTheme {
                MapZoneScreen(
                    prefsRepo = prefsRepo,
                    leafletCss = leafletCss,
                    leafletJs = leafletJs,
                    onClose = { finish() }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapZoneScreen(
    prefsRepo: PreferencesRepository,
    leafletCss: String,
    leafletJs: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var savedZones by remember { mutableStateOf(prefsRepo.getMapZones()) }
    var editingZoneId by remember { mutableStateOf<String?>(savedZones.firstOrNull()?.id) }

    val currentEditingZone = savedZones.firstOrNull { it.id == editingZoneId }
    var zoneName by remember(editingZoneId) {
        mutableStateOf(currentEditingZone?.name ?: "Target Work Area")
    }
    var pickedLat by remember(editingZoneId) {
        mutableStateOf(currentEditingZone?.lat ?: 13.0827)
    }
    var pickedLng by remember(editingZoneId) {
        mutableStateOf(currentEditingZone?.lng ?: 80.2707)
    }
    var radiusKm by remember(editingZoneId) {
        mutableStateOf(currentEditingZone?.radiusKm?.toFloat() ?: 3.0f)
    }
    var pickedAddress by remember { mutableStateOf("Locating selected area...") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSpeedKmh by remember { mutableStateOf(0) }
    var userGpsLat by remember { mutableStateOf<Double?>(null) }
    var userGpsLng by remember { mutableStateOf<Double?>(null) }
    var isMapLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Boundary Editor, 1 = Saved Areas List
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionRationale by remember { mutableStateOf(!hasLocationPermission) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun syncZonesToWebMap(zones: List<MapZone>) {
        val jsonStr = zonesToJsonArray(zones)
        webViewRef?.evaluateJavascript("if (typeof renderSavedZones === 'function') renderSavedZones($jsonStr);", null)
    }

    val updateMapLocation = { lat: Double, lng: Double ->
        pickedLat = lat
        pickedLng = lng
        webViewRef?.evaluateJavascript(
            "if(typeof updateLocation === 'function'){ updateLocation($lat, $lng); } else if(typeof map !== 'undefined'){ map.setView([$lat, $lng], 15); placeMarker($lat, $lng); }",
            null
        )
    }

    val fetchLocationAndCenter = {
        centerOnCurrentLocation(context) { loc ->
            if (loc.hasSpeed()) {
                currentSpeedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
            }
            userGpsLat = loc.latitude
            userGpsLng = loc.longitude
            webViewRef?.evaluateJavascript(
                "if (typeof updateUserGpsLocation === 'function') updateUserGpsLocation(${loc.latitude}, ${loc.longitude});",
                null
            )
            if (currentEditingZone == null) {
                pickedLat = loc.latitude
                pickedLng = loc.longitude
                updateMapLocation(loc.latitude, loc.longitude)
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            showPermissionRationale = false
            fetchLocationAndCenter()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            fetchLocationAndCenter()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE5E7EB))) {
        // ── 1. Fullscreen Map View
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("MapZoneWebView", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapLoading = false
                            val syncJs = buildString {
                                append("if (typeof forceResize === 'function') forceResize();")
                                append("if (typeof updateRadius === 'function') updateRadius(${(radiusKm * 1000).toInt()});")
                                append("if (typeof updateLocation === 'function') updateLocation($pickedLat, $pickedLng);")
                                val gLat = userGpsLat
                                val gLng = userGpsLng
                                if (gLat != null && gLng != null) {
                                    append("if (typeof updateUserGpsLocation === 'function') updateUserGpsLocation($gLat, $gLng);")
                                }
                                append("if (typeof renderSavedZones === 'function') renderSavedZones(${zonesToJsonArray(savedZones)});")
                            }
                            view?.evaluateJavascript(syncJs, null)
                        }
                    }

                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onLocationPicked(lat: Double, lng: Double, address: String?) {
                            if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                                Handler(Looper.getMainLooper()).post {
                                    pickedLat = lat
                                    pickedLng = lng
                                    pickedAddress = if (!address.isNullOrBlank()) {
                                        address
                                    } else {
                                        String.format(Locale.US, "%.4f, %.4f", lat, lng)
                                    }
                                    if (editingZoneId == null || zoneName == "Target Work Area") {
                                        zoneName = pickedAddress.take(30)
                                    }
                                }
                            }
                        }
                    }, "Android")

                    loadDataWithBaseURL(
                        "https://localhost/",
                        buildMapHtml(
                            centerLat = pickedLat,
                            centerLng = pickedLng,
                            markerLat = pickedLat,
                            markerLng = pickedLng,
                            radiusKm = radiusKm.toDouble(),
                            savedZonesJson = zonesToJsonArray(savedZones),
                            cssContent = leafletCss,
                            jsContent = leafletJs
                        ),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            update = { wv -> webViewRef = wv },
            modifier = Modifier.fillMaxSize()
        )

        // ── 2. Map Loading Indicator
        if (isMapLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x990F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
                    Text("Loading Map & Satellite Tiles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── 3. Top Navigation Bar (Back Button + Search Bar)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Circular Back Button
            Box(
                Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = Color(0xFF0F172A), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // Search Bar
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .shadow(4.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text("Search city or landmark...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color(0xFF0F172A), fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    onClick = {
                        val q = searchQuery.trim()
                        if (q.isNotEmpty()) {
                            val clean = q.replace("'", "\\'")
                            webViewRef?.evaluateJavascript("searchAddress('$clean');", null)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🔍", fontSize = 16.sp)
                }
            }
        }

        // ── 4. Speedometer Badge (Top-Left)
        Box(
            Modifier
                .padding(start = 18.dp, top = 76.dp)
                .size(56.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFFBBF24)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$currentSpeedKmh",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.Black,
                    lineHeight = 18.sp
                )
                Text(
                    "km/h",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color.Black,
                    lineHeight = 9.sp
                )
            }
        }

        // ── 5. Floating Zoom & GPS Navigation Controls (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In (+) Button
            Box(
                Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { webViewRef?.evaluateJavascript("zoomIn();", null) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }

            // Zoom Out (-) Button
            Box(
                Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { webViewRef?.evaluateJavascript("zoomOut();", null) },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }

            // Recenter GPS Button
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .clickable {
                        if (hasLocationPermission) {
                            fetchLocationAndCenter()
                            val gLat = userGpsLat
                            val gLng = userGpsLng
                            if (gLat != null && gLng != null) {
                                updateMapLocation(gLat, gLng)
                            }
                        } else {
                            locationLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("🎯", fontSize = 20.sp)
            }
        }

        // ── 6. Bottom Sheet / Area Management Dashboard
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Permission Rationale Banner (if permission not granted)
            if (showPermissionRationale && !hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📍", fontSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text("Location Access Recommended", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                "Enables geofencing validation and centering the map. Stored strictly on device.",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                        Button(
                            onClick = {
                                locationLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Allow", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // Main Management Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Tab Bar: [Editor / Set Boundary] vs [Saved Areas (Count)]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeTab == 0) Color.White else Color.Transparent)
                                .clickable { activeTab = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📍 Work Boundary",
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 0) Color(0xFF0F172A) else Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeTab == 1) Color.White else Color.Transparent)
                                .clickable { activeTab = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📋 Saved Areas (${savedZones.count { it.isEnabled }}/${savedZones.size})",
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 1) Color(0xFF0F172A) else Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (activeTab == 0) {
                        // ── TAB 0: BOUNDARY EDITOR ──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (editingZoneId != null) "EDIT TARGET AREA" else "NEW TARGET AREA",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A),
                                letterSpacing = 0.5.sp
                            )
                            if (editingZoneId != null) {
                                Text(
                                    "+ New Area",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        editingZoneId = null
                                        zoneName = "New Target Area"
                                        radiusKm = 3.0f
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Area Name Input
                        OutlinedTextField(
                            value = zoneName,
                            onValueChange = { zoneName = it },
                            label = { Text("Area / Zone Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            )
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = pickedAddress,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(8.dp))

                        // Radius Slider Row
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Radius:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )

                            Slider(
                                value = radiusKm,
                                onValueChange = { v ->
                                    radiusKm = v
                                    webViewRef?.evaluateJavascript("updateRadius(${(v * 1000).toInt()});", null)
                                },
                                valueRange = 0.5f..25f,
                                steps = 48,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF2563EB),
                                    activeTrackColor = Color(0xFF2563EB),
                                    inactiveTrackColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                String.format(Locale.US, "%.1f km", radiusKm),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFF2563EB)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Save Area Button
                        Button(
                            onClick = {
                                val targetId = editingZoneId ?: UUID.randomUUID().toString()
                                val newZone = MapZone(
                                    id = targetId,
                                    name = if (zoneName.isNotBlank()) zoneName.take(30) else "Target Area",
                                    lat = pickedLat,
                                    lng = pickedLng,
                                    radiusKm = radiusKm.toDouble(),
                                    isEnabled = true
                                )
                                prefsRepo.addOrUpdateMapZone(newZone)
                                prefsRepo.saveMapZone(pickedLat, pickedLng, radiusKm.toDouble())
                                prefsRepo.saveMapZoneEnabled(true)
                                savedZones = prefsRepo.getMapZones()
                                syncZonesToWebMap(savedZones)
                                Toast.makeText(context, "✅ Saved '${newZone.name}' (${String.format(Locale.US, "%.1f", radiusKm)} km)", Toast.LENGTH_SHORT).show()
                                onClose()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text(
                                "SAVE WORK BOUNDARY",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        // ── TAB 1: SAVED TARGET AREAS LIST ──
                        if (savedZones.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No saved target areas. Tap 'Work Boundary' to add one.", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(savedZones, key = { it.id }) { zone ->
                                    val isInside = if (userGpsLat != null && userGpsLng != null) {
                                        GeofenceEngine.isLocationInsideZone(userGpsLat!!, userGpsLng!!, zone)
                                    } else false

                                    val distanceToZone = if (userGpsLat != null && userGpsLng != null) {
                                        GeofenceEngine.haversineDistanceKm(userGpsLat!!, userGpsLng!!, zone.lat, zone.lng)
                                    } else null

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (zone.isEnabled) Color(0xFFF8FAFC) else Color(0xFFF1F5F9)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (zone.id == editingZoneId) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        zone.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (zone.isEnabled) Color(0xFF0F172A) else Color(0xFF64748B)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isInside) Color(0xFFDCFCE7) else Color(0xFFE2E8F0))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            if (isInside) "INSIDE" else "OUTSIDE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isInside) Color(0xFF15803D) else Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                                val distText = if (distanceToZone != null) "${String.format(Locale.US, "%.1f", distanceToZone)} km away • " else ""
                                                Text(
                                                    "$distText${String.format(Locale.US, "%.1f", zone.radiusKm)} km circle",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Edit Button
                                                Text(
                                                    "Edit",
                                                    color = Color(0xFF2563EB),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        editingZoneId = zone.id
                                                        updateMapLocation(zone.lat, zone.lng)
                                                        activeTab = 0
                                                    }
                                                )

                                                // Delete Button
                                                Text(
                                                    "Delete",
                                                    color = Color(0xFFDC2626),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        prefsRepo.deleteMapZone(zone.id)
                                                        savedZones = prefsRepo.getMapZones()
                                                        if (editingZoneId == zone.id) {
                                                            editingZoneId = savedZones.firstOrNull()?.id
                                                        }
                                                        syncZonesToWebMap(savedZones)
                                                    }
                                                )

                                                // Enable/Disable Toggle
                                                Switch(
                                                    checked = zone.isEnabled,
                                                    onCheckedChange = { en ->
                                                        prefsRepo.toggleMapZone(zone.id, en)
                                                        savedZones = prefsRepo.getMapZones()
                                                        syncZonesToWebMap(savedZones)
                                                    },
                                                    modifier = Modifier.height(28.dp)
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
    }
}

@SuppressLint("MissingPermission")
private fun centerOnCurrentLocation(context: Context, onLocationFound: (Location) -> Unit) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val providers = lm.getProviders(true)
        var bestLoc: Location? = null
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (bestLoc == null || loc.accuracy < bestLoc.accuracy) {
                bestLoc = loc
            }
        }
        if (bestLoc != null) {
            onLocationFound(bestLoc)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                onLocationFound(loc)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        for (p in providers) {
            try {
                lm.requestLocationUpdates(p, 5000L, 10f, listener, Looper.getMainLooper())
            } catch (_: Exception) {}
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }, 6000L)
    } catch (_: SecurityException) {}
}

private fun zonesToJsonArray(zones: List<MapZone>): String {
    val arr = JSONArray()
    for (z in zones) {
        val o = JSONObject()
        o.put("id", z.id)
        o.put("name", z.name)
        o.put("lat", z.lat)
        o.put("lng", z.lng)
        o.put("radiusM", (z.radiusKm * 1000).toInt())
        o.put("isEnabled", z.isEnabled)
        arr.put(o)
    }
    return arr.toString()
}

private fun buildMapHtml(
    centerLat: Double,
    centerLng: Double,
    markerLat: Double,
    markerLng: Double,
    radiusKm: Double,
    savedZonesJson: String,
    cssContent: String,
    jsContent: String
): String {
    val radiusM = (radiusKm * 1000).toInt()

    val cssBlock = if (cssContent.isNotBlank()) {
        "<style>$cssContent</style>"
    } else {
        "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\"/>"
    }

    val jsBlock = if (jsContent.isNotBlank()) {
        "<script>$jsContent</script>"
    } else {
        "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>"
    }

    return """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
$cssBlock
$jsBlock
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  html, body {
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 0;
    overflow: hidden;
    background: #E5E7EB;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  #map {
    position: absolute;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    min-height: 100%;
    z-index: 1;
  }
  .leaflet-popup-content-wrapper {
    background: #FFFFFF;
    color: #0F172A;
    font-size: 13px;
    font-weight: 600;
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.25);
    padding: 4px;
  }
  .taptix-marker {
    background: transparent;
    border: none;
  }
  @keyframes gps-pulse {
    0% { transform: scale(0.9); opacity: 0.8; }
    50% { transform: scale(1.6); opacity: 0.2; }
    100% { transform: scale(0.9); opacity: 0.8; }
  }
  .gps-pulse-ring {
    animation: gps-pulse 2.2s infinite ease-in-out;
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', {
  zoomControl: false,
  attributionControl: false,
  maxZoom: 20
}).setView([$centerLat, $centerLng], 15);

// Primary Layer: High-Resolution Google Maps Raster
var googleLayer = L.tileLayer('https://mt{s}.google.com/vt/lyrs=m&hl=en&gl=in&x={x}&y={y}&z={z}', {
  subdomains: ['0', '1', '2', '3'],
  maxZoom: 20,
  crossOrigin: false
}).addTo(map);

// Fallback to CartoDB Voyager if Google tiles fail
googleLayer.on('tileerror', function() {
  if (!map._hasCartoFallback) {
    map._hasCartoFallback = true;
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      subdomains: 'abcd',
      maxZoom: 19,
      crossOrigin: false
    }).addTo(map);
  }
});

var pinIcon = L.divIcon({
  className: 'taptix-marker',
  html: '<div style="position:relative; width:36px; height:36px;">' +
        '<div style="position:absolute; width:34px; height:34px; background:#2563EB; border-radius:50% 50% 50% 0; transform:rotate(-45deg); border:3px solid #FFFFFF; box-shadow:0 4px 14px rgba(0,0,0,0.45); top:0; left:0;"></div>' +
        '<div style="position:absolute; width:12px; height:12px; background:#FFFFFF; border-radius:50%; top:8px; left:11px;"></div>' +
        '</div>',
  iconSize: [36, 36],
  iconAnchor: [18, 34],
  popupAnchor: [0, -34]
});

var marker = null, circle = null, userGpsMarker = null;
var savedZoneLayers = [];
var radiusM = $radiusM;

function zoomIn() { map.zoomIn(); }
function zoomOut() { map.zoomOut(); }

function renderSavedZones(zones) {
  savedZoneLayers.forEach(function(l) { map.removeLayer(l); });
  savedZoneLayers = [];
  if (!zones || !zones.length) return;
  zones.forEach(function(z) {
    var c = L.circle([z.lat, z.lng], {
      radius: z.radiusM,
      color: z.isEnabled ? '#2563EB' : '#94A3B8',
      fillColor: z.isEnabled ? '#3B82F6' : '#64748B',
      fillOpacity: z.isEnabled ? 0.15 : 0.08,
      weight: z.isEnabled ? 2 : 1,
      dashArray: z.isEnabled ? null : '5, 5'
    }).addTo(map);
    c.bindPopup('<div style="font-size:12px; font-weight:700;">' + z.name + '<br><span style="font-size:10px; color:#64748B;">' + (z.radiusM / 1000).toFixed(1) + ' km circle</span></div>');
    savedZoneLayers.push(c);
  });
}

function fetchAddressAndPopup(lat, lng) {
  var url = 'https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng + '&zoom=18&addressdetails=1';
  fetch(url, { headers: { 'Accept': 'application/json' } })
    .then(function(res) { return res.json(); })
    .then(function(data) {
      var addr = '';
      if (data && data.address) {
        var a = data.address;
        var parts = [a.suburb || a.neighbourhood || a.road || a.city_district || a.quarter, a.city || a.town || a.county];
        addr = parts.filter(Boolean).join(', ');
      }
      if (!addr && data && data.display_name) {
        addr = data.display_name.split(',').slice(0, 3).join(',');
      }
      if (!addr) {
        addr = lat.toFixed(4) + ', ' + lng.toFixed(4);
      }
      if (marker) {
        marker.bindPopup('<div style="font-size:12px; font-weight:700; line-height:16px; color:#0F172A;">📍 ' + addr + '</div>', {
          autoClose: false,
          closeOnClick: false
        }).openPopup();
      }
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, addr);
      }
    })
    .catch(function() {
      var fallback = lat.toFixed(4) + ', ' + lng.toFixed(4);
      if (marker) {
        marker.bindPopup('<div style="font-size:12px; font-weight:700; line-height:16px; color:#0F172A;">📍 ' + fallback + '</div>', {
          autoClose: false,
          closeOnClick: false
        }).openPopup();
      }
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, fallback);
      }
    });
}

function placeMarker(lat, lng) {
  if (marker) map.removeLayer(marker);
  if (circle) map.removeLayer(circle);

  if (typeof Android !== 'undefined' && Android.onLocationPicked) {
    Android.onLocationPicked(lat, lng, null);
  }

  marker = L.marker([lat, lng], {
    icon: pinIcon,
    interactive: true,
    bubblingMouseEvents: true
  }).addTo(map);

  circle = L.circle([lat, lng], {
    radius: radiusM,
    color: '#2563EB',
    fillColor: '#2563EB',
    fillOpacity: 0.18,
    weight: 2,
    interactive: true,
    bubblingMouseEvents: true
  }).addTo(map);

  fetchAddressAndPopup(lat, lng);
}

function updateUserGpsLocation(lat, lng) {
  if (userGpsMarker) {
    userGpsMarker.setLatLng([lat, lng]);
  } else {
    var userIcon = L.divIcon({
      className: 'taptix-marker',
      html: '<div style="position:relative; width:28px; height:28px;">' +
            '<div class="gps-pulse-ring" style="position:absolute; width:28px; height:28px; border-radius:50%; background:rgba(37,99,235,0.35);"></div>' +
            '<div style="position:absolute; width:14px; height:14px; border-radius:50%; background:#2563EB; border:3px solid #FFFFFF; top:7px; left:7px; box-shadow:0 2px 8px rgba(0,0,0,0.35);"></div>' +
            '</div>',
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    });
    userGpsMarker = L.marker([lat, lng], {icon: userIcon, zIndexOffset: 1000}).addTo(map);
  }
}

function updateLocation(lat, lng) {
  if (typeof map !== 'undefined' && map) {
    map.setView([lat, lng], 15);
    placeMarker(lat, lng);
  }
}

function updateRadius(r) {
  radiusM = r;
  if (circle && marker) {
    var ll = marker.getLatLng();
    map.removeLayer(circle);
    circle = L.circle([ll.lat, ll.lng], {
      radius: radiusM,
      color: '#2563EB',
      fillColor: '#2563EB',
      fillOpacity: 0.18,
      weight: 2,
      interactive: true,
      bubblingMouseEvents: true
    }).addTo(map);
  }
}

function searchAddress(query) {
  var url = 'https://nominatim.openstreetmap.org/search?format=json&q=' + encodeURIComponent(query);
  fetch(url, { headers: { 'Accept': 'application/json' } })
    .then(function(res) { return res.json(); })
    .then(function(data) {
      if (data && data.length > 0) {
        var first = data[0];
        var lat = parseFloat(first.lat);
        var lon = parseFloat(first.lon);
        map.setView([lat, lon], 15);
        placeMarker(lat, lon);
      }
    });
}

function forceResize() {
  if (typeof map !== 'undefined' && map) {
    map.invalidateSize(true);
  }
}

map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });

placeMarker($markerLat, $markerLng);
updateUserGpsLocation($centerLat, $centerLng);
renderSavedZones($savedZonesJson);

if (typeof ResizeObserver !== 'undefined') {
  new ResizeObserver(forceResize).observe(document.getElementById('map'));
}
window.addEventListener('load', forceResize);
window.addEventListener('resize', forceResize);
setTimeout(forceResize, 100);
setTimeout(forceResize, 350);
setTimeout(forceResize, 800);
</script>
</body>
</html>"""
}
