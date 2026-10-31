package com.cuedeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.ActivePlayback
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 4-Channel Live Audio Fader Console with Triple Fine-Tuning:
 * 1. [FINE] Mode Gear-Ratio Toggle (Option A)
 * 2. [+] and [−] Nudge Stepper Buttons (Option B)
 * 3. Horizontal-to-Fine Dynamic Scrubbing (Option C)
 * 4. Double-Tap Reset to 100% (Unity gain)
 */
@Composable
fun FaderBank(
    activePlaybacks: List<ActivePlayback>,
    faderVolumes: List<Float>,
    fineModeEnabled: Boolean = false,
    onFineModeToggle: (Boolean) -> Unit = {},
    onFaderChange: (channelIndex: Int, volume: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var localFineMode by remember(fineModeEnabled) { mutableStateOf(fineModeEnabled) }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LIVE MIXER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Option A: [FINE] Mode Toggle Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (localFineMode) Color(0xFF00E5FF) else Color(0xFF282828))
                            .clickable {
                                localFineMode = !localFineMode
                                onFineModeToggle(localFineMode)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (localFineMode) "FINE 4X ON" else "FINE OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (localFineMode) Color.Black else Color.LightGray
                        )
                    }
                }

                Text(
                    text = "ACTIVE: ${activePlaybacks.size.coerceAtMost(4)}/4",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activePlaybacks.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFF777777)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4 Channel Strips side by side
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                for (ch in 0..3) {
                    val activeTrack = activePlaybacks.getOrNull(ch)
                    val volume = faderVolumes.getOrNull(ch) ?: 1.0f

                    FaderChannelStrip(
                        channelNumber = ch + 1,
                        activePlayback = activeTrack,
                        volume = volume,
                        isFineMode = localFineMode,
                        onVolumeChange = { onFaderChange(ch, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FaderChannelStrip(
    channelNumber: Int,
    activePlayback: ActivePlayback?,
    volume: Float,
    isFineMode: Boolean,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAssigned = activePlayback != null
    val accentColor = if (isAssigned) Color(activePlayback!!.colorHex) else Color(0xFF444444)

    Surface(
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .border(
                width = 1.dp,
                color = if (isAssigned) accentColor.copy(alpha = 0.5f) else Color(0xFF262626),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            // Channel Header Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isAssigned) Color(0xFF4CAF50) else Color(0xFF555555))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "CH $channelNumber",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Track name
            Text(
                text = activePlayback?.cueName ?: "Standby",
                fontSize = 9.sp,
                fontWeight = if (isAssigned) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isAssigned) Color.White else Color(0xFF666666),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )

            // Option B: Nudge Steppers & Percentage Readout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 1.dp)
            ) {
                // [ - ] Nudge 1% Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable {
                            val newVol = ((volume - 0.01f) * 100).roundToInt() / 100f
                            onVolumeChange(newVol.coerceIn(0f, 1.25f))
                        }
                ) {
                    Text("−", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Volume readout (% or dB) - tap or double-tap to reset to 100%
                Text(
                    text = if (volume <= 0.001f) "-inf" else "${(volume * 100).roundToInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (volume > 1.0f) Color(0xFFFFB300) else if (isAssigned) Color(0xFF4CAF50) else Color.LightGray,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onVolumeChange(1.0f) },
                                onDoubleTap = { onVolumeChange(1.0f) }
                            )
                        }
                )

                // [ + ] Nudge 1% Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable {
                            val newVol = ((volume + 0.01f) * 100).roundToInt() / 100f
                            onVolumeChange(newVol.coerceIn(0f, 1.25f))
                        }
                ) {
                    Text("+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Vertical Tactile Fader Track
            TactileVerticalFader(
                volume = volume,
                isFineMode = isFineMode,
                isAssigned = isAssigned,
                accentColor = accentColor,
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Quick Mute / Reset Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (volume <= 0.001f) PanicRed else Color(0xFF282828))
                    .clickable {
                        if (volume > 0f) onVolumeChange(0f) else onVolumeChange(1.0f)
                    }
            ) {
                Text(
                    text = if (volume <= 0.001f) "MUTED" else "MUTE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = if (volume <= 0.001f) Color.White else Color.Gray
                )
            }
        }
    }
}

