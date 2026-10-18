package com.animationapp.engine.onion

import android.graphics.Canvas
import android.graphics.Paint
import com.animationapp.core.config.StudioConfig
import com.animationapp.model.AnimationProject

/**
 * Tinted Multi-Frame Onion Skin Renderer.
 *
 * Renders past frames in Ruby Red and future frames in Cobalt Cyan with smooth opacity falloff.
 * Pre-allocates its Paint instance so rendering onion skins generates 0 bytes of garbage.
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

        // 1. Render Past Frames (Ruby Red tint, fading out with distance)
        val pastRange = project.onionPastCount
        for (step in pastRange downTo 1) {
            val idx = currentIdx - step
            if (idx >= 0) {
                val falloffAlpha = (StudioConfig.ONION_BASE_ALPHA / step).coerceIn(20, 180)
                drawGhostFrame(
                    canvas = canvas,
                    strokes = frames[idx].strokes,
                    tintColor = StudioConfig.ONION_PAST_TINT_COLOR,
                    alpha = falloffAlpha
                )
            }
        }

        // 2. Render Future Frames (Cobalt Cyan tint, fading out with distance)
        val futureRange = project.onionFutureCount
        for (step in futureRange downTo 1) {
            val idx = currentIdx + step
            if (idx <= frames.lastIndex) {
                val falloffAlpha = (StudioConfig.ONION_BASE_ALPHA / step).coerceIn(20, 180)
                drawGhostFrame(
                    canvas = canvas,
                    strokes = frames[idx].strokes,
                    tintColor = StudioConfig.ONION_FUTURE_TINT_COLOR,
                    alpha = falloffAlpha
                )
            }
        }
    }

    private fun drawGhostFrame(
        canvas: Canvas,
        strokes: List<com.animationapp.model.Stroke>,
        tintColor: Int,
        alpha: Int
    ) {
        onionPaint.color = tintColor
        onionPaint.alpha = alpha

        for (i in 0 until strokes.size) {
            val stroke = strokes[i]
            if (stroke.isEraser) continue
            onionPaint.strokeWidth = stroke.baseSize
            canvas.drawPath(stroke.renderPath, onionPaint)
        }
    }
}
