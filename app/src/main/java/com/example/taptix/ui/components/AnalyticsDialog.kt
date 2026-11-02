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

    val todaySummary = remember(selectedTab) { analyticsManager.getTodaySummary() }
    val weeklySummary = remember(selectedTab) { analyticsManager.getWeeklySummary() }
    val monthlySummary = remember(selectedTab) { analyticsManager.getMonthlySummary() }

    val activeSummary = when (selectedTab) {
        0 -> todaySummary
        1 -> weeklySummary
        else -> monthlySummary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Driver Activity & Performance",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time Range Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Today", "This Week", "This Month").forEachIndexed { idx, label ->
                        val isSelected = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Summary Performance Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${activeSummary.periodLabel.uppercase()} OVERVIEW",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${activeSummary.acceptedCount} Rides Accepted",
                                    color = Color(0xFF34D399),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "${activeSummary.totalOffers} total offers evaluated",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${activeSummary.acceptanceRate}%",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text("Acceptance Rate", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }

                        HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Completed Trips:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("${activeSummary.completedCount}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Filtered / Skipped Offers:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("${activeSummary.rejectedCount}", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Driving Distance:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(String.format(Locale.US, "%.1f km", activeSummary.distanceKm), color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 7-Day Activity Volume Chart (when This Week selected)
                if (selectedTab == 1 && activeSummary.dailyRecords.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "7-DAY ACCEPTED RIDES VOLUME",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val maxRides = maxOf(1, activeSummary.dailyRecords.maxOf { it.acceptedCount })

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                activeSummary.dailyRecords.forEach { day ->
                                    val heightRatio = (day.acceptedCount.toFloat() / maxRides.toFloat()).coerceIn(0.08f, 1f)
                                    val dayName = if (day.dateString.length >= 10) day.dateString.substring(8) else "--"

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "${day.acceptedCount}",
                                            color = if (day.acceptedCount > 0) Color(0xFF34D399) else Color(0xFF64748B),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .fillMaxHeight(heightRatio)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(if (day.acceptedCount > 0) Color(0xFF2563EB) else Color(0xFF334155))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            dayName,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}