/**
 * Custom tactile vertical fader track.
 * Supports:
 * - Option A: [FINE] Mode Gear-Ratio Toggle (4x slower).
 * - Option C: Horizontal-to-Fine scrubbing with visual indicator badge.
 * - Double-Tap Reset to 100% (Unity 0dB).
 * - Immediate physical tracking up & down without sticking.
 */
@Composable
private fun TactileVerticalFader(
    volume: Float,
    isFineMode: Boolean,
    isAssigned: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val faderCapHeight = 24.dp
    val faderCapHeightPx = with(density) { faderCapHeight.toPx() }

    var currentDisplayVol by remember { mutableFloatStateOf(volume) }
    var isScrubbingFine by remember { mutableStateOf(false) }

    LaunchedEffect(volume) {
        currentDisplayVol = volume
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val totalHeight = maxHeight
        val travelHeight = totalHeight - faderCapHeight

        val clampedVol = currentDisplayVol.coerceIn(0f, 1.25f)
        val normalized = clampedVol / 1.25f
        val capYOffset = travelHeight * (1f - normalized)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(faderCapHeightPx, isFineMode) {
                    var lastTapTime = 0L
                    var lastTapY = 0f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = down.uptimeMillis
                        val isDoubleTap = (downTime - lastTapTime) < 350L && abs(down.position.y - lastTapY) < 100f
                        lastTapTime = downTime
                        lastTapY = down.position.y

                        if (isDoubleTap) {
                            down.consume()
                            // Double tap resets instantly to 100% (1.0f)
                            currentDisplayVol = 1.0f
                            onVolumeChange(1.0f)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed) break
                                pointer.consume()
                            }
                            lastTapTime = 0L
                            return@awaitEachGesture
                        }

                        // Start continuous tracking
                        val startX = down.position.x
                        var lastY = down.position.y
                        val travelPx = (size.height.toFloat() - faderCapHeightPx).coerceAtLeast(1f)

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                isScrubbingFine = false
                                break
                            }
                            pointer.consume()

                            val currentY = pointer.position.y
                            val currentX = pointer.position.x
                            val deltaY = currentY - lastY
                            lastY = currentY

                            // Option C: Horizontal distance slows down vertical speed
                            val horizontalDistance = abs(currentX - startX)
                            val horizontalDivisor = if (horizontalDistance > 20f) {
                                (1.0f + (horizontalDistance / 15f)).coerceIn(1f, 8f)
                            } else {
                                1.0f
                            }

                            // Option A: [FINE] mode forces 4x precision gear ratio
                            val divisor = if (isFineMode) 4.0f else horizontalDivisor
                            isScrubbingFine = divisor >= 2.0f

                            // Upward drag (negative deltaY) increases volume
                            val deltaVol = -(deltaY / travelPx) * 1.25f / divisor
                            currentDisplayVol = (currentDisplayVol + deltaVol).coerceIn(0f, 1.25f)
                            onVolumeChange((currentDisplayVol * 1000).roundToInt() / 1000f)
                        }
                        isScrubbingFine = false
                    }
                }
        ) {
            // Track Center Groove
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF111111))
                    .border(0.5.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
            )

            // Unity 0dB Marker Line (at 1.0 volume = 100%)
            val unityY = travelHeight * (1f - (1.0f / 1.25f)) + (faderCapHeight / 2)
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .offset(y = unityY - 1.5.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (abs(clampedVol - 1.0f) < 0.02f) Color(0xFF4CAF50) else Color(0xFF666666))
            )

            // Floating "FINE" Badge when Option C scrub is active
            AnimatedVisibility(
                visible = isScrubbingFine,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF00E5FF))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("FINE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }

            // Physical Tactile Fader Cap
            Box(
                modifier = Modifier
                    .offset(y = capYOffset)
                    .align(Alignment.TopCenter)
                    .width(36.dp)
                    .height(faderCapHeight)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4A4A4A),
                                Color(0xFF2C2C2C),
                                Color(0xFF222222)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAssigned) accentColor else Color(0xFF555555),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val isUnity = abs(clampedVol - 1.0f) < 0.02f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.5.dp)
                        .background(if (isUnity) Color(0xFF4CAF50) else if (isAssigned) accentColor else Color.White)
                )
            }
        }
    }
}
