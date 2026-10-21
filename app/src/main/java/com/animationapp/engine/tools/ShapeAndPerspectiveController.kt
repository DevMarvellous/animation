package com.animationapp.engine.tools

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.model.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 2D Geometric Shape Modes for Phase 13.
 */
enum class Shape2DMode(val icon: String, val label: String) {
    LINE("📏", "Straight Line"),
    RECTANGLE("▭", "Rectangle / Panel"),
    ELLIPSE("◯", "Circle / Ellipse"),
    PERSPECTIVE_RAY("📐", "Perspective Vanishing Ray")
}

/**
 * Geometric 2D Shape Tool & 1/2-Point Perspective Vanishing-Point Ruler Engine (Phase 13).
 *
 * Generates true vector [Stroke] objects for Lines, Rectangles, Circles/Ellipses, and Perspective Rays
 * so all geometric shapes remain 100% compatible with Undo/Redo, Lasso Transform (`⬠ Lasso`),
 * and Vector Intersection Cutting (`✂ Trim`).
 */
class ShapeAndPerspectiveController(
    stageWidth: Int = 1280,
    stageHeight: Int = 720
) {
    var activeShapeMode: Shape2DMode = Shape2DMode.LINE
    var showPerspectiveRuler: Boolean = false

    // Vanishing Points (VP1 = Left/Center, VP2 = Right)
    var vp1X: Float = stageWidth * 0.22f
    var vp1Y: Float = stageHeight * 0.45f
    var vp2X: Float = stageWidth * 0.78f
    var vp2Y: Float = stageHeight * 0.45f

    private var draggingVpIndex = 0 // 0 = None, 1 = VP1, 2 = VP2
    private var isDraggingShape = false
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 195, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    private val rayGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, 0, 225, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val vpHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 180, 0)
        style = Paint.Style.FILL
    }

    private val vpHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val vpLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        isFakeBoldText = true
    }

    fun resetVanishingPoints(stageWidth: Int, stageHeight: Int) {
        vp1X = stageWidth * 0.22f
        vp1Y = stageHeight * 0.45f
        vp2X = stageWidth * 0.78f
        vp2Y = stageHeight * 0.45f
    }

    fun onTouchDown(stageX: Float, stageY: Float): Boolean {
        if (showPerspectiveRuler || activeShapeMode == Shape2DMode.PERSPECTIVE_RAY) {
            if (hypot(stageX - vp1X, stageY - vp1Y) <= 34f) {
                draggingVpIndex = 1
                isDraggingShape = false
                return true
            }
            if (hypot(stageX - vp2X, stageY - vp2Y) <= 34f) {
                draggingVpIndex = 2
                isDraggingShape = false
                return true
            }
        }
        draggingVpIndex = 0
        isDraggingShape = true
        startX = stageX
        startY = stageY
        currentX = stageX
        currentY = stageY
        return false
    }

    fun onTouchMove(stageX: Float, stageY: Float) {
        if (draggingVpIndex == 1) {
            vp1X = stageX
            vp1Y = stageY
            return
        }
        if (draggingVpIndex == 2) {
            vp2X = stageX
            vp2Y = stageY
            return
        }
        if (isDraggingShape) {
            currentX = stageX
            currentY = stageY
        }
    }

    fun onTouchUp(
        stageX: Float,
        stageY: Float,
        color: Int,
        sizePx: Float,
        preset: BrushPreset
    ): Stroke? {
        if (draggingVpIndex != 0) {
            draggingVpIndex = 0
            return null
        }
        if (!isDraggingShape) return null
        isDraggingShape = false
        currentX = stageX
        currentY = stageY

        if (hypot(currentX - startX, currentY - startY) < 3f) {
            return null
        }
        return buildShapeStroke(startX, startY, currentX, currentY, color, sizePx, preset)
    }

    fun buildPreviewStroke(color: Int, sizePx: Float, preset: BrushPreset): Stroke? {
        if (!isDraggingShape) return null
        return buildShapeStroke(startX, startY, currentX, currentY, color, sizePx, preset)
    }

    private fun buildShapeStroke(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        sizePx: Float,
        preset: BrushPreset
    ): Stroke {
        val stroke = Stroke(color = color, baseSize = sizePx, isEraser = false, brushPreset = preset)

        when (activeShapeMode) {
            Shape2DMode.LINE -> {
                val steps = 16
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val px = x0 + (x1 - x0) * t
                    val py = y0 + (y1 - y0) * t
                    stroke.addPoint(px, py, 1f)
                }
                stroke.finishStroke(x1, y1)
            }

            Shape2DMode.RECTANGLE -> {
                addSubdividedSegment(stroke, x0, y0, x1, y0, 8)
                addSubdividedSegment(stroke, x1, y0, x1, y1, 8)
                addSubdividedSegment(stroke, x1, y1, x0, y1, 8)
                addSubdividedSegment(stroke, x0, y1, x0, y0, 8)
                stroke.finishStroke(x0, y0)
            }

            Shape2DMode.ELLIPSE -> {
                val cx = (x0 + x1) * 0.5f
                val cy = (y0 + y1) * 0.5f
                val rx = abs(x1 - x0) * 0.5f
                val ry = abs(y1 - y0) * 0.5f
                val segments = 36
                for (i in 0..segments) {
                    val angle = (2.0 * PI * i) / segments
                    val px = cx + (cos(angle) * rx).toFloat()
                    val py = cy + (sin(angle) * ry).toFloat()
                    stroke.addPoint(px, py, 1f)
                }
                val endX = cx + rx
                stroke.finishStroke(endX, cy)
            }

            Shape2DMode.PERSPECTIVE_RAY -> {
                // Snap (x1, y1) onto the best-aligned ray from (x0, y0) toward VP1, VP2, or Vertical!
                val snapped = snapEndPointToPerspective(x0, y0, x1, y1)
                addSubdividedSegment(stroke, x0, y0, snapped[0], snapped[1], 16)
                stroke.finishStroke(snapped[0], snapped[1])
            }
        }
        return stroke
    }

    private fun addSubdividedSegment(stroke: Stroke, ax: Float, ay: Float, bx: Float, by: Float, steps: Int) {
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            stroke.addPoint(ax + (bx - ax) * t, ay + (by - ay) * t, 1f)
        }
    }

    private fun snapEndPointToPerspective(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val dragDx = x1 - x0
        val dragDy = y1 - y0
        val dragLen = hypot(dragDx, dragDy)
        if (dragLen < 1f) return floatArrayOf(x1, y1)

        // Candidate 1: Ray toward VP1
        val v1Dx = vp1X - x0
        val v1Dy = vp1Y - y0
        val v1Len = hypot(v1Dx, v1Dy).coerceAtLeast(1f)
        val u1x = v1Dx / v1Len
        val u1y = v1Dy / v1Len
        val dot1 = dragDx * u1x + dragDy * u1y

        // Candidate 2: Ray toward VP2
        val v2Dx = vp2X - x0
        val v2Dy = vp2Y - y0
        val v2Len = hypot(v2Dx, v2Dy).coerceAtLeast(1f)
        val u2x = v2Dx / v2Len
        val u2y = v2Dy / v2Len
        val dot2 = dragDx * u2x + dragDy * u2y

        // Candidate 3: Pure Vertical Wall Line (0, 1)
        val dotVert = dragDy

        val score1 = abs(dot1)
        val score2 = abs(dot2)
        val scoreVert = abs(dotVert)

        return if (scoreVert >= score1 && scoreVert >= score2) {
            floatArrayOf(x0, y0 + dotVert)
        } else if (score1 >= score2) {
            floatArrayOf(x0 + u1x * dot1, y0 + u1y * dot1)
        } else {
            floatArrayOf(x0 + u2x * dot2, y0 + u2y * dot2)
        }
    }

    fun drawPerspectiveGuides(canvas: Canvas, stageWidth: Int, stageHeight: Int) {
        if (!showPerspectiveRuler && activeShapeMode != Shape2DMode.PERSPECTIVE_RAY) return

        // Draw Horizon Line connecting VP1 and VP2
        canvas.drawLine(0f, vp1Y, stageWidth.toFloat(), vp2Y, horizonPaint)

        // Draw perspective fan rays from VP1 and VP2
        val fanCount = 8
        for (i in 0..fanCount) {
            val targetY = (stageHeight.toFloat() * i) / fanCount
            canvas.drawLine(vp1X, vp1Y, stageWidth.toFloat(), targetY, rayGuidePaint)
            canvas.drawLine(vp2X, vp2Y, 0f, targetY, rayGuidePaint)
        }

        // Draw draggable VP1 and VP2 handles
        canvas.drawCircle(vp1X, vp1Y, 16f, vpHandleFillPaint)
        canvas.drawCircle(vp1X, vp1Y, 16f, vpHandleStrokePaint)
        canvas.drawText("VP1", vp1X - 18f, vp1Y - 24f, vpLabelPaint)

        canvas.drawCircle(vp2X, vp2Y, 16f, vpHandleFillPaint)
        canvas.drawCircle(vp2X, vp2Y, 16f, vpHandleStrokePaint)
        canvas.drawText("VP2", vp2X - 18f, vp2Y - 24f, vpLabelPaint)
    }
}

