package com.cuedeck.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.Cue
import com.cuedeck.ui.theme.ArmedBlue
import com.cuedeck.util.formatTime

/**
 * Ultra-compact 1-row broadcast stage ribbon for Armed / Standby cue (~38dp height).
 * Saves vertical screen estate so the sequential Cue Stack is never pushed off screen.
 */
@Composable
fun ArmedCueCard(
    armedCue: Cue?,
    onEditCue: ((Cue) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (armedCue != null) ArmedBlue else Color(0xFF333333)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = armedCue != null) {
                armedCue?.let { onEditCue?.invoke(it) }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Left side: [STANDBY] badge + Cue number + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                // Standby pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (armedCue != null) ArmedBlue else Color(0xFF3A3A3A))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (armedCue != null) "STANDBY" else "EMPTY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (armedCue != null) {
                    Text(
                        text = armedCue.cueNumber.removeSuffix(".0"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = armedCue.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (armedCue.preWaitMs > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${armedCue.preWaitMs / 1000f}s",
                            fontSize = 10.sp,
                            color = Color(0xFFFFB300),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "End of Show (No cue armed)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Right side: Duration, Follow action badge, and Edit button
            if (armedCue != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sec = armedCue.effectiveDurationMs / 1000
                    Text(
                        text = formatTime(sec),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFAAAAAA)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF262626))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = armedCue.followAction.name.replace("_", " "),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBBBBBB)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF222834))
                            .clickable { onEditCue?.invoke(armedCue) }
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 12.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
    }
}
