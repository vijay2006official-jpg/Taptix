package com.example.taptix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.taptix.data.ScheduleConfig
import java.util.Calendar

@Composable
fun ScheduleDialog(
    initialConfig: ScheduleConfig,
    onSave: (ScheduleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialConfig.isEnabled) }
    var startHour by remember { mutableStateOf(initialConfig.startHour) }
    var endHour by remember { mutableStateOf(initialConfig.endHour) }
    var activeDays by remember { mutableStateOf(initialConfig.activeDays) }

    val daysList = listOf(
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
        Calendar.SUNDAY to "S"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⏰ Automatic Shift Schedule", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Enable Shift Schedule", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Only auto-accept within active hours", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                if (isEnabled) {
                    HorizontalDivider(color = Color(0xFF334155))

                    Text("Active Days of Week:", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        daysList.forEach { (calDay, label) ->
                            val isSel = calDay in activeDays
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF2563EB) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        activeDays = if (isSel) activeDays - calDay else activeDays + calDay
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, color = if (isSel) Color.White else Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Shift Start Hour: ${String.format("%02d:00", startHour)}", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )

                    Text("Shift End Hour: ${String.format("%02d:00", endHour)}", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = endHour.toFloat(),
                        onValueChange = { endHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ScheduleConfig(
                            isEnabled = isEnabled,
                            startHour = startHour,
                            startMinute = 0,
                            endHour = endHour,
                            endMinute = 0,
                            activeDays = activeDays
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Schedule", fontWeight = FontWeight.Bold, color = Color.White)
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
