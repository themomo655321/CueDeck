package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.*
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.DarkSurfaceVariant
import com.cuedeck.ui.theme.PanicRed

val PRESET_COLORS = listOf(
    0xFFE53935, // Red
    0xFF43A047, // Green
    0xFF1E88E5, // Blue
    0xFFFFB300, // Amber
    0xFF8E24AA, // Purple
    0xFF00ACC1, // Cyan
    0xFFF4511E  // Orange
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueEditorModal(
    cue: Cue,
    onSave: (Cue) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cueNumber by remember { mutableStateOf(cue.cueNumber) }
    var name by remember { mutableStateOf(cue.name) }
    var volume by remember { mutableStateOf(cue.volume) }
    var fadeInSec by remember { mutableStateOf(cue.fadeInMs / 1000f) }
    var fadeOutSec by remember { mutableStateOf(cue.fadeOutMs / 1000f) }
    var inPointSec by remember { mutableStateOf(cue.inPointMs / 1000f) }
    var outPointSec by remember {
        mutableStateOf((if (cue.outPointMs > 0) cue.outPointMs else cue.durationMs) / 1000f)
    }
    var triggerMode by remember { mutableStateOf(cue.triggerMode) }
    var followAction by remember { mutableStateOf(cue.followAction) }
    var selectedColor by remember { mutableStateOf(cue.colorHex) }
    var notes by remember { mutableStateOf(cue.notes) }

    val totalDurationSec = (cue.durationMs / 1000f).coerceAtLeast(1f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "EDIT CUE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cue Number & Name Row
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cueNumber,
                    onValueChange = { cueNumber = it },
                    label = { Text("Cue #") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.width(90.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Non-Destructive Audio Trimming Waveform
            Text("Non-Destructive Trimming", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(4.dp))
            WaveformTrimView(
                cueId = cue.id,
                inPointSec = inPointSec,
                outPointSec = outPointSec,
                totalDurationSec = totalDurationSec,
                onInPointChange = { inPointSec = it },
                onOutPointChange = { outPointSec = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Volume & Fades
            Text("Volume & Fades", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.LightGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gain: ${(volume * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(70.dp))
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..1.5f,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fade-in: %.1fs".format(fadeInSec), fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = fadeInSec,
                        onValueChange = { fadeInSec = it },
                        valueRange = 0f..10f
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fade-out: %.1fs".format(fadeOutSec), fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = fadeOutSec,
                        onValueChange = { fadeOutSec = it },
                        valueRange = 0f..10f
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trigger Mode
            Text("Trigger Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TriggerMode.PLAY_ONCE to "Once", TriggerMode.LOOP to "Loop", TriggerMode.RETRIGGER to "Retrigger").forEach { (mode, label) ->
                    FilterChip(
                        selected = triggerMode == mode,
                        onClick = { triggerMode = mode },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Follow Action (QLab Sequential)
            Text("Follow Action (Sequential)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(FollowAction.WAIT_GO to "Wait GO", FollowAction.AUTO_FOLLOW to "Auto Follow", FollowAction.AUTO_CONTINUE to "Auto Continue").forEach { (action, label) ->
                    FilterChip(
                        selected = followAction == action,
                        onClick = { followAction = action },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color Swatches
            Text("Color Tag", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESET_COLORS.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (selectedColor == hex) 3.dp else 1.dp,
                                color = if (selectedColor == hex) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = hex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Save & Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        onDelete(cue.id)
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Cue", tint = PanicRed)
                }

                Button(
                    onClick = {
                        val updated = cue.copy(
                            cueNumber = cueNumber.trim(),
                            name = name.trim(),
                            volume = volume,
                            fadeInMs = (fadeInSec * 1000).toLong(),
                            fadeOutMs = (fadeOutSec * 1000).toLong(),
                            inPointMs = (inPointSec * 1000).toLong(),
                            outPointMs = (outPointSec * 1000).toLong(),
                            triggerMode = triggerMode,
                            followAction = followAction,
                            colorHex = selectedColor,
                            notes = notes
                        )
                        onSave(updated)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp).padding(horizontal = 8.dp)
                ) {
                    Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
