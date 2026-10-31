package com.cuedeck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Interactive Audio Waveform Editor for Non-Destructive Trimming.
 * Displays real amplitude bars, draggable IN/OUT boundary handles,
 * live moving playhead during preview, and DJ-style scrub audio cueing.
 */
@Composable
fun WaveformTrimView(
    cueId: String,
    inPointSec: Float,
    outPointSec: Float,
    totalDurationSec: Float,
    peaks: List<Float> = emptyList(),
    previewProgressSec: Float? = null,
    onScrub: ((Float) -> Unit)? = null,
    onInPointChange: (Float) -> Unit,
    onOutPointChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = totalDurationSec.coerceAtLeast(0.5f)

    // Fallback baseline peaks if real peaks are still extracting
    val fallbackPeaks = remember(cueId) {
        val seed = cueId.hashCode()
        List(70) { i ->
            val angle = i * 0.18f + (seed % 100) * 0.1f
            val base = abs(sin(angle) * 0.7f + sin(angle * 2.3f) * 0.3f)
            (base * 0.85f + 0.15f).coerceIn(0.12f, 0.98f)
        }
    }

    val displayPeaks = if (peaks.isNotEmpty()) peaks else fallbackPeaks

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Timecode Readouts
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IN: ${formatTime(inPointSec)}",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            val trimmedDuration = (outPointSec - inPointSec).coerceAtLeast(0f)
            Text(
                text = "TRIMMED: ${formatTime(trimmedDuration)} / ${formatTime(duration)}",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "OUT: ${formatTime(outPointSec)}",
                color = Color(0xFFFF5252),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Waveform & Draggable Handles Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0D0D0D))
        ) {
            val parentMaxWidth = maxWidth
            val widthPx = constraints.maxWidth.toFloat()

            var activeDragHandle by remember { mutableStateOf<String?>(null) } // "IN", "OUT", or null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(duration, inPointSec, outPointSec) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val inX = (inPointSec / duration) * widthPx
                                val outX = (outPointSec / duration) * widthPx
                                val distIn = abs(offset.x - inX)
                                val distOut = abs(offset.x - outX)

                                activeDragHandle = if (distIn < distOut && distIn < 80f) {
                                    "IN"
                                } else if (distOut < 80f) {
                                    "OUT"
                                } else {
                                    if (distIn < distOut) "IN" else "OUT"
                                }
                            },
                            onDragEnd = { activeDragHandle = null },
                            onDragCancel = { activeDragHandle = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val newSec = ((change.position.x / widthPx) * duration).coerceIn(0f, duration)
                                val clampedSec = (newSec * 10).toInt() / 10f
                                if (activeDragHandle == "IN") {
                                    val clamped = clampedSec.coerceAtMost((outPointSec - 0.1f).coerceAtLeast(0f))
                                    onInPointChange(clamped)
                                    onScrub?.invoke(clamped)
                                } else if (activeDragHandle == "OUT") {
                                    val clamped = clampedSec.coerceAtLeast((inPointSec + 0.1f).coerceAtMost(duration))
                                    onOutPointChange(clamped)
                                    onScrub?.invoke(clamped)
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val inFraction = (inPointSec / duration).coerceIn(0f, 1f)
                    val outFraction = (outPointSec / duration).coerceIn(0f, 1f)
                    val inX = inFraction * size.width
                    val outX = outFraction * size.width

                    // Dimmed trimmed-out regions
                    if (inX > 0f) {
                        drawRect(
                            color = Color(0x66000000),
                            topLeft = Offset.Zero,
                            size = Size(inX, size.height)
                        )
                    }
                    if (outX < size.width) {
                        drawRect(
                            color = Color(0x66000000),
                            topLeft = Offset(outX, 0f),
                            size = Size(size.width - outX, size.height)
                        )
                    }

                    // Active highlighted region
                    drawRect(
                        color = Color(0x1A00E5FF),
                        topLeft = Offset(inX, 0f),
                        size = Size((outX - inX).coerceAtLeast(0f), size.height)
                    )

                    // Draw amplitude peak bars
                    val peakCount = displayPeaks.size
                    val step = size.width / peakCount
                    val midY = size.height / 2f
                    displayPeaks.forEachIndexed { i, peak ->
                        val barX = i * step + (step / 2f)
                        val barHeight = peak * (size.height * 0.78f)
                        val isInActive = barX in inX..outX
                        val barColor = if (isInActive) Color(0xFF00E5FF) else Color(0xFF383838)

                        drawLine(
                            color = barColor,
                            start = Offset(barX, midY - barHeight / 2f),
                            end = Offset(barX, midY + barHeight / 2f),
                            strokeWidth = (step * 0.65f).coerceAtLeast(2f),
                            cap = StrokeCap.Round
                        )
                    }

                    // Draw IN Boundary Line (Green)
                    drawLine(
                        color = Color(0xFF00E676),
                        start = Offset(inX, 0f),
                        end = Offset(inX, size.height),
                        strokeWidth = 3.dp.toPx()
                    )

                    // Draw OUT Boundary Line (Red)
                    drawLine(
                        color = Color(0xFFFF5252),
                        start = Offset(outX, 0f),
                        end = Offset(outX, size.height),
                        strokeWidth = 3.dp.toPx()
                    )

                    // DJ Playhead line during preview
                    previewProgressSec?.let { pSec ->
                        val playheadFraction = (pSec / duration).coerceIn(0f, 1f)
                        val playheadX = playheadFraction * size.width
                        drawLine(
                            color = Color.White,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, size.height),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 4.dp.toPx(),
                            center = Offset(playheadX, size.height / 2f)
                        )
                    }
                }

                // IN Handle Flag
                val inFraction = (inPointSec / duration).coerceIn(0f, 1f)
                val inOffsetDp = ((parentMaxWidth - 28.dp) * inFraction).let { if (it < 0.dp) 0.dp else it }
                Box(
                    modifier = Modifier
                        .offset(x = inOffsetDp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(bottomEnd = 4.dp))
                        .background(Color(0xFF00E676))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("IN", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }

                // OUT Handle Flag
                val outFraction = (outPointSec / duration).coerceIn(0f, 1f)
                val outOffsetDp = ((parentMaxWidth - 32.dp) * outFraction).let { if (it < 0.dp) 0.dp else it }
                Box(
                    modifier = Modifier
                        .offset(x = outOffsetDp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(topEnd = 4.dp))
                        .background(Color(0xFFFF5252))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("OUT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}

private fun formatTime(sec: Float): String {
    val totalSec = sec.coerceAtLeast(0f)
    val mins = (totalSec / 60f).toInt()
    val remSec = totalSec % 60f
    val secs = remSec.toInt()
    val millis = ((remSec - secs) * 100).toInt()
    return "%d:%02d.%02d".format(mins, secs, millis)
}
