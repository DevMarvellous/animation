package com.animationapp.engine.tools

/**
 * Pluggable 2D studio tool types.
 */
enum class StudioToolType(val label: String) {
    BRUSH("Pen"),
    ERASER("Eraser"),
    LASSO("Lasso"),
    FILL_BUCKET("Fill"),
    EYEDROPPER("Picker"),
    SHIFT_TRACE("LightTable"),
    HAND_PAN("Pan/Zoom")
}
