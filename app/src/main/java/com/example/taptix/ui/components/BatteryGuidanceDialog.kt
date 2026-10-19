package com.example.taptix.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand-specific battery optimization guide for OEM Android flavors (MIUI/HyperOS, ColorOS, Funtouch, OneUI).
 */
@Composable
fun BatteryGuidanceDialog(
    onDismiss: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val manufacturer = Build.MANUFACTURER.uppercase()
    val brandGuidance = when {
        manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO") -> {
            "MIUI / HyperOS Guidance:\n" +
            "1. Settings > Apps > Manage Apps > Taptix > Enable 'Autostart'.\n" +
            "2. Under 'Battery Saver', choose 'No restrictions'.\n" +
            "3. Lock Taptix in recent apps (long press card in Recents and tap the Lock icon)."
        }
        manufacturer.contains("OPPO") || manufacturer.contains("REALME") -> {
            "ColorOS / Realme UI Guidance:\n" +
            "1. Settings > Battery > More battery settings > App Battery Management > Taptix.\n" +
            "2. Enable 'Allow background activity' and 'Allow auto-launch'.\n" +
            "3. Lock Taptix in Recent Apps."
        }
        manufacturer.contains("VIVO") || manufacturer.contains("IQOO") -> {
            "FuntouchOS Guidance:\n" +
            "1. Settings > Battery > Background power consumption management > Taptix.\n" +
            "2. Select 'High background power usage' (allows continuous auto-accept)."
        }
        manufacturer.contains("SAMSUNG") -> {
            "Samsung One UI Guidance:\n" +
            "1. Settings > Apps > Taptix > Battery > Select 'Unrestricted'.\n" +
            "2. Settings > Battery > Background usage limits > Ensure Taptix is NOT in 'Sleeping apps'."
        }
        manufacturer.contains("ONEPLUS") -> {
            "OxygenOS Guidance:\n" +
            "1. Settings > Battery > More settings > Optimize battery use > Taptix > Select 'Don't optimize'."
        }
        else -> {
            "Standard Android Guidance:\n" +
            "1. Settings > Apps > Taptix > Battery > Select 'Unrestricted'.\n" +
            "2. Disable battery saver while working on duty."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⚡ Prevent Service Kills ($manufacturer)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Aggressive device task killers can terminate Taptix while you are driving. Follow these steps to ensure continuous operation:",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        brandGuidance,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
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
