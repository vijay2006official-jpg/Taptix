package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Prominent Accessibility Disclosure conforming strictly to Google Play policy.
 * Explains clearly why the permission is needed, what is read, and guarantees privacy.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🛡️ Accessibility Service Disclosure",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Taptix uses the Android AccessibilityService API solely to provide automated ride-acceptance for drivers.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• WHAT IT READS:", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 12.sp)
                        Text(
                            "Only incoming order button labels (e.g. 'Accept', 'Confirm') inside supported driver apps (Uber, Ola, Rapido, inDrive, Porter, Namma Yatri).",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(Modifier.height(4.dp))
                        Text("• WHAT IT DOES:", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 12.sp)
                        Text(
                            "Simulates a tap on the accept button when your configured platform rules and map zones match.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(Modifier.height(4.dp))
                        Text("• PRIVACY GUARANTEE:", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), fontSize = 12.sp)
                        Text(
                            "Taptix does NOT read personal messages, passwords, credit cards, or passenger details. No screen data is recorded, saved, or uploaded off your device.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    "By tapping 'Agree & Continue', you will be directed to Android System Settings to enable Taptix.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Agree & Continue", fontWeight = FontWeight.Bold, color = Color.White)
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
