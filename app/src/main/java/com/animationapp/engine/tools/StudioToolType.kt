package com.animationapp.engine.tools

/**
 * Pluggable 2D studio tool types.
 */
enum class StudioToolType(val label: String) {
    BRUSH("Pen"),
    ERASER("Eraser"),
    VECTOR_CUTTER("Trim"),
    LASSO("Lasso"),
    FILL_BUCKET("Fill"),
    EYEDROPPER("Picker"),
    CAMERA_2D("2D Cam"),
    SHIFT_TRACE("LightTable"),
    HAND_PAN("Pan/Zoom")
}
