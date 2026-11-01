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
import com.cuedeck.util.formatDecibels
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 4-Channel Live Audio Fader Console with Stage Precision:
 * 1. [FINE] Multi-Ratio Gear Cycle: OFF -> 4X -> 8X -> 16X -> OFF
 * 2. [+] and [−] Nudge Stepper Buttons (1% micro steps)
 * 3. Double-Tap Reset to 100% (0.0 dB unity gain)
 * 4. Zero-latency instant audio response on physical drag
 */
@Composable
fun FaderBank(
    mixerSlots: List<ActivePlayback?>,
    queueCount: Int = 0,
    faderVolumes: List<Float>,
    fineMultiplier: Int = 0, // 0 = OFF, 4 = 4X, 8 = 8X, 16 = 16X
    onCycleFineMultiplier: () -> Unit = {},
    onFaderChange: (channelIndex: Int, volume: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)
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

                    // Multi-Gear [FINE] Mode Cycle Button: OFF -> 4X -> 8X -> 16X -> OFF
                    val fineBgColor = when (fineMultiplier) {
                        4 -> Color(0xFF00E5FF) // Cyan
                        8 -> Color(0xFFFFB300) // Amber
                        16 -> Color(0xFFE040FB) // Magenta
                        else -> Color(0xFF282828)
                    }
                    val fineTextColor = when (fineMultiplier) {
                        4, 8 -> Color.Black
                        16 -> Color.White
                        else -> Color.LightGray
                    }
                    val fineText = when (fineMultiplier) {
                        4 -> "FINE 4X"
                        8 -> "FINE 8X"
                        16 -> "FINE 16X"
                        else -> "FINE OFF"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(fineBgColor)
                            .clickable { onCycleFineMultiplier() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fineText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = fineTextColor
                        )
                    }
                }

                val activeCount = mixerSlots.count { it != null }
                val activeText = if (queueCount > 0) "ACTIVE: $activeCount/4 (+$queueCount QUEUED)" else "ACTIVE: $activeCount/4"
                Text(
                    text = activeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeCount > 0) Color(0xFF4CAF50) else Color(0xFF777777)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4 Channel Strips side by side
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (ch in 0..3) {
                    val activeTrack = mixerSlots.getOrNull(ch)
                    val volume = faderVolumes.getOrNull(ch) ?: 1.0f

                    FaderChannelStrip(
                        channelNumber = ch + 1,
                        activePlayback = activeTrack,
                        volume = volume,
                        fineMultiplier = fineMultiplier,
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
    fineMultiplier: Int,
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

            // Nudge Steppers & Percentage Readout
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
                channelNumber = channelNumber,
                trackName = activePlayback?.cueName ?: "CH $channelNumber",
                volume = volume,
                fineMultiplier = fineMultiplier,
                isAssigned = isAssigned,
                accentColor = accentColor,
                onVolumeChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Control: Full-Width Quick MUTE Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (volume <= 0.001f) PanicRed else Color(0xFF262626))
                    .border(
                        width = 1.dp,
                        color = if (volume <= 0.001f) PanicRed else Color(0xFF333333),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        if (volume > 0f) onVolumeChange(0f) else onVolumeChange(1.0f)
                    }
            ) {
                Text(
                    text = if (volume <= 0.001f) "MUTED" else "MUTE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (volume <= 0.001f) Color.White else Color.Gray,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Virtual Infinite Canvas + Floating Value HUD Tactile Fader.
 * - Virtual Travel: Scales precision based on fineMultiplier (4x, 8x, 16x).
 * - Elastic Chasing: When fineMultiplier > 0, tracks glide smoothly to finger position at gear-ratio speed.
 * - Floating HUD: High-contrast bubble floats above the thumb showing % and dB.
 * - Double-Tap: Resets to 100% (0.0 dB).
 */
@Composable
private fun TactileVerticalFader(
    channelNumber: Int,
    trackName: String,
    volume: Float,
    fineMultiplier: Int,
    isAssigned: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val faderCapHeight = 24.dp
    val faderCapHeightPx = with(density) { faderCapHeight.toPx() }

    var currentDisplayVol by remember { mutableFloatStateOf(volume) }
    var isDragging by remember { mutableStateOf(false) }
    var targetVol by remember { mutableStateOf<Float?>(null) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(volume) {
        if (!isDragging && !isHolding) {
            currentDisplayVol = volume
        }
    }

    // Elastic Throttle Chasing Fader Loop in FINE mode
    LaunchedEffect(isHolding, fineMultiplier, targetVol) {
        if (!isHolding || fineMultiplier <= 0) return@LaunchedEffect
        while (isHolding && fineMultiplier > 0) {
            delay(16)
            val dt = 0.016f

            val currentTarget = targetVol ?: break
            val diff = currentTarget - currentDisplayVol
            if (abs(diff) > 0.001f) {
                val normalizedDist = (abs(diff) / 1.25f).coerceIn(0f, 1f)
                val speedMultiplier = 4f / fineMultiplier.toFloat()
                val speed = (0.05f + (normalizedDist * 0.23f)) * speedMultiplier
                val step = (speed * dt).coerceAtMost(abs(diff))

                val newVol = (currentDisplayVol + sign(diff) * step).coerceIn(0f, 1.25f)
                currentDisplayVol = newVol
                onVolumeChange((newVol * 1000).roundToInt() / 1000f)
            }
        }
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

        // Virtual travel distance (scales with fine gear ratio)
        val virtualTravelPx = with(density) {
            val base = maxOf(260.dp.toPx(), travelHeight.toPx())
            if (fineMultiplier > 0) base * fineMultiplier.toFloat() else base
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(faderCapHeightPx, fineMultiplier, virtualTravelPx) {
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

                        // Start continuous tracking / pursuit
                        isDragging = true
                        isHolding = true

                        val travelHeightPx = travelHeight.toPx()
                        if (fineMultiplier > 0) {
                            val touchY = (down.position.y - faderCapHeightPx / 2f).coerceIn(0f, travelHeightPx)
                            targetVol = ((1f - (touchY / travelHeightPx)) * 1.25f).coerceIn(0f, 1.25f)
                        }

                        var lastY = down.position.y

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                isDragging = false
                                isHolding = false
                                targetVol = null
                                break
                            }
                            pointer.consume()

                            if (fineMultiplier > 0) {
                                // Finger position dynamically steers pursuit target
                                val touchY = (pointer.position.y - faderCapHeightPx / 2f).coerceIn(0f, travelHeightPx)
                                targetVol = ((1f - (touchY / travelHeightPx)) * 1.25f).coerceIn(0f, 1.25f)
                            } else {
                                val currentY = pointer.position.y
                                val deltaY = currentY - lastY
                                lastY = currentY

                                // Natural logarithmic audio taper sensitivity:
                                val audioTaperFactor = when {
                                    clampedVol > 0.7f -> 0.85f
                                    clampedVol < 0.2f -> 1.15f
                                    else -> 1.0f
                                }

                                // Upward drag (negative deltaY) increases volume, downward decreases
                                val deltaVol = -(deltaY / virtualTravelPx) * 1.25f * audioTaperFactor
                                currentDisplayVol = (currentDisplayVol + deltaVol).coerceIn(0f, 1.25f)
                                onVolumeChange((currentDisplayVol * 1000).roundToInt() / 1000f)
                            }
                        }
                        isDragging = false
                        isHolding = false
                        targetVol = null
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
                    .background(if (abs(clampedVol - 1.0f) < 0.02f) Color(0xFF00E676) else Color(0xFF666666))
            )

            // Target pursuit indicator dot when in FINE mode
            if (fineMultiplier > 0 && isHolding && targetVol != null) {
                val targetNorm = (targetVol!! / 1.25f).coerceIn(0f, 1f)
                val targetYOffset = travelHeight * (1f - targetNorm) + (faderCapHeight / 2) - 4.dp
                val dotColor = when (fineMultiplier) {
                    4 -> Color(0xFF00E5FF)
                    8 -> Color(0xFFFFB300)
                    16 -> Color(0xFFE040FB)
                    else -> Color(0xFF00E5FF)
                }
                Box(
                    modifier = Modifier
                        .offset(y = targetYOffset)
                        .align(Alignment.TopCenter)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, Color.White, CircleShape)
                )
            }

            // Floating Value HUD Bubble
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .offset(y = (capYOffset - 36.dp).coerceAtLeast(0.dp))
                    .align(Alignment.TopCenter)
            ) {
                val isUnity = abs(clampedVol - 1.0f) < 0.02f
                val dBVal = formatDecibels(clampedVol)
                Surface(
                    color = Color(0xFF141414),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isUnity) Color(0xFF00E676) else Color(0xFF00E5FF)
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (clampedVol <= 0.001f) "-inf" else "${(clampedVol * 100).roundToInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (clampedVol <= 0.001f) PanicRed else Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isUnity) "0.0dB" else dBVal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnity) Color(0xFF00E676) else Color.LightGray
                        )
                    }
                }
            }

            // Tactile Fader Cap
            val isUnity = abs(clampedVol - 1.0f) < 0.02f
            Surface(
                color = if (clampedVol <= 0.001f) Color(0xFF2E1A1A) else Color(0xFF2E2E2E),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isUnity) Color(0xFF00E676) else if (isAssigned) accentColor else Color(0xFF555555)
                ),
                shadowElevation = if (isDragging) 8.dp else 4.dp,
                modifier = Modifier
                    .width(38.dp)
                    .height(faderCapHeight)
                    .offset(y = capYOffset)
                    .align(Alignment.TopCenter)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (clampedVol <= 0.001f) PanicRed
                                else if (isUnity) Color(0xFF00E676)
                                else Color.White
                            )
                    )
                }
            }
        }
    }
}
