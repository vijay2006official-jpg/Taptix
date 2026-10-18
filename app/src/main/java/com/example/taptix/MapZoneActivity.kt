package com.example.taptix

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.ui.theme.TaptixTheme

class MapZoneActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)
        val settings = prefsRepo.getSettings()

        setContent {
            TaptixTheme {
                MapZoneScreen(
                    initialLat = settings.mapZoneLat,
                    initialLng = settings.mapZoneLng,
                    initialRadius = settings.mapZoneRadiusKm,
                    onSave = { lat, lng, radius ->
                        prefsRepo.saveMapZone(lat, lng, radius)
                        prefsRepo.saveMapZoneEnabled(true)
                        Toast.makeText(
                            this,
                            "✅ Zone saved! ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)} — ${radius}km",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapZoneScreen(
    initialLat: Double,
    initialLng: Double,
    initialRadius: Double,
    onSave: (Double, Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    var pickedLat by remember { mutableStateOf(initialLat) }
    var pickedLng by remember { mutableStateOf(initialLng) }
    var radiusKm  by remember { mutableStateOf(initialRadius.toFloat()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("📍 Tap on the map to set your zone") }

    // Default to Chennai if no saved location
    val startLat = if (initialLat == 0.0) 13.0827 else initialLat
    val startLng = if (initialLng == 0.0) 80.2707 else initialLng
    val hasMarker = initialLat != 0.0 || initialLng != 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(16.dp)
        ) {
            Column {
                Text("🗺️ SET MAP ZONE", fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontSize = 18.sp, letterSpacing = 1.sp)
                Text("Tap on map to place zone circle",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }

        // ── Status bar ───────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(10.dp)
        ) {
            Text(statusText, color = Color(0xFF00E5FF), fontSize = 12.sp,
                fontWeight = FontWeight.Medium)
        }

        // ── Map WebView ──────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled  = true
                            loadWithOverviewMode = true
                            useWideViewPort    = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            // Allow mixed content & cross-origin for tile loading
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient   = WebViewClient()

                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onLocationPicked(lat: Double, lng: Double) {
                                pickedLat  = lat
                                pickedLng  = lng
                                statusText = "✅ Zone: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
                            }
                        }, "Android")

                        // Load tiles from OSM directly — base URL allows network requests
                        loadDataWithBaseURL(
                            "https://tile.openstreetmap.org/",
                            buildMapHtml(startLat, startLng, initialLat, initialLng,
                                if (hasMarker) initialRadius else 2.0),
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

        // ── Radius Slider ────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zone Radius:", color = Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${String.format("%.1f", radiusKm)} km",
                    color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
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
                    thumbColor      = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.5 km", color = Color(0xFF8892A4), fontSize = 11.sp)
                Text("20 km",  color = Color(0xFF8892A4), fontSize = 11.sp)
            }
        }

        // ── Save / Cancel ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFF0D0D0F))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444455))
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    if (pickedLat == 0.0 && pickedLng == 0.0) {
                        statusText = "⚠️ Please tap on the map first!"
                    } else {
                        onSave(pickedLat, pickedLng, radiusKm.toDouble())
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("💾 Save Zone", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun buildMapHtml(
    centerLat: Double, centerLng: Double,
    markerLat: Double, markerLng: Double,
    radiusKm: Double
): String {
    val hasMarker = markerLat != 0.0 || markerLng != 0.0
    val radiusM   = (radiusKm * 1000).toInt()

    // Leaflet CSS + JS embedded inline (hosted on CDN but loaded via base URL trick)
    return """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#111; }
  #map { width:100vw; height:100vh; }
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
    color: '#00E5FF', fillColor: '#00E5FF', fillOpacity: 0.18, weight: 2
  }).addTo(map);
  if (typeof Android !== 'undefined') Android.onLocationPicked(lat, lng);
}

${if (hasMarker) "placeMarker($markerLat, $markerLng);" else ""}

function updateRadius(r) {
  radiusM = r;
  if (circle && marker) {
    var ll = marker.getLatLng();
    map.removeLayer(circle);
    circle = L.circle([ll.lat, ll.lng], {
      radius: radiusM,
      color: '#00E5FF', fillColor: '#00E5FF', fillOpacity: 0.18, weight: 2
    }).addTo(map);
  }
}

map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); });
</script>
</body>
</html>"""
}
