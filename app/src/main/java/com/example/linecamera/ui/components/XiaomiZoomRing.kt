package com.example.linecamera.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun XiaomiZoomRing(
    currentZoom: Float,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialWidth by remember { mutableFloatStateOf(240f) }
    val scope = rememberCoroutineScope()

    // Auto-collapse after 2.5 seconds of inactivity
    LaunchedEffect(isExpanded, lastInteractionTime) {
        if (isExpanded) {
            delay(2500)
            if (System.currentTimeMillis() - lastInteractionTime >= 2400) {
                onExpandedChanged(false)
            }
        }
    }

    val updatedOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val updatedOnExpandedChanged by rememberUpdatedState(onExpandedChanged)

    // Smooth animated zoom transition when clicking lens buttons
    fun animateZoomTo(targetZoom: Float) {
        scope.launch(Dispatchers.Main) {
            val startZoom = currentZoom
            val duration = 250L
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                // Ease out cubic interpolation
                val eased = 1.0f - (1.0f - progress).pow(3)
                val interpolated = startZoom + (targetZoom - startZoom) * eased
                updatedOnZoomChanged(interpolated)
                if (progress >= 1f) break
                delay(12)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 340.dp)
    ) {
        // Curved Xiaomi Arc Dial (visible when swiping, pinching, or active)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.size.width > 0) {
                            dialWidth = coordinates.size.width.toFloat()
                        }
                    }
                    .pointerInput(Unit) {
                        var dragX = dialWidth / 2f
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                updatedOnExpandedChanged(true)
                                lastInteractionTime = System.currentTimeMillis()
                                dragX = offset.x
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                lastInteractionTime = System.currentTimeMillis()
                                dragX = (dragX + dragAmount).coerceIn(0f, dialWidth)

                                val progress = (dragX / dialWidth).coerceIn(0f, 1f)
                                val minZoom = 0.5f
                                val maxZoom = 100f
                                val calculatedZoom = minZoom * (maxZoom / minZoom).pow(progress)
                                updatedOnZoomChanged(calculatedZoom)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val arcPath = Path().apply {
                        moveTo(0f, h)
                        quadraticTo(w / 2f, h - 60f, w, h)
                    }
                    drawPath(
                        path = arcPath,
                        color = Color.White.copy(alpha = 0.25f),
                        style = Stroke(width = 2f)
                    )

                    val tickCount = 30
                    for (i in 0..tickCount) {
                        val fraction = i / tickCount.toFloat()
                        val x = w * fraction
                        val y = h - (kotlin.math.sin(fraction * Math.PI) * 30f).toFloat()

                        val isMajor = i % 10 == 0
                        val tickLength = if (isMajor) 14f else 6f
                        val color = if (isMajor) Color.White else Color.Gray.copy(alpha = 0.6f)

                        drawLine(
                            color = color,
                            start = Offset(x, y),
                            end = Offset(x, y - tickLength),
                            strokeWidth = if (isMajor) 3f else 1.5f
                        )
                    }

                    drawLine(
                        color = Color(0xFF38D352),
                        start = Offset(w / 2f, h - 35f),
                        end = Offset(w / 2f, h - 10f),
                        strokeWidth = 4f
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", currentZoom),
                        color = Color(0xFF38D352),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Idle Quick Lens Buttons (0.6, 1x, 2, 3, 5, 10, 30, 100)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ ->
                        updatedOnExpandedChanged(true)
                        lastInteractionTime = System.currentTimeMillis()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(0.6f, 1.0f, 2.0f, 3.0f, 5.0f, 10.0f, 30.0f, 100.0f)
            presets.forEach { ratio ->
                val isSelected = abs(currentZoom - ratio) < 0.2f
                val label = when (ratio) {
                    0.6f -> "0.6"
                    1.0f -> "1x"
                    else -> "${ratio.toInt()}"
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF38D352) else Color.Transparent)
                        .clickable {
                            updatedOnExpandedChanged(true)
                            lastInteractionTime = System.currentTimeMillis()
                            animateZoomTo(ratio)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
