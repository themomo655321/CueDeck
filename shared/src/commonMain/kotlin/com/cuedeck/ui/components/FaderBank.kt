package com.cuedeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.ActivePlayback
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.DarkSurfaceVariant
import com.cuedeck.ui.theme.PanicRed
import kotlin.math.roundToInt

/**
 * 4-Channel Live Audio Fader Console.
 * Directly maps each fader to the active sounding cues in sequence.
 */
@Composable
fun FaderBank(
    activePlaybacks: List<ActivePlayback>,
    faderVolumes: List<Float>,
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

            // Volume readout (% or dB)
            Text(
                text = if (volume <= 0.001f) "-inf" else "${(volume * 100).roundToInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (volume > 1.0f) Color(0xFFFFB300) else if (isAssigned) Color(0xFF4CAF50) else Color.LightGray
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tactile Vertical Fader
            TactileVerticalFader(
                volume = volume,
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
 * Custom tactile vertical fader track with smooth drag mechanics.
 */
@Composable
private fun TactileVerticalFader(
    volume: Float,
    isAssigned: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val totalHeight = maxHeight
        val faderCapHeight = 24.dp
        val travelHeight = totalHeight - faderCapHeight

        // Calculate cap Y position: volume 0f = bottom, volume 1f = unity, 1.2f = top
        val clampedVol = volume.coerceIn(0f, 1.25f)
        val normalized = clampedVol / 1.25f
        val capYOffset = travelHeight * (1f - normalized)

        // Gesture detector on the whole track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalHeight) {
                    detectTapGestures { offset ->
                        val trackHeightPx = size.height
                        val touchY = offset.y.coerceIn(0f, trackHeightPx)
                        val newNormalized = (1f - (touchY / trackHeightPx)).coerceIn(0f, 1f)
                        val newVol = newNormalized * 1.25f
                        onVolumeChange((newVol * 100).roundToInt() / 100f)
                    }
                }
                .pointerInput(totalHeight) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val trackHeightPx = size.height
                        val touchY = change.position.y.coerceIn(0f, trackHeightPx)
                        val newNormalized = (1f - (touchY / trackHeightPx)).coerceIn(0f, 1f)
                        val newVol = newNormalized * 1.25f
                        onVolumeChange((newVol * 100).roundToInt() / 100f)
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

            // Unity 0dB Marker Line (at ~80% height = 1.0 volume)
            val unityY = travelHeight * (1f - (1.0f / 1.25f)) + (faderCapHeight / 2)
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(1.dp)
                    .offset(y = unityY)
                    .align(Alignment.TopCenter)
                    .background(Color(0xFF555555))
            )

            // Physical Tactile Fader Cap
            Box(
                modifier = Modifier
                    .offset(y = capYOffset)
                    .align(Alignment.TopCenter)
                    .width(34.dp)
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
                // Fader Cap Center Indicator Stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp)
                        .background(if (isAssigned) accentColor else Color.White)
                )
            }
        }
    }
}
