package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Stage-grade horizontal draggable splitter bar with tactile visual grip.
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .pointerInput(totalWidthPx) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (totalWidthPx > 0f) {
                            val deltaRatio = dragAmount.x / totalWidthPx
                            val newRatio = (splitRatio + deltaRatio).coerceIn(0.20f, 0.80f)
                            onRatioChange(newRatio)
                        }
                    }
                )
            }
    ) {
        // Divider line
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(if (isDragging) Color(0xFF00E5FF) else Color(0xFF333333))
        )

        // Center 3-Dot Grip Handle
        Surface(
            color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF262626),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = if (isDragging) 6.dp else 2.dp,
            modifier = Modifier
                .width(10.dp)
                .height(40.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (isDragging) Color.Black else Color.LightGray)
                    )
                    if (it < 2) Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Stage-grade vertical draggable splitter bar to resize panel height.
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
            .height(14.dp)
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
        // Divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (isDragging) Color(0xFF00E5FF) else Color(0xFF333333))
        )

        // Horizontal Grip Handle
        Surface(
            color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF262626),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = if (isDragging) 4.dp else 1.dp,
            modifier = Modifier
                .width(40.dp)
                .height(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (isDragging) Color.Black else Color.LightGray)
                    )
                    if (it < 2) Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}
