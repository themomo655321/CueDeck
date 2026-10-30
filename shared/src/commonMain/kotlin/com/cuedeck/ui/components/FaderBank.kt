package com.cuedeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 4-Channel Live Audio Fader Console with Procreate-Style StreamLine Smoothing.
 * Directly maps each fader to the active sounding cues in sequence.
 */
@Composable
fun FaderBank(
    activePlaybacks: List<ActivePlayback>,
    faderVolumes: List<Float>,
    smoothingPercent: Int = 65,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE MIXER (4 CHANNELS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SMOOTH: ${smoothingPercent}% • ACTIVE: ${activePlaybacks.size.coerceAtMost(4)}/4",
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
                        smoothingPercent = smoothingPercent,
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
    smoothingPercent: Int,
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
            // Channel Pill & Title
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

            Spacer(modifier = Modifier.height(4.dp))

            // Procreate StreamLine Tactile Vertical Fader
            TactileVerticalFader(
                volume = volume,
                smoothingPercent = smoothingPercent,
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
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (volume > 0f) onVolumeChange(0f) else onVolumeChange(1.0f)
                        }
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
 * Custom tactile vertical fader track with Procreate-style StreamLine smoothing.
 * Supports continuous bi-directional drag (up and down), single tap to jump,
 * and double-tap to instantly reset back to 100% (Unity gain).
 */
@Composable
private fun TactileVerticalFader(
    volume: Float,
    smoothingPercent: Int,
    isAssigned: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val faderCapHeight = 24.dp
    val faderCapHeightPx = with(density) { faderCapHeight.toPx() }

    // Target position driven directly by touch
    var targetVol by remember { mutableFloatStateOf(volume) }
    // Smoothed visual & audio position trailing the target with StreamLine inertia
    var smoothedVol by remember { mutableFloatStateOf(volume) }
    // Whether user is actively touching/dragging
    var isDragging by remember { mutableStateOf(false) }

    // Sync from external volume changes (e.g. MUTE button, show load, panic stop) when NOT dragging
    LaunchedEffect(volume) {
        if (!isDragging) {
            targetVol = volume
            if (abs(volume - smoothedVol) > 0.05f) {
                smoothedVol = volume
            }
        }
    }

    // StreamLine smoothing frame loop: runs continuously when target differs or while dragging
    LaunchedEffect(smoothingPercent) {
        var lastNotified = smoothedVol
        snapshotFlow { targetVol to isDragging }.collectLatest { (target, dragging) ->
            val factor = (smoothingPercent / 100f).coerceIn(0f, 0.90f)
            if (factor <= 0.05f) {
                smoothedVol = target
                if (abs(smoothedVol - lastNotified) >= 0.002f || smoothedVol == target) {
                    lastNotified = smoothedVol
                    onVolumeChange((smoothedVol * 1000).roundToInt() / 1000f)
                }
            } else {
                val rate = (1.0f - factor).coerceIn(0.05f, 0.65f)
                while (abs(targetVol - smoothedVol) > 0.0005f || isDragging) {
                    withFrameNanos {
                        smoothedVol += (targetVol - smoothedVol) * rate
                    }
                    if (abs(smoothedVol - lastNotified) >= 0.002f || smoothedVol == targetVol) {
                        lastNotified = smoothedVol
                        onVolumeChange((smoothedVol * 1000).roundToInt() / 1000f)
                    }
                }
                smoothedVol = targetVol
                if (lastNotified != smoothedVol) {
                    lastNotified = smoothedVol
                    onVolumeChange((smoothedVol * 1000).roundToInt() / 1000f)
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val totalHeight = maxHeight
        val travelHeight = totalHeight - faderCapHeight

        // Fader cap Y is mapped directly to smoothedVol
        val clampedVol = smoothedVol.coerceIn(0f, 1.25f)
        val normalized = clampedVol / 1.25f
        val capYOffset = travelHeight * (1f - normalized)

        // Gesture detector on the track using unified pointer input
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(faderCapHeightPx) {
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
                            // Double tap: reset to 100% (1.0f Unity gain)
                            targetVol = 1.0f
                            smoothedVol = 1.0f
                            onVolumeChange(1.0f)
                            // Wait until all touches lift
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed) break
                                pointer.consume()
                            }
                            isDragging = false
                            lastTapTime = 0L
                            return@awaitEachGesture
                        }

                        // Touch / Drag handler
                        isDragging = true
                        val travelPx = (size.height.toFloat() - faderCapHeightPx).coerceAtLeast(1f)
                        val capHalf = faderCapHeightPx / 2f
                        val initialTop = (down.position.y - capHalf).coerceIn(0f, travelPx)
                        targetVol = ((1f - (initialTop / travelPx)) * 1.25f).coerceIn(0f, 1.25f)

                        // Track continuous movement up and down
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                isDragging = false
                                break
                            }
                            pointer.consume()
                            val curTop = (pointer.position.y - capHalf).coerceIn(0f, travelPx)
                            targetVol = ((1f - (curTop / travelPx)) * 1.25f).coerceIn(0f, 1.25f)
                        }
                        isDragging = false
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

            // Unity 0dB Marker Line (at 1.0 volume = 100%) - tap to snap to 100%
            val unityY = travelHeight * (1f - (1.0f / 1.25f)) + (faderCapHeight / 2)
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .offset(y = unityY - 1.5.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (abs(volume - 1.0f) < 0.02f) Color(0xFF4CAF50) else Color(0xFF666666))
            )

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
                // Fader Cap Center Indicator Stripe (glows green at 100% Unity gain)
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
