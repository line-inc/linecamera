package com.example.linecamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.linecamera.model.FlashMode

@Composable
fun TopBarControls(
    flashMode: FlashMode,
    isProControlsOpen: Boolean,
    isFrontCamera: Boolean,
    onFlashToggle: () -> Unit,
    onProControlsToggle: () -> Unit,
    onCameraSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash Mode Button
        IconButton(
            onClick = onFlashToggle,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            val icon = when (flashMode) {
                FlashMode.OFF -> Icons.Default.FlashOff
                FlashMode.ON -> Icons.Default.FlashOn
                FlashMode.AUTO -> Icons.Default.FlashAuto
                FlashMode.TORCH -> Icons.Default.FlashOn
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash",
                tint = if (flashMode != FlashMode.OFF) Color(0xFFFFB300) else Color.White
            )
        }

        // Pro Controls Toggle Button
        IconButton(
            onClick = onProControlsToggle,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isProControlsOpen) Color(0xFF38D352) else Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Pro Controls",
                tint = if (isProControlsOpen) Color.Black else Color.White
            )
        }

        // Camera Switch Button
        IconButton(
            onClick = onCameraSwitch,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = Icons.Default.FlipCameraAndroid,
                contentDescription = "Switch Camera",
                tint = Color.White
            )
        }
    }
}
