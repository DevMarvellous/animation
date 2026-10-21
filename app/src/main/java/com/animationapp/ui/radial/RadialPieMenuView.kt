package com.animationapp.ui.radial

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Floating One-Handed Quick-Access 8-Slice Radial Pie Menu & Draggable Puck (Phase 25).
 *
 * When collapsed, displays a compact draggable `🎯` thumb puck.
 * When tapped, expands into an 8-sector Radial Pie Menu for instant one-handed access to
 * Pen, Eraser, Paint Bucket, Lasso, Undo, Redo, Play/Pause, and +New Frame!
 */
class RadialPieMenuView(
    context: Context,
    var onActionSelected: ((RadialAction) -> Unit)? = null
) : View(context) {

    enum class RadialAction(val label: String) {
        PEN("✏ Pen"),
        ERASE("⌫ Erase"),
        FILL("🪣 Fill"),
        LASSO("⬠ Lasso"),
        UNDO("↶ Undo"),
        REDO("↷ Redo"),
        PLAY_PAUSE("▶ Play"),
        ADD_FRAME("＋ Frame")
    }

    var isExpanded: Boolean = false
        private set
    private var hoveredSectorIndex: Int = -1

    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var startMarginLeft = 0
    private var startMarginTop = 0
    private var hasDraggedPuck = false

    private val puckBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 38, 44, 62)
        style = Paint.Style.FILL
    }
    private val puckBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val pieSlicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 28, 32, 46)
        style = Paint.Style.FILL
    }
    private val pieSliceHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(245, 65, 115, 235)
        style = Paint.Style.FILL
    }
    private val pieDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 90, 110, 150)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 23f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pieOval = RectF()
    private val actions = RadialAction.values()

    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.5f
        val cy = height * 0.5f

        if (!isExpanded) {
            val puckR = 42f
            canvas.drawCircle(cx, cy, puckR, puckBgPaint)
            canvas.drawCircle(cx, cy, puckR, puckBorderPaint)
            canvas.drawText("🎯", cx, cy + 8f, labelPaint)
            return
        }

        val outerR = (width.coerceAtMost(height) * 0.47f)
        val innerR = 42f
        pieOval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)

        val sweepDeg = 360f / actions.size
        for (i in actions.indices) {
            val startDeg = i * sweepDeg - (sweepDeg * 0.5f)
            val paint = if (i == hoveredSectorIndex) pieSliceHighlightPaint else pieSlicePaint
            canvas.drawArc(pieOval, startDeg, sweepDeg, true, paint)
            canvas.drawArc(pieOval, startDeg, sweepDeg, true, pieDividerPaint)

            val midRad = Math.toRadians((i * sweepDeg).toDouble())
            val labelR = (innerR + outerR) * 0.54f
            val lx = cx + (cos(midRad) * labelR).toFloat()
            val ly = cy + (sin(midRad) * labelR).toFloat() + 7f
            canvas.drawText(actions[i].label, lx, ly, labelPaint)
        }

        // Center close hub
        canvas.drawCircle(cx, cy, innerR, puckBgPaint)
        canvas.drawCircle(cx, cy, innerR, puckBorderPaint)
        canvas.drawText("✕", cx, cy + 8f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width * 0.5f
        val cy = height * 0.5f
        val dx = event.x - cx
        val dy = event.y - cy
        val dist = hypot(dx, dy)

        if (!isExpanded) {
            // Check if touch is inside the compact 46px puck
            if (event.actionMasked == MotionEvent.ACTION_DOWN && dist > 54f) {
                return false // Let canvas underneath receive touch!
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = layoutParams as? FrameLayout.LayoutParams
                    dragDownRawX = event.rawX
                    dragDownRawY = event.rawY
                    startMarginLeft = lp?.leftMargin ?: 0
                    startMarginTop = lp?.topMargin ?: 0
                    hasDraggedPuck = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveDx = event.rawX - dragDownRawX
                    val moveDy = event.rawY - dragDownRawY
                    if (hypot(moveDx, moveDy) > 12f) {
                        hasDraggedPuck = true
                        val lp = layoutParams as? FrameLayout.LayoutParams
                        if (lp != null) {
                            lp.leftMargin = (startMarginLeft + moveDx.toInt()).coerceAtLeast(-60)
                            lp.topMargin = (startMarginTop + moveDy.toInt()).coerceAtLeast(20)
                            layoutParams = lp
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasDraggedPuck) {
                        isExpanded = true
                        hoveredSectorIndex = -1
                        invalidate()
                    }
                    return true
                }
            }
            return true
        }

        // Expanded Pie Menu interaction
        val outerR = (width.coerceAtMost(height) * 0.48f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (dist in 44f..outerR) {
                    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val sweep = 360f / actions.size
                    deg = (deg + sweep * 0.5f + 360f) % 360f
                    hoveredSectorIndex = (deg / sweep).toInt().coerceIn(0, actions.lastIndex)
                } else {
                    hoveredSectorIndex = -1
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val chosenIdx = hoveredSectorIndex
                isExpanded = false
                hoveredSectorIndex = -1
                invalidate()
                if (chosenIdx in actions.indices) {
                    onActionSelected?.invoke(actions[chosenIdx])
                }
            }
        }
        return true
    }
}

