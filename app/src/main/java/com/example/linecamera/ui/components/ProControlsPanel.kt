package com.example.linecamera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linecamera.model.*

@Composable
fun ProControlsPanel(
    isOpen: Boolean,
    uiState: CameraUiState,
    onIsoChanged: (IsoSetting) -> Unit,
    onShutterSpeedChanged: (ShutterSpeedSetting) -> Unit,
    onFocusChanged: (FocusSetting) -> Unit,
    onWbChanged: (WhiteBalancePreset) -> Unit,
    onEvChanged: (Int) -> Unit,
    onOisToggle: () -> Unit,
    onEisToggle: () -> Unit,
    onLensCorrectionToggle: () -> Unit,
    onNoiseReductionToggle: () -> Unit,
    onSharpeningToggle: () -> Unit,
    onFpsChanged: (VideoFps) -> Unit,
    onResolutionChanged: (VideoResolution) -> Unit,
    onVideoCodecChanged: (VideoCodec) -> Unit,
    onVideoBitrateChanged: (VideoBitrate) -> Unit,
    onGridModeChanged: (GridMode) -> Unit,
    onToggleLevel: () -> Unit,
    onToggleHistogram: () -> Unit,
    onZoomChanged: (Float) -> Unit,
    onMainLensSelected: () -> Unit,
    onUltraWideLensSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        var activeTab by remember { mutableStateOf("LENS") }
        val detailScrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(240.dp)
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(Color.Black.copy(alpha = 0.9f))
                .padding(8.dp)
        ) {
            // Left Category Navigation Rail
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val tabs = listOf("LENS", "ISO", "SS", "FOCUS", "WB", "EV", "VIDEO", "TOOLS")
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray.copy(alpha = 0.5f))
                            .clickable { activeTab = tab }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Detail Control Panel (Compact & Scrollable)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(detailScrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PRO: $activeTab",
                    color = Color(0xFF38D352),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                when (activeTab) {
                    "LENS" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Lens Presets:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (uiState.zoomRatio < 0.9f) Color(0xFF38D352) else Color.DarkGray)
                                        .clickable { onUltraWideLensSelected() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "0.6x Ultra", color = if (uiState.zoomRatio < 0.9f) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (uiState.zoomRatio in 0.9f..1.5f) Color(0xFF38D352) else Color.DarkGray)
                                        .clickable { onMainLensSelected() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "1.0x Main", color = if (uiState.zoomRatio in 0.9f..1.5f) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = String.format(java.util.Locale.US, "Zoom: %.1fx", uiState.zoomRatio), color = Color.White, fontSize = 11.sp)
                            
                            Slider(
                                value = uiState.zoomRatio,
                                onValueChange = { onZoomChanged(it) },
                                valueRange = 0.5f..100.0f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF38D352), activeTrackColor = Color(0xFF38D352))
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2.0f, 5.0f, 10.0f, 100.0f).forEach { z ->
                                    val isSelected = kotlin.math.abs(uiState.zoomRatio - z) < 0.1f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                            .clickable { onZoomChanged(z) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "${z.toInt()}x", color = if (isSelected) Color.Black else Color.White, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }

                    "ISO" -> {
                        val isAuto = uiState.isoSetting is IsoSetting.Auto
                        val currentIso = (uiState.isoSetting as? IsoSetting.Manual)?.iso ?: 400
                        val hwMax = uiState.hardwareInfo.isoRange?.endInclusive ?: 3200
                        val maxIso = hwMax.coerceAtLeast(19500)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Auto ISO", color = Color.White, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isAuto,
                                onCheckedChange = { checked ->
                                    if (checked) onIsoChanged(IsoSetting.Auto)
                                    else onIsoChanged(IsoSetting.Manual(currentIso))
                                }
                            )
                        }

                        if (!isAuto) {
                            Text(text = "ISO: $currentIso", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = currentIso.toFloat().coerceIn(50f, maxIso.toFloat()),
                                onValueChange = { onIsoChanged(IsoSetting.Manual(it.toInt())) },
                                valueRange = 50f..maxIso.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF38D352), activeTrackColor = Color(0xFF38D352))
                            )
                        }
                    }

                    "SS" -> {
                        val isAuto = uiState.shutterSpeedSetting is ShutterSpeedSetting.Auto
                        val currentSs = (uiState.shutterSpeedSetting as? ShutterSpeedSetting.Manual)?.durationNanos ?: 10_000_000L
                        val range = uiState.hardwareInfo.shutterSpeedRange ?: (1_000_000L..1_000_000_000L)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Auto Shutter", color = Color.White, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isAuto,
                                onCheckedChange = { checked ->
                                    if (checked) onShutterSpeedChanged(ShutterSpeedSetting.Auto)
                                    else onShutterSpeedChanged(ShutterSpeedSetting.Manual(currentSs))
                                }
                            )
                        }

                        if (!isAuto) {
                            val manualSs = uiState.shutterSpeedSetting as? ShutterSpeedSetting.Manual
                            Text(text = "Speed: ${manualSs?.toFractionString() ?: "1/100s"}", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = currentSs.toFloat(),
                                onValueChange = { onShutterSpeedChanged(ShutterSpeedSetting.Manual(it.toLong())) },
                                valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF38D352), activeTrackColor = Color(0xFF38D352))
                            )
                        }
                    }

                    "FOCUS" -> {
                        val isDefault = uiState.focusSetting is FocusSetting.Default
                        val isContinuous = uiState.focusSetting is FocusSetting.Continuous
                        val isManual = uiState.focusSetting is FocusSetting.Manual
                        val currentDist = (uiState.focusSetting as? FocusSetting.Manual)?.distanceDiopters ?: 0f
                        val maxDist = uiState.hardwareInfo.minFocusDistance

                        Text(text = "Focus Mode:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDefault) Color(0xFF38D352) else Color.DarkGray)
                                    .clickable { onFocusChanged(FocusSetting.Default) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = "Default", color = if (isDefault) Color.Black else Color.White, fontSize = 9.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isContinuous) Color(0xFF38D352) else Color.DarkGray)
                                    .clickable { onFocusChanged(FocusSetting.Continuous) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = "Continuous", color = if (isContinuous) Color.Black else Color.White, fontSize = 9.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isManual) Color(0xFF38D352) else Color.DarkGray)
                                    .clickable { onFocusChanged(FocusSetting.Manual(maxDist / 2f)) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = "Manual", color = if (isManual) Color.Black else Color.White, fontSize = 9.sp)
                            }
                        }

                        if (isManual && maxDist > 0f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = String.format(java.util.Locale.US, "Focus Diopter: %.2f", currentDist), color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = currentDist,
                                onValueChange = { onFocusChanged(FocusSetting.Manual(it)) },
                                valueRange = 0f..maxDist,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF38D352), activeTrackColor = Color(0xFF38D352))
                            )
                        }
                    }

                    "WB" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            WhiteBalancePreset.entries.forEach { preset ->
                                val isSelected = uiState.whiteBalance == preset
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                        .clickable { onWbChanged(preset) }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    "EV" -> {
                        val evRange = uiState.hardwareInfo.evRange ?: (-3..3)
                        Text(text = "Exposure: ${uiState.exposureCompensation}", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = uiState.exposureCompensation.toFloat(),
                            onValueChange = { onEvChanged(it.toInt()) },
                            valueRange = evRange.start.toFloat()..evRange.endInclusive.toFloat(),
                            steps = (evRange.endInclusive - evRange.start - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38D352), activeTrackColor = Color(0xFF38D352))
                        )
                    }

                    "VIDEO" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "FPS:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoFps.entries.forEach { fps ->
                                    val isSelected = uiState.videoFps == fps
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                            .clickable { onFpsChanged(fps) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "${fps.fps} FPS", color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Resolution:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            VideoResolution.entries.forEach { res ->
                                val isSelected = uiState.videoResolution == res
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                        .clickable { onResolutionChanged(res) }
                                        .padding(8.dp)
                                ) {
                                    Text(text = res.label, color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Codec:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoCodec.entries.forEach { codec ->
                                    val isSelected = uiState.videoCodec == codec
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                            .clickable { onVideoCodecChanged(codec) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = codec.label, color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Bit Rate:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                VideoBitrate.entries.forEach { bitrate ->
                                    val isSelected = uiState.videoBitrate == bitrate
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                            .clickable { onVideoBitrateChanged(bitrate) }
                                            .padding(8.dp)
                                    ) {
                                        Text(text = bitrate.label, color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    "TOOLS" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Lens Correction
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Lens Correction", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.isLensCorrectionEnabled,
                                    onCheckedChange = { onLensCorrectionToggle() }
                                )
                            }

                            // Reduce Noise
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Reduce Noise", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.isNoiseReductionEnabled,
                                    onCheckedChange = { onNoiseReductionToggle() }
                                )
                            }

                            // Increase Sharpening
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Increase Sharpening", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.isSharpeningEnabled,
                                    onCheckedChange = { onSharpeningToggle() }
                                )
                            }

                            // OIS Toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "OIS (Optical)", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.isOisEnabled,
                                    onCheckedChange = { onOisToggle() }
                                )
                            }

                            // EIS Toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "EIS (Electronic)", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.isEisEnabled,
                                    onCheckedChange = { onEisToggle() }
                                )
                            }

                            Text(text = "Grid:", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GridMode.entries.forEach { grid ->
                                    val isSelected = uiState.gridMode == grid
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) Color(0xFF38D352) else Color.DarkGray)
                                            .clickable { onGridModeChanged(grid) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = when (grid) {
                                                GridMode.NONE -> "Off"
                                                GridMode.GRID_3X3 -> "3x3"
                                                GridMode.GOLDEN_RATIO -> "Golden"
                                            },
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Spirit Level", color = Color.White, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = uiState.showLevel,
                                    onCheckedChange = { onToggleLevel() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
