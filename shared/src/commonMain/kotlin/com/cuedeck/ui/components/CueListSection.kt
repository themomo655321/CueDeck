package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cuedeck.model.Cue
import com.cuedeck.model.FollowAction
import com.cuedeck.ui.theme.ArmedBlue
import com.cuedeck.ui.theme.DarkSurface

@Composable
fun CueListSection(
    cues: List<Cue>,
    armedCueId: String?,
    isPerformanceLocked: Boolean,
    onArmCue: (String) -> Unit,
    onEditCue: (Cue) -> Unit,
    onAddCueClick: () -> Unit,
    onMoveUp: ((String) -> Unit)? = null,
    onMoveDown: ((String) -> Unit)? = null,
    onMoveCue: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }

    // Auto-scroll to armed cue whenever armedCueId changes so operator never loses track
    LaunchedEffect(armedCueId) {
        if (armedCueId != null && draggingIndex == null) {
            val armedIndex = cues.indexOfFirst { it.id == armedCueId }
            if (armedIndex >= 0) {
                listState.animateScrollToItem(armedIndex)
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "CUE STACK (${cues.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            if (!isPerformanceLocked) {
                TextButton(
                    onClick = onAddCueClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Cue", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD CUE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (cues.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
            ) {
                Text(
                    text = if (isPerformanceLocked) "No cues in this show." else "Tap + ADD CUE to import audio",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(cues, key = { _, item -> item.id }) { index, cue ->
                    val isArmed = cue.id == armedCueId
                    val isDragging = draggingIndex == index

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragAccumulatedY else 0f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                            }
                    ) {
                        CueRow(
                            cue = cue,
                            isArmed = isArmed,
                            isDragging = isDragging,
                            isPerformanceLocked = isPerformanceLocked,
                            onArm = { onArmCue(cue.id) },
                            onEdit = { onEditCue(cue) },
                            onMoveUp = onMoveUp?.let { { it(cue.id) } },
                            onMoveDown = onMoveDown?.let { { it(cue.id) } },
                            onDragGestureModifier = if (!isPerformanceLocked && onMoveCue != null) {
                                Modifier.pointerInput(cues.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragAccumulatedY = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragAccumulatedY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragAccumulatedY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAccumulatedY += dragAmount.y
                                            val itemHeightPx = 110f // approx row height
                                            val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress

                                            if (dragAccumulatedY > itemHeightPx && currentIdx < cues.size - 1) {
                                                onMoveCue(currentIdx, currentIdx + 1)
                                                draggingIndex = currentIdx + 1
                                                dragAccumulatedY -= itemHeightPx
                                            } else if (dragAccumulatedY < -itemHeightPx && currentIdx > 0) {
                                                onMoveCue(currentIdx, currentIdx - 1)
                                                draggingIndex = currentIdx - 1
                                                dragAccumulatedY += itemHeightPx
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CueRow(
    cue: Cue,
    isArmed: Boolean,
    isDragging: Boolean,
    isPerformanceLocked: Boolean,
    onArm: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDragGestureModifier: Modifier = Modifier
) {
    val sec = cue.effectiveDurationMs / 1000
    val durationText = "%d:%02d".format(sec / 60, sec % 60)

    Surface(
        color = when {
            isDragging -> Color(0xFF223647)
            isArmed -> Color(0xFF1B2B38)
            else -> DarkSurface
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)) else Modifier
            )
            .border(
                width = if (isArmed || isDragging) 1.5.dp else 1.dp,
                color = when {
                    isDragging -> Color(0xFF00E5FF)
                    isArmed -> ArmedBlue
                    else -> Color(0xFF2C2C2C)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .then(onDragGestureModifier)
            .clickable { onArm() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Color tag bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(cue.colorHex))
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Cue Number
            Text(
                text = cue.cueNumber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isArmed || isDragging) ArmedBlue else Color.White,
                modifier = Modifier.width(36.dp)
            )

            // Cue Title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cue.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                )
                if (cue.notes.isNotBlank()) {
                    Text(
                        text = cue.notes,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            // Duration
            Text(
                text = durationText,
                fontSize = 11.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Follow Action Icon
            Icon(
                imageVector = when (cue.followAction) {
                    FollowAction.WAIT_GO -> Icons.Default.Pause
                    FollowAction.AUTO_FOLLOW -> Icons.Default.ArrowDownward
                    FollowAction.AUTO_CONTINUE -> Icons.Default.FastForward
                },
                contentDescription = cue.followAction.name,
                tint = when (cue.followAction) {
                    FollowAction.WAIT_GO -> Color.Gray
                    FollowAction.AUTO_FOLLOW -> Color(0xFF4CAF50)
                    FollowAction.AUTO_CONTINUE -> Color(0xFFFFB300)
                },
                modifier = Modifier.size(15.dp)
            )

            if (!isPerformanceLocked) {
                // Reorder buttons (quick one-tap fallback)
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Cue",
                        tint = Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Drag indicator handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Hold to drag",
                    tint = if (isDragging) Color(0xFF00E5FF) else Color(0xFF555555),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                )
            }
        }
    }
}
