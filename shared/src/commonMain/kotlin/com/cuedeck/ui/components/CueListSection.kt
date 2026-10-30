package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(cues, key = { it.id }) { cue ->
                    val isArmed = cue.id == armedCueId
                    CueRow(
                        cue = cue,
                        isArmed = isArmed,
                        isPerformanceLocked = isPerformanceLocked,
                        onArm = { onArmCue(cue.id) },
                        onEdit = { onEditCue(cue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CueRow(
    cue: Cue,
    isArmed: Boolean,
    isPerformanceLocked: Boolean,
    onArm: () -> Unit,
    onEdit: () -> Unit
) {
    val sec = cue.effectiveDurationMs / 1000
    val durationText = "%d:%02d".format(sec / 60, sec % 60)

    Surface(
        color = if (isArmed) Color(0xFF1B2B38) else DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isArmed) 1.5.dp else 1.dp,
                color = if (isArmed) ArmedBlue else Color(0xFF2C2C2C),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onArm() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Color tag bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(cue.colorHex))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Cue Number
            Text(
                text = cue.cueNumber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isArmed) ArmedBlue else Color.White,
                modifier = Modifier.width(40.dp)
            )

            // Cue Title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cue.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                )
                if (cue.notes.isNotBlank()) {
                    Text(
                        text = cue.notes,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            // Duration
            Text(
                text = durationText,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 6.dp)
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
                modifier = Modifier.size(16.dp)
            )

            if (!isPerformanceLocked) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Cue",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
