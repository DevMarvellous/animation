package com.animationapp.engine.onion

import android.graphics.Canvas
import android.graphics.Paint
import com.animationapp.core.config.StudioConfig
import com.animationapp.model.AnimationFrame
import com.animationapp.model.AnimationProject
import kotlin.math.abs

/**
 * Tinted Multi-Frame & Multi-Layer Onion Skin Renderer + Shift-and-Trace (Virtual Light Table).
 *
 * Shift-and-Trace (a signature Toon Boom Harmony / TVPaint feature) lets animators temporarily
 * drag/slide the previous onion-skin ghost frame to align proportions when drawing fast-moving
 * in-between frames, then snap it back to (0, 0) in one tap!
 */
class OnionSkinRenderer {

    private val onionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Shift-and-Trace (Virtual Light Table) temporary offset in Stage Pixels
    var shiftTraceOffsetX: Float = 0f
        private set
    var shiftTraceOffsetY: Float = 0f
        private set

    fun isShifted(): Boolean = abs(shiftTraceOffsetX) > 0.5f || abs(shiftTraceOffsetY) > 0.5f

    fun addShiftOffset(dx: Float, dy: Float) {
        shiftTraceOffsetX += dx
        shiftTraceOffsetY += dy
    }

    fun resetShiftOffset() {
        shiftTraceOffsetX = 0f
        shiftTraceOffsetY = 0f
    }

    fun drawOnionSkins(canvas: Canvas, project: AnimationProject) {
        if (!project.isOnionSkinEnabled) return

        val currentIdx = project.currentFrameIndex
        val frames = project.frames

        canvas.save()
        if (isShifted()) {
            canvas.translate(shiftTraceOffsetX, shiftTraceOffsetY)
        }

        // 1. Render Past Frames (Ruby Red tint)
        val pastRange = project.onionPastCount
        for (step in pastRange downTo 1) {
            val idx = currentIdx - step
            if (idx >= 0) {
                val falloffAlpha = (StudioConfig.ONION_BASE_ALPHA / step).coerceIn(20, 180)
                drawGhostFrame(
                    canvas = canvas,
                    frame = frames[idx],
                    project = project,
                    tintColor = StudioConfig.ONION_PAST_TINT_COLOR,
                    alpha = falloffAlpha
                )
            }
        }

        // 2. Render Future Frames (Cobalt Cyan tint)
        val futureRange = project.onionFutureCount
        for (step in futureRange downTo 1) {
            val idx = currentIdx + step
            if (idx <= frames.lastIndex) {
                val falloffAlpha = (StudioConfig.ONION_BASE_ALPHA / step).coerceIn(20, 180)
                drawGhostFrame(
                    canvas = canvas,
                    frame = frames[idx],
                    project = project,
                    tintColor = StudioConfig.ONION_FUTURE_TINT_COLOR,
                    alpha = falloffAlpha
                )
            }
        }

        canvas.restore()
    }

    private fun drawGhostFrame(
        canvas: Canvas,
        frame: AnimationFrame,
        project: AnimationProject,
        tintColor: Int,
        alpha: Int
    ) {
        onionPaint.color = tintColor
        onionPaint.alpha = alpha

        val layers = project.layers
        for (layerIdx in 0 until layers.size) {
            if (!layers[layerIdx].isVisible) continue
            val cel = frame.getCel(layerIdx)
            val strokes = cel.strokes
            for (sIdx in 0 until strokes.size) {
                val stroke = strokes[sIdx]
                if (stroke.isEraser) continue
                onionPaint.strokeWidth = stroke.baseSize
                canvas.drawPath(stroke.renderPath, onionPaint)
            }
        }
    }
}
