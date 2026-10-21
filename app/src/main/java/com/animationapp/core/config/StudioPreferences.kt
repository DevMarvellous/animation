package com.animationapp.core.config

import android.content.Context

/**
 * Live User-Tunable Preferences & UX Comfort Settings (`SharedPreferences` backed).
 *
 * Not all "bugs" are code crashes—many are UX feel issues (e.g., palm touches triggering drawing,
 * double-tap undo being too sensitive, or a budget phone needing extra FPS boost).
 *
 * This class allows the user to tune every behavior live inside the app via `🎛 Tune UX`,
 * and lets us export/copy the exact JSON snapshot of their settings when they report feedback!
 */
object StudioPreferences {

    private const val PREFS_NAME = "pro_animation_studio_ux_prefs"

    // 1. Input & Ergonomics
    var stylusOnlyPalmRejection: Boolean = false
    var simulatedVelocityPressure: Boolean = true
    var enableMultiTouchTapUndoRedo: Boolean = true
    var twoFingerTapMaxMs: Long = 220L

    // 2. Low-End Device Turbo & Memory
    var lowSpecTurboMode: Boolean = false // Disables anti-alias & onion skins during fast scrubs on weak phones
    var showGridOverlay: Boolean = false
    var gridSpacingPx: Int = 120

    // 3. Brush, Coloring & Feeling
    var defaultStabilizerPercent: Int = 45
    var fillGapClosingRadiusPx: Int = 2 // Seals small 2px–8px line-art gaps during Paint Bucket fill
    var pingPongPlaybackMode: Boolean = false

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        stylusOnlyPalmRejection = prefs.getBoolean("stylusOnlyPalmRejection", false)
        simulatedVelocityPressure = prefs.getBoolean("simulatedVelocityPressure", true)
        enableMultiTouchTapUndoRedo = prefs.getBoolean("enableMultiTouchTapUndoRedo", true)
        twoFingerTapMaxMs = prefs.getLong("twoFingerTapMaxMs", 220L)
        lowSpecTurboMode = prefs.getBoolean("lowSpecTurboMode", false)
        showGridOverlay = prefs.getBoolean("showGridOverlay", false)
        gridSpacingPx = prefs.getInt("gridSpacingPx", 120)
        defaultStabilizerPercent = prefs.getInt("defaultStabilizerPercent", 45)
        fillGapClosingRadiusPx = prefs.getInt("fillGapClosingRadiusPx", 2)
        pingPongPlaybackMode = prefs.getBoolean("pingPongPlaybackMode", false)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean("stylusOnlyPalmRejection", stylusOnlyPalmRejection)
            putBoolean("simulatedVelocityPressure", simulatedVelocityPressure)
            putBoolean("enableMultiTouchTapUndoRedo", enableMultiTouchTapUndoRedo)
            putLong("twoFingerTapMaxMs", twoFingerTapMaxMs)
            putBoolean("lowSpecTurboMode", lowSpecTurboMode)
            putBoolean("showGridOverlay", showGridOverlay)
            putInt("gridSpacingPx", gridSpacingPx)
            putInt("defaultStabilizerPercent", defaultStabilizerPercent)
            putInt("fillGapClosingRadiusPx", fillGapClosingRadiusPx)
            putBoolean("pingPongPlaybackMode", pingPongPlaybackMode)
            apply()
        }
    }

    fun toDiagnosticSummary(): String {
        return "Prefs[StylusOnly=$stylusOnlyPalmRejection, VelPressure=$simulatedVelocityPressure, " +
            "TapUndo=$enableMultiTouchTapUndoRedo(${twoFingerTapMaxMs}ms), " +
            "LowSpecTurbo=$lowSpecTurboMode, Grid=$showGridOverlay, GapClose=${fillGapClosingRadiusPx}px]"
    }
}

