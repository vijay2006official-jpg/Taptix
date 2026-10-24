package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.UnparsedDataFallback

@Composable
fun SmartRulesDialog(
    initialRules: SmartAcceptRules,
    onSave: (SmartAcceptRules) -> Unit,
    onDismiss: () -> Unit
) {
    var minFareEnabled by remember { mutableStateOf(initialRules.minFareEnabled) }
    var minFareText by remember { mutableStateOf(if (initialRules.minFareAmount > 0) initialRules.minFareAmount.toInt().toString() else "150") }

    var maxFareEnabled by remember { mutableStateOf(initialRules.maxFareEnabled) }
    var maxFareText by remember { mutableStateOf(if (initialRules.maxFareAmount > 0) initialRules.maxFareAmount.toInt().toString() else "1000") }

    var maxDistEnabled by remember { mutableStateOf(initialRules.maxPickupDistanceEnabled) }
    var maxDistKm by remember { mutableStateOf(initialRules.maxPickupDistanceKm.toFloat()) }

    var destFilterEnabled by remember { mutableStateOf(initialRules.destinationFilterEnabled) }
    var destKeywords by remember { mutableStateOf(initialRules.destinationKeywords) }

    var fallbackReject by remember { mutableStateOf(initialRules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🎯 Smart Offer-Matching Rules", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minimum Fare
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum Fare Filter", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Only accept orders paying at least this fare", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = minFareEnabled, onCheckedChange = { minFareEnabled = it })
                }
                if (minFareEnabled) {
                    OutlinedTextField(
                        value = minFareText,
                        onValueChange = { minFareText = it.filter { c -> c.isDigit() } },
                        label = { Text("Minimum Fare (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Maximum Pickup Distance
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Max Pickup Distance", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Ignore bookings farther than ${String.format("%.1f", maxDistKm)} km away", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = maxDistEnabled, onCheckedChange = { maxDistEnabled = it })
                }
                if (maxDistEnabled) {
                    Slider(
                        value = maxDistKm,
                        onValueChange = { maxDistKm = it },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Preferred Destination Areas
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Preferred Destination Keywords", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Only accept if trip goes to these areas", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = destFilterEnabled, onCheckedChange = { destFilterEnabled = it })
                }
                if (destFilterEnabled) {
                    OutlinedTextField(
                        value = destKeywords,
                        onValueChange = { destKeywords = it },
                        placeholder = { Text("e.g. Airport, Central, Tech Park") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Unparsed Data Safety Rule
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict Safety Fallback", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Skip order if fare/distance cannot be parsed reliably", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = fallbackReject, onCheckedChange = { fallbackReject = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SmartAcceptRules(
                            minFareEnabled = minFareEnabled,
                            minFareAmount = minFareText.toDoubleOrNull() ?: 0.0,
                            maxFareEnabled = maxFareEnabled,
                            maxFareAmount = maxFareText.toDoubleOrNull() ?: 0.0,
                            maxPickupDistanceEnabled = maxDistEnabled,
                            maxPickupDistanceKm = maxDistKm.toDouble(),
                            destinationFilterEnabled = destFilterEnabled,
                            destinationKeywords = destKeywords,
                            fallbackOnUnparsedData = if (fallbackReject) UnparsedDataFallback.REJECT else UnparsedDataFallback.ACCEPT
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Rules", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp)
    )
}
