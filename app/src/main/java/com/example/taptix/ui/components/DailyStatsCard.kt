package com.example.taptix.ui.components

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
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.LogStatus
import java.util.Calendar

/**
 * Driver Daily Shift Stats aggregated strictly from local Activity Log items.
 * Computes: seen requests, accepted count, acceptance rate %, skipped reasons, best hour.
 */
@Composable
fun DailyStatsCard(
    logs: List<ActivityLogItem>,
    onResetStats: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val totalSeen = logs.size
    val accepted = logs.count { it.status == LogStatus.ACCEPTED }
    val rejectedKeywords = logs.count { it.status == LogStatus.REJECTED_KEYWORD }
    val outsideZone = logs.count { it.status == LogStatus.OUTSIDE_ZONE }
    val failed = logs.count { it.status == LogStatus.FAILED || it.status == LogStatus.ERROR }

    val acceptRate = if (totalSeen > 0) (accepted * 100) / totalSeen else 0

    // Compute best hour for accepted rides
    val hourCounts = remember(logs) {
        val cal = Calendar.getInstance()
        val counts = mutableMapOf<Int, Int>()
        logs.filter { it.status == LogStatus.ACCEPTED }.forEach { item ->
            cal.timeInMillis = item.timestamp
            val h = cal.get(Calendar.HOUR_OF_DAY)
            counts[h] = (counts[h] ?: 0) + 1
        }
        counts
    }
    val bestHourEntry = hourCounts.maxByOrNull { it.value }
    val bestHourText = if (bestHourEntry != null && bestHourEntry.value > 0) {
        val h = bestHourEntry.key
        val ampm = if (h >= 12) "PM" else "AM"
        val displayH = if (h % 12 == 0) 12 else h % 12
        "$displayH:00 $ampm (${bestHourEntry.value} rides)"
    } else {
        "N/A"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A8A))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📊 SHIFT ANALYTICS & STATS", fontWeight = FontWeight.Black, color = Color.White, fontSize = 13.sp, letterSpacing = 1.sp)
                    Text("Live performance from local trip log", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (totalSeen > 0) {
                        TextButton(onClick = onResetStats) {
                            Text("Reset", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(if (isExpanded) "Collapse" else "Details", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick Metric Highlights
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(title = "Accepted", value = "$accepted", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                MetricTile(title = "Accept Rate", value = "$acceptRate%", color = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                MetricTile(title = "Requests", value = "$totalSeen", color = Color(0xFFF1F5F9), modifier = Modifier.weight(1f))
            }

            if (isExpanded) {
                HorizontalDivider(color = Color(0xFF1E293B))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow(label = "Peak Shift Hour:", value = bestHourText, color = Color(0xFFF59E0B))
                    StatRow(label = "Blocked by Keywords (e.g. CASH):", value = "$rejectedKeywords", color = Color(0xFFEF4444))
                    StatRow(label = "Skipped (Outside Map Zone):", value = "$outsideZone", color = Color(0xFFF59E0B))
                    StatRow(label = "Click Failures / Errors:", value = "$failed", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
            Text(title, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
