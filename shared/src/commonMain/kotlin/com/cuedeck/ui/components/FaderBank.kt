package com.cuedeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.zIndex
import com.cuedeck.model.ActivePlayback
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 4-Channel Live Audio Fader Console with Triple Fine-Tuning:
 * 1. [FINE] Mode Gear-Ratio Toggle (Option A)
 * 2. [+] and [−] Nudge Stepper Buttons (Option B)
 * 3. Horizontal-to-Fine Dynamic Scrubbing (Option C)
 * 4. Double-Tap Reset to 100% (Unity gain)
 */
@Composable
fun FaderBank(
    mixerSlots: List<ActivePlayback?>,
    queueCount: Int = 0,
    faderVolumes: List<Float>,
    faderTailEnabled: List<Boolean> = listOf(false, false, false, false),
    fineModeEnabled: Boolean = false,
    onFineModeToggle: (Boolean) -> Unit = {},
    onToggleFaderTail: (Int) -> Unit = {},
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
                        isFineMode = localFineMode,
                        isTailEnabled = faderTailEnabled.getOrElse(ch) { false },
                        onToggleTail = { onToggleFaderTail(ch) },
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
    isTailEnabled: Boolean,
    onToggleTail: () -> Unit,
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
                channelNumber = channelNumber,
                trackName = activePlayback?.cueName ?: "CH $channelNumber",
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

            // Bottom Controls: Quick Mute & Reverb Tail Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                // Quick Mute / Reset Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
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

                // Reverb Fadeout Tail Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isTailEnabled) Color(0xFF7B1FA2) else Color(0xFF222222))
                        .border(
                            width = 1.dp,
                            color = if (isTailEnabled) Color(0xFFCE93D8) else Color(0xFF333333),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onToggleTail() }
                ) {
                    Text(
                        text = if (isTailEnabled) "✦ TAIL" else "TAIL",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isTailEnabled) Color.White else Color(0xFF777777)
                    )
                }
            }
        }
    }
}

/**
 * Option 1: Virtual Infinite Canvas + Floating Value HUD Tactile Fader.
 * - Virtual Travel: Expands 65dp physical travel to a 260dp virtual canvas (4x precision).
 * - Infinite Tracking: Finger can slide up past the fader box without stopping.
 * - Floating HUD: High-contrast bubble floats above the thumb showing % and dB.
 * - Natural Audio Taper: Silky 0.1dB control near unity 0dB.
 * - Double-Tap: Resets to 100% (0.0 dB).
 */
@Composable
private fun TactileVerticalFader(
    channelNumber: Int,
    trackName: String,
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
    var isDragging by remember { mutableStateOf(false) }
    var targetVol by remember { mutableStateOf<Float?>(null) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(volume) {
        currentDisplayVol = volume
    }

    // Option A: Elastic Throttle Chasing Fader Loop in FINE mode
    LaunchedEffect(isHolding, isFineMode, targetVol) {
        if (!isHolding || !isFineMode) return@LaunchedEffect
        var lastTimeNanos = withFrameNanos { it }
        while (isHolding && isFineMode) {
            val nowNanos = withFrameNanos { it }
            val dt = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastTimeNanos = nowNanos

            val currentTarget = targetVol ?: break
            val diff = currentTarget - currentDisplayVol
            if (abs(diff) > 0.002f) {
                // Elastic Throttle Speed:
                // Close to target (<= 0.10f diff): ~0.05f/sec (0.05dB micro steps)
                // Far from target (>= 0.70f diff): up to ~0.28f/sec (swift transition)
                val normalizedDist = (abs(diff) / 1.25f).coerceIn(0f, 1f)
                val speed = 0.05f + (normalizedDist * 0.23f)
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

        // Option 1: Virtual travel distance (260dp base or full physical height, 4x in FINE mode)
        val virtualTravelPx = with(density) {
            val base = maxOf(260.dp.toPx(), travelHeight.toPx())
            if (isFineMode) base * 4f else base
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(faderCapHeightPx, isFineMode, virtualTravelPx) {
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
                            // Double tap resets instantly to 100% (1.0f / 0.0dB)
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
                        if (isFineMode) {
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

                            if (isFineMode) {
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
            if (isFineMode && isHolding && targetVol != null) {
                val targetNorm = (targetVol!! / 1.25f).coerceIn(0f, 1f)
                val targetYOffset = travelHeight * (1f - targetNorm) + (faderCapHeight / 2) - 4.dp
                Box(
                    modifier = Modifier
                        .offset(y = targetYOffset)
                        .align(Alignment.TopCenter)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                        .border(1.dp, Color.White, CircleShape)
                )
            }

            // Option 1: Floating Value HUD Bubble (Floats above finger, never blocked by thumb)
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(100f)
            ) {
                val isUnity = abs(clampedVol - 1.0f) < 0.02f
                val dBVal = if (clampedVol <= 0.001f) "-∞ dB" else "%.1f dB".format(20 * log10(clampedVol))
                val pctVal = "${(clampedVol * 100).roundToInt()}%"

                Surface(
                    color = Color(0xFF0D1B2A),
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.5.dp, if (isUnity) Color(0xFF00E676) else Color(0xFF00E5FF)),
                    modifier = Modifier
                        .offset(y = (-36).dp)
                        .widthIn(min = 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = pctVal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isUnity) Color(0xFF00E676) else Color.White
                        )
                        Text(
                            text = dBVal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (clampedVol <= 0.001f) PanicRed else Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // Physical Tactile Fader Cap
            val isUnity = abs(clampedVol - 1.0f) < 0.02f
            Box(
                modifier = Modifier
                    .offset(y = capYOffset)
                    .align(Alignment.TopCenter)
                    .width(36.dp)
                    .height(faderCapHeight)
                    .shadow(elevation = if (isDragging) 8.dp else 4.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDragging) {
                                listOf(Color(0xFF555555), Color(0xFF383838), Color(0xFF262626))
                            } else {
                                listOf(Color(0xFF444444), Color(0xFF2C2C2C), Color(0xFF202020))
                            }
                        )
                    )
                    .border(
                        width = if (isDragging) 1.5.dp else 1.dp,
                        color = if (isDragging) Color(0xFF00E5FF) else if (isAssigned) accentColor else Color(0xFF555555),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.5.dp)
                        .background(if (isUnity) Color(0xFF00E676) else if (isAssigned) accentColor else Color.White)
                )
            }
        }
    }
}
