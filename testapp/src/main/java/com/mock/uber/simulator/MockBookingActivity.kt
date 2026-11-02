package com.mock.uber.simulator

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * PHASE Q: CONTROLLED BOOKING SIMULATOR APPLICATION
 *
 * Simulates real ride-hailing partner screens (Uber Premier / Go format)
 * with controlled synthetic scenarios for automated accessibility testing.
 */
class MockBookingActivity : ComponentActivity() {

    private var currentScenario by mutableStateOf("VALID_BOOKING")
    private var isAcceptedState by mutableStateOf(false)
    private var isProcessingState by mutableStateOf(false)
    private var isExpiredState by mutableStateOf(false)
    private var isCancelledState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseScenario(intent)

        setContent {
            MaterialTheme {
                MockBookingScreen(
                    scenario = currentScenario,
                    isAccepted = isAcceptedState,
                    isProcessing = isProcessingState,
                    isExpired = isExpiredState,
                    isCancelled = isCancelledState,
                    onAcceptClicked = { handleAcceptClick() },
                    onScenarioSelected = { scenario ->
                        resetState()
                        currentScenario = scenario
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseScenario(intent)
    }

    private fun parseScenario(intent: Intent?) {
        val scenarioExtra = intent?.getStringExtra("scenario") ?: intent?.getStringExtra("SCENARIO")
        if (!scenarioExtra.isNullOrBlank()) {
            resetState()
            currentScenario = scenarioExtra.uppercase()
            if (currentScenario == "EXPIRED_BOOKING") isExpiredState = true
            if (currentScenario == "CANCELLED_BOOKING") isCancelledState = true
            if (currentScenario == "ACCEPTED_CONFIRMATION") isAcceptedState = true
        }
    }

    private fun resetState() {
        isAcceptedState = false
        isProcessingState = false
        isExpiredState = false
        isCancelledState = false
    }

    private fun handleAcceptClick() {
        when (currentScenario) {
            "VALID_BOOKING" -> {
                // Immediate positive confirmation transition
                isAcceptedState = true
            }
            "DELAYED_RESPONSE" -> {
                // Button remains disabled in processing state for 2000ms
                isProcessingState = true
                Handler(Looper.getMainLooper()).postDelayed({
                    isProcessingState = false
                    isAcceptedState = true
                }, 2000L)
            }
            "SCREEN_TRANSITION" -> {
                // Navigates away immediately
                finish()
            }
            else -> {
                isAcceptedState = true
            }
        }
    }
}

@Composable
fun MockBookingScreen(
    scenario: String,
    isAccepted: Boolean,
    isProcessing: Boolean,
    isExpired: Boolean,
    isCancelled: Boolean,
    onAcceptClicked: () -> Unit,
    onScenarioSelected: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Uber Driver (Simulator)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("TEST HARNESS", color = Color(0xFF10B981), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }

        // Scenario Selector Buttons
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { onScenarioSelected("VALID_BOOKING") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (scenario == "VALID_BOOKING") Color(0xFF2563EB) else Color(0xFF334155))
            ) {
                Text("Valid", fontSize = 11.sp)
            }
            Button(
                onClick = { onScenarioSelected("FILTER_FAIL_FARE") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (scenario == "FILTER_FAIL_FARE") Color(0xFF2563EB) else Color(0xFF334155))
            ) {
                Text("Low Fare", fontSize = 11.sp)
            }
            Button(
                onClick = { onScenarioSelected("FILTER_FAIL_CASH") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (scenario == "FILTER_FAIL_CASH") Color(0xFF2563EB) else Color(0xFF334155))
            ) {
                Text("Payment", fontSize = 11.sp)
            }
            Button(
                onClick = { onScenarioSelected("DELAYED_RESPONSE") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (scenario == "DELAYED_RESPONSE") Color(0xFF2563EB) else Color(0xFF334155))
            ) {
                Text("Delayed", fontSize = 11.sp)
            }
        }

        // Offer Card Area
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    isAccepted -> {
                        // Positive Confirmation Screen
                        Text("RIDE ACCEPTED", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("NAVIGATE TO PICKUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Passenger: Priya S. (Rating 4.9)", color = Color.LightGray, fontSize = 14.sp)
                        Text("Pickup: Central Railway Station (1.8 km away)", color = Color.Gray, fontSize = 13.sp)
                        Text("NAVIGATE TO PICKUP SPOT", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    isExpired -> {
                        // Expired Screen
                        Text("OFFER EXPIRED", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Booking timed out. Searching for nearby trips...", color = Color.LightGray, fontSize = 14.sp)
                    }

                    isCancelled -> {
                        // Cancelled Screen
                        Text("BOOKING CANCELLED", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Passenger cancelled this trip request.", color = Color.LightGray, fontSize = 14.sp)
                    }

                    else -> {
                        // Booking Offer Details
                        when (scenario) {
                            "VALID_BOOKING" -> {
                                Text("Uber Premier Request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Fare: ₹ 380.00", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("Pickup: 1.8 km (4 mins)", color = Color.LightGray, fontSize = 14.sp)
                                Text("Drop: Airport Terminal 2", color = Color.Gray, fontSize = 13.sp)
                            }
                            "FILTER_FAIL_FARE" -> {
                                Text("Uber Go Request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Fare: ₹ 80.00", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("Pickup: 2.0 km", color = Color.LightGray, fontSize = 14.sp)
                                Text("Drop: Local Market", color = Color.Gray, fontSize = 13.sp)
                            }
                            "FILTER_FAIL_CASH" -> {
                                Text("Uber Go Request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Fare: ₹ 350.00", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("Pickup: 1.5 km", color = Color.LightGray, fontSize = 14.sp)
                                Text("Payment: CASH ORDER", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            "DELAYED_RESPONSE" -> {
                                Text("Uber Premier Request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Fare: ₹ 420.00", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("Pickup: 2.0 km", color = Color.LightGray, fontSize = 14.sp)
                                Text("Drop: Tech Park", color = Color.Gray, fontSize = 13.sp)
                            }
                            "UNAVAILABLE_BUTTON" -> {
                                Text("Uber Premier Request", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("Fare: ₹ 380.00", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("Pickup: 1.8 km", color = Color.LightGray, fontSize = 14.sp)
                            }
                            else -> {
                                Text("Uber Request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Fare: ₹ 250.00", color = Color.White, fontSize = 20.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Accept Button
                        val buttonEnabled = !isProcessing && scenario != "UNAVAILABLE_BUTTON"
                        val buttonText = when {
                            isProcessing -> "PROCESSING..."
                            scenario == "UNAVAILABLE_BUTTON" -> "ACCEPT (DISABLED)"
                            else -> "ACCEPT"
                        }

                        Button(
                            onClick = onAcceptClicked,
                            enabled = buttonEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (buttonEnabled) Color(0xFF10B981) else Color(0xFF52525B),
                                disabledContainerColor = Color(0xFF3F3F46)
                            )
                        ) {
                            Text(buttonText, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
