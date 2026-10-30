package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.*
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.DarkSurfaceVariant

@Composable
fun CartGridSection(
    banks: List<CartBank>,
    activeBankIndex: Int,
    cues: List<Cue>,
    activePlaybacks: List<ActivePlayback>,
    isPerformanceLocked: Boolean,
    onSelectBank: (Int) -> Unit,
    onTriggerSlot: (Int) -> Unit,
    onAssignSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBank = banks.getOrNull(activeBankIndex) ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        // Bank Switcher Tabs
        ScrollableTabRow(
            selectedTabIndex = activeBankIndex,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            banks.forEachIndexed { index, bank ->
                Tab(
                    selected = activeBankIndex == index,
                    onClick = { onSelectBank(index) },
                    text = {
                        Text(
                            text = bank.name.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = if (activeBankIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // 3x3 Grid of Cart Pads
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(9) { slotIndex ->
                val slot = activeBank.slots.find { it.index == slotIndex }
                val cue = cues.find { it.id == slot?.cueId }
                val active = activePlaybacks.find { it.cueId == cue?.id }

                CartPadCell(
                    slotIndex = slotIndex,
                    cue = cue,
                    activePlayback = active,
                    isPerformanceLocked = isPerformanceLocked,
                    onTrigger = { onTriggerSlot(slotIndex) },
                    onAssign = { onAssignSlot(slotIndex) }
                )
            }
        }
    }
}

@Composable
private fun CartPadCell(
    slotIndex: Int,
    cue: Cue?,
    activePlayback: ActivePlayback?,
    isPerformanceLocked: Boolean,
    onTrigger: () -> Unit,
    onAssign: () -> Unit
) {
    val isPlaying = activePlayback != null
    val padColor = if (cue != null) Color(cue.colorHex) else Color(0xFF242424)

    Surface(
        color = if (isPlaying) padColor.copy(alpha = 0.35f) else DarkSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .aspectRatio(1f)
            .border(
                width = if (isPlaying) 2.dp else 1.dp,
                color = if (isPlaying) padColor else Color(0xFF333333),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable {
                if (cue != null) {
                    onTrigger()
                } else if (!isPerformanceLocked) {
                    onAssign()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (cue != null) {
                // Top header: duration or remaining time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (cue.triggerMode == TriggerMode.LOOP) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = "Looping",
                            tint = if (isPlaying) Color(0xFF4CAF50) else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(14.dp))
                    }

                    val sec = if (isPlaying) activePlayback!!.remainingMs / 1000 else cue.effectiveDurationMs / 1000
                    Text(
                        text = "%d:%02d".format(sec / 60, sec % 60),
                        fontSize = 11.sp,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlaying) Color(0xFF4CAF50) else Color.LightGray
                    )
                }

                // Center Cue Title
                Text(
                    text = cue.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Bottom: Progress bar when active
                if (isPlaying) {
                    LinearProgressIndicator(
                        progress = { activePlayback!!.progress },
                        color = padColor,
                        trackColor = Color(0xFF222222),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .align(Alignment.BottomCenter)
                    )
                }
            } else {
                // Empty Slot
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!isPerformanceLocked) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Assign Pad",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "PAD ${slotIndex + 1}",
                        fontSize = 11.sp,
                        color = Color(0xFF555555),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
