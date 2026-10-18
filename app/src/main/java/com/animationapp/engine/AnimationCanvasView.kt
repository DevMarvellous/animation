package com.animationapp.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Hardware-accelerated, zero-allocation 2D animation canvas engine prototype.
 *
 * Designed specifically for smooth 60-120 FPS drawing on low-end Android phones:
 * 1. Pre-allocates all Paint, Path, and telemetry buffers (zero garbage collection inside onDraw).
 * 2. Uses MotionEvent historical coordinates (getHistoricalX/Y) for high-precision curves on budget digitizers.
 * 3. Caches completed strokes directly to an offscreen Bitmap buffer so drawing complexity stays O(1)
 *    even after 10,000+ brush strokes.
 */
class AnimationCanvasView(context: Context) : View(context) {

    // Offscreen layer bitmap cache (prevents re-tessellating thousands of paths every frame)
    private var layerBitmap: Bitmap? = null
    private var layerCanvas: Canvas? = null

    // Active stroke path (reused across strokes to avoid object allocation)
    private val activePath = Path()

    // Pre-allocated Paint for drawing strokes
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 10f
    }

    // Pre-allocated Paint for canvas background & UI HUD
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 163)
        textSize = 32f
        isFakeBoldText = true
    }

    private val hudBgPaint = Paint().apply {
        color = Color.argb(180, 18, 18, 24)
        style = Paint.Style.FILL
    }

    // Touch state tracking (primitive floats to avoid boxing)
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDrawing = false
    private var strokeCount = 0

    // Real-time FPS & RAM telemetry (updated without per-frame string allocations where possible)
    private var lastFrameTimeMs = SystemClock.uptimeMillis()
    private var frameCounter = 0
    private var currentFps = 60
    private var usedRamMb = 0L
    private var hudText = "FPS: -- | RAM: -- MB | Strokes: 0"

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // Recycle previous bitmap if screen resized
        val oldBitmap = layerBitmap
        // ARGB_8888 for active transparent layer
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        newCanvas.drawColor(Color.rgb(24, 24, 30)) // Dark pro studio background

        if (oldBitmap != null && !oldBitmap.isRecycled) {
            newCanvas.drawBitmap(oldBitmap, 0f, 0f, null)
            oldBitmap.recycle()
        }

        layerBitmap = newBitmap
        layerCanvas = newCanvas
        updateTelemetry( forceUpdate = true )
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Draw cached offscreen layer bitmap in O(1) time
        layerBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            }
        }

        // 2. Draw only the currently active stroke on top
        if (isDrawing) {
            canvas.drawPath(activePath, strokePaint)
        }

        // 3. Draw lightweight performance HUD overlay (top-left)
        updateTelemetry(forceUpdate = false)
        canvas.drawRoundRect(20f, 20f, 640f, 84f, 12f, 12f, hudBgPaint)
        canvas.drawText(hudText, 40f, 62f, hudPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePath.reset()
                activePath.moveTo(x, y)
                lastTouchX = x
                lastTouchY = y
                isDrawing = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Process historical batched points for silky-smooth curves on budget screens
                val historySize = event.historySize
                for (i in 0 until historySize) {
                    val hx = event.getHistoricalX(i)
                    val hy = event.getHistoricalY(i)
                    addQuadraticPoint(hx, hy)
                }
                addQuadraticPoint(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    activePath.lineTo(x, y)
                    // Bake finished stroke into the offscreen bitmap cache
                    layerCanvas?.drawPath(activePath, strokePaint)
                    activePath.reset()
                    isDrawing = false
                    strokeCount++
                    updateTelemetry(forceUpdate = true)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun addQuadraticPoint(x: Float, y: Float) {
        val dx = abs(x - lastTouchX)
        val dy = abs(y - lastTouchY)
        if (dx >= 2f || dy >= 2f) {
            val midX = (x + lastTouchX) * 0.5f
            val midY = (y + lastTouchY) * 0.5f
            activePath.quadTo(lastTouchX, lastTouchY, midX, midY)
            lastTouchX = x
            lastTouchY = y
        }
    }

    private fun updateTelemetry(forceUpdate: Boolean) {
        frameCounter++
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastFrameTimeMs
        if (elapsed >= 500L || forceUpdate) {
            if (elapsed > 0) {
                currentFps = ((frameCounter * 1000L) / elapsed).toInt()
            }
            frameCounter = 0
            lastFrameTimeMs = now

            val runtime = Runtime.getRuntime()
            usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            hudText = "FPS: $currentFps | RAM: ${usedRamMb}MB | Strokes: $strokeCount"
        }
    }

    /**
     * Releases native pixel memory immediately when view is detached or low memory occurs.
     */
    fun releaseMemory() {
        layerBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        layerBitmap = null
        layerCanvas = null
    }
}

