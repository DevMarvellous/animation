package com.animationapp.model

import com.animationapp.audio.LipSyncPhoneme

/**
 * Represents a vertical column in the timeline (a single Frame across multiple Layers).
 *
 * Supports:
 * - Exposure Holds (1s, 2s, 3s, 4s) so holding a pose across multiple timeline ticks uses 0 extra memory.
 * - Lip-Sync Phoneme Mouth Chart Marker (`LipSyncPhoneme`) for 2D voice/mouth synchronization.
 */
class AnimationFrame(
    layerCount: Int = 2,
    var exposureHold: Int = 1,
    var phonemeMarker: LipSyncPhoneme = LipSyncPhoneme.NONE
) {
    val cels = ArrayList<LayerCel>(layerCount)

    // Flag indicating if composite frame cache needs rebuilding
    var isCacheDirty: Boolean = true

    init {
        for (i in 0 until layerCount) {
            cels.add(LayerCel())
        }
    }

    fun getCel(layerIndex: Int): LayerCel {
        while (cels.size <= layerIndex) {
            cels.add(LayerCel())
        }
        return cels[layerIndex]
    }

    val strokes: ArrayList<Stroke>
        get() = getCel(0).strokes

    fun addStrokeToLayer(layerIndex: Int, stroke: Stroke) {
        getCel(layerIndex).addStroke(stroke)
        isCacheDirty = true
    }

    fun undoOnLayer(layerIndex: Int): Boolean {
        val changed = getCel(layerIndex).undo()
        if (changed) isCacheDirty = true
        return changed
    }

    fun redoOnLayer(layerIndex: Int): Boolean {
        val changed = getCel(layerIndex).redo()
        if (changed) isCacheDirty = true
        return changed
    }

    fun hasAnyContent(): Boolean {
        for (i in 0 until cels.size) {
            if (cels[i].strokes.isNotEmpty() || cels[i].fillBitmap != null) return true
        }
        return false
    }

    fun clearLayer(layerIndex: Int) {
        getCel(layerIndex).clear()
        isCacheDirty = true
    }

    fun clearAll() {
        for (i in 0 until cels.size) {
            cels[i].clear()
        }
        isCacheDirty = true
    }

    fun duplicate(): AnimationFrame {
        val dup = AnimationFrame(0, exposureHold, phonemeMarker)
        for (i in 0 until cels.size) {
            dup.cels.add(cels[i].duplicate())
        }
        dup.isCacheDirty = true
        return dup
    }
}
