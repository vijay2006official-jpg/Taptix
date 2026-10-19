package com.example.taptix.ui.components

import androidx.compose.foundation.background
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
    onOpenBatteryGuide: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val allPassed = diagnostics.all { it.isGranted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPassed) Color(0xFF0F1E19) else Color(0xFF1E1E28)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (allPassed) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (allPassed) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Column {
                        Text(
                            if (allPassed) "✅ SYSTEM HEALTH: 100% READY" else "⚠️ PERMISSION DIAGNOSTICS",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            if (allPassed) "All background engines active" else "${diagnostics.count { !it.isGranted }} permissions require attention",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenBatteryGuide) {
                        Text("⚡ Battery", color = Color(0xFFEAB308), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(if (isExpanded) "Hide" else "Check", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diagnostics.forEach { diag ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(diag.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(diag.description, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }

                            if (diag.isGranted) {
                                Surface(
                                    color = Color(0xFF064E3B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "OK",
                                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = diag.onFix,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
