package com.animationapp.model

import android.graphics.Path
import com.animationapp.core.config.StudioConfig
import kotlin.math.abs

/**
 * Memory-packed vector stroke representation.
 *
 * Uses a single primitive FloatArray (interleaved x, y, pressure) instead of ArrayList<Point>
 * to eliminate object header overhead and prevent Garbage Collection pauses on budget phones.
 */
class Stroke(
    val color: Int,
    val baseSize: Float,
    val isEraser: Boolean = false
) {
    // Interleaved [x0, y0, p0, x1, y1, p1, ...]
    private var pointData = FloatArray(StudioConfig.INITIAL_STROKE_FLOAT_CAPACITY)
    var pointCount = 0
        private set

    // Pre-built hardware Path cached for fast rendering
    val renderPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    fun addPoint(x: Float, y: Float, pressure: Float) {
        val requiredCapacity = (pointCount + 1) * 3
        if (requiredCapacity > pointData.size) {
            pointData = pointData.copyOf(pointData.size * 2)
        }

        val offset = pointCount * 3
        pointData[offset] = x
        pointData[offset + 1] = y
        pointData[offset + 2] = pressure

        if (pointCount == 0) {
            renderPath.reset()
            renderPath.moveTo(x, y)
            lastX = x
            lastY = y
        } else {
            val dx = abs(x - lastX)
            val dy = abs(y - lastY)
            if (dx >= StudioConfig.MIN_POINT_DISTANCE_PX || dy >= StudioConfig.MIN_POINT_DISTANCE_PX) {
                val midX = (x + lastX) * 0.5f
                val midY = (y + lastY) * 0.5f
                renderPath.quadTo(lastX, lastY, midX, midY)
                lastX = x
                lastY = y
            }
        }
        pointCount++
    }

    fun finishStroke(x: Float, y: Float) {
        if (pointCount > 0) {
            renderPath.lineTo(x, y)
        }
    }

    fun getX(index: Int): Float = pointData[index * 3]
    fun getY(index: Int): Float = pointData[index * 3 + 1]
    fun getPressure(index: Int): Float = pointData[index * 3 + 2]

    fun deepCopy(): Stroke {
        val copy = Stroke(color, baseSize, isEraser)
        copy.pointData = pointData.copyOf(pointCount * 3)
        copy.pointCount = pointCount
        copy.renderPath.set(renderPath)
        copy.lastX = lastX
        copy.lastY = lastY
        return copy
    }
}

