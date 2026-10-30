package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.AppSettings
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.GoGreen
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var streamLinePercent by remember { mutableStateOf(settings.faderStreamLinePercent) }
    var panicFadeMs by remember { mutableStateOf(settings.panicFadeMs) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SETTINGS & PREFERENCES",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Procreate StreamLine Fader Smoothing
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Fader Smoothing (StreamLine)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$streamLinePercent%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = GoGreen
                )
            }
            Text(
                text = "Fader cap glides behind your finger with smooth inertia (Procreate brush style), absorbing hand shake for ultra-fine volume tuning.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Slider(
                value = streamLinePercent.toFloat(),
                onValueChange = { streamLinePercent = it.roundToInt() },
                valueRange = 0f..90f,
                colors = SliderDefaults.colors(
                    thumbColor = GoGreen,
                    activeTrackColor = GoGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0 to "Direct (0%)",
                    35 to "Light (35%)",
                    65 to "Medium (65%)",
                    85 to "Heavy (85%)"
                ).forEach { (pct, label) ->
                    FilterChip(
                        selected = streamLinePercent == pct,
                        onClick = { streamLinePercent = pct },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 12.dp))

            // Panic Stop Behavior
            Text(
                text = "Panic All Stop Behavior",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = panicFadeMs == 0L,
                    onClick = { panicFadeMs = 0L },
                    label = { Text("Hard Cut (0ms)") }
                )
                FilterChip(
                    selected = panicFadeMs == 1000L,
                    onClick = { panicFadeMs = 1000L },
                    label = { Text("1s Panic Fade") }
                )
                FilterChip(
                    selected = panicFadeMs == 2000L,
                    onClick = { panicFadeMs = 2000L },
                    label = { Text("2s Panic Fade") }
                )
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 12.dp))

            // In-App Auto Update Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "App Updates",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Installed: v1.0.1",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                OutlinedButton(
                    onClick = {
                        onCheckForUpdates()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Check for Updates",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check for Update", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            faderStreamLinePercent = streamLinePercent,
                            panicFadeMs = panicFadeMs
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "SAVE PREFERENCES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
