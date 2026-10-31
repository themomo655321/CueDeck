package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onOpenStorageInspector: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var fineModeEnabled by remember { mutableStateOf(settings.fineModeEnabled) }
    var fineTuneRatio by remember { mutableStateOf(settings.fineTuneRatio) }
    var volumeButtonsTriggerGo by remember { mutableStateOf(settings.volumeButtonsTriggerGo) }
    var keepScreenAwake by remember { mutableStateOf(settings.keepScreenAwake) }
    var hapticFeedbackEnabled by remember { mutableStateOf(settings.hapticFeedbackEnabled) }
    var isHighContrastStageTheme by remember { mutableStateOf(settings.isHighContrastStageTheme) }
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
                .verticalScroll(rememberScrollState())
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

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Physical Volume Keys as Cue Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hardware Volume Keys as Cue Selector",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Vol UP = Select Previous Cue, Vol DOWN = Select Next Cue.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = volumeButtonsTriggerGo,
                    onCheckedChange = { volumeButtonsTriggerGo = it }
                )
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 10.dp))

            // Section: Stage Display & Performance Protections
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep Screen Awake During Show",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Prevents screen sleep/timeout while CueDeck is active.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = keepScreenAwake,
                    onCheckedChange = { keepScreenAwake = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptic Vibration Feedback",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Tactile click on GO, Panic, soundboard pads, and buttons.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = { hapticFeedbackEnabled = it }
                )
            }

            // High-Contrast Red Night-Vision Stage Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Night-Vision Booth Theme",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Red & black high-contrast palette for dark lighting booths.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isHighContrastStageTheme,
                    onCheckedChange = { isHighContrastStageTheme = it }
                )
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 10.dp))

            // Section: Fader Fine-Tuning Gear Ratio
            Text(
                text = "Fader Fine-Tuning Gear Ratio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Defines the reduction gear ratio when Fine mode is engaged.",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2.0f to "2x Slower", 4.0f to "4x Slower (Default)", 8.0f to "8x Micro").forEach { (ratio, label) ->
                    FilterChip(
                        selected = fineTuneRatio == ratio,
                        onClick = { fineTuneRatio = ratio },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 10.dp))

            // Panic Stop Fade
            Text(
                text = "Panic All Stop Behavior",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0L to "Hard Cut (0ms)", 1000L to "1s Fade", 2000L to "2s Fade").forEach { (ms, label) ->
                    FilterChip(
                        selected = panicFadeMs == ms,
                        onClick = { panicFadeMs = ms },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 10.dp))

            // Storage & Diagnostics Tools Row
            Text(
                text = "Project Storage & System Tools",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenStorageInspector,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Storage Bloat Inspector", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diagnostics & Logs", fontSize = 11.sp)
                }
            }

            Divider(color = Color(0xFF2C2C2C), modifier = Modifier.padding(vertical = 10.dp))

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
                        text = "Installed: v1.1.1",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                OutlinedButton(
                    onClick = onCheckForUpdates,
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

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            fineModeEnabled = fineModeEnabled,
                            fineTuneRatio = fineTuneRatio,
                            volumeButtonsTriggerGo = volumeButtonsTriggerGo,
                            keepScreenAwake = keepScreenAwake,
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            isHighContrastStageTheme = isHighContrastStageTheme,
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
