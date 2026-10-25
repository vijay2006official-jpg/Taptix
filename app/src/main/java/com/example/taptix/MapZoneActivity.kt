package com.example.taptix

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.model.AreaGroup
import com.example.taptix.model.MapZone
import com.example.taptix.ui.theme.TaptixTheme
import java.util.UUID

class MapZoneActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository
    private var leafletCss: String = ""
    private var leafletJs: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)

        // Read local offline Leaflet bundle from assets
        try {
            leafletCss = assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
            leafletJs = assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapZoneScreen(
    prefsRepo: PreferencesRepository,
    leafletCss: String,
    leafletJs: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var zones by remember { mutableStateOf(prefsRepo.getMapZones()) }
    var areaGroups by remember { mutableStateOf(prefsRepo.getAreaGroups()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Map Editor, 1: Saved Zones, 2: Area Lists

    // Selected zone being edited
    var editingZoneId by remember { mutableStateOf<String?>(null) }
    var zoneName by remember { mutableStateOf("Work Zone") }
    var pickedLat by remember { mutableStateOf(13.0827) }
    var pickedLng by remember { mutableStateOf(80.2707) }
    var radiusKm by remember { mutableStateOf(2.0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("📍 Tap map to set zone center") }
    // Permission launcher for Location
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            statusText = "📍 Fetching GPS location..."
            centerOnCurrentLocation(context) { loc ->
                pickedLat = loc.latitude
                pickedLng = loc.longitude
                val latStr = String.format("%.4f", loc.latitude)
                val lngStr = String.format("%.4f", loc.longitude)
                statusText = "📍 Current GPS: $latStr, $lngStr"
                webViewRef?.evaluateJavascript(
                    "map.setView([${loc.latitude}, ${loc.longitude}], 14); placeMarker(${loc.latitude}, ${loc.longitude});",
                    null
                )
            }
        } else {
            Toast.makeText(context, "Location permission is required for GPS centering", Toast.LENGTH_SHORT).show()
        }
    }

    // Init from existing first zone or current GPS
    LaunchedEffect(Unit) {
        val first = zones.firstOrNull()
        if (first != null) {
            editingZoneId = first.id
            zoneName = first.name
            pickedLat = first.lat
            pickedLng = first.lng
            radiusKm = first.radiusKm.toFloat()
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                statusText = "📍 Fetching GPS location..."
                centerOnCurrentLocation(context) { loc ->
                    pickedLat = loc.latitude
                    pickedLng = loc.longitude
                    val latStr = String.format("%.4f", loc.latitude)
                    val lngStr = String.format("%.4f", loc.longitude)
                    statusText = "📍 Current GPS: $latStr, $lngStr"
                    webViewRef?.evaluateJavascript(
                        "map.setView([${loc.latitude}, ${loc.longitude}], 14); placeMarker(${loc.latitude}, ${loc.longitude});",
                        null
                    )
                }
            } else {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(Color(0xFF0F172A))) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "🗺️ WORK ZONES & AREAS",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            "Multi-zone geofencing for automatic accept",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onClose) {
                        Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Map Editor", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Zones (${zones.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Area Lists (${areaGroups.size})", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        containerColor = Color(0xFF0B0F19)
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: Map Editor
                    Column(Modifier.fillMaxSize()) {
                        // Status & GPS Centering Bar
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                statusText,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (fine || coarse) {
                                        centerOnCurrentLocation(context) { loc ->
                                            pickedLat = loc.latitude
                                            pickedLng = loc.longitude
                                            statusText = "📍 Centered at GPS location"
                                            webViewRef?.evaluateJavascript(
                                                "map.setView([${loc.latitude}, ${loc.longitude}], 14); placeMarker(${loc.latitude}, ${loc.longitude});",
                                                null
                                            )
                                        }
                                    } else {
                                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📍 My GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Map View
                        Box(Modifier.weight(1f)) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        with(settings) {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            // Security hardening: restrict dangerous file access
                                            allowFileAccess = false
                                            @Suppress("DEPRECATION")
                                            allowFileAccessFromFileURLs = false
                                            @Suppress("DEPRECATION")
                                            allowUniversalAccessFromFileURLs = false
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                        }
                                        webChromeClient = WebChromeClient()
                                        webViewClient = WebViewClient()

                                        addJavascriptInterface(object : Any() {
                                            @JavascriptInterface
                                            fun onLocationPicked(lat: Double, lng: Double) {
                                                // Validate JS input
                                                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                                                    pickedLat = lat
                                                    pickedLng = lng
                                                    statusText = "Selected: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
                                                }
                                            }
                                        }, "Android")

                                        loadDataWithBaseURL(
                                            "https://tile.openstreetmap.org/",
                                            buildMapHtml(
                                                centerLat = pickedLat,
                                                centerLng = pickedLng,
                                                markerLat = pickedLat,
                                                markerLng = pickedLng,
                                                radiusKm = radiusKm.toDouble(),
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
                        }

                        // Controls Drawer (Zone Name, Radius Slider, Save)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(14.dp)
                        ) {
                            OutlinedTextField(
                                value = zoneName,
                                onValueChange = { zoneName = it },
                                label = { Text("Zone Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Radius:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${String.format("%.1f", radiusKm)} km", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }

                            Slider(
                                value = radiusKm,
                                onValueChange = { v ->
                                    radiusKm = v
                                    webViewRef?.evaluateJavascript("updateRadius(${(v * 1000).toInt()});", null)
                                },
                                valueRange = 0.5f..20f,
                                steps = 38,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF38BDF8)
                                )
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        editingZoneId = null
                                        zoneName = "Zone ${zones.size + 1}"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("New Zone", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        val currentId = editingZoneId ?: UUID.randomUUID().toString()
                                        val newZone = MapZone(
                                            id = currentId,
                                            name = zoneName.ifBlank { "Zone ${zones.size + 1}" },
                                            lat = pickedLat,
                                            lng = pickedLng,
                                            radiusKm = radiusKm.toDouble(),
                                            isEnabled = true
                                        )
                                        prefsRepo.addOrUpdateMapZone(newZone)
                                        prefsRepo.saveMapZoneEnabled(true)
                                        zones = prefsRepo.getMapZones()
                                        editingZoneId = newZone.id
                                        Toast.makeText(context, "✅ Zone '${newZone.name}' saved!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("💾 Save Zone", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: Saved Zones List
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "SAVED ZONES (${zones.size})",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (zones.isEmpty()) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No zones saved yet. Tap 'Map Editor' to create one.", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }

                        items(zones, key = { it.id }) { zone ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(zone.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            "${String.format("%.4f", zone.lat)}, ${String.format("%.4f", zone.lng)} — ${zone.radiusKm} km radius",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Switch(
                                        checked = zone.isEnabled,
                                        onCheckedChange = { chk ->
                                            val updated = zone.copy(isEnabled = chk)
                                            prefsRepo.addOrUpdateMapZone(updated)
                                            zones = prefsRepo.getMapZones()
                                        }
                                    )

                                    Spacer(Modifier.width(8.dp))

                                    IconButton(onClick = {
                                        editingZoneId = zone.id
                                        zoneName = zone.name
                                        pickedLat = zone.lat
                                        pickedLng = zone.lng
                                        radiusKm = zone.radiusKm.toFloat()
                                        selectedTab = 0
                                    }) {
                                        Text("✏️")
                                    }

                                    IconButton(onClick = {
                                        prefsRepo.deleteMapZone(zone.id)
                                        zones = prefsRepo.getMapZones()
                                        Toast.makeText(context, "Deleted zone", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("🗑️")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: Custom Area Lists
                    var newGroupName by remember { mutableStateOf("") }

                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "CUSTOM AREA LISTS",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Group zones into duty profiles (e.g., 'Morning Peak', 'Airport Zone')",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newGroupName,
                                        onValueChange = { newGroupName = it },
                                        placeholder = { Text("List Name (e.g. Airport Area)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (newGroupName.isNotBlank()) {
                                                val allZoneIds = zones.map { it.id }
                                                val group = AreaGroup(
                                                    name = newGroupName.trim(),
                                                    zoneIds = allZoneIds,
                                                    isSelected = areaGroups.isEmpty()
                                                )
                                                val updated = areaGroups + group
                                                prefsRepo.saveAreaGroups(updated)
                                                areaGroups = prefsRepo.getAreaGroups()
                                                newGroupName = ""
                                                Toast.makeText(context, "Created area list", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+ Create")
                                    }
                                }
                            }
                        }

                        items(areaGroups, key = { it.id }) { group ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (group.isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newSel = if (group.isSelected) null else group.id
                                        prefsRepo.selectAreaGroup(newSel)
                                        areaGroups = prefsRepo.getAreaGroups()
                                    }
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = group.isSelected,
                                        onClick = {
                                            val newSel = if (group.isSelected) null else group.id
                                            prefsRepo.selectAreaGroup(newSel)
                                            areaGroups = prefsRepo.getAreaGroups()
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            if (group.isSelected) "ACTIVE LIST (${group.zoneIds.size} zones included)" else "${group.zoneIds.size} zones included",
                                            color = if (group.isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = {
                                        val updated = areaGroups.filterNot { it.id == group.id }
                                        prefsRepo.saveAreaGroups(updated)
                                        areaGroups = prefsRepo.getAreaGroups()
                                    }) {
                                        Text("🗑️")
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
            return
        }

        // Active GPS listener fallback when cached location is null
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                onLocationFound(loc)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        for (p in providers) {
            try {
                lm.requestLocationUpdates(p, 500L, 0f, listener, android.os.Looper.getMainLooper())
            } catch (_: Exception) {}
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }, 8000L)
    } catch (e: SecurityException) {
        // Location permission handled by caller
    }
}

private fun buildMapHtml(
    centerLat: Double,
    centerLng: Double,
    markerLat: Double,
    markerLng: Double,
    radiusKm: Double,
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
  body { background:#0B0F19; font-family:sans-serif; }
  #map { width:100vw; height:100vh; }
  .leaflet-container { background: #1E293B; }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', {zoomControl:true, attributionControl:false})
           .setView([$centerLat, $centerLng], 13);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  crossOrigin: true
}).addTo(map);

var marker = null, circle = null;
var radiusM = $radiusM;

function placeMarker(lat, lng) {
  if (marker) map.removeLayer(marker);
  if (circle) map.removeLayer(circle);
  marker = L.marker([lat, lng]).addTo(map).bindPopup('📍 Zone Center').openPopup();
  circle = L.circle([lat, lng], {
    radius: radiusM,
    color: '#38BDF8', fillColor: '#38BDF8', fillOpacity: 0.22, weight: 2
  }).addTo(map);
  if (typeof Android !== 'undefined' && Android.onLocationPicked) {
    Android.onLocationPicked(lat, lng);
  }
}

placeMarker($markerLat, $markerLng);

function updateRadius(r) {
  radiusM = r;
  if (circle && marker) {
    var ll = marker.getLatLng();
    map.removeLayer(circle);
    circle = L.circle([ll.lat, ll.lng], {
      radius: radiusM,
      color: '#38BDF8', fillColor: '#38BDF8', fillOpacity: 0.22, weight: 2
    }).addTo(map);
  }
}

map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });
</script>
</body>
</html>"""
}
