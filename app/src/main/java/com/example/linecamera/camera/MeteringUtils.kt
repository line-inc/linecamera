package com.example.linecamera.camera

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.MeteringRectangle
import kotlin.math.max
import kotlin.math.min

object MeteringUtils {

    fun calculateTapMeteringArea(
        tapXNorm: Float, // [0..1]
        tapYNorm: Float, // [0..1]
        sensorOrientation: Int,
        isFrontCamera: Boolean,
        characteristics: CameraCharacteristics,
        weight: Int = MeteringRectangle.METERING_WEIGHT_MAX
    ): MeteringRectangle {
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: Rect(0, 0, 2000, 2000)

        var px = tapXNorm
        var py = tapYNorm

        if (isFrontCamera) {
            px = 1.0f - px
        }

        val boxWidth = (activeArray.width() * 0.15f).toInt()
        val boxHeight = (activeArray.height() * 0.15f).toInt()

        val centerX = (px * activeArray.width()).toInt() + activeArray.left
        val centerY = (py * activeArray.height()).toInt() + activeArray.top

        val left = max(activeArray.left, centerX - boxWidth / 2)
        val top = max(activeArray.top, centerY - boxHeight / 2)
        val right = min(activeArray.right, left + boxWidth)
        val bottom = min(activeArray.bottom, top + boxHeight)

        val rect = Rect(left, top, right, bottom)

        return MeteringRectangle(rect, weight)
    }
}
