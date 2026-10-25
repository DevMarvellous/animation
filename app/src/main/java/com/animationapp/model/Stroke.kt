package com.animationapp.model

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.animationapp.core.config.StudioConfig
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.engine.brush.ScreentoneEngine.ScreentonePattern
import kotlin.math.abs

/**
 * Memory-packed 2D vector stroke representation (Phase 11).
 *
 * Uses a single primitive FloatArray (interleaved x, y, pressure) instead of ArrayList<Point>
 * to eliminate object header overhead and prevent Garbage Collection pauses on budget phones.
 *
 * Supports:
 * - Pluggable `BrushPreset` (G-Pen, Sketch Pencil, Marker, Soft Airbrush, Pixel-Art).
 * - Manga Screentone & Halftone Dot Shading (Phase 31).
 * - In-place 2D affine transformations (Translate, Scale, Rotate) for the Lasso Tool.
 */
class Stroke(
    val color: Int,
    val baseSize: Float,
    val isEraser: Boolean = false,
    val brushPreset: BrushPreset = BrushPreset.G_PEN,
    var screentonePattern: ScreentonePattern = ScreentonePattern.NONE
) {
    // Interleaved [x0, y0, p0, x1, y1, p1, ...]
    private var pointData = FloatArray(StudioConfig.INITIAL_STROKE_FLOAT_CAPACITY)
    var pointCount = 0
        private set

    // Pre-built hardware Path cached for fast rendering
    val renderPath = Path()
    val bounds = RectF()
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
            bounds.set(x, y, x, y)
            lastX = x
            lastY = y
        } else {
            bounds.union(x, y)
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
            bounds.union(x, y)
            renderPath.lineTo(x, y)
        }
    }

    /**
     * Transforms all points of this 2D stroke in-place using a 2D affine Matrix (Zero GC allocation).
     */
    fun applyTransform(matrix: Matrix, scratchPt: FloatArray) {
        if (pointCount == 0) return
        renderPath.reset()
        bounds.setEmpty()

        for (i in 0 until pointCount) {
            val offset = i * 3
            scratchPt[0] = pointData[offset]
            scratchPt[1] = pointData[offset + 1]
            matrix.mapPoints(scratchPt)

            val nx = scratchPt[0]
            val ny = scratchPt[1]
            pointData[offset] = nx
            pointData[offset + 1] = ny

            if (i == 0) {
                renderPath.moveTo(nx, ny)
                bounds.set(nx, ny, nx, ny)
                lastX = nx
                lastY = ny
            } else {
                bounds.union(nx, ny)
                val midX = (nx + lastX) * 0.5f
                val midY = (ny + lastY) * 0.5f
                renderPath.quadTo(lastX, lastY, midX, midY)
                lastX = nx
                lastY = ny
            }
        }
        if (pointCount > 0) {
            renderPath.lineTo(lastX, lastY)
        }
    }

    /**
     * Phase 17: 4-Point Perspective Corner-Pin / Bilinear Cage Warp Deform (Zero GC allocation).
     *
     * @param srcBounds The current bounding box of the Lasso selection before this drag step.
     * @param cornerIndex 0 = Top-Left, 1 = Top-Right, 2 = Bottom-Right, 3 = Bottom-Left.
     * @param dx Horizontal displacement of the dragged corner handle.
     * @param dy Vertical displacement of the dragged corner handle.
     */
    fun applyCornerPinDeform(srcBounds: RectF, cornerIndex: Int, dx: Float, dy: Float) {
        if (pointCount == 0) return
        val w = srcBounds.width().coerceAtLeast(8f)
        val h = srcBounds.height().coerceAtLeast(8f)

        renderPath.reset()
        bounds.setEmpty()

        for (i in 0 until pointCount) {
            val offset = i * 3
            val px = pointData[offset]
            val py = pointData[offset + 1]

            val u = ((px - srcBounds.left) / w).coerceIn(0f, 1f)
            val v = ((py - srcBounds.top) / h).coerceIn(0f, 1f)

            val weight = when (cornerIndex) {
                0 -> (1f - u) * (1f - v) // Top-Left
                1 -> u * (1f - v)        // Top-Right
                2 -> u * v               // Bottom-Right
                else -> (1f - u) * v     // Bottom-Left
            }

            val nx = px + dx * weight
            val ny = py + dy * weight
            pointData[offset] = nx
            pointData[offset + 1] = ny

            if (i == 0) {
                renderPath.moveTo(nx, ny)
                bounds.set(nx, ny, nx, ny)
                lastX = nx
                lastY = ny
            } else {
                bounds.union(nx, ny)
                val midX = (nx + lastX) * 0.5f
                val midY = (ny + lastY) * 0.5f
                renderPath.quadTo(lastX, lastY, midX, midY)
                lastX = nx
                lastY = ny
            }
        }
        if (pointCount > 0) {
            renderPath.lineTo(lastX, lastY)
        }
    }

    fun assignPathWithPoints(path: Path, sampledPoints: FloatArray, count: Int, pathBounds: RectF) {
        pointData = sampledPoints.copyOf(count * 3)
        pointCount = count
        renderPath.set(path)
        bounds.set(pathBounds)
        if (count > 0) {
            lastX = sampledPoints[(count - 1) * 3]
            lastY = sampledPoints[(count - 1) * 3 + 1]
        }
    }

    fun getX(index: Int): Float = pointData[index * 3]
    fun getY(index: Int): Float = pointData[index * 3 + 1]
    fun getPressure(index: Int): Float = pointData[index * 3 + 2]

    fun deepCopy(): Stroke {
        val copy = Stroke(color, baseSize, isEraser, brushPreset)
        copy.pointData = pointData.copyOf(pointCount * 3)
        copy.pointCount = pointCount
        copy.renderPath.set(renderPath)
        copy.bounds.set(bounds)
        copy.lastX = lastX
        copy.lastY = lastY
        return copy
    }
}
