package com.cuedeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.ActivePlayback
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed
import com.cuedeck.util.formatCountdown
import com.cuedeck.util.formatTime

/**
 * Stage-grade Now Playing Drawer.
 * Compact and capped to max height so it never squashes or pushes the Cue Stack off screen.
 * Includes instant Pause/Resume and live touch/drag scrubber seek bar.
 */
@Composable
fun ActiveCuesDrawer(
    activePlaybacks: List<ActivePlayback>,
    onStopCue: (playbackId: String, fadeOutMs: Long) -> Unit,
    onTogglePause: (playbackId: String) -> Unit = {},
    onSeek: (playbackId: String, positionMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 72
) {
    AnimatedVisibility(
        visible = activePlaybacks.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)
                ) {
                    Text(
                        text = "NOW PLAYING (${activePlaybacks.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        letterSpacing = 0.8.sp
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = maxHeightDp.dp)
                ) {
                    items(activePlaybacks, key = { it.playbackId }) { playback ->
                        ActiveCueRow(
                            playback = playback,
                            onTogglePause = { onTogglePause(playback.playbackId) },
                            onSeek = { posMs -> onSeek(playback.playbackId, posMs) },
                            onFadeStop = { onStopCue(playback.playbackId, 1000L) },
                            onKillStop = { onStopCue(playback.playbackId, 0L) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCueRow(
    playback: ActivePlayback,
    onTogglePause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onFadeStop: () -> Unit,
    onKillStop: () -> Unit
) {
    val totalSec = playback.totalMs / 1000
    val remSec = playback.remainingMs / 1000
    var showDurationMode by remember { mutableStateOf(false) }
    var draggingRatio by remember { mutableStateOf<Float?>(null) }

    Surface(
        color = Color(0xFF222222),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Control and metadata header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                // Color pill & Cue Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(playback.colorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = playback.cueName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Countdown / Duration badge
                val timeLabel = when {
                    playback.isPaused -> "PAUSED"
                    playback.isLooping -> "LOOP"
                    showDurationMode -> formatTime(totalSec)
                    else -> formatCountdown(remSec)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { showDurationMode = !showDurationMode }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            playback.isPaused -> Color(0xFFFFB300)
                            playback.isFadingOut -> Color(0xFFFFB300)
                            showDurationMode -> Color(0xFF00E5FF)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Pause / Resume button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onTogglePause() }
                ) {
                    Icon(
                        imageVector = if (playback.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (playback.isPaused) "Resume" else "Pause",
                        tint = if (playback.isPaused) Color(0xFF4CAF50) else Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Fade button (1s fade)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onFadeStop() }
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "Fade Out",
                        tint = Color.LightGray,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Hard Kill button (0s cutoff)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onKillStop() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cut Off",
                        tint = PanicRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Interactive Scrubber & Seek Bar
            BoxWithConstraints(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .padding(horizontal = 4.dp)
                    .pointerInput(playback.playbackId, playback.totalMs) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val totalWidth = size.width.toFloat()
                            if (totalWidth > 0f) {
                                val ratio = (down.position.x / totalWidth).coerceIn(0f, 1f)
                                draggingRatio = ratio
                                onSeek((ratio * playback.totalMs).toLong())
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                if (totalWidth > 0f) {
                                    val ratio = (change.position.x / totalWidth).coerceIn(0f, 1f)
                                    draggingRatio = ratio
                                    onSeek((ratio * playback.totalMs).toLong())
                                }
                            }
                            draggingRatio = null
                        }
                    }
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val progressRatio = (draggingRatio ?: playback.progress).coerceIn(0f, 1f)
                val barColor = when {
                    playback.isPaused -> Color(0xFFFFB300)
                    playback.isFadingOut -> Color(0xFFFFB300)
                    else -> Color(playback.colorHex)
                }

                // Background track (4dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333333))
                )

                // Played filled track (4dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressRatio)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )

                // Sleek scrubber thumb circle
                if (totalWidthPx > 0f) {
                    val thumbOffsetDp = with(LocalDensity.current) {
                        ((totalWidthPx * progressRatio) - 4.dp.toPx()).coerceAtLeast(0f).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffsetDp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}
