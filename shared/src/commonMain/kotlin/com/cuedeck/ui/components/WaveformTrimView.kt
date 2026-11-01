package com.cuedeck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.sin

/**
 * Real non-destructive audio waveform scrubber & in/out trimmer with length-zooming.
 * Supports 1x (fit), 2x (double length), 4x (quad length) horizontal zoom with free scrolling.
 * Independent top (IN) and bottom (OUT) touch targets allow 1:1 gliding drag without blocking
 * horizontal waveform panning.
 */
@Composable
fun WaveformTrimView(
    cueId: String,
    peaks: List<Float>,
    totalDurationSec: Float,
    inPointSec: Float,
    outPointSec: Float,
    previewProgressSec: Float? = null,
    onScrub: ((Float) -> Unit)? = null,
    onInPointChange: (Float) -> Unit,
    onOutPointChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = totalDurationSec.coerceAtLeast(0.5f)
    var zoomLevel by remember { mutableIntStateOf(1) } // 1x, 2x, 4x length zoom
    var lastScrubTimestamp by remember { mutableLongStateOf(0L) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

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

    // Auto-scroll towards in-point when zooming in
    LaunchedEffect(zoomLevel) {
        if (zoomLevel > 1) {
            val targetScroll = ((inPointSec / duration) * scrollState.maxValue).toInt()
            scrollState.animateScrollTo(targetScroll)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Timecode & Zoom Header Row
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

            // Horizontal Length Zoom pills (1X, 2X, 4X)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "LENGTH:",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                listOf(1 to "1X", 2 to "2X", 4 to "4X").forEach { (lvl, lbl) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (zoomLevel == lvl) Color(0xFF00E5FF) else Color(0xFF242424))
                            .clickable { zoomLevel = lvl }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (zoomLevel == lvl) Color.Black else Color.LightGray
                        )
                    }
                }
            }

            Text(
                text = "OUT: ${formatTime(outPointSec)}",
                color = Color(0xFFFF5252),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Waveform & Draggable Handles Canvas (Horizontally Scrollable when Zoomed)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0D0D0D))
        ) {
            val viewportWidthDp = maxWidth
            val totalWidthDp = viewportWidthDp * zoomLevel
            val totalWidthPx = constraints.maxWidth.toFloat() * zoomLevel

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .fillMaxHeight()
                ) {
                    // 1. Background Waveform Canvas (Tap to scrub/preview, drags pass through to horizontalScroll)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(duration, totalWidthPx) {
                                detectTapGestures { offset ->
                                    val sec = (offset.x / totalWidthPx * duration).coerceIn(0f, duration)
                                    onScrub?.invoke(sec)
                                }
                            }
                    ) {
                        val inFraction = (inPointSec / duration).coerceIn(0f, 1f)
                        val outFraction = (outPointSec / duration).coerceIn(0f, 1f)
                        val inX = inFraction * size.width
                        val outX = outFraction * size.width

                        // Shaded trimmed-out regions
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
                            color = Color(0x1F00E5FF),
                            topLeft = Offset(inX, 0f),
                            size = Size((outX - inX).coerceAtLeast(0f), size.height)
                        )

                        // Amplitude peak bars across expanded width
                        val peakCount = displayPeaks.size
                        val step = size.width / peakCount
                        val midY = size.height / 2f
                        displayPeaks.forEachIndexed { i, peak ->
                            val barX = i * step + (step / 2f)
                            val barHeight = peak.coerceIn(0.08f, 1.0f) * (size.height * 0.78f)
                            val isInActive = barX in inX..outX
                            val barColor = if (isInActive) Color(0xFF00E5FF) else Color(0xFF383838)

                            drawLine(
                                color = barColor,
                                start = Offset(barX, midY - barHeight / 2f),
                                end = Offset(barX, midY + barHeight / 2f),
                                strokeWidth = (step * 0.65f).coerceIn(2f, 12f),
                                cap = StrokeCap.Round
                            )
                        }

                        // IN Boundary Line (Green)
                        drawLine(
                            color = Color(0xFF00E676),
                            start = Offset(inX, 0f),
                            end = Offset(inX, size.height),
                            strokeWidth = 3.dp.toPx()
                        )

                        // OUT Boundary Line (Red)
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

                    // 2. IN Handle (Top Half: Width 48dp centered on inX)
                    val inFraction = (inPointSec / duration).coerceIn(0f, 1f)
                    val inXPx = inFraction * totalWidthPx
                    val inXDp = with(density) { inXPx.toDp() }
                    val inOffsetDp = (inXDp - 24.dp).coerceAtLeast(0.dp)

                    var inDragAccPx by remember { mutableFloatStateOf(0f) }
                    var inDragStartSec by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .offset(x = inOffsetDp)
                            .width(48.dp)
                            .height(44.dp)
                            .align(Alignment.TopStart)
                            .pointerInput(duration, totalWidthPx, outPointSec) {
                                detectDragGestures(
                                    onDragStart = {
                                        inDragStartSec = inPointSec
                                        inDragAccPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        inDragAccPx += dragAmount.x
                                        val deltaSec = (inDragAccPx / totalWidthPx) * duration
                                        val newSec = (inDragStartSec + deltaSec).coerceIn(0f, (outPointSec - 0.05f).coerceAtLeast(0f))
                                        onInPointChange(newSec)
                                        val now = Clock.System.now().toEpochMilliseconds()
                                        if (now - lastScrubTimestamp > 60L) {
                                            lastScrubTimestamp = now
                                            onScrub?.invoke(newSec)
                                        }
                                    },
                                    onDragEnd = {
                                        onScrub?.invoke(inPointSec)
                                    },
                                    onDragCancel = {
                                        onScrub?.invoke(inPointSec)
                                    }
                                )
                            }
                    ) {
                        Surface(
                            color = Color(0xFF00E676),
                            shape = RoundedCornerShape(bottomEnd = 4.dp, bottomStart = 4.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "IN Point Handle",
                                    tint = Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("IN", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // 3. OUT Handle (Bottom Half: Width 48dp centered on outX)
                    val outFraction = (outPointSec / duration).coerceIn(0f, 1f)
                    val outXPx = outFraction * totalWidthPx
                    val outXDp = with(density) { outXPx.toDp() }
                    val outOffsetDp = (outXDp - 24.dp).coerceAtLeast(0.dp)

                    var outDragAccPx by remember { mutableFloatStateOf(0f) }
                    var outDragStartSec by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .offset(x = outOffsetDp)
                            .width(48.dp)
                            .height(44.dp)
                            .align(Alignment.BottomStart)
                            .pointerInput(duration, totalWidthPx, inPointSec) {
                                detectDragGestures(
                                    onDragStart = {
                                        outDragStartSec = outPointSec
                                        outDragAccPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        outDragAccPx += dragAmount.x
                                        val deltaSec = (outDragAccPx / totalWidthPx) * duration
                                        val newSec = (outDragStartSec + deltaSec).coerceIn(inPointSec + 0.05f, duration)
                                        onOutPointChange(newSec)
                                        val now = Clock.System.now().toEpochMilliseconds()
                                        if (now - lastScrubTimestamp > 60L) {
                                            lastScrubTimestamp = now
                                            onScrub?.invoke(newSec)
                                        }
                                    },
                                    onDragEnd = {
                                        onScrub?.invoke(outPointSec)
                                    },
                                    onDragCancel = {
                                        onScrub?.invoke(outPointSec)
                                    }
                                )
                            }
                    ) {
                        Surface(
                            color = Color(0xFFFF5252),
                            shape = RoundedCornerShape(topEnd = 4.dp, topStart = 4.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "OUT Point Handle",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("OUT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(sec: Float): String {
    val totalSeconds = sec.toInt()
    val tenths = ((sec - totalSeconds) * 10).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val sStr = if (seconds in 0..9) "0$seconds" else "$seconds"
    return "$minutes:$sStr.$tenths"
}
