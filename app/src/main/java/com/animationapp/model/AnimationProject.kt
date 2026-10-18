package com.animationapp.model

import android.graphics.Color
import com.animationapp.core.config.StudioConfig

/**
 * Top-level animation project container managing stage dimensions, FPS, onion skinning settings,
 * and frame sequence.
 */
class AnimationProject(
    val stageWidth: Int = StudioConfig.DEFAULT_STAGE_WIDTH,
    val stageHeight: Int = StudioConfig.DEFAULT_STAGE_HEIGHT,
    var fps: Int = StudioConfig.DEFAULT_FPS,
    var stageBackgroundColor: Int = Color.WHITE
) {
    val frames = ArrayList<AnimationFrame>().apply {
        add(AnimationFrame())
    }

    var currentFrameIndex: Int = 0
        private set

    var isOnionSkinEnabled: Boolean = true
    var onionPastCount: Int = StudioConfig.DEFAULT_ONION_PAST_FRAMES
    var onionFutureCount: Int = StudioConfig.DEFAULT_ONION_FUTURE_FRAMES

    val currentFrame: AnimationFrame
        get() = frames[currentFrameIndex]

    fun selectFrame(index: Int) {
        if (frames.isEmpty()) return
        currentFrameIndex = index.coerceIn(0, frames.lastIndex)
    }

    fun addBlankFrameAfterCurrent(): Int {
        val insertIdx = (currentFrameIndex + 1).coerceAtMost(frames.size)
        frames.add(insertIdx, AnimationFrame())
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

    fun deleteCurrentFrame(): Boolean {
        if (frames.size <= 1) {
            // If only 1 frame exists, clear it instead of leaving 0 frames
            currentFrame.clearAll()
            return true
        }
        frames.removeAt(currentFrameIndex)
        if (currentFrameIndex > frames.lastIndex) {
            currentFrameIndex = frames.lastIndex
        }
        return true
    }

    fun stepForwardLooping(): Int {
        currentFrameIndex = (currentFrameIndex + 1) % frames.size
        return currentFrameIndex
    }

    fun stepBackwardLooping(): Int {
        currentFrameIndex = if (currentFrameIndex - 1 < 0) frames.lastIndex else currentFrameIndex - 1
        return currentFrameIndex
    }
}
