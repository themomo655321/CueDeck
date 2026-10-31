package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stage-grade horizontal draggable splitter bar with tactile visual grip.
 * Continuous frame-accurate delta accumulation + generous 32dp touch target.
 * Adjusts column split ratio (0.20f .. 0.80f).
 */
@Composable
fun HorizontalResizeDivider(
    splitRatio: Float,
    onRatioChange: (Float) -> Unit,
    totalWidthPx: Float,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentRatioState = rememberUpdatedState(splitRatio)
    var dragRatio by remember { mutableFloatStateOf(splitRatio) }

    LaunchedEffect(splitRatio) {
        if (!isDragging) {
            dragRatio = splitRatio
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(32.dp)
            .fillMaxHeight()
            .pointerInput(totalWidthPx) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragRatio = currentRatioState.value
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (totalWidthPx > 0f) {
                            val deltaRatio = dragAmount.x / totalWidthPx
                            dragRatio = (dragRatio + deltaRatio).coerceIn(0.20f, 0.80f)
                            onRatioChange(dragRatio)
                        }
                    }
                )
            }
    ) {
        // Vertical divider guide line
        Box(
            modifier = Modifier
                .width(if (isDragging) 3.dp else 2.dp)
                .fillMaxHeight()
                .background(if (isDragging) Color(0xFF00E5FF) else Color(0xFF2C2C2C))
        )

        // Tactile Center Pill Handle
        Surface(
            color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF222222),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = if (isDragging) 8.dp else 3.dp,
            modifier = Modifier
                .width(14.dp)
                .height(48.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (isDragging) Color.Black else Color.LightGray)
                    )
                    if (it < 3) Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Stage-grade vertical draggable splitter bar to resize panel height.
 * Generous 26dp touch target with tactile visual grip.
 */
@Composable
fun VerticalResizeDivider(
    onDragDelta: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    }
                )
            }
    ) {
        // Horizontal divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isDragging) 3.dp else 2.dp)
                .background(if (isDragging) Color(0xFF00E5FF) else Color(0xFF2C2C2C))
        )

        // Tactile Horizontal Grip Handle
        Surface(
            color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF222222),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = if (isDragging) 6.dp else 2.dp,
            modifier = Modifier
                .width(48.dp)
                .height(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (isDragging) Color.Black else Color.LightGray)
                    )
                    if (it < 3) Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}
