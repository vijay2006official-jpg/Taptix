package com.example.taptix

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.service.AutoClickService
import com.example.taptix.ui.theme.TaptixTheme

/**
 * Built-in Demo Testing Screen: lets drivers safely simulate and verify
 * auto-accept behavior without opening real third-party ride apps.
 */
class DemoActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsRepo = PreferencesRepository(this)

        setContent {
            TaptixTheme {
                DemoScreen(
                    prefsRepo = prefsRepo,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun DemoScreen(
    prefsRepo: PreferencesRepository,
    onClose: () -> Unit
) {
    val settings = remember { prefsRepo.getSettings() }
    val isMasterOn = settings.masterControlEnabled
    val isAccessibilityActive = AutoClickService.isServiceRunning()

    var selectedPlatform by remember { mutableStateOf("Uber Premier") }
    var includeCashNegative by remember { mutableStateOf(false) }
    var isSimulating by remember { mutableStateOf(false) }
    var offerStartTime by remember { mutableStateOf(0L) }
    var acceptLatencyMs by remember { mutableStateOf<Long?>(null) }
    var statusMessage by remember { mutableStateOf("Ready to simulate booking") }
    var isAccepted by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        // Top Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🧪 AUTO-ACCEPT SIMULATOR", fontWeight = FontWeight.Black, color = Color.White, fontSize = 17.sp)
                Text("Safely test keywords & tap speed", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            IconButton(onClick = onClose) {
                Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Service Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMasterOn && isAccessibilityActive) Color(0xFF064E3B) else Color(0xFF450A0A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isMasterOn && isAccessibilityActive) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Column {
                        Text(
                            if (isMasterOn && isAccessibilityActive) "Taptix Engine: READY" else "Taptix Engine: INACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            if (!isAccessibilityActive) "Accessibility service is OFF in Settings"
                            else if (!isMasterOn) "Master Switch is OFF in MainActivity"
                            else "Ready to auto-accept simulated requests",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Test Controls Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SIMULATION CONFIGURATION", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Uber Premier", "Ola Mini", "Rapido").forEach { name ->
                            val sel = selectedPlatform == name
                            Button(
                                onClick = {
                                    selectedPlatform = name
                                    isSimulating = false
                                    isAccepted = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sel) Color(0xFF2563EB) else Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text(name.split(" ")[0], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Include 'CASH' Tag (Negative Keyword Test)", color = Color.White, fontSize = 13.sp)
                            Text("Tests if negative keywords correctly block order", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = includeCashNegative,
                            onCheckedChange = {
                                includeCashNegative = it
                                isSimulating = false
                                isAccepted = false
                            }
                        )
                    }

                    Button(
                        onClick = {
                            isAccepted = false
                            acceptLatencyMs = null
                            isSimulating = true
                            offerStartTime = SystemClock.uptimeMillis()
                            statusMessage = "📡 Simulated request incoming..."
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("⚡ Trigger Incoming Ride Offer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Simulated Ride Offer Card
            AnimatedVisibility(
                visible = isSimulating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            2.dp,
                            if (isAccepted) Color(0xFF10B981) else Color(0xFF38BDF8),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🚘 $selectedPlatform Request",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (includeCashNegative) Color(0xFFB91C1C) else Color(0xFF1D4ED8)
                            ) {
                                Text(
                                    if (includeCashNegative) "CASH ORDER" else "DIGITAL PREPAID",
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text("Pickup: Central Railway Station (2.1 km)", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text("Drop: Airport Terminal 2 • Fare: ₹380", color = Color(0xFF94A3B8), fontSize = 13.sp)

                        if (isAccepted) {
                            Surface(
                                color = Color(0xFF064E3B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🎉 RIDE ACCEPTED BY TAPTIX!", color = Color(0xFF34D399), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    Text("Execution Speed: ${acceptLatencyMs ?: 0} ms", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        } else {
                            // Target Button to be matched and clicked by AutoClickService
                            Button(
                                onClick = {
                                    val now = SystemClock.uptimeMillis()
                                    acceptLatencyMs = now - offerStartTime
                                    isAccepted = true
                                    statusMessage = "✅ Auto-Accepted in ${acceptLatencyMs}ms"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (selectedPlatform.contains("Uber")) "ACCEPT" else "CONFIRM",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Instructions Box
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "💡 How it works: If Master Control & Accessibility are ON, Taptix will detect the 'ACCEPT' button above the moment you trigger the offer and tap it automatically.",
                    Modifier.padding(12.dp),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
