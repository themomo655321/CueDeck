package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var faderSmoothingMs by remember { mutableStateOf(settings.faderSmoothingMs) }
    var panicFadeMs by remember { mutableStateOf(settings.panicFadeMs) }

    val smoothingOptions = listOf(
        0L to "Instant (0ms)",
        50L to "Fast (50ms)",
        150L to "Smooth (150ms)",
        350L to "Slow (350ms)",
        600L to "Cinematic (600ms)"
    )

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

            // Fader Smoothing Section
            Text(
                text = "Fader Smoothing Speed",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Controls volume interpolation to prevent zipper noise and audio clicks when riding the 4 live faders.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                smoothingOptions.forEach { (ms, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = faderSmoothingMs == ms,
                            onClick = { faderSmoothingMs = ms },
                            colors = RadioButtonDefaults.colors(selectedColor = GoGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (faderSmoothingMs == ms) FontWeight.Bold else FontWeight.Normal,
                            color = if (faderSmoothingMs == ms) Color.White else Color.LightGray
                        )
                    }
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

            Spacer(modifier = Modifier.height(28.dp))

            // Save Button
            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            faderSmoothingMs = faderSmoothingMs,
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
