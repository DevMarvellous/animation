package com.animationapp.engine.canvas

import android.graphics.Matrix
import com.animationapp.core.config.StudioConfig
import kotlin.math.abs

/**
 * Manages the 2D stage camera transform (Pan, Pinch-Zoom, Two-Finger Rotate, and Horizontal Mirror Flip).
 *
 * Pre-allocates both forward and inverse matrices so converting screen touch coordinates
 * to stage canvas coordinates causes zero Garbage Collection allocations.
 */
class ViewportController {

    val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val tempPoint = FloatArray(2)
    private val matrixValues = FloatArray(9)

    var zoomScale: Float = 1.0f
        private set
    var rotationDegrees: Float = 0.0f
        private set
    var isMirroredHorizontally: Boolean = false
        private set

    private var stageWidth = StudioConfig.DEFAULT_STAGE_WIDTH.toFloat()
    private var stageHeight = StudioConfig.DEFAULT_STAGE_HEIGHT.toFloat()
    private var viewportWidth = 1f
    private var viewportHeight = 1f

    fun fitStageToViewport(
        viewW: Int,
        viewH: Int,
        stageW: Int,
        stageH: Int,
        topInsetPx: Float = 90f,
        bottomInsetPx: Float = 130f
    ) {
        if (viewW <= 0 || viewH <= 0) return
        viewportWidth = viewW.toFloat()
        viewportHeight = viewH.toFloat()
        stageWidth = stageW.toFloat()
        stageHeight = stageH.toFloat()

        val availableW = (viewportWidth - 180f).coerceAtLeast(100f)
        val availableH = (viewportHeight - topInsetPx - bottomInsetPx).coerceAtLeast(100f)

        val scaleX = availableW / stageWidth
        val scaleY = availableH / stageHeight
        zoomScale = minOf(scaleX, scaleY).coerceIn(StudioConfig.MIN_ZOOM_SCALE, StudioConfig.MAX_ZOOM_SCALE)
        rotationDegrees = 0f
        isMirroredHorizontally = false

        val dx = (viewportWidth - stageWidth * zoomScale) * 0.5f
        val dy = topInsetPx + (availableH - stageHeight * zoomScale) * 0.5f

        viewMatrix.reset()
        viewMatrix.postScale(zoomScale, zoomScale)
        viewMatrix.postTranslate(dx, dy)
        rebuildInverse()
    }

    fun applyPanZoomRotate(
        panDx: Float,
        panDy: Float,
        scaleFactor: Float,
        deltaDegrees: Float,
        focusX: Float,
        focusY: Float
    ) {
        // Pan
        viewMatrix.postTranslate(panDx, panDy)

        // Zoom clamped within limits
        val nextScale = (zoomScale * scaleFactor).coerceIn(
            StudioConfig.MIN_ZOOM_SCALE,
            StudioConfig.MAX_ZOOM_SCALE
        )
        val effectiveScale = nextScale / zoomScale
        zoomScale = nextScale
        viewMatrix.postScale(effectiveScale, effectiveScale, focusX, focusY)

        // Rotate with subtle snap near 0 degrees
        rotationDegrees = (rotationDegrees + deltaDegrees) % 360f
        viewMatrix.postRotate(deltaDegrees, focusX, focusY)

        rebuildInverse()
    }

    fun toggleHorizontalMirror() {
        isMirroredHorizontally = !isMirroredHorizontally
        val centerX = viewportWidth * 0.5f
        val centerY = viewportHeight * 0.5f
        viewMatrix.postScale(-1f, 1f, centerX, centerY)
        rebuildInverse()
    }

    fun resetRotationToZero() {
        if (abs(rotationDegrees) > 0.01f) {
            val centerX = viewportWidth * 0.5f
            val centerY = viewportHeight * 0.5f
            viewMatrix.postRotate(-rotationDegrees, centerX, centerY)
            rotationDegrees = 0f
            rebuildInverse()
        }
    }

    /**
     * Maps screen touch coordinates (px) into Stage Canvas coordinates without allocating objects.
     */
    fun screenToStageX(screenX: Float, screenY: Float): Float {
        tempPoint[0] = screenX
        tempPoint[1] = screenY
        inverseMatrix.mapPoints(tempPoint)
        return tempPoint[0]
    }

    fun screenToStageY(screenX: Float, screenY: Float): Float {
        tempPoint[0] = screenX
        tempPoint[1] = screenY
        inverseMatrix.mapPoints(tempPoint)
        return tempPoint[1]
    }

    private fun rebuildInverse() {
        viewMatrix.invert(inverseMatrix)
    }
}
