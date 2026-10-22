package com.animationapp.model

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.animationapp.engine.tween.LayerTweenTrack

/**
 * Hardware-accelerated layer blend modes.
 */
enum class LayerBlendMode(val label: String, val porterDuffMode: PorterDuff.Mode) {
    NORMAL("Normal", PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", PorterDuff.Mode.SCREEN),
    ADD("Add (Glow)", PorterDuff.Mode.ADD),
    OVERLAY("Overlay", PorterDuff.Mode.OVERLAY),
    DARKEN("Darken", PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", PorterDuff.Mode.LIGHTEN),
    XOR("XOR / Invert", PorterDuff.Mode.XOR);

    val xfermode: PorterDuffXfermode = PorterDuffXfermode(porterDuffMode)
}

/**
 * Represents a single Cel (the content of one Layer at one Frame).
 *
 * Supports:
 * - Vector strokes + optional raster flood-fill bitmaps
 * - Phase 16 Linked Cels / Reusable 2D Symbols (`isLinkedSymbol`)
 * - Phase 18 Virtual Memory Disk Swap (`celId`, `swappedDiskPath`)
 */
class LayerCel {
    companion object {
        private var nextIdCounter = 1
    }

    val celId: Int = nextIdCounter++
    var isLinkedSymbol: Boolean = false
    var swappedDiskPath: String? = null

    val strokes = ArrayList<Stroke>(24)
    private val redoStack = ArrayList<Stroke>(12)

    // Optional flood-fill bitmap overlay for bucket fills on this cel
    var fillBitmap: Bitmap? = null
    var isCacheDirty: Boolean = true

    fun hasContent(): Boolean {
        return strokes.isNotEmpty() || fillBitmap != null || swappedDiskPath != null
    }

    fun addStroke(stroke: Stroke) {
        strokes.add(stroke)
        redoStack.clear()
        isCacheDirty = true
    }

    fun canUndo(): Boolean = strokes.isNotEmpty() || fillBitmap != null
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): Boolean {
        if (strokes.isNotEmpty()) {
            val removed = strokes.removeAt(strokes.lastIndex)
            redoStack.add(removed)
            isCacheDirty = true
            return true
        }
        if (fillBitmap != null) {
            fillBitmap?.recycle()
            fillBitmap = null
            swappedDiskPath = null
            isCacheDirty = true
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val restored = redoStack.removeAt(redoStack.lastIndex)
        strokes.add(restored)
        isCacheDirty = true
        return true
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
        fillBitmap?.let { if (!it.isRecycled) it.recycle() }
        fillBitmap = null
        swappedDiskPath = null
        isCacheDirty = true
    }

    fun duplicate(): LayerCel {
        val copy = LayerCel()
        for (i in 0 until strokes.size) {
            copy.strokes.add(strokes[i].deepCopy())
        }
        fillBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                copy.fillBitmap = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
            }
        }
        copy.swappedDiskPath = swappedDiskPath
        copy.isCacheDirty = true
        return copy
    }
}

/**
 * Represents an animation track Layer (e.g., "Line Art", "Colors", "Rough Sketch").
 */
class AnimationLayer(
    var name: String,
    var isVisible: Boolean = true,
    var isLocked: Boolean = false,
    var isAlphaLocked: Boolean = false,
    var isReferenceLayer: Boolean = false,
    var isClippingMask: Boolean = false,
    var opacity: Float = 1.0f,
    var blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    var vfxLineBoilPx: Float = 0f,
    var vfxGlowRadiusPx: Float = 0f,
    var vfxDropShadowOffsetPx: Float = 0f
) {
    val tweenTrack = LayerTweenTrack()
    val compositingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun hasActiveVfx(): Boolean {
        return vfxLineBoilPx > 0.1f || vfxGlowRadiusPx > 0.5f || vfxDropShadowOffsetPx > 0.5f
    }

    fun syncCompositingPaint() {
        compositingPaint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        compositingPaint.xfermode = if (blendMode == LayerBlendMode.NORMAL) null else blendMode.xfermode
    }
}
