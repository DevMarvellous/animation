package com.animationapp.model

import android.graphics.Color
import com.animationapp.core.config.StudioConfig

/**
 * Top-level animation project container managing stage dimensions, FPS, Multi-Layer stack,
 * Exposure Holds (1s/2s/3s), Reference Layer, and Onion Skinning.
 */
class AnimationProject(
    var stageWidth: Int = StudioConfig.DEFAULT_STAGE_WIDTH,
    var stageHeight: Int = StudioConfig.DEFAULT_STAGE_HEIGHT,
    var fps: Int = StudioConfig.DEFAULT_FPS,
    var stageBackgroundColor: Int = Color.WHITE,
    var projectName: String = "Untitled_Scene"
) {
    var canvasPaperColor: Int
        get() = stageBackgroundColor
        set(value) {
            stageBackgroundColor = value
        }

    fun markAllFramesDirty() {
        for (f in frames) {
            f.isCacheDirty = true
        }
    }

    fun resizeStage(newWidth: Int, newHeight: Int) {
        stageWidth = newWidth.coerceIn(128, 2560)
        stageHeight = newHeight.coerceIn(128, 2560)
        markAllFramesDirty()
    }
    // Default studio setup: Layer 0 = "Color Fill" (bottom), Layer 1 = "Ink Line-Art" (top, set as Reference)
    val layers = ArrayList<AnimationLayer>().apply {
        add(AnimationLayer(name = "Color Layer", isReferenceLayer = false))
        add(AnimationLayer(name = "Line Art (Ref)", isReferenceLayer = true))
    }

    var activeLayerIndex: Int = 1 // Start on Line Art layer by default
        private set

    val frames = ArrayList<AnimationFrame>().apply {
        add(AnimationFrame(layerCount = 2))
    }

    var currentFrameIndex: Int = 0
        private set

    var isOnionSkinEnabled: Boolean = true
    var onionPastCount: Int = StudioConfig.DEFAULT_ONION_PAST_FRAMES
    var onionFutureCount: Int = StudioConfig.DEFAULT_ONION_FUTURE_FRAMES

    // Phase 15: Timeline Loop Play Range Brackets ([ In .. Out ])
    var isLoopRangeEnabled: Boolean = false
    var loopStartFrame: Int = 0
    var loopEndFrame: Int = 0

    val currentFrame: AnimationFrame
        get() = frames[currentFrameIndex]

    val activeLayer: AnimationLayer
        get() = layers[activeLayerIndex.coerceIn(0, layers.lastIndex)]

    val activeCel: LayerCel
        get() = currentFrame.getCel(activeLayerIndex)

    fun selectLayer(index: Int) {
        if (layers.isEmpty()) return
        activeLayerIndex = index.coerceIn(0, layers.lastIndex)
    }

    fun addLayer(name: String = "Layer ${layers.size + 1}"): Int {
        val newLayer = AnimationLayer(name = name)
        layers.add(newLayer)
        val newIdx = layers.lastIndex
        for (f in frames) {
            f.getCel(newIdx)
            f.isCacheDirty = true
        }
        activeLayerIndex = newIdx
        return activeLayerIndex
    }

    fun deleteLayer(index: Int): Boolean {
        if (layers.size <= 1 || index !in layers.indices) return false
        layers.removeAt(index)
        for (f in frames) {
            if (index < f.cels.size) {
                f.cels.removeAt(index)
            }
            f.isCacheDirty = true
        }
        activeLayerIndex = activeLayerIndex.coerceIn(0, layers.lastIndex)
        return true
    }

    fun setReferenceLayer(index: Int) {
        for (i in layers.indices) {
            layers[i].isReferenceLayer = (i == index)
        }
    }

    fun getReferenceLayerIndex(): Int {
        val refIdx = layers.indexOfFirst { it.isReferenceLayer }
        return if (refIdx >= 0) refIdx else activeLayerIndex
    }

    fun selectFrame(index: Int) {
        if (frames.isEmpty()) return
        currentFrameIndex = index.coerceIn(0, frames.lastIndex)
    }

    fun addBlankFrameAfterCurrent(): Int {
        val insertIdx = (currentFrameIndex + 1).coerceAtMost(frames.size)
        frames.add(insertIdx, AnimationFrame(layerCount = layers.size))
        currentFrameIndex = insertIdx
        return currentFrameIndex
    }

    fun duplicateCurrentFrame(): Int {
        val copy = currentFrame.duplicate()
        val insertIdx = (currentFrameIndex + 1).coerceAtMost(frames.size)
        frames.add(insertIdx, copy)
        currentFrameIndex = insertIdx
        return currentFrameIndex
    }

    /**
     * Phase 16: Links the active layer's Cel onto the next frame (creating a new frame if at the end)
     * as a shared Symbol reference! Editing this layer in either frame updates all linked frames automatically.
     */
    fun linkActiveLayerToNextFrame(): Int {
        val sharedCel = activeCel
        sharedCel.isLinkedSymbol = true
        val nextIdx = if (currentFrameIndex + 1 < frames.size) {
            currentFrameIndex + 1
        } else {
            val newFrame = AnimationFrame(layerCount = layers.size)
            frames.add(newFrame)
            frames.lastIndex
        }
        frames[nextIdx].setSharedCel(activeLayerIndex, sharedCel)
        currentFrameIndex = nextIdx
        return currentFrameIndex
    }

    fun unlinkActiveLayerOnCurrentFrame() {
        currentFrame.unlinkCel(activeLayerIndex)
    }

    fun invalidateFramesSharingCel(targetCel: LayerCel) {
        for (i in 0 until frames.size) {
            val f = frames[i]
            for (cIdx in 0 until f.cels.size) {
                if (f.cels[cIdx] === targetCel) {
                    f.isCacheDirty = true
                }
            }
        }
    }

    fun deleteCurrentFrame(): Boolean {
        if (frames.size <= 1) {
            currentFrame.clearAll()
            return true
        }
        frames.removeAt(currentFrameIndex)
        if (currentFrameIndex > frames.lastIndex) {
            currentFrameIndex = frames.lastIndex
        }
        return true
    }

    fun cycleCurrentFrameExposureHold(): Int {
        val nextHold = when (currentFrame.exposureHold) {
            1 -> 2
            2 -> 3
            3 -> 4
            else -> 1
        }
        currentFrame.exposureHold = nextHold
        return nextHold
    }

    fun setLoopInAtCurrent() {
        loopStartFrame = currentFrameIndex.coerceIn(0, frames.lastIndex)
        if (!isLoopRangeEnabled || loopEndFrame < loopStartFrame) {
            loopEndFrame = frames.lastIndex
        }
        isLoopRangeEnabled = true
    }

    fun setLoopOutAtCurrent() {
        loopEndFrame = currentFrameIndex.coerceIn(0, frames.lastIndex)
        if (!isLoopRangeEnabled || loopStartFrame > loopEndFrame) {
            loopStartFrame = 0
        }
        isLoopRangeEnabled = true
    }

    fun clearLoopRange() {
        isLoopRangeEnabled = false
        loopStartFrame = 0
        loopEndFrame = frames.lastIndex
    }

    fun stepForwardLooping(): Int {
        if (isLoopRangeEnabled && frames.size > 1) {
            val start = loopStartFrame.coerceIn(0, frames.lastIndex)
            val end = loopEndFrame.coerceIn(start, frames.lastIndex)
            currentFrameIndex = if (currentFrameIndex >= end || currentFrameIndex < start) {
                start
            } else {
                currentFrameIndex + 1
            }
            return currentFrameIndex
        }
        currentFrameIndex = (currentFrameIndex + 1) % frames.size
        return currentFrameIndex
    }

    fun stepBackwardLooping(): Int {
        if (isLoopRangeEnabled && frames.size > 1) {
            val start = loopStartFrame.coerceIn(0, frames.lastIndex)
            val end = loopEndFrame.coerceIn(start, frames.lastIndex)
            currentFrameIndex = if (currentFrameIndex <= start || currentFrameIndex > end) {
                end
            } else {
                currentFrameIndex - 1
            }
            return currentFrameIndex
        }
        currentFrameIndex = if (currentFrameIndex - 1 < 0) frames.lastIndex else currentFrameIndex - 1
        return currentFrameIndex
    }
}
