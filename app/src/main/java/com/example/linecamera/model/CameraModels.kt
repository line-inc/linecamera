package com.example.linecamera.model

import android.net.Uri
import android.util.Size

enum class CameraMode {
    PHOTO,
    RAW_PHOTO,
    VIDEO
}

enum class FlashMode {
    OFF,
    ON,
    AUTO,
    TORCH
}

enum class GridMode {
    NONE,
    GRID_3X3,
    GOLDEN_RATIO
}

enum class VideoFps(val fps: Int) {
    FPS_30(30),
    FPS_60(60)
}

enum class VideoCodec(val label: String) {
    HEVC("HEVC (H.265)"),
    H264("H.264")
}

enum class VideoBitrate(val label: String, val bps: Int) {
    MAX_160MBPS("Max (160Mbps)", 160_000_000),
    HIGH_67MBPS("High (67Mbps)", 67_000_000),
    MEDIUM_50MBPS("Medium (50Mbps)", 50_000_000),
    LOW_33MBPS("Low (33Mbps)", 33_000_000)
}

enum class VideoResolution(val label: String, val size: Size) {
    RES_4K("4K", Size(3840, 2160)),
    RES_OPENGATE_4_3("OpenGate 4:3", Size(1440, 1080)),
    RES_1080P("1080p", Size(1920, 1080)),
    RES_720P("720p", Size(1280, 720))
}

sealed class IsoSetting {
    object Auto : IsoSetting()
    data class Manual(val iso: Int) : IsoSetting()
}

sealed class ShutterSpeedSetting {
    object Auto : ShutterSpeedSetting()
    data class Manual(val durationNanos: Long) : ShutterSpeedSetting() {
        fun toFractionString(): String {
            if (durationNanos <= 0) return "Auto"
            val seconds = durationNanos / 1_000_000_000.0
            return if (seconds < 1.0) {
                val denom = (1.0 / seconds).toInt()
                "1/$denom"
            } else {
                String.format(java.util.Locale.US, "%.1fs", seconds)
            }
        }
    }
}

sealed class FocusSetting {
    object Default : FocusSetting()
    object Continuous : FocusSetting()
    data class Manual(val distanceDiopters: Float) : FocusSetting()
    data class TapToFocus(val x: Float, val y: Float) : FocusSetting()
}

enum class WhiteBalancePreset(val label: String, val modeValue: Int) {
    AUTO("Auto", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT("Daylight", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("Cloudy", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    INCANDESCENT("Incandescent", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("Fluorescent", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
    TWILIGHT("Twilight", android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_TWILIGHT)
}

data class CameraHardwareInfo(
    val cameraId: String = "0",
    val isFront: Boolean = false,
    val isoRange: ClosedRange<Int>? = null,
    val shutterSpeedRange: ClosedRange<Long>? = null,
    val minFocusDistance: Float = 0f,
    val isOisSupported: Boolean = false,
    val isEisSupported: Boolean = false,
    val isRawSupported: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val maxAfRegions: Int = 0,
    val maxAeRegions: Int = 0,
    val availableAwbModes: List<Int> = emptyList(),
    val availableFpsRanges: List<IntArray> = emptyList(),
    val evRange: ClosedRange<Int>? = null,
    val evStep: Float = 0f,
    val maxZoomRatio: Float = 1.0f,
    val photoPreviewSize: Size = Size(1440, 1080),
    val videoPreviewSize: Size = Size(1920, 1080),
    val supportedPreviewSizes: List<Size> = emptyList(),
    val supportedJpegSizes: List<Size> = emptyList(),
    val supportedRawSizes: List<Size> = emptyList(),
    val supportedVideoSizes: List<Size> = emptyList()
)

data class CameraUiState(
    val mode: CameraMode = CameraMode.PHOTO,
    val cameraId: String = "0",
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val isoSetting: IsoSetting = IsoSetting.Auto,
    val shutterSpeedSetting: ShutterSpeedSetting = ShutterSpeedSetting.Auto,
    val focusSetting: FocusSetting = FocusSetting.Default,
    val whiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val exposureCompensation: Int = 0,
    val isOisEnabled: Boolean = true,
    val isEisEnabled: Boolean = false,
    val isLandscapeLocked: Boolean = true,
    val rotationOffsetDegrees: Int = 0,
    val isLensCorrectionEnabled: Boolean = true,
    val isNoiseReductionEnabled: Boolean = true,
    val isSharpeningEnabled: Boolean = true,
    val audioGain: Float = 1.0f,
    val zoomRatio: Float = 1.0f,
    val videoFps: VideoFps = VideoFps.FPS_30,
    val videoResolution: VideoResolution = VideoResolution.RES_1080P,
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val videoBitrate: VideoBitrate = VideoBitrate.MAX_160MBPS,
    val gridMode: GridMode = GridMode.NONE,
    val showLevel: Boolean = false,
    val showHistogram: Boolean = false,
    val isProControlsOpen: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0,
    val lastCapturedMediaUri: Uri? = null,
    val lastCapturedIsVideo: Boolean = false,
    val hardwareInfo: CameraHardwareInfo = CameraHardwareInfo(),
    val pitchAngle: Float = 0f,
    val rollAngle: Float = 0f,
    val errorMessage: String? = null,
    val captureFlashState: Boolean = false,
    val focusTargetPoint: Pair<Float, Float>? = null
)
