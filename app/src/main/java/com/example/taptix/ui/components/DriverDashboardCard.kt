package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.model.DailyAnalyticsRecord
import java.util.Locale

@Composable
fun DriverDashboardCard(
    isOnline: Boolean,
    isAutomationActive: Boolean,
    activeZoneName: String?,
    todayRecord: DailyAnalyticsRecord,
    onOpenMap: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Top Status Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Online/Offline Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Text(
                        if (isOnline) "ONLINE • READY" else "OFFLINE",
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Automation Mode Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isAutomationActive) Color(0x332563EB) else Color(0xFF1E293B))
                        .border(
                            1.dp,
                            if (isAutomationActive) Color(0xFF38BDF8) else Color(0xFF334155),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (isAutomationActive) "⚡ Auto-Accept Active" else "⏸ Idle / Paused",
                        color = if (isAutomationActive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Today's Earnings Hero Tile ──
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAnalytics() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "TODAY'S ESTIMATED EARNINGS",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            String.format(Locale.US, "₹ %.2f", todayRecord.earnings),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (todayRecord.totalExpenses > 0) {
                            Text(
                                String.format(Locale.US, "Net Profit: ₹ %.2f (Fuel/Exp: ₹ %.2f)", todayRecord.netProfit, todayRecord.totalExpenses),
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("View Trends ➔", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (!activeZoneName.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("📍 $activeZoneName", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }

            // ── 4-Grid Key Metrics ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardMetric(
                    label = "Accepted",
                    value = "${todayRecord.acceptedCount}",
                    color = Color(0xFF34D399),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = "Filtered",
                    value = "${todayRecord.rejectedCount}",
                    color = Color(0xFFF87171),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = "Distance",
                    value = if (todayRecord.distanceKm > 0) String.format(Locale.US, "%.1f km", todayRecord.distanceKm) else "0 km",
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = "Accept Rate",
                    value = "${todayRecord.acceptanceRate}%",
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Quick Access Action Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardQuickBtn(
                    label = "🎯 Work Map",
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f)
                )
                DashboardQuickBtn(
                    label = "⚙️ Filters",
                    onClick = onOpenRules,
                    modifier = Modifier.weight(1f)
                )
                DashboardQuickBtn(
                    label = "📊 Analytics",
                    onClick = onOpenAnalytics,
                    modifier = Modifier.weight(1f)
                )
                DashboardQuickBtn(
                    label = "📋 History",
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DashboardMetric(
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
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 15.sp, color = color)
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DashboardQuickBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFFF1F5F9),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}
