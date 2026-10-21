package com.animationapp.engine.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import com.animationapp.model.Stroke
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Zero-Allocation 2D Freehand Lasso Selection, Transform & 4-Point Mesh Cage Deform Tool (Phase 6 & Phase 17).
 *
 * Allows animators to:
 * 1. Draw a freehand lasso loop around 2D strokes on the active layer.
 * 2. Drag inside the bounding box to Move (Translate).
 * 3. Drag the Bottom-Right Blue Handle to Scale, or Top-Center Stem Handle to Rotate.
 * 4. Drag any of the Amber Corner-Pin Handles (Top-Left, Top-Right, Bottom-Left) to apply a real-time
 *    2D Bilinear Cage / Perspective Warp Deform (squash, stretch, skew, or perspective tilt)!
 */
class LassoTransformController {

    enum class DragMode {
        NONE,
        DRAWING_LASSO,
        MOVING_SELECTION,
        SCALING_SELECTION,
        ROTATING_SELECTION,
        CORNER_PIN_WARP
    }

    val lassoPath = Path()
    val selectionBounds = RectF()
    private val deformSourceBounds = RectF()
    val selectedStrokes = ArrayList<Stroke>(32)

    var dragMode: DragMode = DragMode.NONE
        private set
    private var activeWarpCornerIndex = 0 // 0=TL, 1=TR, 2=BR, 3=BL

    private val lassoRegion = Region()
    private val clipRegion = Region(0, 0, 8192, 8192)
    private val transformMatrix = Matrix()
    private val scratchPoint = FloatArray(2)

    private var lastDragX = 0f
    private var lastDragY = 0f

