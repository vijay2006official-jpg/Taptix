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
    val zones = remember { prefsRepo.getMapZones() }

    var pickedLat by remember { mutableStateOf(13.0827) }
    var pickedLng by remember { mutableStateOf(80.2707) }
    var radiusKm by remember { mutableStateOf(2.0f) }
    var pickedAddress by remember { mutableStateOf("Chinnaiyan Colony, Perambur, Chennai") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSpeedKmh by remember { mutableStateOf(43) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Init from existing first zone or current GPS
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            centerOnCurrentLocation(context) { loc ->
                pickedLat = loc.latitude
                pickedLng = loc.longitude
                if (loc.hasSpeed()) {
                    currentSpeedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
                }
                webViewRef?.evaluateJavascript(
                    "map.setView([${loc.latitude}, ${loc.longitude}], 14); placeMarker(${loc.latitude}, ${loc.longitude});",
                    null
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val first = zones.firstOrNull()
        if (first != null) {
            pickedLat = first.lat
            pickedLng = first.lng
            radiusKm = first.radiusKm.toFloat()
        } else {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                centerOnCurrentLocation(context) { loc ->
                    pickedLat = loc.latitude
                    pickedLng = loc.longitude
                    if (loc.hasSpeed()) {
                        currentSpeedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
                    }
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

    Box(Modifier.fillMaxSize().background(Color(0xFFE5E7EB))) {
        // ── 1. Fullscreen Map View
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
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onLocationPicked(lat: Double, lng: Double, address: String?) {
                            if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                                pickedLat = lat
                                pickedLng = lng
                                if (!address.isNullOrBlank()) {
                                    pickedAddress = address
                                }
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

        // ── 2. Top Bar (Back Button + Search Area Pill)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

            // "Search area..." Pill
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
                        Text("Search area...", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
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

        // ── 3. Speedometer Badge (Top-Left)
        Box(
            Modifier
                .padding(start = 18.dp, top = 80.dp)
                .size(62.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFFBBF24)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$currentSpeedKmh",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color.Black,
                    lineHeight = 20.sp
                )
                Text(
                    "km/h",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.Black,
                    lineHeight = 10.sp
                )
            }
        }

        // ── 4. Floating GPS Target Button (Bottom-Right, above bottom card)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 225.dp)
                .size(48.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        centerOnCurrentLocation(context) { loc ->
                            pickedLat = loc.latitude
                            pickedLng = loc.longitude
                            if (loc.hasSpeed()) {
                                currentSpeedKmh = (loc.speed * 3.6f).toInt().coerceAtLeast(0)
                            }
                            webViewRef?.evaluateJavascript(
                                "map.setView([${loc.latitude}, ${loc.longitude}], 14); placeMarker(${loc.latitude}, ${loc.longitude});",
                                null
                            )
                        }
                    } else {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("🎯", fontSize = 20.sp)
        }

        // ── 5. Bottom Sheet: SET WORK BOUNDARY
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "SET WORK BOUNDARY",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Long press on map to drop a pin. The bot will only accept orders within this circle.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(14.dp))

                // Radius Slider Row
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Radius:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )

                    Slider(
                        value = radiusKm,
                        onValueChange = { v ->
                            radiusKm = v
                            webViewRef?.evaluateJavascript("updateRadius(${(v * 1000).toInt()});", null)
                        },
                        valueRange = 0.5f..20f,
                        steps = 38,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0066FF),
                            activeTrackColor = Color(0xFF0066FF),
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Text(
                        "${radiusKm.toInt()} km",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF0066FF)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Save Boundary Button
                Button(
                    onClick = {
                        val newZone = MapZone(
                            id = UUID.randomUUID().toString(),
                            name = "Work Zone",
                            lat = pickedLat,
                            lng = pickedLng,
                            radiusKm = radiusKm.toDouble(),
                            isEnabled = true
                        )
                        prefsRepo.addOrUpdateMapZone(newZone)
                        prefsRepo.saveMapZone(pickedLat, pickedLng, radiusKm.toDouble())
                        prefsRepo.saveMapZoneEnabled(true)
                        Toast.makeText(context, "✅ Work boundary saved: ${radiusKm.toInt()} km circle", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF))
                ) {
                    Text(
                        "SAVE BOUNDARY",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
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
    } catch (_: SecurityException) {}
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
  body { background:#F1F5F9; font-family:sans-serif; }
  #map { width:100vw; height:100vh; }
  .leaflet-popup-content-wrapper {
    background: #FFFFFF;
    color: #0F172A;
    font-size: 12px;
    font-weight: 600;
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.18);
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', {zoomControl:false, attributionControl:false})
           .setView([$centerLat, $centerLng], 13);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  crossOrigin: true
}).addTo(map);

var marker = null, circle = null;
var radiusM = $radiusM;

function fetchAddressAndPopup(lat, lng) {
  var url = 'https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng;
  fetch(url, { headers: { 'Accept': 'application/json' } })
    .then(function(res) { return res.json(); })
    .then(function(data) {
      var addr = data && data.display_name ? data.display_name : (lat.toFixed(4) + ', ' + lng.toFixed(4));
      if (marker) {
        marker.bindPopup('<div style="font-size:12px; font-weight:600; line-height:16px;">' + addr + '</div>').openPopup();
      }
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, addr);
      }
    })
    .catch(function() {
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, '');
      }
    });
}

function placeMarker(lat, lng) {
  if (marker) map.removeLayer(marker);
  if (circle) map.removeLayer(circle);

  marker = L.marker([lat, lng]).addTo(map);
  circle = L.circle([lat, lng], {
    radius: radiusM,
    color: '#0066FF',
    fillColor: '#0066FF',
    fillOpacity: 0.18,
    weight: 2
  }).addTo(map);

  fetchAddressAndPopup(lat, lng);
}

placeMarker($markerLat, $markerLng);

function updateRadius(r) {
  radiusM = r;
  if (circle && marker) {
    var ll = marker.getLatLng();
    map.removeLayer(circle);
    circle = L.circle([ll.lat, ll.lng], {
      radius: radiusM,
      color: '#0066FF',
      fillColor: '#0066FF',
      fillOpacity: 0.18,
      weight: 2
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
        map.setView([lat, lon], 14);
        placeMarker(lat, lon);
      }
    });
}

map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });
map.on('contextmenu', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });
</script>
</body>
</html>"""
}
