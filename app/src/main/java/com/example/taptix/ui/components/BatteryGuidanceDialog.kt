package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taptix.util.BatteryOptimizationHelper
import com.example.taptix.util.SettingClassification

/**
 * Brand-specific battery optimization & background reliability guide.
 *
 * Guarantees:
 * 1. Categorizes steps into [SettingClassification.OFFICIAL_ANDROID_SETTING] vs [SettingClassification.MANUFACTURER_SPECIFIC_GUIDANCE].
 * 2. Displays explicit educational disclaimer that exemption does not guarantee immunity from OEM task killers.
 * 3. Never triggers automated or spontaneous background setting changes.
 */
@Composable
fun BatteryGuidanceDialog(
    onDismiss: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val guidance = BatteryOptimizationHelper.getManufacturerGuidance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⚡ Background Reliability Guide",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Detected Device: ${guidance.manufacturerName} (${guidance.osFamily})",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp
                )

                Text(
                    text = "Follow these manufacturer-specific settings to prevent background kills while driving:",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Steps list
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    guidance.steps.forEach { step ->
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${step.stepNumber}. ${step.title}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    val badgeColor = if (step.classification == SettingClassification.OFFICIAL_ANDROID_SETTING) {
                                        Color(0xFF22C55E) // Green for standard Android
                                    } else {
                                        Color(0xFFF59E0B) // Amber for OEM custom UI
                                    }
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (step.classification == SettingClassification.OFFICIAL_ANDROID_SETTING) "AOSP Setting" else "OEM Specific",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = step.instruction,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Realistic educational disclaimer card
                Surface(
                    color = Color(0xFF451A03),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚠️ Realistic Background Policy",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp
                        )
                        Text(
                            text = guidance.disclaimer,
                            color = Color(0xFFFEF3C7),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenBatterySettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Open Battery Settings", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp)
    )
}
