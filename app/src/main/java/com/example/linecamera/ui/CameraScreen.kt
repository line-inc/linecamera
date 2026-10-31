package com.example.linecamera.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.linecamera.model.CameraMode
import com.example.linecamera.model.FlashMode
import com.example.linecamera.model.FocusSetting
import com.example.linecamera.ui.components.*

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val uiState by viewModel.uiState.collectAsState()
    var isZoomRingExpanded by remember { mutableStateOf(false) }

    // Lock orientation ONLY when actively recording video (unspecified otherwise so app follows system rotation)
    LaunchedEffect(uiState.isRecording) {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = if (uiState.isRecording) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Refresh camera session on rotation to update preview buffer orientation
    LaunchedEffect(isLandscape) {
        if (!uiState.isRecording) {
            viewModel.startCameraEngine()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true &&
                permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    // Lifecycle Aware Camera Management (Prevents Camera Error Code 3 on app exit/re-enter)
    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (hasCameraPermission) {
                        viewModel.startCameraEngine()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopCameraEngine()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopCameraEngine()
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Line Camera requires Camera and Audio permissions",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38D352))
                ) {
                    Text(text = "Grant Permissions", color = Color.Black)
                }
            }
        }
        return
    }

    // Calculate Aspect Ratio strictly:
    // Portrait: Photo = 3:4, Video = 9:16 (or OpenGate 4:3)
    // Landscape: Photo = 4:3, Video = 16:9
    val targetAspectRatio = when {
        isLandscape -> {
            if (uiState.mode == CameraMode.VIDEO) {
                if (uiState.videoResolution == com.example.linecamera.model.VideoResolution.RES_OPENGATE_4_3) 4f / 3f else 16f / 9f
            } else {
                4f / 3f  // 4:3 Landscape Photo / RAW
            }
        }
        else -> { // Portrait
            if (uiState.mode == CameraMode.VIDEO) {
                if (uiState.videoResolution == com.example.linecamera.model.VideoResolution.RES_OPENGATE_4_3) 3f / 4f else 9f / 16f
            } else {
                3f / 4f  // 3:4 Portrait Photo / RAW
            }
        }
    }

    val activePreviewSize = if (uiState.mode == CameraMode.VIDEO) {
        uiState.hardwareInfo.videoPreviewSize
    } else {
        uiState.hardwareInfo.photoPreviewSize
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Viewfinder persistent composition with smooth buffer size updates
        Viewfinder(
            previewSize = activePreviewSize,
            targetAspectRatio = targetAspectRatio,
            isLandscape = isLandscape,
            currentZoomRatio = uiState.zoomRatio,
            maxZoomRatio = uiState.hardwareInfo.maxZoomRatio,
            onSurfaceReady = { surface -> viewModel.setPreviewSurface(surface) },
            onTapToFocus = { xNorm, yNorm -> viewModel.setFocusSetting(FocusSetting.TapToFocus(xNorm, yNorm)) },
            onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
            onPinchZoom = { isZoomRingExpanded = true },
            previewRotation = 0f
        )

        // 2. Grid, Level & Tap Target Overlay
        GridAndLevelOverlay(
            gridMode = uiState.gridMode,
            showLevel = uiState.showLevel,
            rollAngle = uiState.rollAngle,
            focusTargetPoint = uiState.focusTargetPoint
        )

        // 3. Shutter Flash Feedback
        AnimatedVisibility(
            visible = uiState.captureFlashState,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // 4. Video Recording Header Timer
        VideoRecordingHeader(
            isRecording = uiState.isRecording,
            durationSeconds = uiState.recordingDurationSeconds,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // 5. Top Left Row (Flash Button + Stereo Microphone Meter beside it)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode Button
            IconButton(
                onClick = {
                    val nextFlash = when (uiState.flashMode) {
                        FlashMode.OFF -> FlashMode.ON
                        FlashMode.ON -> FlashMode.AUTO
                        FlashMode.AUTO -> FlashMode.TORCH
                        FlashMode.TORCH -> FlashMode.OFF
                    }
                    viewModel.setFlashMode(nextFlash)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                val icon = when (uiState.flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                    FlashMode.TORCH -> Icons.Default.FlashOn
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Flash",
                    tint = if (uiState.flashMode != FlashMode.OFF) Color(0xFFFFB300) else Color.White
                )
            }

            // Stereo Microphone Meter beside Flash
            StereoMicMeter(
                isRecording = uiState.isRecording,
                audioGain = uiState.audioGain
            )
        }

        // 6. Top Right Controls (Pro Controls Only)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pro Controls Toggle Button
            IconButton(
                onClick = { viewModel.toggleProControls() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (uiState.isProControlsOpen) Color(0xFF38D352) else Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Pro Controls",
                    tint = if (uiState.isProControlsOpen) Color.Black else Color.White
                )
            }
        }

        // 7. Main Capture Controls (Responsive Layout with Camera Switch next to Shutter)
        if (isLandscape) {
            // Landscape Mode: Shutter button and controls on the RIGHT side
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Xiaomi-style Zoom Ring with Pop-in Arc Dial
                XiaomiZoomRing(
                    currentZoom = uiState.zoomRatio,
                    isExpanded = isZoomRingExpanded,
                    onExpandedChanged = { isZoomRingExpanded = it },
                    onZoomChanged = { viewModel.setZoomRatio(it) }
                )

                // Mode Selector
                ModeSelector(
                    currentMode = uiState.mode,
                    onModeSelected = { viewModel.setCameraMode(it) }
                )

                // Shutter Button
                ShutterButton(
                    mode = uiState.mode,
                    isRecording = uiState.isRecording,
                    onClick = { viewModel.onShutterClicked() }
                )

                // Camera Switch Button (Next to shutter button)
                IconButton(
                    onClick = { viewModel.switchCamera() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }

                // Gallery Shortcut
                GalleryShortcut(
                    latestUri = uiState.lastCapturedMediaUri
                )
            }
        } else {
            // Portrait Mode: Shutter button and controls at the BOTTOM
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Xiaomi-style Zoom Ring with Pop-in Arc Dial
                XiaomiZoomRing(
                    currentZoom = uiState.zoomRatio,
                    isExpanded = isZoomRingExpanded,
                    onExpandedChanged = { isZoomRingExpanded = it },
                    onZoomChanged = { viewModel.setZoomRatio(it) }
                )

                // Mode Selector
                ModeSelector(
                    currentMode = uiState.mode,
                    onModeSelected = { viewModel.setCameraMode(it) }
                )

                // Shutter Controls Row (Gallery on left, Shutter in center, Camera Switch on right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Shortcut
                    GalleryShortcut(
                        latestUri = uiState.lastCapturedMediaUri
                    )

                    // Shutter Button
                    ShutterButton(
                        mode = uiState.mode,
                        isRecording = uiState.isRecording,
                        onClick = { viewModel.onShutterClicked() }
                    )

                    // Camera Switch Button (Right next to shutter button)
                    IconButton(
                        onClick = { viewModel.switchCamera() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 8. Pro Controls Panel Drawer (ON THE LEFT & COMPACT WITH DEDICATED LENS TAB)
        ProControlsPanel(
            isOpen = uiState.isProControlsOpen,
            uiState = uiState,
            onIsoChanged = { viewModel.setIsoSetting(it) },
            onShutterSpeedChanged = { viewModel.setShutterSpeedSetting(it) },
            onFocusChanged = { viewModel.setFocusSetting(it) },
            onWbChanged = { viewModel.setWhiteBalance(it) },
            onEvChanged = { viewModel.setExposureCompensation(it) },
            onOisToggle = { viewModel.toggleOis() },
            onEisToggle = { viewModel.toggleEis() },
            onLensCorrectionToggle = { viewModel.toggleLensCorrection() },
            onNoiseReductionToggle = { viewModel.toggleNoiseReduction() },
            onSharpeningToggle = { viewModel.toggleSharpening() },
            onFpsChanged = { viewModel.setVideoFps(it) },
            onResolutionChanged = { viewModel.setVideoResolution(it) },
            onVideoCodecChanged = { viewModel.setVideoCodec(it) },
            onVideoBitrateChanged = { viewModel.setVideoBitrate(it) },
            onGridModeChanged = { viewModel.setGridMode(it) },
            onToggleLevel = { viewModel.toggleLevel() },
            onToggleHistogram = { viewModel.toggleHistogram() },
            onZoomChanged = { viewModel.setZoomRatio(it) },
            onMainLensSelected = { viewModel.switchToMainLens() },
            onUltraWideLensSelected = { viewModel.switchToUltraWideLens() },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 9. Error Message Notification
        uiState.errorMessage?.let { errorMsg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                containerColor = Color(0xFFD32F2F)
            ) {
                Text(text = errorMsg, color = Color.White)
            }
        }
    }
}
