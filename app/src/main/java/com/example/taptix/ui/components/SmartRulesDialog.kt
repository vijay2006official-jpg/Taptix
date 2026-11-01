package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.model.BookingServiceCategory
import com.example.taptix.model.SmartAcceptRules
import com.example.taptix.model.UnparsedDataFallback
import java.util.Locale

@Composable
fun SmartRulesDialog(
    initialRules: SmartAcceptRules,
    onSave: (SmartAcceptRules) -> Unit,
    onDismiss: () -> Unit
) {
    // ── Fare States ──
    var minFareEnabled by remember { mutableStateOf(initialRules.minFareEnabled) }
    var minFareText by remember {
        mutableStateOf(if (initialRules.minFareAmount > 0) initialRules.minFareAmount.toInt().toString() else "150")
    }

    var maxFareEnabled by remember { mutableStateOf(initialRules.maxFareEnabled) }
    var maxFareText by remember {
        mutableStateOf(if (initialRules.maxFareAmount > 0) initialRules.maxFareAmount.toInt().toString() else "1500")
    }

    var minFarePerKmEnabled by remember { mutableStateOf(initialRules.minFarePerKmEnabled) }
    var minFarePerKmText by remember {
        mutableStateOf(if (initialRules.minFarePerKmAmount > 0) String.format(Locale.US, "%.0f", initialRules.minFarePerKmAmount) else "18")
    }

    // ── Distance States ──
    var maxPickupDistEnabled by remember { mutableStateOf(initialRules.maxPickupDistanceEnabled) }
    var maxPickupDistKm by remember { mutableStateOf(initialRules.maxPickupDistanceKm.toFloat()) }

    var maxTripDistEnabled by remember { mutableStateOf(initialRules.maxTripDistanceEnabled) }
    var maxTripDistKm by remember { mutableStateOf(initialRules.maxTripDistanceKm.toFloat()) }

    // ── Category Filter States ──
    var categoryFilterEnabled by remember { mutableStateOf(initialRules.categoryFilterEnabled) }
    var selectedCategories by remember { mutableStateOf(initialRules.allowedCategories.toMutableSet()) }

    // ── Destination & Safety States ──
    var destFilterEnabled by remember { mutableStateOf(initialRules.destinationFilterEnabled) }
    var destKeywords by remember { mutableStateOf(initialRules.destinationKeywords) }
    var fallbackReject by remember { mutableStateOf(initialRules.fallbackOnUnparsedData == UnparsedDataFallback.REJECT) }

    // Validation
    val minFareVal = minFareText.toDoubleOrNull() ?: 0.0
    val maxFareVal = maxFareText.toDoubleOrNull() ?: 0.0
    val fareRangeInvalid = minFareEnabled && maxFareEnabled && maxFareVal > 0.0 && minFareVal > maxFareVal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🎯 Booking Filters",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp
                )
                TextButton(
                    onClick = {
                        val def = SmartAcceptRules.defaultRules()
                        minFareEnabled = def.minFareEnabled
                        minFareText = def.minFareAmount.toInt().toString()
                        maxFareEnabled = def.maxFareEnabled
                        maxFareText = "1500"
                        minFarePerKmEnabled = def.minFarePerKmEnabled
                        minFarePerKmText = String.format(Locale.US, "%.0f", def.minFarePerKmAmount)
                        maxPickupDistEnabled = def.maxPickupDistanceEnabled
                        maxPickupDistKm = def.maxPickupDistanceKm.toFloat()
                        maxTripDistEnabled = def.maxTripDistanceEnabled
                        maxTripDistKm = def.maxTripDistanceKm.toFloat()
                        categoryFilterEnabled = def.categoryFilterEnabled
                        selectedCategories = def.allowedCategories.toMutableSet()
                        destFilterEnabled = def.destinationFilterEnabled
                        destKeywords = def.destinationKeywords
                        fallbackReject = def.fallbackOnUnparsedData == UnparsedDataFallback.REJECT
                    }
                ) {
                    Text("Reset Defaults", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Validation Error Banner
                if (fareRangeInvalid) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7F1D1D))
                            .padding(10.dp)
                    ) {
                        Text(
                            "⚠️ Minimum fare (₹$minFareVal) cannot exceed maximum fare (₹$maxFareVal)",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // ── SECTION 1: FARE FILTERING ──
                Text(
                    "💰 FARE & RATE FILTERS",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                // Minimum Fare
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum Booking Fare", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Reject orders paying below this amount", color = Color(0xFF94A3B8), fontSize = 11.sp)
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

                // Maximum Fare Cap
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Maximum Fare Limit (Cap)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Optional limit for local short-trip preference", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = maxFareEnabled, onCheckedChange = { maxFareEnabled = it })
                }
                if (maxFareEnabled) {
                    OutlinedTextField(
                        value = maxFareText,
                        onValueChange = { maxFareText = it.filter { c -> c.isDigit() } },
                        label = { Text("Maximum Fare Cap (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Minimum Fare Per KM
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum Fare per Kilometre", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Enforce minimum earnings per KM of ride distance", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = minFarePerKmEnabled, onCheckedChange = { minFarePerKmEnabled = it })
                }
                if (minFarePerKmEnabled) {
                    OutlinedTextField(
                        value = minFarePerKmText,
                        onValueChange = { minFarePerKmText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Min Rate (₹ / km)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // ── SECTION 2: DISTANCE FILTERING ──
                Text(
                    "📍 DISTANCE CONSTRAINTS",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                // Maximum Pickup Distance
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Max Pickup Distance", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Ignore bookings farther than ${String.format(Locale.US, "%.1f", maxPickupDistKm)} km away", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = maxPickupDistEnabled, onCheckedChange = { maxPickupDistEnabled = it })
                }
                if (maxPickupDistEnabled) {
                    Slider(
                        value = maxPickupDistKm,
                        onValueChange = { maxPickupDistKm = it },
                        valueRange = 0.5f..15f,
                        steps = 28
                    )
                }

                // Maximum Trip Distance
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Max Trip Distance", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Ignore long-distance rides beyond ${String.format(Locale.US, "%.0f", maxTripDistKm)} km", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = maxTripDistEnabled, onCheckedChange = { maxTripDistEnabled = it })
                }
                if (maxTripDistEnabled) {
                    Slider(
                        value = maxTripDistKm,
                        onValueChange = { maxTripDistKm = it },
                        valueRange = 5f..60f,
                        steps = 10
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // ── SECTION 3: SERVICE CATEGORIES ──
                Text(
                    "🚗 SERVICE CATEGORIES",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Filter by Service Category", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Accept only selected vehicle and duty types", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = categoryFilterEnabled, onCheckedChange = { categoryFilterEnabled = it })
                }

                if (categoryFilterEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tap categories to enable or disable:", color = Color(0xFF94A3B8), fontSize = 11.sp)

                        val categories = BookingServiceCategory.entries
                        for (i in categories.indices step 2) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val cat1 = categories[i]
                                val isSelected1 = selectedCategories.contains(cat1.id)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected1) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                                        .border(
                                            1.dp,
                                            if (isSelected1) Color(0xFF38BDF8) else Color(0xFF475569),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val copy = selectedCategories.toMutableSet()
                                            if (isSelected1) copy.remove(cat1.id) else copy.add(cat1.id)
                                            selectedCategories = copy
                                        }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${if (isSelected1) "✓ " else ""}${cat1.displayName}",
                                        color = if (isSelected1) Color.White else Color(0xFF94A3B8),
                                        fontWeight = if (isSelected1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }

                                if (i + 1 < categories.size) {
                                    val cat2 = categories[i + 1]
                                    val isSelected2 = selectedCategories.contains(cat2.id)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected2) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                                            .border(
                                                1.dp,
                                                if (isSelected2) Color(0xFF38BDF8) else Color(0xFF475569),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val copy = selectedCategories.toMutableSet()
                                                if (isSelected2) copy.remove(cat2.id) else copy.add(cat2.id)
                                                selectedCategories = copy
                                            }
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${if (isSelected2) "✓ " else ""}${cat2.displayName}",
                                            color = if (isSelected2) Color.White else Color(0xFF94A3B8),
                                            fontWeight = if (isSelected2) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                // ── SECTION 4: DESTINATION & SAFETY ──
                Text(
                    "🛡️ DESTINATION & SAFETY",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                // Preferred Destination Areas
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Destination Keywords Filter", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Only accept if drop address matches keywords", color = Color(0xFF94A3B8), fontSize = 11.sp)
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

                // Strict Safety Fallback
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict Safety Fallback", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Reject booking if active filter metrics cannot be reliably parsed", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = fallbackReject, onCheckedChange = { fallbackReject = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!fareRangeInvalid) {
                        onSave(
                            SmartAcceptRules(
                                minFareEnabled = minFareEnabled,
                                minFareAmount = minFareVal,
                                maxFareEnabled = maxFareEnabled,
                                maxFareAmount = maxFareVal,
                                minFarePerKmEnabled = minFarePerKmEnabled,
                                minFarePerKmAmount = minFarePerKmText.toDoubleOrNull() ?: 0.0,
                                maxPickupDistanceEnabled = maxPickupDistEnabled,
                                maxPickupDistanceKm = maxPickupDistKm.toDouble(),
                                maxTripDistanceEnabled = maxTripDistEnabled,
                                maxTripDistanceKm = maxTripDistKm.toDouble(),
                                destinationFilterEnabled = destFilterEnabled,
                                destinationKeywords = destKeywords,
                                categoryFilterEnabled = categoryFilterEnabled,
                                allowedCategories = selectedCategories,
                                excludedCategories = BookingServiceCategory.ALL_IDS - selectedCategories,
                                fallbackOnUnparsedData = if (fallbackReject) UnparsedDataFallback.REJECT else UnparsedDataFallback.ACCEPT
                            )
                        )
                    }
                },
                enabled = !fareRangeInvalid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    disabledContainerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Filters", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp)
    )
}
