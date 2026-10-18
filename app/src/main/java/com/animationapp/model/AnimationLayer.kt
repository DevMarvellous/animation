package com.animationapp.model

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * Hardware-accelerated layer blend modes.
 */
enum class LayerBlendMode(val label: String, val porterDuffMode: PorterDuff.Mode) {
    NORMAL("Normal", PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", PorterDuff.Mode.SCREEN),
    ADD("Add (Glow)", PorterDuff.Mode.ADD),
    OVERLAY("Overlay", PorterDuff.Mode.OVERLAY);

    val xfermode: PorterDuffXfermode = PorterDuffXfermode(porterDuffMode)
}

/**
 * Represents a single Cel (the content of one Layer at one Frame).
 *
 * Supports both vector strokes and optional raster flood-fill bitmaps,
 * plus per-cel Undo/Redo stacks.
 */
class LayerCel {
    val strokes = ArrayList<Stroke>(24)
    private val redoStack = ArrayList<Stroke>(12)

    // Optional flood-fill bitmap overlay for bucket fills on this cel
    var fillBitmap: Bitmap? = null
    var isCacheDirty: Boolean = true

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
        copy.isCacheDirty = true
        return copy
    }
}

/**
 * Represents an animation track Layer (e.g., "Line Art", "Colors", "Rough Sketch").
 *
 * Includes pro studio flags:
 * - isVisible: Hide/show layer
 * - isLocked: Prevent accidental drawing
 * - isAlphaLocked: Only paint inside existing opaque pixels on this layer
 * - isReferenceLayer: Used as boundary map for Smart Fill Bucket on other layers
 * - opacity: 0.0f to 1.0f
 * - blendMode: Normal, Multiply, Screen, Add, Overlay
 */
class AnimationLayer(
    var name: String,
    var isVisible: Boolean = true,
    var isLocked: Boolean = false,
    var isAlphaLocked: Boolean = false,
    var isReferenceLayer: Boolean = false,
    var opacity: Float = 1.0f,
    var blendMode: LayerBlendMode = LayerBlendMode.NORMAL
) {
    val compositingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun syncCompositingPaint() {
        compositingPaint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        compositingPaint.xfermode = if (blendMode == LayerBlendMode.NORMAL) null else blendMode.xfermode
    }
}
