package com.example.linecamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.linecamera.model.GridMode
import kotlin.math.abs

@Composable
fun GridAndLevelOverlay(
    gridMode: GridMode,
    showLevel: Boolean,
    rollAngle: Float,
    focusTargetPoint: Pair<Float, Float>?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw Grid
        when (gridMode) {
            GridMode.NONE -> {}
            GridMode.GRID_3X3 -> {
                val gridColor = Color.White.copy(alpha = 0.35f)
                val stroke = Stroke(width = 1.5f)

                // Vertical lines
                drawLine(gridColor, Offset(width / 3f, 0f), Offset(width / 3f, height), stroke.width)
                drawLine(gridColor, Offset(2f * width / 3f, 0f), Offset(2f * width / 3f, height), stroke.width)

                // Horizontal lines
                drawLine(gridColor, Offset(0f, height / 3f), Offset(width, height / 3f), stroke.width)
                drawLine(gridColor, Offset(0f, 2f * height / 3f), Offset(width, 2f * height / 3f), stroke.width)
            }
            GridMode.GOLDEN_RATIO -> {
                val gridColor = Color.White.copy(alpha = 0.35f)
                val phi = 0.618f

                drawLine(gridColor, Offset(width * (1f - phi), 0f), Offset(width * (1f - phi), height), 1.5f)
                drawLine(gridColor, Offset(width * phi, 0f), Offset(width * phi, height), 1.5f)

                drawLine(gridColor, Offset(0f, height * (1f - phi)), Offset(width, height * (1f - phi)), 1.5f)
                drawLine(gridColor, Offset(0f, height * phi), Offset(width, height * phi), 1.5f)
            }
        }

        // 2. Draw Tap-to-Focus Target Box
        focusTargetPoint?.let { (nx, ny) ->
            val fx = nx * width
            val fy = ny * height
            val boxSize = 64.dp.toPx()

            drawRect(
                color = Color(0xFF38D352),
                topLeft = Offset(fx - boxSize / 2f, fy - boxSize / 2f),
                size = Size(boxSize, boxSize),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Draw Horizon Level Bar
        if (showLevel) {
            val isLevel = abs(rollAngle) < 1.0f
            val levelColor = if (isLevel) Color(0xFF38D352) else Color.White.copy(alpha = 0.8f)

            rotate(degrees = rollAngle, pivot = center) {
                val barWidth = 120.dp.toPx()
                drawLine(
                    color = levelColor,
                    start = Offset(center.x - barWidth / 2f, center.y),
                    end = Offset(center.x + barWidth / 2f, center.y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

private val Int.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this.toFloat())
