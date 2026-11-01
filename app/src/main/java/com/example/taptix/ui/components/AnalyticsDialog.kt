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
import com.example.taptix.data.AnalyticsManager
import java.util.Locale

@Composable
fun AnalyticsDialog(
    analyticsManager: AnalyticsManager,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(1) } // 0 = Today, 1 = This Week, 2 = This Month
    var fuelText by remember { mutableStateOf("") }
    var otherExpenseText by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    val todaySummary = remember(refreshTrigger) { analyticsManager.getTodaySummary() }
    val weeklySummary = remember(refreshTrigger) { analyticsManager.getWeeklySummary() }
    val monthlySummary = remember(refreshTrigger) { analyticsManager.getMonthlySummary() }

    val activeSummary = when (selectedTab) {
        0 -> todaySummary
        1 -> weeklySummary
        else -> monthlySummary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📊 Driver Earnings & Analytics",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("✕ Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Period Tabs ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PeriodTabItem(label = "Today", isSelected = selectedTab == 0, modifier = Modifier.weight(1f)) {
                        selectedTab = 0
                    }
                    PeriodTabItem(label = "This Week", isSelected = selectedTab == 1, modifier = Modifier.weight(1f)) {
                        selectedTab = 1
                    }
                    PeriodTabItem(label = "This Month", isSelected = selectedTab == 2, modifier = Modifier.weight(1f)) {
                        selectedTab = 2
                    }
                }

                // ── Hero Summary Card ──
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${activeSummary.periodTitle.uppercase()} EARNINGS",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            String.format(Locale.US, "₹ %.2f", activeSummary.totalEarnings),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (activeSummary.totalExpenses > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    String.format(Locale.US, "Net Profit: ₹ %.2f", activeSummary.netProfit),
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    String.format(Locale.US, "• Expenses: ₹ %.2f", activeSummary.totalExpenses),
                                    color = Color(0xFFF87171),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ── Weekly Earnings Visual Bar Chart ──
                if (selectedTab == 1 && weeklySummary.dailyBreakdown.isNotEmpty()) {
                    Text(
                        "7-DAY EARNINGS TREND",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )

                    val maxEarning = (weeklySummary.dailyBreakdown.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(100.0)

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklySummary.dailyBreakdown.forEach { (dayName, earning) ->
                                val barHeightFraction = (earning / maxEarning).toFloat().coerceIn(0.06f, 1f)
                                val isBestDay = earning == weeklySummary.dailyBreakdown.maxOf { it.second } && earning > 0

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (earning > 0) {
                                        Text(
                                            "₹${earning.toInt()}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBestDay) Color(0xFF34D399) else Color(0xFF94A3B8)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .fillMaxHeight(barHeightFraction * 0.72f)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                if (isBestDay) Color(0xFF10B981)
                                                else if (earning > 0) Color(0xFF2563EB)
                                                else Color(0xFF334155)
                                            )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        dayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Performance Metrics Breakdown ──
                Text(
                    "TRIP & RATE STATISTICS",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalyticsStatTile(
                        label = "Avg Fare / Trip",
                        value = String.format(Locale.US, "₹ %.0f", activeSummary.averageFare),
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatTile(
                        label = "Completed Trips",
                        value = "${activeSummary.completedTrips}",
                        color = Color(0xFF34D399),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalyticsStatTile(
                        label = "Acceptance Rate",
                        value = "${activeSummary.acceptanceRate}%",
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatTile(
                        label = "Total Distance",
                        value = if (activeSummary.totalDistanceKm > 0) String.format(Locale.US, "%.1f km", activeSummary.totalDistanceKm) else "0 km",
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = Color(0xFF334155))

                // ── Expense Tracking Section ──
                Text(
                    "⛽ EXPENSE TRACKER (OPTIONAL)",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "Log fuel or toll costs to calculate accurate net driver earnings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fuelText,
                        onValueChange = { fuelText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Fuel (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = otherExpenseText,
                        onValueChange = { otherExpenseText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Toll/Misc (₹)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Button(
                    onClick = {
                        val fuel = fuelText.toDoubleOrNull() ?: 0.0
                        val other = otherExpenseText.toDoubleOrNull() ?: 0.0
                        if (fuel > 0.0 || other > 0.0) {
                            analyticsManager.recordExpense(fuel, other)
                            fuelText = ""
                            otherExpenseText = ""
                            refreshTrigger++
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Add to Today's Expenses", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", color = Color.White)
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun PeriodTabItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AnalyticsStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, color = color)
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
