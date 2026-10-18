package com.animationapp.engine.onion

import android.graphics.Canvas
import android.graphics.Paint
import com.animationapp.core.config.StudioConfig
import com.animationapp.model.AnimationFrame
import com.animationapp.model.AnimationProject

/**
 * Tinted Multi-Frame & Multi-Layer Onion Skin Renderer.
 *
 * Renders all visible layers of past frames in Ruby Red and future frames in Cobalt Cyan
 * with smooth opacity falloff. Zero allocations during rendering.
 */
class OnionSkinRenderer {

    private val onionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun drawOnionSkins(canvas: Canvas, project: AnimationProject) {
        if (!project.isOnionSkinEnabled) return

        val currentIdx = project.currentFrameIndex
        val frames = project.frames

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
