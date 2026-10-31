package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.ViewMode
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed
import com.cuedeck.ui.theme.WarningAmber

@Composable
fun TopSafetyBar(
    title: String,
    viewMode: ViewMode,
    isPerformanceLocked: Boolean,
    masterVolume: Float,
    onViewModeChange: (ViewMode) -> Unit,
    onToggleLock: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPanicStop: () -> Unit,
    onSaveShow: () -> Unit = {},
    onOpenShowManager: () -> Unit = {},
    onOpenLayoutManager: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showVolumePopup by remember { mutableStateOf(false) }

    Surface(
        color = DarkSurface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show Title & Lock indicator & Save button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Clickable Show Title / Show Switcher
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenShowManager() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Show Files",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Performance Lock button
                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPerformanceLocked) WarningAmber.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (isPerformanceLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock Performance",
                            tint = if (isPerformanceLocked) WarningAmber else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Save Show Button
                    IconButton(
                        onClick = onSaveShow,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Show",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Quick Controls: Layout, Settings, Volume & Panic Stop
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Workspace Layout Manager Button
                    IconButton(
                        onClick = onOpenLayoutManager,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = "Workspace Layout Manager",
                            tint = Color(0xFF00E5FF)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Mixer & Show Settings",
                            tint = Color.LightGray
                        )
                    }

                    // Volume trigger with double-tap to reset to 100%
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { showVolumePopup = !showVolumePopup },
                                    onDoubleTap = { onVolumeChange(1.0f) }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = if (masterVolume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Master Volume (Double-tap to 100%)",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // PANIC STOP Button
                    Button(
                        onClick = onPanicStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PanicRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Panic Stop",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PANIC",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Expanded Volume slider popup if opened
            if (showVolumePopup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF262626))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onVolumeChange(1.0f) },
                                    onDoubleTap = { onVolumeChange(1.0f) }
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Master: ${(masterVolume * 100).toInt()}% [100%]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if ((masterVolume * 100).toInt() == 100) Color(0xFF00E5FF) else Color.LightGray
                        )
                    }
                    Slider(
                        value = masterVolume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1.5f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
