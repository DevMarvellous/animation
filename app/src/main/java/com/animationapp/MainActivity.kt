package com.animationapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.ui.workspace.StudioWorkspaceLayout

/**
 * Ultra-lightweight native Android Activity.
 *
 * Uses direct android.app.Activity (zero AppCompat/Compose bloat), negotiates 90Hz/120Hz display refresh rate,
 * enables edge-to-edge notch layout, prevents screen sleep while drawing, installs In-App Crash Catcher,
 * handles safe exit confirmation, and hosts the modular StudioWorkspaceLayout.
 */
class MainActivity : Activity() {

    private var workspaceLayout: StudioWorkspaceLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Keep screen active while animating and expand into notches/cutouts
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 2. Install In-App Crash Catcher so we never need local Android Studio Logcat
        CrashReporter.install(this)

        // 3. Request highest display refresh rate (90Hz / 120Hz) where supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = getHighestRefreshRateModeId()
        }

        // 4. Mount modular Studio Workspace UI
        val layout = StudioWorkspaceLayout(this)
        workspaceLayout = layout
        setContentView(layout)

        enableImmersiveFullscreen()

        // 5. Check if a crash occurred in a previous session and offer 1-tap copy
        window.decorView.post {
            CrashReporter.checkAndShowPendingCrashReport(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }

    private fun getHighestRefreshRateModeId(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val modes = display?.supportedModes ?: return 0
            return modes.maxByOrNull { it.refreshRate }?.modeId ?: 0
        }
        return 0
    }

    override fun onPause() {
        workspaceLayout?.canvasView?.triggerAutosave()
        super.onPause()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit ProAnimation Studio?")
            .setMessage("All frames and layers are autosaved atomically.")
            .setPositiveButton("Exit") { _, _ ->
                workspaceLayout?.canvasView?.triggerAutosave()
                super.onBackPressed()
            }
            .setNegativeButton("Keep Animating", null)
            .show()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            CrashReporter.logInfo("Memory", "OS reported critical low memory (level=$level)")
            System.gc()
        }
    }

    override fun onDestroy() {
        workspaceLayout?.canvasView?.releaseMemory()
        workspaceLayout = null
        super.onDestroy()
    }
}
