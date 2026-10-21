package com.animationapp.engine.tools

/**
 * Pluggable 2D studio tool types.
 */
enum class StudioToolType(val label: String) {
    BRUSH("Pen"),
    ERASER("Eraser"),
    SHAPE_GUIDE("Shapes"),
    VECTOR_CUTTER("Trim"),
    LASSO("Lasso"),
    BONE_RIG("2D Rig"),
    LAYER_TWEEN("Tween"),
    FILL_BUCKET("Fill"),
    GRADIENT("Gradient"),
    TEXT("Text"),
    EYEDROPPER("Picker"),
    CAMERA_2D("2D Cam"),
    SHIFT_TRACE("LightTable"),
    HAND_PAN("Pan/Zoom")
}
