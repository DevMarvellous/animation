package com.animationapp.core.config

/**
 * Central tuning parameters for ProAnimation Studio.
 *
 * All performance limits, brush smoothing defaults, memory thresholds, and studio settings
 * live here so they can be tweaked in one place without hunting across files.
 */
object StudioConfig {

    // --- Default Project Stage Settings ---
    const val DEFAULT_STAGE_WIDTH = 1920
    const val DEFAULT_STAGE_HEIGHT = 1080
    const val DEFAULT_FPS = 24
    const val MIN_FPS = 1
    const val MAX_FPS = 60

    // --- Viewport & Multi-Touch Navigation ---
    const val MIN_ZOOM_SCALE = 0.15f
    const val MAX_ZOOM_SCALE = 32.0f
    const val ROTATION_SNAP_DEGREES = 5.0f // Snap to 0 deg when within 5 deg
    const val TWO_FINGER_TAP_MAX_DURATION_MS = 220L
    const val TWO_FINGER_TAP_MAX_SLOP_PX = 36f

    // --- Brush & Input Stabilizer Defaults ---
    const val DEFAULT_BRUSH_SIZE_PX = 8.0f
    const val MIN_BRUSH_SIZE_PX = 1.0f
    const val MAX_BRUSH_SIZE_PX = 250.0f
    const val DEFAULT_ERASER_SIZE_PX = 36.0f
    const val DEFAULT_STABILIZER_STRENGTH = 0.45f // 0.0 = raw input, 0.95 = heavy rope smoothing
    const val MIN_POINT_DISTANCE_PX = 1.5f

    // --- Onion Skinning Defaults ---
    const val DEFAULT_ONION_PAST_FRAMES = 2
    const val DEFAULT_ONION_FUTURE_FRAMES = 1
    const val ONION_PAST_TINT_COLOR = 0xFFFF3B5C.toInt()   // Ruby Red for previous frames
    const val ONION_FUTURE_TINT_COLOR = 0xFF00C2FF.toInt() // Cyan/Cobalt Blue for future frames
    const val ONION_BASE_ALPHA = 115                        // Max ghost opacity (0-255)

    // --- Memory & Low-End Device Guardrails ---
    const val MAX_UNDO_HISTORY_STEPS = 50
    const val INITIAL_STROKE_FLOAT_CAPACITY = 256 // 64 points * 4 floats (x, y, pressure, width)
    const val LOW_RAM_WARNING_MB = 180L
}
