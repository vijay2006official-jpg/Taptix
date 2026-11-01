package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.data.ActivityLogManager
import com.example.taptix.model.ActivityLogItem
import com.example.taptix.model.LogStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookingHistoryDialog(
    logManager: ActivityLogManager,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") } // "All", "Accepted", "Filtered"
    var refreshKey by remember { mutableStateOf(0) }

    val allLogs = remember(refreshKey) { logManager.getLogs() }

    val filteredLogs = remember(allLogs, searchQuery, selectedPlatform, selectedStatus) {
        allLogs.filter { item ->
            // Search filter
            val matchesSearch = searchQuery.isBlank() ||
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.destination.contains(searchQuery, ignoreCase = true) ||
                    item.details.contains(searchQuery, ignoreCase = true)

            // Platform filter
            val matchesPlatform = selectedPlatform == "All" ||
                    item.appName.contains(selectedPlatform, ignoreCase = true)

            // Status filter
            val matchesStatus = when (selectedStatus) {
                "Accepted" -> item.status == LogStatus.ACCEPTED
                "Filtered" -> item.status != LogStatus.ACCEPTED && item.status != LogStatus.SYSTEM_EVENT
                else -> true
            }

            matchesSearch && matchesPlatform && matchesStatus
        }
    }

    val timeFormatter = remember { SimpleDateFormat("h:mm a • MMM d", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "📋 Booking History",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    Text(
                        "${filteredLogs.size} records",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (allLogs.isNotEmpty()) {
                        TextButton(onClick = {
                            logManager.clearLogs()
                            refreshKey++
                        }) {
                            Text("Clear", color = Color(0xFFF87171), fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("✕ Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search platform or area...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Platform Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All", "Uber", "Ola", "Rapido", "inDrive", "Porter", "Namma Yatri").forEach { p ->
                        HistoryFilterChip(
                            label = p,
                            isSelected = selectedPlatform == p,
                            onClick = { selectedPlatform = p }
                        )
                    }
                }

                // Status Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilterChip(
                        label = "All Statuses",
                        isSelected = selectedStatus == "All",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedStatus = "All" }
                    )
                    HistoryFilterChip(
                        label = "Accepted Only",
                        isSelected = selectedStatus == "Accepted",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedStatus = "Accepted" }
                    )
                    HistoryFilterChip(
                        label = "Filtered Only",
                        isSelected = selectedStatus == "Filtered",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedStatus = "Filtered" }
                    )
                }

                // Booking List
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No booking records match your criteria.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { item ->
                            HistoryItemCard(item = item, timeFormatter = timeFormatter)
                        }
                    }
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
private fun HistoryFilterChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
            .border(
                1.dp,
                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
private fun HistoryItemCard(
    item: ActivityLogItem,
    timeFormatter: SimpleDateFormat
) {
    val isAccepted = item.status == LogStatus.ACCEPTED
    val timeStr = timeFormatter.format(Date(item.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAccepted) Color(0xFF059669) else Color(0xFF334155)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.appName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isAccepted) Color(0xFF065F46) else Color(0xFF451A03)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isAccepted) "ACCEPTED" else "FILTERED",
                        color = if (isAccepted) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Fare and Distance Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.fare != null && item.fare > 0) {
                        Text(
                            String.format(Locale.US, "₹ %.2f", item.fare),
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    if (item.distanceKm != null && item.distanceKm > 0) {
                        Text(
                            String.format(Locale.US, "• %.1f km", item.distanceKm),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(timeStr, color = Color(0xFF64748B), fontSize = 10.sp)
            }

            if (item.destination.isNotBlank()) {
                Text(
                    "📍 ${item.destination}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            if (item.details.isNotBlank()) {
                Text(
                    item.details,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
        }
    }
}