    // Pre-allocated Paints for Lasso, 3x3 Mesh Cage & Gizmo handles
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(75, 140, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val meshGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 255, 185, 40)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(75, 140, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val warpHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 175, 25)
        style = Paint.Style.FILL
    }

    private val handleRadius = 15f
    private val rotateHandleOffset = 46f

    fun hasSelection(): Boolean = selectedStrokes.isNotEmpty() && !selectionBounds.isEmpty

    fun clearSelection() {
        selectedStrokes.clear()
        selectionBounds.setEmpty()
        lassoPath.reset()
        dragMode = DragMode.NONE
    }

    fun onTouchDown(stageX: Float, stageY: Float) {
        lastDragX = stageX
        lastDragY = stageY

        if (hasSelection()) {
            val hitRadius = handleRadius * 2.2f

            // 1. Check Rotate Handle (top center above selection box)
            val rotX = selectionBounds.centerX()
            val rotY = selectionBounds.top - rotateHandleOffset
            if (hypot(stageX - rotX, stageY - rotY) <= hitRadius) {
                dragMode = DragMode.ROTATING_SELECTION
                return
            }

            // 2. Check Bottom-Right Proportional Scale Handle
            if (hypot(stageX - selectionBounds.right, stageY - selectionBounds.bottom) <= hitRadius) {
                dragMode = DragMode.SCALING_SELECTION
                return
            }

            // 3. Check Amber Corner-Pin Mesh Warp Handles (0 = TL, 1 = TR, 3 = BL)
            if (hypot(stageX - selectionBounds.left, stageY - selectionBounds.top) <= hitRadius) {
                dragMode = DragMode.CORNER_PIN_WARP
                activeWarpCornerIndex = 0
                return
            }
            if (hypot(stageX - selectionBounds.right, stageY - selectionBounds.top) <= hitRadius) {
                dragMode = DragMode.CORNER_PIN_WARP
                activeWarpCornerIndex = 1
                return
            }
            if (hypot(stageX - selectionBounds.left, stageY - selectionBounds.bottom) <= hitRadius) {
                dragMode = DragMode.CORNER_PIN_WARP
                activeWarpCornerIndex = 3
                return
            }

            // 4. Check inside Selection Box -> Move
            if (selectionBounds.contains(stageX, stageY)) {
                dragMode = DragMode.MOVING_SELECTION
                return
            }
        }

        // Otherwise start a fresh Lasso polygon
        clearSelection()
        dragMode = DragMode.DRAWING_LASSO
        lassoPath.moveTo(stageX, stageY)
    }

    fun onTouchMove(stageX: Float, stageY: Float): Boolean {
        val dx = stageX - lastDragX
        val dy = stageY - lastDragY

        when (dragMode) {
            DragMode.DRAWING_LASSO -> {
                lassoPath.lineTo(stageX, stageY)
                lastDragX = stageX
                lastDragY = stageY
                return false
            }

            DragMode.MOVING_SELECTION -> {
                transformMatrix.reset()
                transformMatrix.setTranslate(dx, dy)
                applyMatrixToSelectedStrokes()
                lastDragX = stageX
                lastDragY = stageY
                return true
            }

            DragMode.SCALING_SELECTION -> {
                val pivotX = selectionBounds.left
                val pivotY = selectionBounds.top
                val prevW = (lastDragX - pivotX).coerceAtLeast(10f)
                val prevH = (lastDragY - pivotY).coerceAtLeast(10f)
                val newW = (stageX - pivotX).coerceAtLeast(10f)
                val newH = (stageY - pivotY).coerceAtLeast(10f)
                val sx = (newW / prevW).coerceIn(0.2f, 5.0f)
                val sy = (newH / prevH).coerceIn(0.2f, 5.0f)

                transformMatrix.reset()
                transformMatrix.setScale(sx, sy, pivotX, pivotY)
                applyMatrixToSelectedStrokes()
                lastDragX = stageX
                lastDragY = stageY
                return true
            }

            DragMode.ROTATING_SELECTION -> {
                val cx = selectionBounds.centerX()
                val cy = selectionBounds.centerY()
                val prevAngle = atan2((lastDragY - cy).toDouble(), (lastDragX - cx).toDouble())
                val currAngle = atan2((stageY - cy).toDouble(), (stageX - cx).toDouble())
                val deltaDeg = Math.toDegrees(currAngle - prevAngle).toFloat()

                transformMatrix.reset()
                transformMatrix.setRotate(deltaDeg, cx, cy)
                applyMatrixToSelectedStrokes()
                lastDragX = stageX
                lastDragY = stageY
                return true
            }

            DragMode.CORNER_PIN_WARP -> {
                deformSourceBounds.set(selectionBounds)
                selectionBounds.setEmpty()
                for (i in 0 until selectedStrokes.size) {
                    val s = selectedStrokes[i]
                    s.applyCornerPinDeform(deformSourceBounds, activeWarpCornerIndex, dx, dy)
                    if (selectionBounds.isEmpty) {
                        selectionBounds.set(s.bounds)
                    } else {
                        selectionBounds.union(s.bounds)
                    }
                }
                if (!selectionBounds.isEmpty) {
                    selectionBounds.inset(-12f, -12f)
                }
                lastDragX = stageX
                lastDragY = stageY
                return true
            }

            DragMode.NONE -> return false
        }
    }

    fun onTouchUp(stageX: Float, stageY: Float, layerStrokes: List<Stroke>) {
        if (dragMode == DragMode.DRAWING_LASSO) {
            lassoPath.lineTo(stageX, stageY)
            lassoPath.close()
            computeSelectedStrokes(layerStrokes)
            lassoPath.reset()
        }
        dragMode = DragMode.NONE
    }

    private fun computeSelectedStrokes(layerStrokes: List<Stroke>) {
        selectedStrokes.clear()
        selectionBounds.setEmpty()

        lassoRegion.setPath(lassoPath, clipRegion)
        if (lassoRegion.isEmpty) return

        for (i in 0 until layerStrokes.size) {
            val s = layerStrokes[i]
            if (s.isEraser || s.pointCount == 0) continue

            var inside = false
            val step = (s.pointCount / 8).coerceAtLeast(1)
            var p = 0
            while (p < s.pointCount) {
                if (lassoRegion.contains(s.getX(p).toInt(), s.getY(p).toInt())) {
                    inside = true
                    break
                }
                p += step
            }
            if (inside) {
                selectedStrokes.add(s)
                if (selectionBounds.isEmpty) {
                    selectionBounds.set(s.bounds)
                } else {
                    selectionBounds.union(s.bounds)
                }
            }
        }

        if (!selectionBounds.isEmpty) {
            selectionBounds.inset(-12f, -12f)
        }
    }

    private fun applyMatrixToSelectedStrokes() {
        selectionBounds.setEmpty()
        for (i in 0 until selectedStrokes.size) {
            val s = selectedStrokes[i]
            s.applyTransform(transformMatrix, scratchPoint)
            if (selectionBounds.isEmpty) {
                selectionBounds.set(s.bounds)
            } else {
                selectionBounds.union(s.bounds)
            }
        }
        if (!selectionBounds.isEmpty) {
            selectionBounds.inset(-12f, -12f)
        }
    }

    fun drawOverlay(canvas: Canvas) {
        if (dragMode == DragMode.DRAWING_LASSO && !lassoPath.isEmpty) {
            canvas.drawPath(lassoPath, lassoPaint)
        }

        if (hasSelection()) {
            // Draw 3x3 internal Mesh Cage Subdivision lines
            val w3 = selectionBounds.width() / 3f
            val h3 = selectionBounds.height() / 3f
            for (k in 1..2) {
                val gx = selectionBounds.left + w3 * k
                val gy = selectionBounds.top + h3 * k
                canvas.drawLine(gx, selectionBounds.top, gx, selectionBounds.bottom, meshGridPaint)
                canvas.drawLine(selectionBounds.left, gy, selectionBounds.right, gy, meshGridPaint)
            }

            // Draw selection bounding box
            canvas.drawRect(selectionBounds, boxPaint)

            // Draw Top Rotation Stem & Handle
            val cx = selectionBounds.centerX()
            val rotY = selectionBounds.top - rotateHandleOffset
            canvas.drawLine(cx, selectionBounds.top, cx, rotY, boxPaint)
            canvas.drawCircle(cx, rotY, handleRadius, handleFillPaint)
            canvas.drawCircle(cx, rotY, handleRadius, handleStrokePaint)

            // Draw Bottom-Right Scale Handle (White/Blue)
            canvas.drawCircle(selectionBounds.right, selectionBounds.bottom, handleRadius, handleFillPaint)
            canvas.drawCircle(selectionBounds.right, selectionBounds.bottom, handleRadius, handleStrokePaint)

            // Draw 3 Amber Corner-Pin Warp Handles (TL, TR, BL)
            canvas.drawCircle(selectionBounds.left, selectionBounds.top, handleRadius * 0.85f, warpHandleFillPaint)
            canvas.drawCircle(selectionBounds.right, selectionBounds.top, handleRadius * 0.85f, warpHandleFillPaint)
            canvas.drawCircle(selectionBounds.left, selectionBounds.bottom, handleRadius * 0.85f, warpHandleFillPaint)
        }
    }
}
