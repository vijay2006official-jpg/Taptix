package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

data class DiagnosticItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onFix: () -> Unit
)

@Composable
fun SelfCheckCard(
    diagnostics: List<DiagnosticItem>,
    onOpenBatteryGuide: () -> Unit,
    onCardClick: (() -> Unit)? = null
) {
    val allPassed = diagnostics.all { it.isGranted }
    // ALWAYS expanded by default if any permission is missing
    var isExpanded by remember(allPassed) { mutableStateOf(!allPassed) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPassed) Color(0xFF0F1E19) else Color(0xFF1E1E28)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (allPassed) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFEF4444)
        )
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (allPassed) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Column {
                        Text(
                            if (allPassed) "✅ SYSTEM HEALTH: 100% READY" else "⚠️ PERMISSION REQUIRED",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            if (allPassed) "All background engines active" else "${diagnostics.count { !it.isGranted }} permissions must be granted",
                            color = if (allPassed) Color(0xFF34D399) else Color(0xFFFF8888),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(
                            if (isExpanded) "Hide" else "Show",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diagnostics.forEach { diag ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !diag.isGranted) { diag.onFix() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    diag.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    diag.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            if (diag.isGranted) {
                                Surface(
                                    color = Color(0xFF064E3B),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "GRANTED ✅",
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = diag.onFix,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("GRANT ➔", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.3f))

                    // OEM Battery Guidance Row at bottom of card
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBatteryGuide() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⚡", fontSize = 14.sp)
                            Column {
                                Text("OEM Battery Optimization Guide", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Prevent Vivo / Xiaomi from killing background auto-accept", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                        Text("View ➔", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
