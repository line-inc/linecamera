package com.example.linecamera.ui

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import com.example.linecamera.camera.CameraEngine
import com.example.linecamera.model.*
import com.example.linecamera.sensors.LevelSensor
import com.example.linecamera.sensors.OrientationManager
import kotlinx.coroutines.flow.StateFlow

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraEngine = CameraEngine(application)
    val uiState: StateFlow<CameraUiState> = cameraEngine.uiState

    private val orientationManager = OrientationManager(application) { degrees ->
        cameraEngine.updateDeviceOrientation(degrees)
    }

    private val levelSensor = LevelSensor(application) { pitch, roll ->
        cameraEngine.updateLevelAngles(pitch, roll)
    }

    fun startCameraEngine() {
        cameraEngine.start()
        orientationManager.start()
        levelSensor.start()
        cameraEngine.openCamera()
    }

    fun stopCameraEngine() {
        orientationManager.stop()
        levelSensor.stop()
        cameraEngine.stop()
    }

    fun setPreviewSurface(surface: Surface?) {
        cameraEngine.setPreviewSurface(surface)
    }

    fun switchCamera() {
        cameraEngine.switchCamera()
    }

    fun switchToMainLens() {
        cameraEngine.switchToMainLens()
    }

    fun switchToUltraWideLens() {
        cameraEngine.switchToUltraWideLens()
    }

    fun setCameraMode(mode: CameraMode) {
        cameraEngine.setCameraMode(mode)
    }

    fun setIsoSetting(iso: IsoSetting) {
        cameraEngine.setIsoSetting(iso)
    }

    fun setShutterSpeedSetting(ss: ShutterSpeedSetting) {
        cameraEngine.setShutterSpeedSetting(ss)
    }

    fun setFocusSetting(focus: FocusSetting) {
        cameraEngine.setFocusSetting(focus)
    }

    fun setWhiteBalance(wb: WhiteBalancePreset) {
        cameraEngine.setWhiteBalance(wb)
    }

    fun setExposureCompensation(ev: Int) {
        cameraEngine.setExposureCompensation(ev)
    }

    fun toggleOis() {
        cameraEngine.toggleOis()
    }

    fun toggleEis() {
        cameraEngine.toggleEis()
    }

    fun toggleLensCorrection() {
        cameraEngine.toggleLensCorrection()
    }

    fun toggleNoiseReduction() {
        cameraEngine.toggleNoiseReduction()
    }

    fun toggleSharpening() {
        cameraEngine.toggleSharpening()
    }

    fun toggleOrientationLock() {
        cameraEngine.toggleOrientationLock()
    }

    fun setRotationOffsetDegrees(degrees: Int) {
        cameraEngine.setRotationOffsetDegrees(degrees)
    }

    fun setAudioGain(gain: Float) {
        cameraEngine.setAudioGain(gain)
    }

    fun setVideoCodec(codec: VideoCodec) {
        cameraEngine.setVideoCodec(codec)
    }

    fun setVideoBitrate(bitrate: VideoBitrate) {
        cameraEngine.setVideoBitrate(bitrate)
    }

    fun setZoomRatio(ratio: Float) {
        cameraEngine.setZoomRatio(ratio)
    }

    fun setFlashMode(flashMode: FlashMode) {
        cameraEngine.setFlashMode(flashMode)
    }

    fun setVideoFps(fps: VideoFps) {
        cameraEngine.setVideoFps(fps)
    }

    fun setVideoResolution(res: VideoResolution) {
        cameraEngine.setVideoResolution(res)
    }

    fun setGridMode(gridMode: GridMode) {
        cameraEngine.setGridMode(gridMode)
    }

    fun toggleLevel() {
        cameraEngine.toggleLevel()
    }

    fun toggleHistogram() {
        cameraEngine.toggleHistogram()
    }

    fun toggleProControls() {
        cameraEngine.toggleProControls()
    }

    fun onShutterClicked() {
        cameraEngine.onShutterClicked()
    }
}
