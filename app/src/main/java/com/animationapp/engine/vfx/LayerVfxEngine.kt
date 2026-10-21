package com.animationapp.engine.vfx

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.animationapp.core.config.StudioPreferences
import com.animationapp.model.AnimationLayer
import com.animationapp.model.Stroke

/**
 * Non-Destructive 2D Layer VFX Engine (Phase 21).
 *
 * Provides 3 signature 2D studio effects per layer with zero heap allocations during rendering:
 * 1. Procedural Hand-Drawn Line-Boil (shimmers ink lines on held frames for living 2D texture).
 * 2. Anime Glow / Outer Luminescence Bloom.
 * 3. Multi-Plane 2D Cel Drop Shadow.
 */
object LayerVfxEngine {

    private val boiledScratchPath = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 15, 18, 30)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var cachedGlowRadius = -1f
    private var cachedBlurFilter: BlurMaskFilter? = null

    /**
     * Returns either the original [Stroke.renderPath] or the non-destructively boiled [boiledScratchPath]
     * if [AnimationLayer.vfxLineBoilPx] > 0f.
     */
    fun getEffectiveStrokePath(stroke: Stroke, layer: AnimationLayer, frameIndex: Int): Path {
        val boilAmp = layer.vfxLineBoilPx
        if (boilAmp <= 0.1f || stroke.pointCount < 2) {
            return stroke.renderPath
        }

        boiledScratchPath.reset()
        var prevX = 0f
        var prevY = 0f

        for (i in 0 until stroke.pointCount) {
            // Fast deterministic integer hash for organic 2D hand-drawn boil
            val hashX = ((frameIndex + 1) * 73856093) xor ((i + 1) * 19349663)
            val hashY = ((frameIndex + 7) * 83492791) xor ((i + 3) * 297121507)
            val normX = ((hashX and 0xFF) / 127.5f) - 1.0f
            val normY = ((hashY and 0xFF) / 127.5f) - 1.0f

            val bx = stroke.getX(i) + normX * boilAmp
            val by = stroke.getY(i) + normY * boilAmp

            if (i == 0) {
                boiledScratchPath.moveTo(bx, by)
                prevX = bx
                prevY = by
            } else {
                val midX = (bx + prevX) * 0.5f
                val midY = (by + prevY) * 0.5f
                boiledScratchPath.quadTo(prevX, prevY, midX, midY)
                prevX = bx
                prevY = by
            }
        }
        boiledScratchPath.lineTo(prevX, prevY)
        return boiledScratchPath
    }

    /**
     * Renders non-destructive 2D Cel Drop Shadow and Anime Glow/Bloom behind [strokePath] when enabled on [layer].
     */
    fun drawStrokeUnderlayVfx(
        targetCanvas: Canvas,
        stroke: Stroke,
        strokePath: Path,
        layer: AnimationLayer
    ) {
        if (stroke.isEraser) return

        // 1. 2D Cel Drop Shadow
        val shadowOffset = layer.vfxDropShadowOffsetPx
        if (shadowOffset > 0.5f) {
            shadowPaint.strokeWidth = stroke.baseSize
            targetCanvas.save()
            targetCanvas.translate(shadowOffset, shadowOffset)
            targetCanvas.drawPath(strokePath, shadowPaint)
            targetCanvas.restore()
        }

        // 2. 2D Anime Glow / Bloom (Skipped in Potato Phone Turbo Mode for max FPS)
        val glowRadius = layer.vfxGlowRadiusPx
        if (glowRadius > 0.5f && !StudioPreferences.lowSpecTurboMode) {
            if (glowRadius != cachedGlowRadius) {
                cachedGlowRadius = glowRadius
                cachedBlurFilter = BlurMaskFilter(glowRadius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            glowPaint.color = stroke.color
            glowPaint.alpha = 135
            glowPaint.strokeWidth = stroke.baseSize + glowRadius * 1.3f
            glowPaint.maskFilter = cachedBlurFilter
            targetCanvas.drawPath(strokePath, glowPaint)
            glowPaint.maskFilter = null
        }
    }
}

