package com.animationapp.core.profiler

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock

/**
 * Zero-allocation real-time FPS, Frame Time (ms), and Heap RAM monitor.
 */
class PerformanceHud {

    private val bgPaint = Paint().apply {
        color = Color.argb(190, 16, 18, 24)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 255, 163)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 163)
        textSize = 24f
        isFakeBoldText = true
    }

    private val bounds = RectF()
    private var lastSampleTimeMs = SystemClock.uptimeMillis()
    private var frameCount = 0
    var currentFps = 60
        private set
    var usedRamMb = 0L
        private set
    var maxRamMb = 0L
        private set

    private var hudString = "FPS: 60 | RAM: -- MB"

    fun recordFrameAndDraw(
        canvas: Canvas,
        x: Float,
        y: Float,
        frameIndex: Int,
        totalFrames: Int,
        zoomPercent: Int
    ) {
        frameCount++
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastSampleTimeMs

        if (elapsed >= 500L) {
            currentFps = ((frameCount * 1000L) / elapsed).toInt().coerceIn(1, 240)
            frameCount = 0
            lastSampleTimeMs = now

            val rt = Runtime.getRuntime()
            usedRamMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            maxRamMb = rt.maxMemory() / (1024 * 1024)

            textPaint.color = when {
                currentFps >= 50 -> Color.rgb(0, 255, 163) // Healthy Green
                currentFps >= 30 -> Color.rgb(255, 204, 0) // Warning Amber
                else -> Color.rgb(255, 75, 92)             // Lag Red
            }

            hudString = "FPS:$currentFps | RAM:${usedRamMb}M | F:${frameIndex + 1}/$totalFrames | ${zoomPercent}%"
        }

        val textWidth = textPaint.measureText(hudString)
        bounds.set(x, y, x + textWidth + 32f, y + 46f)
        canvas.drawRoundRect(bounds, 10f, 10f, bgPaint)
        canvas.drawRoundRect(bounds, 10f, 10f, borderPaint)
        canvas.drawText(hudString, x + 16f, y + 31f, textPaint)
    }

    fun hitTest(touchX: Float, touchY: Float): Boolean {
        return bounds.contains(touchX, touchY)
    }
}

