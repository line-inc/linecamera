package com.example.linecamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LensSwitchRow(
    isUltraWideActive: Boolean,
    onMainLensSelected: () -> Unit,
    onUltraWideLensSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 0.5x Ultra-Wide Lens
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isUltraWideActive) Color(0xFF38D352) else Color.Transparent)
                .clickable { onUltraWideLensSelected() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "0.5x",
                color = if (isUltraWideActive) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 1x Main Lens
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (!isUltraWideActive) Color(0xFF38D352) else Color.Transparent)
                .clickable { onMainLensSelected() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1x",
                color = if (!isUltraWideActive) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
