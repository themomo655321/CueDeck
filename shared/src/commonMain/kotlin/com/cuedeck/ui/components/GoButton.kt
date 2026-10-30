package com.cuedeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.model.Cue
import com.cuedeck.ui.theme.GoGreen

@Composable
fun GoButton(
    armedCue: Cue?,
    onGoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = armedCue != null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(if (isPressed) 0.96f else 1.0f)

    val buttonGradient = if (isEnabled) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF66BB6A),
                GoGreen,
                Color(0xFF2E7D32)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3A3A3A), Color(0xFF2C2C2C))
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(buttonGradient)
            .clickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onGoClick
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "G O",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = if (isEnabled) Color.White else Color.Gray,
                letterSpacing = 4.sp
            )

            if (armedCue != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${armedCue.cueNumber} - ${armedCue.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}
