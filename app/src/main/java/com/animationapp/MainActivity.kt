package com.animationapp

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.ui.workspace.StudioWorkspaceLayout

/**
 * Ultra-lightweight native Android Activity.
 *
 * Uses direct android.app.Activity (zero AppCompat/Compose bloat), negotiates 90Hz/120Hz display refresh rate,
 * installs our In-App Crash & Diagnostics Catcher, and hosts the modular StudioWorkspaceLayout.
 */
class MainActivity : Activity() {

    private var workspaceLayout: StudioWorkspaceLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Install In-App Crash Catcher so we never need local Android Studio Logcat
        CrashReporter.install(this)

        // 2. Request highest display refresh rate (90Hz / 120Hz) where supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = getHighestRefreshRateModeId()
        }

        // 3. Mount modular Studio Workspace UI
        val layout = StudioWorkspaceLayout(this)
        workspaceLayout = layout
        setContentView(layout)

        enableImmersiveFullscreen()

        // 4. Check if a crash occurred in a previous session and offer 1-tap copy
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
