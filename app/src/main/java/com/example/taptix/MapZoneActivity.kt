package com.example.taptix

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
    private var savedLat = 0.0
    private var savedLng = 0.0
    private var savedRadius = 2.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)
        val settings = prefsRepo.getSettings()
        savedLat = settings.mapZoneLat
        savedLng = settings.mapZoneLng
        savedRadius = settings.mapZoneRadiusKm

        setContent {
            TaptixTheme {
                MapZoneScreen(
                    initialLat = savedLat,
                    initialLng = savedLng,
                    initialRadius = savedRadius,
                    onSave = { lat, lng, radius ->
                        prefsRepo.saveMapZone(lat, lng, radius)
                        prefsRepo.saveMapZoneEnabled(true)
                        Toast.makeText(this, "✅ Map Zone Saved! ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)} — ${radius}km radius", Toast.LENGTH_LONG).show()
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
    var radiusKm by remember { mutableStateOf(initialRadius.toFloat()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("📍 Tap on the map to set your target zone") }

    // Default center: Chennai, India if no saved location
    val startLat = if (initialLat == 0.0) 13.0827 else initialLat
    val startLng = if (initialLng == 0.0) 80.2707 else initialLng

    val htmlContent = buildMapHtml(startLat, startLng, if (initialLat == 0.0) 0.0 else initialLat,
        if (initialLng == 0.0) 0.0 else initialLng, initialRadius)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

        // Status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(10.dp)
        ) {
            Text(statusText, color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        // Map WebView
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()

                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onLocationPicked(lat: Double, lng: Double) {
                                pickedLat = lat
                                pickedLng = lng
                                statusText = "✅ Zone set at: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)}"
                            }
                        }, "Android")

                        loadDataWithBaseURL("https://unpkg.com", htmlContent, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Radius Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zone Radius:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${String.format("%.1f", radiusKm)} km",
                    color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Slider(
                value = radiusKm,
                onValueChange = { v ->
                    radiusKm = v
                    webViewRef?.evaluateJavascript("updateRadius(${v * 1000});", null)
                },
                valueRange = 0.5f..20f,
                steps = 38,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.5 km", color = Color(0xFF8892A4), fontSize = 11.sp)
                Text("20 km", color = Color(0xFF8892A4), fontSize = 11.sp)
            }
        }

        // Save / Cancel buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body { margin: 0; padding: 0; }
  #map { width: 100vw; height: 100vh; }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map').setView([$centerLat, $centerLng], 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '© OpenStreetMap'
}).addTo(map);

var marker = null;
var circle = null;
var radiusMeters = ${(radiusKm * 1000).toInt()};

function placeMarker(lat, lng) {
  if (marker) { map.removeLayer(marker); }
  if (circle) { map.removeLayer(circle); }
  marker = L.marker([lat, lng]).addTo(map).bindPopup('📍 Zone Center').openPopup();
  circle = L.circle([lat, lng], {
    radius: radiusMeters,
    color: '#00E5FF',
    fillColor: '#00E5FF',
    fillOpacity: 0.15,
    weight: 2
  }).addTo(map);
  Android.onLocationPicked(lat, lng);
}

${if (hasMarker) "placeMarker($markerLat, $markerLng);" else ""}

function updateRadius(r) {
  radiusMeters = r;
  if (circle && marker) {
    var latlng = marker.getLatLng();
    map.removeLayer(circle);
    circle = L.circle([latlng.lat, latlng.lng], {
      radius: radiusMeters,
      color: '#00E5FF',
      fillColor: '#00E5FF',
      fillOpacity: 0.15,
      weight: 2
    }).addTo(map);
  }
}

map.on('click', function(e) {
  placeMarker(e.latlng.lat, e.latlng.lng);
});
</script>
</body>
</html>
    """.trimIndent()
}
