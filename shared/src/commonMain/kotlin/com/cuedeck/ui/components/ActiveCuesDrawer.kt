package com.cuedeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.ActivePlayback
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed

@Composable
fun ActiveCuesDrawer(
    activePlaybacks: List<ActivePlayback>,
    onStopCue: (playbackId: String, fadeOutMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activePlaybacks.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "NOW PLAYING (${activePlaybacks.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(activePlaybacks, key = { it.playbackId }) { playback ->
                        ActiveCueRow(
                            playback = playback,
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
    onFadeStop: () -> Unit,
    onKillStop: () -> Unit
) {
    val totalSec = playback.totalMs / 1000
    val remSec = playback.remainingMs / 1000
    var showDurationMode by remember { mutableStateOf(false) } // false = Countdown (-m:ss), true = Total Duration (m:ss)

    Surface(
        color = Color(0xFF262626),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(playback.colorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playback.cueName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // Remaining time countdown vs Total duration toggleable on tap
                val timeLabel = when {
                    playback.isLooping -> "LOOPING"
                    showDurationMode -> "%d:%02d".format(totalSec / 60, totalSec % 60)
                    else -> "-%d:%02d".format(remSec / 60, remSec % 60)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showDurationMode = !showDurationMode }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playback.isFadingOut) Color(0xFFFFB300) else if (showDurationMode) Color(0xFF00E5FF) else Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Fade button
                IconButton(
                    onClick = onFadeStop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "Fade Out",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Hard Kill button
                IconButton(
                    onClick = onKillStop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cut Off",
                        tint = PanicRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Smooth progress bar
            LinearProgressIndicator(
                progress = { playback.progress },
                color = if (playback.isFadingOut) Color(0xFFFFB300) else Color(playback.colorHex),
                trackColor = Color(0xFF383838),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}
