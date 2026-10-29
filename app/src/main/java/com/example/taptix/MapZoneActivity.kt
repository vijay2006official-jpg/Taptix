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
    var pickedAddress by remember { mutableStateOf("Locating your area...") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSpeedKmh by remember { mutableStateOf(0) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
            updateMapLocation(loc.latitude, loc.longitude)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocationAndCenter()
        }
    }

    LaunchedEffect(Unit) {
        val first = zones.firstOrNull()
        if (first != null) {
            pickedLat = first.lat
            pickedLng = first.lng
            radiusKm = first.radiusKm.toFloat()
        }

        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            fetchLocationAndCenter()
        } else {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                        databaseEnabled = true
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
                            view?.evaluateJavascript("if (typeof forceResize === 'function') forceResize();", null)
                        }
                    }

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
                        "https://localhost/",
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
                        Text("Search city or area...", color = Color(0xFF94A3B8), fontSize = 14.sp)
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
                .padding(end = 20.dp, bottom = 245.dp)
                .size(52.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        fetchLocationAndCenter()
                    } else {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("🎯", fontSize = 22.sp)
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
                    text = pickedAddress,
                    color = Color(0xFF0066FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tap map or drag slider to set area radius. The bot will accept orders within this circle.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
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
                            name = if (pickedAddress.isNotBlank()) pickedAddress.take(25) else "Work Zone",
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
                lm.requestLocationUpdates(p, 500L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {}
        }
        Handler(Looper.getMainLooper()).postDelayed({
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

// Primary Layer: High-Resolution Google Maps Raster (full local street names, areas, landmarks in English & local script)
var googleLayer = L.tileLayer('https://mt{s}.google.com/vt/lyrs=m&hl=en&gl=in&x={x}&y={y}&z={z}', {
  subdomains: ['0', '1', '2', '3'],
  maxZoom: 20
}).addTo(map);

// Fallback to CartoDB Voyager if Google tiles ever fail
googleLayer.on('tileerror', function() {
  if (!map._hasCartoFallback) {
    map._hasCartoFallback = true;
    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      subdomains: 'abcd',
      maxZoom: 19
    }).addTo(map);
  }
});

var pinIcon = L.divIcon({
  className: 'taptix-marker',
  html: '<div style="position:relative; width:36px; height:36px;">' +
        '<div style="position:absolute; width:34px; height:34px; background:#0066FF; border-radius:50% 50% 50% 0; transform:rotate(-45deg); border:3px solid #FFFFFF; box-shadow:0 4px 14px rgba(0,0,0,0.45); top:0; left:0;"></div>' +
        '<div style="position:absolute; width:12px; height:12px; background:#FFFFFF; border-radius:50%; top:8px; left:11px;"></div>' +
        '</div>',
  iconSize: [36, 36],
  iconAnchor: [18, 34],
  popupAnchor: [0, -34]
});

var marker = null, circle = null, userGpsMarker = null;
var radiusM = $radiusM;

function fetchAddressAndPopup(lat, lng) {
  var url = 'https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng + '&zoom=18&addressdetails=1';
  fetch(url, { headers: { 'Accept': 'application/json', 'User-Agent': 'TaptixDriverApp/2.2' } })
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
        marker.bindPopup('<div style="font-size:12px; font-weight:700; line-height:16px; color:#0F172A;">📍 ' + addr + '</div>').openPopup();
      }
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, addr);
      }
    })
    .catch(function() {
      var fallback = lat.toFixed(4) + ', ' + lng.toFixed(4);
      if (typeof Android !== 'undefined' && Android.onLocationPicked) {
        Android.onLocationPicked(lat, lng, fallback);
      }
    });
}

function placeMarker(lat, lng) {
  if (marker) map.removeLayer(marker);
  if (circle) map.removeLayer(circle);

  marker = L.marker([lat, lng], {icon: pinIcon}).addTo(map);
  circle = L.circle([lat, lng], {
    radius: radiusM,
    color: '#0066FF',
    fillColor: '#0066FF',
    fillOpacity: 0.18,
    weight: 2
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
            '<div class="gps-pulse-ring" style="position:absolute; width:28px; height:28px; border-radius:50%; background:rgba(0,102,255,0.35);"></div>' +
            '<div style="position:absolute; width:14px; height:14px; border-radius:50%; background:#0066FF; border:3px solid #FFFFFF; top:7px; left:7px; box-shadow:0 2px 8px rgba(0,0,0,0.35);"></div>' +
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
    updateUserGpsLocation(lat, lng);
  }
}

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
  fetch(url, { headers: { 'Accept': 'application/json', 'User-Agent': 'TaptixDriverApp/2.2' } })
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
    map.invalidateSize();
  }
}

map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });
map.on('contextmenu', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });

placeMarker($markerLat, $markerLng);
updateUserGpsLocation($centerLat, $centerLng);

window.addEventListener('load', forceResize);
window.addEventListener('resize', forceResize);
setTimeout(forceResize, 100);
setTimeout(forceResize, 350);
setTimeout(forceResize, 800);
setTimeout(forceResize, 1500);
</script>
</body>
</html>"""
}
