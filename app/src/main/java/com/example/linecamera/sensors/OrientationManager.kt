package com.example.linecamera.sensors

import android.content.Context
import android.view.OrientationEventListener

class OrientationManager(
    context: Context,
    private val onOrientationChanged: (orientationDegrees: Int) -> Unit
) {
    private var lastOrientationDegrees: Int = 0

    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val newDegrees = when (orientation) {
                in 45..134 -> 90
                in 135..224 -> 180
                in 225..314 -> 270
                else -> 0
            }
            if (newDegrees != lastOrientationDegrees) {
                lastOrientationDegrees = newDegrees
                onOrientationChanged(newDegrees)
            }
        }
    }

    fun start() {
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
    }

    fun stop() {
        listener.disable()
    }

    fun getOrientationDegrees(): Int = lastOrientationDegrees
}
