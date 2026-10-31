package com.example.linecamera.ui.components

import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

@Composable
fun Viewfinder(
    previewSize: Size,
    targetAspectRatio: Float,
    isLandscape: Boolean,
    currentZoomRatio: Float,
    maxZoomRatio: Float,
    onSurfaceReady: (Surface?) -> Unit,
    onTapToFocus: (xNorm: Float, yNorm: Float) -> Unit,
    onZoomChanged: (newZoomRatio: Float) -> Unit,
    onPinchZoom: () -> Unit = {},
    previewRotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val updatedZoom by rememberUpdatedState(currentZoomRatio)
    val updatedMaxZoom by rememberUpdatedState(maxZoomRatio)
    val updatedOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val updatedOnTapToFocus by rememberUpdatedState(onTapToFocus)
    val updatedOnPinchZoom by rememberUpdatedState(onPinchZoom)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val sizingModifier = if (isLandscape) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(targetAspectRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(targetAspectRatio, matchHeightConstraintsFirst = false)
        }

        Box(
            modifier = sizingModifier
                .graphicsLayer(rotationZ = previewRotation)
                .onGloballyPositioned { coordinates ->
                    viewSize = coordinates.size
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (abs(zoom - 1f) > 0.005f) {
                            val newZoom = (updatedZoom * zoom).coerceIn(0.6f, updatedMaxZoom)
                            updatedOnZoomChanged(newZoom)
                            updatedOnPinchZoom()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (viewSize.width > 0 && viewSize.height > 0) {
                            val xNorm = (offset.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                            val yNorm = (offset.y / viewSize.height.toFloat()).coerceIn(0f, 1f)
                            updatedOnTapToFocus(xNorm, yNorm)
                        }
                    }
                }
        ) {
            // Key SurfaceView on previewSize and targetAspectRatio to force clean recreation on mode switch without stretching
            key(previewSize, targetAspectRatio) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            holder.setFixedSize(previewSize.width, previewSize.height)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    holder.setFixedSize(previewSize.width, previewSize.height)
                                    onSurfaceReady(holder.surface)
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    onSurfaceReady(null)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                    if (holder.surface.isValid) {
                                        onSurfaceReady(holder.surface)
                                    }
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
