package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
    cartPageIndex: Int,
    cues: List<Cue>,
    activePlaybacks: List<ActivePlayback>,
    isPerformanceLocked: Boolean,
    onSelectBank: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onTriggerSlot: (Int) -> Unit,
    onAssignSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBank = banks.getOrNull(activeBankIndex) ?: return

    val totalSlots = 18 // Support up to 18 pads across 6 pages of 3 pads
    val totalPages = (totalSlots / 3).coerceAtLeast(1)
    val pageStartSlot = (cartPageIndex % totalPages) * 3

    Column(modifier = modifier.fillMaxWidth()) {
        // Bank Header & Page indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${activeBank.name.uppercase()} • PAGE ${cartPageIndex + 1}/$totalPages",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            Text(
                text = "PADS ${pageStartSlot + 1}-${pageStartSlot + 3}",
                fontSize = 10.sp,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 1x3 Soundcart with PREV and NEXT buttons on left and right
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
        ) {
            // PREVIOUS PAGE BUTTON (<)
            Surface(
                color = Color(0xFF222222),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPrevPage() }
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Sounds",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 3 CART PADS (1x3)
            for (offset in 0..2) {
                val slotIndex = pageStartSlot + offset
                val slot = activeBank.slots.find { it.index == slotIndex }
                val cue = cues.find { it.id == slot?.cueId }
                val active = activePlaybacks.find { it.cueId == cue?.id }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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

            // NEXT PAGE BUTTON (>)
            Surface(
                color = Color(0xFF222222),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNextPage() }
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Sounds",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
            .fillMaxSize()
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
                .padding(6.dp)
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
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(13.dp))
                    }

                    val sec = if (isPlaying) activePlayback!!.remainingMs / 1000 else cue.effectiveDurationMs / 1000
                    Text(
                        text = "%d:%02d".format(sec / 60, sec % 60),
                        fontSize = 10.sp,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlaying) Color(0xFF4CAF50) else Color.LightGray
                    )
                }

                // Center Cue Title
                Text(
                    text = cue.name,
                    fontSize = 12.sp,
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "PAD ${slotIndex + 1}",
                        fontSize = 10.sp,
                        color = Color(0xFF555555),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
