package com.example.linecamera.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linecamera.model.CameraMode

@Composable
fun ShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val ringColor = when (mode) {
        CameraMode.PHOTO -> Color.White
        CameraMode.RAW_PHOTO -> Color(0xFFFFB300)
        CameraMode.VIDEO -> Color(0xFFFF3D00)
    }

    val innerColor = when (mode) {
        CameraMode.PHOTO -> Color.White
        CameraMode.RAW_PHOTO -> Color(0xFFFFB300)
        CameraMode.VIDEO -> Color(0xFFFF3D00)
    }

    val innerShape = if (mode == CameraMode.VIDEO && isRecording) {
        RoundedCornerShape(8.dp)
    } else {
        CircleShape
    }

    val innerSizeDp by animateFloatAsState(
        targetValue = if (mode == CameraMode.VIDEO && isRecording) 28f else 54f,
        animationSpec = tween(durationMillis = 200),
        label = "shutterInnerSize"
    )

    Box(
        modifier = modifier
            .size(76.dp)
            .border(4.dp, ringColor, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSizeDp.dp)
                .clip(innerShape)
                .background(innerColor),
            contentAlignment = Alignment.Center
        ) {
            if (mode == CameraMode.RAW_PHOTO) {
                Text(
                    text = "RAW",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
