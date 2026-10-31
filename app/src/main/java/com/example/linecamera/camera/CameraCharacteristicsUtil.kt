package com.example.linecamera.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import android.view.SurfaceHolder
import com.example.linecamera.model.CameraHardwareInfo
import kotlin.math.abs

data class CameraLensInfo(
    val backMainId: String = "0",
    val ultraWideId: String? = null,
    val telephotoId: String? = null,
    val frontId: String? = null
)

object CameraCharacteristicsUtil {

    fun getCameraHardwareInfo(cameraManager: CameraManager, cameraId: String): CameraHardwareInfo {
        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            return CameraHardwareInfo(cameraId = cameraId)
        }

        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

        // Capabilities
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hasRawCapability = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        // Stream sizes
        val map: StreamConfigurationMap? = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList()?.sortedByDescending { it.width * it.height } ?: emptyList()
        val isRawSupported = hasRawCapability && rawSizes.isNotEmpty()

        // Flash
        val isFlashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        // AF/AE Regions
        val maxAfRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        val maxAeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

        // AWB Modes
        val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList()

        // Ranges
        val isoRangeUtil = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoClosedRange = isoRangeUtil?.let { it.lower..it.upper }

        val shutterRangeUtil = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val shutterClosedRange = shutterRangeUtil?.let { it.lower..it.upper }

        val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        // OIS
        val oisModes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
        val isOisSupported = oisModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        // EIS
        val eisModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
        val isEisSupported = eisModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

        // Target FPS ranges
        val stdFpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { intArrayOf(it.lower, it.upper) } ?: emptyList()

        val highSpeedFpsRanges = try {
            map?.highSpeedVideoFpsRanges?.map { intArrayOf(it.lower, it.upper) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val allFpsRanges = (stdFpsRanges + highSpeedFpsRanges).distinctBy { "${it[0]}_${it[1]}" }

        // EV
        val evRangeUtil = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evClosedRange = evRangeUtil?.let { it.lower..it.upper }
        val evRational = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val evStep = evRational?.toFloat() ?: 0.333f

        // Zoom (Up to 100x Zoom Support)
        val nativeMaxZoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 1.0f
        } else {
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        }
        val maxZoom = nativeMaxZoom.coerceAtLeast(100.0f)

        val previewSizes = map?.getOutputSizes(SurfaceHolder::class.java)?.toList()?.sortedByDescending { it.width * it.height } ?: emptyList()

        val photoPreviewSize = previewSizes.find {
            val r = it.width.toFloat() / it.height.toFloat()
            (abs(r - 4f / 3f) < 0.05f || abs(r - 3f / 4f) < 0.05f) && (it.width <= 1920 || it.height <= 1920)
        } ?: previewSizes.firstOrNull { it.width <= 1920 && it.height <= 1080 } ?: Size(1440, 1080)

        val videoPreviewSize = previewSizes.find {
            val r = it.width.toFloat() / it.height.toFloat()
            (abs(r - 16f / 9f) < 0.05f || abs(r - 9f / 16f) < 0.05f) && (it.width <= 1920 || it.height <= 1920)
        } ?: previewSizes.firstOrNull { it.width <= 1920 && it.height <= 1080 } ?: Size(1920, 1080)

        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList()?.sortedByDescending { it.width * it.height } ?: emptyList()
        val videoSizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList()?.sortedByDescending { it.width * it.height } ?: emptyList()

        return CameraHardwareInfo(
            cameraId = cameraId,
            isFront = isFront,
            isoRange = isoClosedRange,
            shutterSpeedRange = shutterClosedRange,
            minFocusDistance = minFocusDistance,
            isOisSupported = isOisSupported,
            isEisSupported = isEisSupported,
            isRawSupported = isRawSupported,
            isFlashAvailable = isFlashAvailable,
            maxAfRegions = maxAfRegions,
            maxAeRegions = maxAeRegions,
            availableAwbModes = awbModes,
            availableFpsRanges = allFpsRanges,
            evRange = evClosedRange,
            evStep = evStep,
            maxZoomRatio = maxZoom,
            photoPreviewSize = photoPreviewSize,
            videoPreviewSize = videoPreviewSize,
            supportedPreviewSizes = previewSizes,
            supportedJpegSizes = jpegSizes,
            supportedRawSizes = rawSizes,
            supportedVideoSizes = videoSizes
        )
    }

    fun findBackAndFrontCameraIds(cameraManager: CameraManager): Pair<String?, String?> {
        val lensInfo = discoverCameraLenses(cameraManager)
        return Pair(lensInfo.backMainId, lensInfo.frontId)
    }

    fun discoverCameraLenses(cameraManager: CameraManager): CameraLensInfo {
        var backMainId: String? = null
        var ultraWideId: String? = null
        var telephotoId: String? = null
        var frontId: String? = null

        val backCameraIds = mutableListOf<String>()

        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (frontId == null) frontId = id
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraIds.add(id)
                }
            }

            if (backCameraIds.isNotEmpty()) {
                backMainId = backCameraIds[0] // Camera ID "0" is Main
                if (backCameraIds.size >= 2) {
                    ultraWideId = backCameraIds[1] // Camera ID "2" / second back ID is Ultra-Wide on POCO/Xiaomi/Samsung
                }
                if (backCameraIds.size >= 3) {
                    telephotoId = backCameraIds[2] // Camera ID "3" / third back ID is Telephoto/Macro
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return CameraLensInfo(
            backMainId = backMainId ?: "0",
            ultraWideId = ultraWideId,
            telephotoId = telephotoId,
            frontId = frontId
        )
    }
}
