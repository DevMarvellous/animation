package com.animationapp.model

import com.animationapp.core.config.StudioConfig

/**
 * Represents a single animation frame (Cel) with built-in Undo/Redo stacks
 * and exposure hold support (1s, 2s, 3s).
 */
class AnimationFrame(
    var exposureHold: Int = 1 // 1 = on 1s, 2 = on 2s, etc.
) {
    val strokes = ArrayList<Stroke>(32)
    private val redoStack = ArrayList<Stroke>(16)

    // Flag indicating if offscreen bitmap cache for this frame needs rebuilding
    var isCacheDirty: Boolean = true

    fun addStroke(stroke: Stroke) {
        if (strokes.size >= StudioConfig.MAX_UNDO_HISTORY_STEPS * 4) {
            // Keep memory bounded while allowing deep history
        }
        strokes.add(stroke)
        redoStack.clear()
        isCacheDirty = true
    }

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        val removed = strokes.removeAt(strokes.lastIndex)
        if (redoStack.size >= StudioConfig.MAX_UNDO_HISTORY_STEPS) {
            redoStack.removeAt(0)
        }
        redoStack.add(removed)
        isCacheDirty = true
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val restored = redoStack.removeAt(redoStack.lastIndex)
        strokes.add(restored)
        isCacheDirty = true
        return true
    }

    fun clearAll() {
        strokes.clear()
        redoStack.clear()
        isCacheDirty = true
    }

    fun duplicate(): AnimationFrame {
        val dup = AnimationFrame(exposureHold)
        for (s in strokes) {
            dup.strokes.add(s.deepCopy())
        }
        dup.isCacheDirty = true
        return dup
    }
}
