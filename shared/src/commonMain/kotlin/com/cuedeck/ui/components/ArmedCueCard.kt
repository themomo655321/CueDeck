package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.cuedeck.ui.theme.ArmedBlue
import com.cuedeck.ui.theme.DarkSurfaceVariant

@Composable
fun ArmedCueCard(
    armedCue: Cue?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, if (armedCue != null) ArmedBlue else Color(0xFF444444), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Standby pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (armedCue != null) ArmedBlue else Color.Gray)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (armedCue != null) "ARMED / STANDBY" else "STANDBY EMPTY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                if (armedCue != null && armedCue.preWaitMs > 0) {
                    Text(
                        text = "Pre-wait: ${(armedCue.preWaitMs / 1000f)}s",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (armedCue != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cue ${armedCue.cueNumber}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = armedCue.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val sec = armedCue.effectiveDurationMs / 1000
                    val min = sec / 60
                    val remSec = sec % 60
                    Text(
                        text = "Duration: %d:%02d".format(min, remSec),
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )

                    Text(
                        text = "Follow: ${armedCue.followAction.name.replace("_", " ")}",
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            } else {
                Text(
                    text = "End of Show or No Cues in Project",
                    fontSize = 15.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}
