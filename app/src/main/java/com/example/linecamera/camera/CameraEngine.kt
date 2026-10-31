package com.example.linecamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import com.example.linecamera.media.MediaStoreManager
import com.example.linecamera.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "CameraEngine"

class CameraEngine(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val prefs = context.getSharedPreferences("line_camera_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Background Thread
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Camera Hardware
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var characteristics: CameraCharacteristics? = null

    // Surfaces
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var videoOutputUri: Uri? = null
    private var videoContentValues: android.content.ContentValues? = null

    // State
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var currentOrientationDegrees: Int = getInitialOrientationDegrees(context)
    private var recordingJob: Job? = null
    private var focusClearJob: Job? = null

    // RAW Capture Coordination
    private var pendingRawImage: Image? = null
    private var pendingRawResult: TotalCaptureResult? = null

    init {
        val (backId, frontId) = CameraCharacteristicsUtil.findBackAndFrontCameraIds(cameraManager)
        val initialId = backId ?: "0"
        val info = CameraCharacteristicsUtil.getCameraHardwareInfo(cameraManager, initialId)

        // Restore persisted user settings
        val savedMode = CameraMode.valueOf(prefs.getString("pref_mode", CameraMode.PHOTO.name) ?: CameraMode.PHOTO.name)
        val savedIso = if (prefs.getBoolean("pref_iso_auto", true)) IsoSetting.Auto else IsoSetting.Manual(prefs.getInt("pref_iso_val", 400))
        val savedSs = if (prefs.getBoolean("pref_ss_auto", true)) ShutterSpeedSetting.Auto else ShutterSpeedSetting.Manual(prefs.getLong("pref_ss_val", 10_000_000L))
        val savedWb = WhiteBalancePreset.entries.find { it.name == prefs.getString("pref_wb", WhiteBalancePreset.AUTO.name) } ?: WhiteBalancePreset.AUTO
        val savedEv = prefs.getInt("pref_ev", 0)
        val savedOis = prefs.getBoolean("pref_ois", true)
        val savedEis = prefs.getBoolean("pref_eis", false)
        val savedLandscapeLock = prefs.getBoolean("pref_landscape_lock", true)
        val savedRotationOffset = prefs.getInt("pref_rotation_offset", 0)
        val savedLensCorrection = prefs.getBoolean("pref_lens_corr", true)
        val savedNoiseReduction = prefs.getBoolean("pref_noise_red", true)
        val savedSharpening = prefs.getBoolean("pref_sharpening", true)
        val savedAudioGain = prefs.getFloat("pref_audio_gain", 1.0f)
        val savedFps = VideoFps.entries.find { it.fps == prefs.getInt("pref_fps", 30) } ?: VideoFps.FPS_30
        val savedRes = VideoResolution.entries.find { it.name == prefs.getString("pref_res", VideoResolution.RES_1080P.name) } ?: VideoResolution.RES_1080P
        val savedCodec = VideoCodec.entries.find { it.name == prefs.getString("pref_codec", VideoCodec.HEVC.name) } ?: VideoCodec.HEVC
        val savedBitrate = VideoBitrate.entries.find { it.name == prefs.getString("pref_bitrate", VideoBitrate.MAX_160MBPS.name) } ?: VideoBitrate.MAX_160MBPS
        val savedGrid = GridMode.entries.find { it.name == prefs.getString("pref_grid", GridMode.NONE.name) } ?: GridMode.NONE
        val savedLevel = prefs.getBoolean("pref_level", false)
        val savedHistogram = prefs.getBoolean("pref_histogram", false)

        _uiState.update {
            it.copy(
                mode = savedMode,
                isoSetting = savedIso,
                shutterSpeedSetting = savedSs,
                whiteBalance = savedWb,
                exposureCompensation = savedEv,
                isOisEnabled = savedOis,
                isEisEnabled = savedEis,
                isLandscapeLocked = savedLandscapeLock,
                rotationOffsetDegrees = savedRotationOffset,
                isLensCorrectionEnabled = savedLensCorrection,
                isNoiseReductionEnabled = savedNoiseReduction,
                isSharpeningEnabled = savedSharpening,
                audioGain = savedAudioGain,
                videoFps = savedFps,
                videoResolution = savedRes,
                videoCodec = savedCodec,
                videoBitrate = savedBitrate,
                gridMode = savedGrid,
                showLevel = savedLevel,
                showHistogram = savedHistogram,
                cameraId = initialId,
                isFrontCamera = info.isFront,
                hardwareInfo = info
            )
        }
        refreshLatestThumbnail()
    }

    fun start() {
        startBackgroundThread()
        currentOrientationDegrees = getInitialOrientationDegrees(context)
    }

    fun stop() {
        closeCamera()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("LineCameraBg").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        backgroundThread = null
        backgroundHandler = null
    }

    fun setPreviewSurface(surface: Surface?) {
        previewSurface = surface
        if (surface != null && surface.isValid) {
            if (cameraDevice != null) {
                startCaptureSession()
            }
        } else {
            captureSession?.close()
            captureSession = null
        }
    }

    fun updateDeviceOrientation(degrees: Int) {
        currentOrientationDegrees = degrees
    }

    fun updateLevelAngles(pitch: Float, roll: Float) {
        _uiState.update { it.copy(pitchAngle = pitch, rollAngle = roll) }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String? = null) {
        startBackgroundThread()

        val targetId = cameraId ?: uiState.value.cameraId
        closeCamera()

        val info = CameraCharacteristicsUtil.getCameraHardwareInfo(cameraManager, targetId)
        characteristics = try {
            cameraManager.getCameraCharacteristics(targetId)
        } catch (e: Exception) {
            null
        }

        _uiState.update {
            it.copy(
                cameraId = targetId,
                isFrontCamera = info.isFront,
                hardwareInfo = info,
                errorMessage = null
            )
        }

        try {
            cameraManager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    if (previewSurface != null && previewSurface!!.isValid) {
                        startCaptureSession()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    captureSession = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    captureSession = null
                    _uiState.update { it.copy(errorMessage = "Camera error code: $error") }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Failed to open camera: ${e.localizedMessage}") }
        }
    }

    fun switchCamera() {
        if (uiState.value.isRecording) return
        val (backId, frontId) = CameraCharacteristicsUtil.findBackAndFrontCameraIds(cameraManager)
        val currentIsFront = uiState.value.isFrontCamera
        val nextId = if (currentIsFront) {
            backId ?: "0"
        } else {
            frontId ?: backId ?: "0"
        }
        openCamera(nextId)
    }

    fun switchToMainLens() {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(zoomRatio = 1.0f) }
        applyRepeatingPreviewRequest()
    }

    fun switchToUltraWideLens() {
        if (uiState.value.isRecording) return
        _uiState.update { it.copy(zoomRatio = 0.6f) }
        applyRepeatingPreviewRequest()
    }

    fun setCameraMode(mode: CameraMode) {
        if (uiState.value.mode == mode || uiState.value.isRecording) return
        _uiState.update { it.copy(mode = mode) }
        prefs.edit().putString("pref_mode", mode.name).apply()
        if (cameraDevice != null && previewSurface != null && previewSurface!!.isValid) {
            startCaptureSession()
        }
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val surface = previewSurface ?: return
        if (!surface.isValid) return

        val info = uiState.value.hardwareInfo
        val mode = uiState.value.mode

        captureSession?.close()
        captureSession = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null

        val surfaces = mutableListOf<Surface>(surface)

        when (mode) {
            CameraMode.PHOTO -> {
                val jpegSize = info.supportedJpegSizes.firstOrNull() ?: Size(1920, 1080)
                jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
                surfaces.add(jpegReader!!.surface)
            }
            CameraMode.RAW_PHOTO -> {
                if (info.isRawSupported && info.supportedRawSizes.isNotEmpty()) {
                    val rawSize = info.supportedRawSizes.first()
                    rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                        setOnImageAvailableListener({ reader ->
                            val img = reader.acquireLatestImage()
                            if (img != null) {
                                pendingRawImage?.close()
                                pendingRawImage = img
                                checkAndProcessRawCapture()
                            }
                        }, backgroundHandler)
                    }
                    surfaces.add(rawReader!!.surface)
                } else {
                    _uiState.update { it.copy(errorMessage = "RAW capture is not supported on this camera. Falling back to Photo.") }
                    val jpegSize = info.supportedJpegSizes.firstOrNull() ?: Size(1920, 1080)
                    jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
                    surfaces.add(jpegReader!!.surface)
                }
            }
            CameraMode.VIDEO -> {
                // Standard preview surface
            }
        }

        try {
            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    applyRepeatingPreviewRequest()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed silently")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session: ${e.message}", e)
        }
    }

    private fun buildCaptureRequest(template: Int, isRecording: Boolean = false): CaptureRequest.Builder? {
        val camera = cameraDevice ?: return null
        val surface = previewSurface ?: return null
        if (!surface.isValid) return null

        val state = uiState.value
        val info = state.hardwareInfo

        val builder = camera.createCaptureRequest(template)
        builder.addTarget(surface)

        if (isRecording && mediaRecorder != null) {
            try {
                builder.addTarget(mediaRecorder!!.surface)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding mediaRecorder target: ${e.message}")
            }
        }

        if (info.isFlashAvailable) {
            when (state.flashMode) {
                FlashMode.OFF -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.ON -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.AUTO -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.TORCH -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        when (val iso = state.isoSetting) {
            is IsoSetting.Auto -> {
                when (val ss = state.shutterSpeedSetting) {
                    is ShutterSpeedSetting.Auto -> {
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                    is ShutterSpeedSetting.Manual -> {
                        info.shutterSpeedRange?.let { range ->
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                            val clamped = ss.durationNanos.coerceIn(range.start, range.endInclusive)
                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clamped)
                        }
                    }
                }
            }
            is IsoSetting.Manual -> {
                info.isoRange?.let { range ->
                    val clampedIso = iso.iso.coerceIn(range.start, range.endInclusive)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)

                    if (state.shutterSpeedSetting is ShutterSpeedSetting.Manual) {
                        info.shutterSpeedRange?.let { ssRange ->
                            val clampedSs = state.shutterSpeedSetting.durationNanos.coerceIn(ssRange.start, ssRange.endInclusive)
                            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedSs)
                        }
                    }
                }
            }
        }

        when (val focus = state.focusSetting) {
            is FocusSetting.Default, is FocusSetting.Continuous -> {
                val afMode = if (state.mode == CameraMode.VIDEO) {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                } else {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                }
                builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            }
            is FocusSetting.Manual -> {
                if (info.minFocusDistance > 0f) {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    val clampedDist = focus.distanceDiopters.coerceIn(0f, info.minFocusDistance)
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDist)
                }
            }
            is FocusSetting.TapToFocus -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                characteristics?.let { chars ->
                    val tapRect = MeteringUtils.calculateTapMeteringArea(
                        focus.x, focus.y,
                        currentOrientationDegrees,
                        state.isFrontCamera,
                        chars
                    )
                    if (info.maxAfRegions > 0) {
                        builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(tapRect))
                    }
                    if (info.maxAeRegions > 0) {
                        builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(tapRect))
                    }
                }
            }
        }

        if (info.availableAwbModes.contains(state.whiteBalance.modeValue)) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, state.whiteBalance.modeValue)
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        info.evRange?.let { evRange ->
            val clampedEv = state.exposureCompensation.coerceIn(evRange.start, evRange.endInclusive)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
        }

        if (info.isOisSupported) {
            val oisVal = if (state.isOisEnabled) {
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            } else {
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
            }
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, oisVal)
        }

        if (state.mode == CameraMode.VIDEO && state.isEisEnabled) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        } else {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val distortionMode = if (state.isLensCorrectionEnabled) CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY else CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
            builder.set(CaptureRequest.DISTORTION_CORRECTION_MODE, distortionMode)
        }

        val noiseMode = if (state.isNoiseReductionEnabled) CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY else CaptureRequest.NOISE_REDUCTION_MODE_OFF
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseMode)

        val edgeMode = if (state.isSharpeningEnabled) CaptureRequest.EDGE_MODE_HIGH_QUALITY else CaptureRequest.EDGE_MODE_OFF
        builder.set(CaptureRequest.EDGE_MODE, edgeMode)

        // SMOOTH MULTI-CAMERA LOGICAL & CROP REGION ZOOM (0.5x/0.6x ULTRA-WIDE TO 100x TELEPHOTO)
        val clampedZoom = state.zoomRatio.coerceIn(0.1f, 100.0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
        } else {
            applyCropRegionZoom(builder, clampedZoom)
        }

        if (state.mode == CameraMode.VIDEO) {
            val targetFps = state.videoFps.fps
            val availableRanges = info.availableFpsRanges

            if (targetFps == 60) {
                val match60 = availableRanges.find { it[0] >= 60 }
                    ?: availableRanges.find { it[1] >= 60 }
                    ?: intArrayOf(60, 60)
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(match60[0], match60[1]))
                try {
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 16_666_666L)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val match30 = availableRanges.find { it[0] >= 30 }
                    ?: availableRanges.find { it[1] >= 30 }
                    ?: intArrayOf(30, 30)
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(match30[0], match30[1]))
                try {
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 33_333_333L)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return builder
    }

    fun applyRepeatingPreviewRequest() {
        val session = captureSession ?: return
        val state = uiState.value

        val template = if (state.mode == CameraMode.VIDEO && state.isRecording) {
            CameraDevice.TEMPLATE_RECORD
        } else {
            CameraDevice.TEMPLATE_PREVIEW
        }

        val builder = buildCaptureRequest(template, isRecording = state.isRecording) ?: return

        try {
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Preview capture result callback
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying capture request: ${e.message}", e)
        }
    }

    private fun applyCropRegionZoom(builder: CaptureRequest.Builder, zoomRatio: Float) {
        characteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { activeArray ->
            val effRatio = zoomRatio.coerceAtLeast(1.0f)
            val cropW = (activeArray.width() / effRatio).toInt().coerceAtLeast(1)
            val cropH = (activeArray.height() / effRatio).toInt().coerceAtLeast(1)
            val left = (activeArray.width() - cropW) / 2
            val top = (activeArray.height() - cropH) / 2
            val cropRect = Rect(left, top, left + cropW, top + cropH)
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
        }
    }

    fun setIsoSetting(iso: IsoSetting) {
        _uiState.update { it.copy(isoSetting = iso) }
        prefs.edit().apply {
            when (iso) {
                is IsoSetting.Auto -> {
                    putBoolean("pref_iso_auto", true)
                }
                is IsoSetting.Manual -> {
                    putBoolean("pref_iso_auto", false)
                    putInt("pref_iso_val", iso.iso)
                }
            }
            apply()
        }
        applyRepeatingPreviewRequest()
    }

    fun setShutterSpeedSetting(ss: ShutterSpeedSetting) {
        _uiState.update { it.copy(shutterSpeedSetting = ss) }
        prefs.edit().apply {
            when (ss) {
                is ShutterSpeedSetting.Auto -> {
                    putBoolean("pref_ss_auto", true)
                }
                is ShutterSpeedSetting.Manual -> {
                    putBoolean("pref_ss_auto", false)
                    putLong("pref_ss_val", ss.durationNanos)
                }
            }
            apply()
        }
        applyRepeatingPreviewRequest()
    }

    fun setFocusSetting(focus: FocusSetting) {
        _uiState.update { it.copy(focusSetting = focus) }
        focusClearJob?.cancel()
        if (focus is FocusSetting.TapToFocus) {
            _uiState.update { it.copy(focusTargetPoint = Pair(focus.x, focus.y)) }
            focusClearJob = scope.launch {
                delay(1500)
                _uiState.update { it.copy(focusTargetPoint = null) }
            }
        } else {
            _uiState.update { it.copy(focusTargetPoint = null) }
        }
        applyRepeatingPreviewRequest()
    }

    fun setWhiteBalance(wb: WhiteBalancePreset) {
        _uiState.update { it.copy(whiteBalance = wb) }
        prefs.edit().putString("pref_wb", wb.name).apply()
        applyRepeatingPreviewRequest()
    }

    fun setExposureCompensation(ev: Int) {
        _uiState.update { it.copy(exposureCompensation = ev) }
        prefs.edit().putInt("pref_ev", ev).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleOis() {
        val newVal = !uiState.value.isOisEnabled
        _uiState.update { it.copy(isOisEnabled = newVal) }
        prefs.edit().putBoolean("pref_ois", newVal).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleEis() {
        val newVal = !uiState.value.isEisEnabled
        _uiState.update { it.copy(isEisEnabled = newVal) }
        prefs.edit().putBoolean("pref_eis", newVal).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleLensCorrection() {
        val newVal = !uiState.value.isLensCorrectionEnabled
        _uiState.update { it.copy(isLensCorrectionEnabled = newVal) }
        prefs.edit().putBoolean("pref_lens_corr", newVal).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleNoiseReduction() {
        val newVal = !uiState.value.isNoiseReductionEnabled
        _uiState.update { it.copy(isNoiseReductionEnabled = newVal) }
        prefs.edit().putBoolean("pref_noise_red", newVal).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleSharpening() {
        val newVal = !uiState.value.isSharpeningEnabled
        _uiState.update { it.copy(isSharpeningEnabled = newVal) }
        prefs.edit().putBoolean("pref_sharpening", newVal).apply()
        applyRepeatingPreviewRequest()
    }

    fun toggleOrientationLock() {
        val newVal = !uiState.value.isLandscapeLocked
        _uiState.update { it.copy(isLandscapeLocked = newVal) }
        prefs.edit().putBoolean("pref_landscape_lock", newVal).apply()
    }

    fun setRotationOffsetDegrees(degrees: Int) {
        _uiState.update { it.copy(rotationOffsetDegrees = degrees) }
        prefs.edit().putInt("pref_rotation_offset", degrees).apply()
    }

    fun setAudioGain(gain: Float) {
        val clamped = gain.coerceIn(0.0f, 2.0f)
        _uiState.update { it.copy(audioGain = clamped) }
        prefs.edit().putFloat("pref_audio_gain", clamped).apply()
    }

    fun setVideoCodec(codec: VideoCodec) {
        _uiState.update { it.copy(videoCodec = codec) }
        prefs.edit().putString("pref_codec", codec.name).apply()
    }

    fun setVideoBitrate(bitrate: VideoBitrate) {
        _uiState.update { it.copy(videoBitrate = bitrate) }
        prefs.edit().putString("pref_bitrate", bitrate.name).apply()
    }

    fun setZoomRatio(ratio: Float) {
        val max = 100.0f
        val clamped = ratio.coerceIn(0.1f, max)
        _uiState.update { it.copy(zoomRatio = clamped) }
        applyRepeatingPreviewRequest()
    }

    fun setFlashMode(flashMode: FlashMode) {
        _uiState.update { it.copy(flashMode = flashMode) }
        applyRepeatingPreviewRequest()
    }

    fun setVideoFps(fps: VideoFps) {
        _uiState.update { it.copy(videoFps = fps) }
        prefs.edit().putInt("pref_fps", fps.fps).apply()
        applyRepeatingPreviewRequest()
    }

    fun setVideoResolution(res: VideoResolution) {
        _uiState.update { it.copy(videoResolution = res) }
        prefs.edit().putString("pref_res", res.name).apply()
        if (uiState.value.mode == CameraMode.VIDEO && cameraDevice != null && previewSurface != null) {
            startCaptureSession()
        }
    }

    fun setGridMode(gridMode: GridMode) {
        _uiState.update { it.copy(gridMode = gridMode) }
        prefs.edit().putString("pref_grid", gridMode.name).apply()
    }

    fun toggleLevel() {
        val newVal = !uiState.value.showLevel
        _uiState.update { it.copy(showLevel = newVal) }
        prefs.edit().putBoolean("pref_level", newVal).apply()
    }

    fun toggleHistogram() {
        val newVal = !uiState.value.showHistogram
        _uiState.update { it.copy(showHistogram = newVal) }
        prefs.edit().putBoolean("pref_histogram", newVal).apply()
    }

    fun toggleProControls() {
        _uiState.update { it.copy(isProControlsOpen = !it.isProControlsOpen) }
    }

    fun onShutterClicked() {
        when (uiState.value.mode) {
            CameraMode.PHOTO -> captureJpegPhoto()
            CameraMode.RAW_PHOTO -> captureRawPhoto()
            CameraMode.VIDEO -> {
                if (uiState.value.isRecording) {
                    stopVideoRecording()
                } else {
                    startVideoRecording()
                }
            }
        }
    }

    private fun captureJpegPhoto() {
        val session = captureSession ?: return
        val reader = jpegReader ?: return

        try {
            triggerShutterFlash()

            val captureBuilder = buildCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) ?: return
            captureBuilder.addTarget(reader.surface)

            val jpegOrientation = getSensorOrientationForCapture(characteristics, uiState.value.isFrontCamera)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0)

            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    scope.launch {
                        val savedUri = MediaStoreManager.saveJpegImage(context, bytes, jpegOrientation)
                        if (savedUri != null) {
                            _uiState.update { it.copy(lastCapturedMediaUri = savedUri, lastCapturedIsVideo = false) }
                        } else {
                            _uiState.update { it.copy(errorMessage = "Failed to save photo") }
                        }
                    }
                }
            }, backgroundHandler)

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    applyRepeatingPreviewRequest()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing photo: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Capture failed: ${e.localizedMessage}") }
        }
    }

    private fun captureRawPhoto() {
        val session = captureSession ?: return
        val reader = rawReader ?: return

        if (!uiState.value.hardwareInfo.isRawSupported) {
            _uiState.update { it.copy(errorMessage = "RAW capture is not supported on this camera") }
            return
        }

        try {
            triggerShutterFlash()

            val captureBuilder = buildCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE) ?: return
            captureBuilder.addTarget(reader.surface)

            pendingRawImage?.close()
            pendingRawImage = null
            pendingRawResult = null

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    pendingRawResult = result
                    checkAndProcessRawCapture()
                    applyRepeatingPreviewRequest()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing RAW photo: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "RAW capture failed: ${e.localizedMessage}") }
        }
    }

    private fun checkAndProcessRawCapture() {
        val image = pendingRawImage ?: return
        val result = pendingRawResult ?: return
        val chars = characteristics ?: return

        pendingRawImage = null
        pendingRawResult = null

        val jpegOrientation = getSensorOrientationForCapture(characteristics, uiState.value.isFrontCamera)

        scope.launch(Dispatchers.IO) {
            try {
                val savedUri = MediaStoreManager.createDngOutputStream(context) { outputStream, _ ->
                    try {
                        val dngCreator = DngCreator(chars, result)
                        dngCreator.setOrientation(getDngOrientation(jpegOrientation))
                        dngCreator.writeImage(outputStream, image)
                        dngCreator.close()
                        image.close()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "DngCreator exception: ${e.message}", e)
                        image.close()
                        false
                    }
                }

                if (savedUri != null) {
                    _uiState.update { it.copy(lastCapturedMediaUri = savedUri, lastCapturedIsVideo = false) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Failed to write DNG file") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RAW processing exception: ${e.message}", e)
                image.close()
                _uiState.update { it.copy(errorMessage = "RAW processing error: ${e.localizedMessage}") }
            }
        }
    }

    private fun getDngOrientation(degrees: Int): Int =
        when (degrees) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }

    private fun startVideoRecording() {
        val camera = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            scope.launch {
                val outputPair = MediaStoreManager.createVideoOutputUri(context)
                if (outputPair == null) {
                    _uiState.update { it.copy(errorMessage = "Failed to create video file destination") }
                    return@launch
                }

                videoOutputUri = outputPair.first
                videoContentValues = outputPair.second

                val targetFps = uiState.value.videoFps.fps
                val codec = uiState.value.videoCodec
                val bitrate = uiState.value.videoBitrate.bps

                mediaRecorder = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setVideoSource(MediaRecorder.VideoSource.SURFACE)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                        val pfd = context.contentResolver.openFileDescriptor(videoOutputUri!!, "rw")
                        if (pfd != null) {
                            setOutputFile(pfd.fileDescriptor)
                        }

                        // HIGH QUALITY STEREO AUDIO SETTINGS (48 kHz, 192 kbps AAC)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(48000)
                        setAudioEncodingBitRate(192_000)
                        setAudioChannels(2)

                        val videoEnc = if (codec == VideoCodec.HEVC) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264
                        setVideoEncoder(videoEnc)

                        val res = uiState.value.videoResolution.size
                        setVideoSize(res.width, res.height)
                        setVideoFrameRate(targetFps)
                        setVideoEncodingBitRate(bitrate)

                        val hint = getSensorOrientationForCapture(characteristics, uiState.value.isFrontCamera)
                        setOrientationHint(hint)

                        prepare()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MediaRecorder prepare failed: ${e.message}")
                    _uiState.update { it.copy(errorMessage = "Video recorder error: ${e.localizedMessage}") }
                    return@launch
                }

                val recorderSurface = mediaRecorder!!.surface
                captureSession?.close()

                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(surface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val builder = buildCaptureRequest(CameraDevice.TEMPLATE_RECORD, isRecording = true)
                            if (builder != null) {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)

                                mediaRecorder?.start()
                                _uiState.update { it.copy(isRecording = true, recordingDurationSeconds = 0) }

                                val startTime = SystemClock.elapsedRealtime()
                                recordingJob = scope.launch {
                                    while (isActive && uiState.value.isRecording) {
                                        val elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000
                                        _uiState.update { it.copy(recordingDurationSeconds = elapsedSec) }
                                        delay(500)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting recorder: ${e.message}", e)
                            _uiState.update { it.copy(errorMessage = "Failed to start recording") }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Video capture session configuration failed silently")
                    }
                }, backgroundHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up video recording: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Video setup error: ${e.localizedMessage}") }
        }
    }

    private fun stopVideoRecording() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null

            val uri = videoOutputUri
            val values = videoContentValues
            if (uri != null && values != null) {
                scope.launch {
                    MediaStoreManager.finalizeVideoUri(context, uri, values)
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingDurationSeconds = 0,
                            lastCapturedMediaUri = uri,
                            lastCapturedIsVideo = true
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isRecording = false, recordingDurationSeconds = 0) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Error saving video") }
        }

        if (cameraDevice != null && previewSurface != null) {
            startCaptureSession()
        }
    }

    private fun triggerShutterFlash() {
        scope.launch {
            _uiState.update { it.copy(captureFlashState = true) }
            delay(100)
            _uiState.update { it.copy(captureFlashState = false) }
        }
    }

    private fun refreshLatestThumbnail() {
        scope.launch {
            val latest = MediaStoreManager.getLatestMediaThumbnail(context)
            if (latest != null) {
                _uiState.update { it.copy(lastCapturedMediaUri = latest) }
            }
        }
    }

    private fun closeCamera() {
        recordingJob?.cancel()
        recordingJob = null
        focusClearJob?.cancel()
        focusClearJob = null

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        jpegReader?.close()
        jpegReader = null

        rawReader?.close()
        rawReader = null

        pendingRawImage?.close()
        pendingRawResult = null
    }

    private fun getInitialOrientationDegrees(context: Context): Int {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun getSensorOrientationForCapture(characteristics: CameraCharacteristics?, isFront: Boolean): Int {
        val sensorOrientation = characteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val base = if (isFront) {
            (sensorOrientation + currentOrientationDegrees + 360) % 360
        } else {
            (sensorOrientation - currentOrientationDegrees + 360) % 360
        }
        return (base + uiState.value.rotationOffsetDegrees + 360) % 360
    }
}
