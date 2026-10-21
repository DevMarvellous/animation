package com.animationapp.engine.brush

import android.graphics.BlurMaskFilter
import android.graphics.Paint

/**
 * Pluggable 2D Pro Brush Presets (Phase 11).
 *
 * Includes:
 * - G_PEN: Crisp anime/manga line-art pen with high opacity.
 * - PENCIL_SKETCH: Semi-transparent rough sketching pencil for key-pose construction.
 * - AIRBRUSH: Soft feathered shading brush using hardware BlurMaskFilter.
 * - MARKER: Bold chisel/felt-tip coloring marker (70% opacity for blending).
 * - PIXEL_ART: 100% aliased (anti-alias OFF) square-cap brush for retro 2D pixel sprite animation!
 */
enum class BrushPreset(
    val label: String,
    val icon: String,
    val alphaMultiplier: Float,
    val isAntiAliased: Boolean,
    val strokeCap: Paint.Cap,
    val blurRadiusPx: Float
) {
    G_PEN("G-Pen (Ink)", "✒", 1.0f, true, Paint.Cap.ROUND, 0f),
    PENCIL_SKETCH("Sketch Pencil", "✏", 0.55f, true, Paint.Cap.ROUND, 0f),
    MARKER("Copics Marker", "🖍", 0.72f, true, Paint.Cap.SQUARE, 0f),
    AIRBRUSH("Soft Airbrush", "💨", 0.45f, true, Paint.Cap.ROUND, 12f),
    PIXEL_ART("Pixel Art", "👾", 1.0f, false, Paint.Cap.SQUARE, 0f);

    private val cachedMaskFilter: BlurMaskFilter? =
        if (blurRadiusPx > 0f) BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL) else null

    /**
     * Configures a pre-allocated Paint object with zero new heap allocations.
     */
    fun applyToPaint(paint: Paint, baseColor: Int, strokeSize: Float) {
        paint.color = baseColor
        val baseAlpha = (baseColor ushr 24) and 0xFF
        val effectiveAlpha = ((if (baseAlpha == 0) 255 else baseAlpha) * alphaMultiplier).toInt().coerceIn(10, 255)
        paint.alpha = effectiveAlpha
        paint.isAntiAlias = isAntiAliased
        paint.strokeCap = strokeCap
        paint.strokeJoin = if (this == PIXEL_ART) Paint.Join.MITER else Paint.Join.ROUND
        paint.strokeWidth = strokeSize
        paint.maskFilter = cachedMaskFilter
    }
}

