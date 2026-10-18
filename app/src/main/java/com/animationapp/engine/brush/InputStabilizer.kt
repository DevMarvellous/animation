package com.animationapp.engine.brush

import com.animationapp.core.config.StudioConfig

/**
 * Real-time Lazy-Mouse / Exponential Moving Average input stabilizer.
 *
 * Budget phones often have noisy, low-polling capacitive touch digitizers that cause
 * diagonal lines to wobble ("stair-stepping"). This stabilizer filters raw coordinates
 * with zero allocations.
 */
class InputStabilizer {

    var strength: Float = StudioConfig.DEFAULT_STABILIZER_STRENGTH
        set(value) {
            field = value.coerceIn(0f, 0.92f)
        }

    var smoothedX: Float = 0f
        private set
    var smoothedY: Float = 0f
        private set

    fun reset(startX: Float, startY: Float) {
        smoothedX = startX
        smoothedY = startY
    }

    fun filter(rawX: Float, rawY: Float) {
        val alpha = 1.0f - strength
        smoothedX += (rawX - smoothedX) * alpha
        smoothedY += (rawY - smoothedY) * alpha
    }
}
