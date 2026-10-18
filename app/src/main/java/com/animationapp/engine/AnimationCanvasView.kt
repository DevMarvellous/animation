package com.animationapp.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.animationapp.core.config.StudioConfig
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.core.profiler.PerformanceHud
import com.animationapp.engine.brush.InputStabilizer
import com.animationapp.engine.canvas.ViewportController
import com.animationapp.engine.onion.OnionSkinRenderer
import com.animationapp.engine.tools.StudioToolType
import com.animationapp.model.AnimationProject
import com.animationapp.model.Stroke
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Hardware-accelerated, zero-allocation 2D animation stage & viewport engine.
 *
 * Capabilities:
 * - Multi-touch Pan, Pinch-to-Zoom (15% to 3200%), Two-Finger Canvas Rotation, and Horizontal Mirror Flip.
 * - Two-Finger Quick Tap for instant Undo; Three-Finger Quick Tap for instant Redo.
 * - Input Stabilizer (Lazy-Mouse / Exponential Moving Average) for silky lines on budget screens.
 * - Offscreen Frame Bitmap Cache so completed strokes render in O(1) time.
 * - Tinted Past (Ruby Red) & Future (Cyan) Multi-Frame Onion Skinning.
 * - Real-time FPS / RAM / Frame / Zoom HUD (tap HUD to open Live Diagnostics).
 */
class AnimationCanvasView(
    context: Context,
    val project: AnimationProject = AnimationProject()
) : View(context) {

    var onStateChanged: (() -> Unit)? = null
    var onDiagnosticsRequested: (() -> Unit)? = null

    val viewport = ViewportController()
    val stabilizer = InputStabilizer()
    private val onionSkinRenderer = OnionSkinRenderer()
    val performanceHud = PerformanceHud()

    // Active Tool & Brush Settings
    var activeTool: StudioToolType = StudioToolType.BRUSH
    var brushColor: Int = Color.rgb(20, 22, 28)
    var brushSizePx: Float = StudioConfig.DEFAULT_BRUSH_SIZE_PX
    var eraserSizePx: Float = StudioConfig.DEFAULT_ERASER_SIZE_PX

    // Playback loop state
    var isPlaying: Boolean = false
        private set
    private var lastPlaybackStepTimeMs = 0L

    // Offscreen Bitmap Cache for current active frame
    private var frameCacheBitmap: Bitmap? = null
    private var frameCacheCanvas: Canvas? = null

    // Pre-allocated Paints & Rects (Zero allocations in onDraw)
    private val workspaceBgColor = Color.rgb(18, 19, 24)
    private val stagePaperPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 60, 75)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val eraserXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val stageRect = RectF(
        0f,
        0f,
        project.stageWidth.toFloat(),
        project.stageHeight.toFloat()
    )

    // Active stroke in progress
    private var activeStroke: Stroke? = null

    // Multi-touch gesture tracking (primitive floats to avoid allocations)
    private var isMultiTouchGesture = false
    private var maxPointersInGesture = 0
    private var gestureDownTimeMs = 0L
    private var gestureMaxMoveDistancePx = 0f
    private var prevFocalX = 0f
    private var prevFocalY = 0f
    private var prevSpan = 0f
    private var prevAngleDeg = 0f

    init {
        ensureFrameCacheAllocated()
    }

    private fun ensureFrameCacheAllocated() {
        val currentBmp = frameCacheBitmap
        if (currentBmp == null || currentBmp.isRecycled ||
            currentBmp.width != project.stageWidth || currentBmp.height != project.stageHeight
        ) {
            currentBmp?.let { if (!it.isRecycled) it.recycle() }
            val bmp = Bitmap.createBitmap(
                project.stageWidth,
                project.stageHeight,
                Bitmap.Config.ARGB_8888
            )
            frameCacheBitmap = bmp
            frameCacheCanvas = Canvas(bmp)
            project.currentFrame.isCacheDirty = true
        }
    }

    private fun rebuildFrameCacheIfNeeded() {
        val frame = project.currentFrame
        if (!frame.isCacheDirty) return

        ensureFrameCacheAllocated()
        val cacheCanvas = frameCacheCanvas ?: return
        cacheCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val strokes = frame.strokes
        for (i in 0 until strokes.size) {
            drawStrokeOntoCanvas(cacheCanvas, strokes[i])
        }
        frame.isCacheDirty = false
    }

    private fun drawStrokeOntoCanvas(targetCanvas: Canvas, stroke: Stroke) {
        if (stroke.isEraser) {
            strokePaint.xfermode = eraserXfermode
            strokePaint.strokeWidth = stroke.baseSize
            strokePaint.color = Color.TRANSPARENT
        } else {
            strokePaint.xfermode = null
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.baseSize
        }
        targetCanvas.drawPath(stroke.renderPath, strokePaint)
        strokePaint.xfermode = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            viewport.fitStageToViewport(w, h, project.stageWidth, project.stageHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Advance timeline playback if playing
        if (isPlaying) {
            val now = SystemClock.uptimeMillis()
            val frameDurationMs = (1000L / project.fps.coerceAtLeast(1))
            if (now - lastPlaybackStepTimeMs >= frameDurationMs) {
                lastPlaybackStepTimeMs = now
                project.stepForwardLooping()
                project.currentFrame.isCacheDirty = true
                onStateChanged?.invoke()
            }
        }

        // 2. Draw dark studio workspace background
        canvas.drawColor(workspaceBgColor)

        // 3. Apply camera viewport transform (Pan, Zoom, Rotate, Mirror)
        canvas.save()
        canvas.concat(viewport.viewMatrix)

        // 4. Draw animation stage paper & subtle border
        stagePaperPaint.color = project.stageBackgroundColor
        canvas.drawRect(stageRect, stagePaperPaint)
        canvas.drawRect(stageRect, stageBorderPaint)

        // Clip drawing strictly to the stage bounds
        canvas.clipRect(stageRect)

        // 5. Draw multi-frame tinted Onion Skins (only when paused for max playback speed)
        if (!isPlaying && project.isOnionSkinEnabled) {
            onionSkinRenderer.drawOnionSkins(canvas, project)
        }

        // 6. Draw cached active frame bitmap in O(1)
        rebuildFrameCacheIfNeeded()
        frameCacheBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            }
        }

        // 7. Draw active stroke currently being drawn
        activeStroke?.let { stroke ->
            if (stroke.isEraser) {
                // Visual preview ring/trail for eraser before baking into cache
                strokePaint.xfermode = null
                strokePaint.color = Color.WHITE
                strokePaint.strokeWidth = stroke.baseSize
                canvas.drawPath(stroke.renderPath, strokePaint)
            } else {
                drawStrokeOntoCanvas(canvas, stroke)
            }
        }

        canvas.restore()

        // 8. Draw live Performance HUD in top-left (below top bar)
        performanceHud.recordFrameAndDraw(
            canvas = canvas,
            x = 16f,
            y = 82f,
            frameIndex = project.currentFrameIndex,
            totalFrames = project.frames.size,
            zoomPercent = (viewport.zoomScale * 100f).toInt()
        )

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerCount = event.pointerCount

        // Check if user tapped the Performance HUD to open Live Diagnostics
        if (action == MotionEvent.ACTION_DOWN && pointerCount == 1) {
            if (performanceHud.hitTest(event.x, event.y)) {
                onDiagnosticsRequested?.invoke()
                return true
            }
        }

        // Handle Multi-Touch (2+ fingers: Pinch Zoom, Two-Finger Pan, Rotate, or 2/3-finger Tap Undo/Redo)
        if (pointerCount >= 2 || activeTool == StudioToolType.HAND_PAN) {
            handleMultiTouchOrPanGesture(event)
            return true
        }

        // If transitioning out of a multi-touch gesture, ignore trailing single finger until UP
        if (isMultiTouchGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finishMultiTouchGesture(event)
            }
            return true
        }

        // Single-finger / Stylus Drawing
        if (isPlaying) {
            pausePlayback()
        }

        val stageX = viewport.screenToStageX(event.x, event.y)
        val stageY = viewport.screenToStageY(event.x, event.y)
        val pressure = event.pressure.coerceIn(0.1f, 1.0f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                stabilizer.reset(stageX, stageY)
                val isEraser = (activeTool == StudioToolType.ERASER)
                val size = if (isEraser) eraserSizePx else brushSizePx
                val stroke = Stroke(
                    color = brushColor,
                    baseSize = size,
                    isEraser = isEraser
                )
                stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                activeStroke = stroke
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val stroke = activeStroke ?: return true
                val historySize = event.historySize
                for (i in 0 until historySize) {
                    val hx = viewport.screenToStageX(event.getHistoricalX(i), event.getHistoricalY(i))
                    val hy = viewport.screenToStageY(event.getHistoricalX(i), event.getHistoricalY(i))
                    val hp = event.getHistoricalPressure(i).coerceIn(0.1f, 1.0f)
                    stabilizer.filter(hx, hy)
                    stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, hp)
                }
                stabilizer.filter(stageX, stageY)
                stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = activeStroke
                if (stroke != null) {
                    stabilizer.filter(stageX, stageY)
                    stroke.finishStroke(stabilizer.smoothedX, stabilizer.smoothedY)
                    project.currentFrame.addStroke(stroke)
                    frameCacheCanvas?.let { drawStrokeOntoCanvas(it, stroke) }
                    project.currentFrame.isCacheDirty = false
                    activeStroke = null
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
        }
        return true
    }

    private fun handleMultiTouchOrPanGesture(event: MotionEvent) {
        val action = event.actionMasked
        val pointerCount = event.pointerCount

        // Cancel any unintentional half-started single-finger stroke when 2nd finger touches down
        if (activeStroke != null) {
            activeStroke = null
        }

        if (!isMultiTouchGesture) {
            isMultiTouchGesture = true
            maxPointersInGesture = pointerCount
            gestureDownTimeMs = SystemClock.uptimeMillis()
            gestureMaxMoveDistancePx = 0f
            prevFocalX = computeFocalX(event)
            prevFocalY = computeFocalY(event)
            prevSpan = computeSpan(event, prevFocalX, prevFocalY)
            prevAngleDeg = computeAngleDeg(event)
            return
        }

        if (pointerCount > maxPointersInGesture) {
            maxPointersInGesture = pointerCount
        }

        when (action) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                prevFocalX = computeFocalX(event)
                prevFocalY = computeFocalY(event)
                prevSpan = computeSpan(event, prevFocalX, prevFocalY)
                prevAngleDeg = computeAngleDeg(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val focusX = computeFocalX(event)
                val focusY = computeFocalY(event)
                val dx = focusX - prevFocalX
                val dy = focusY - prevFocalY
                gestureMaxMoveDistancePx += hypot(dx, dy)

                var scaleFactor = 1.0f
                var deltaAngle = 0.0f

                if (pointerCount >= 2) {
                    val span = computeSpan(event, focusX, focusY)
                    if (prevSpan > 10f && span > 10f) {
                        scaleFactor = span / prevSpan
                    }
                    val angle = computeAngleDeg(event)
                    deltaAngle = angle - prevAngleDeg
                    if (deltaAngle > 180f) deltaAngle -= 360f
                    if (deltaAngle < -180f) deltaAngle += 360f

                    prevSpan = span
                    prevAngleDeg = angle
                }

                viewport.applyPanZoomRotate(
                    panDx = dx,
                    panDy = dy,
                    scaleFactor = scaleFactor,
                    deltaDegrees = deltaAngle,
                    focusX = focusX,
                    focusY = focusY
                )

                prevFocalX = focusX
                prevFocalY = focusY
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishMultiTouchGesture(event)
            }
        }
    }

    private fun finishMultiTouchGesture(event: MotionEvent) {
        val duration = SystemClock.uptimeMillis() - gestureDownTimeMs
        if (duration <= StudioConfig.TWO_FINGER_TAP_MAX_DURATION_MS &&
            gestureMaxMoveDistancePx <= StudioConfig.TWO_FINGER_TAP_MAX_SLOP_PX
        ) {
            if (maxPointersInGesture == 2) {
                // Two-finger quick tap -> Undo!
                performUndo()
            } else if (maxPointersInGesture >= 3) {
                // Three-finger quick tap -> Redo!
                performRedo()
            }
        }
        isMultiTouchGesture = false
        maxPointersInGesture = 0
        invalidate()
    }

    private fun computeFocalX(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount
        for (i in 0 until count) sum += event.getX(i)
        return sum / count
    }

    private fun computeFocalY(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount
        for (i in 0 until count) sum += event.getY(i)
        return sum / count
    }

    private fun computeSpan(event: MotionEvent, focusX: Float, focusY: Float): Float {
        var sum = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            sum += hypot(event.getX(i) - focusX, event.getY(i) - focusY)
        }
        return sum / count
    }

    private fun computeAngleDeg(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    // --- Public Studio Actions ---

    fun performUndo() {
        if (project.currentFrame.undo()) {
            CrashReporter.logInfo("Canvas", "Undo on frame ${project.currentFrameIndex + 1}")
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun performRedo() {
        if (project.currentFrame.redo()) {
            CrashReporter.logInfo("Canvas", "Redo on frame ${project.currentFrameIndex + 1}")
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun togglePlayback() {
        isPlaying = !isPlaying
        if (isPlaying) {
            lastPlaybackStepTimeMs = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        } else {
            project.currentFrame.isCacheDirty = true
            invalidate()
        }
        onStateChanged?.invoke()
    }

    fun pausePlayback() {
        if (isPlaying) {
            isPlaying = false
            project.currentFrame.isCacheDirty = true
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun selectFrame(index: Int) {
        pausePlayback()
        project.selectFrame(index)
        project.currentFrame.isCacheDirty = true
        onStateChanged?.invoke()
        invalidate()
    }

    fun addBlankFrame() {
        pausePlayback()
        project.addBlankFrameAfterCurrent()
        project.currentFrame.isCacheDirty = true
        CrashReporter.logInfo("Timeline", "Added frame ${project.currentFrameIndex + 1}")
        onStateChanged?.invoke()
        invalidate()
    }

    fun duplicateCurrentFrame() {
        pausePlayback()
        project.duplicateCurrentFrame()
        project.currentFrame.isCacheDirty = true
        CrashReporter.logInfo("Timeline", "Duplicated frame ${project.currentFrameIndex + 1}")
        onStateChanged?.invoke()
        invalidate()
    }

    fun deleteCurrentFrame() {
        pausePlayback()
        project.deleteCurrentFrame()
        project.currentFrame.isCacheDirty = true
        CrashReporter.logInfo("Timeline", "Deleted/Cleared frame ${project.currentFrameIndex + 1}")
        onStateChanged?.invoke()
        invalidate()
    }

    fun fitStageToScreen() {
        viewport.fitStageToViewport(width, height, project.stageWidth, project.stageHeight)
        invalidate()
    }

    fun toggleMirrorHorizontal() {
        viewport.toggleHorizontalMirror()
        onStateChanged?.invoke()
        invalidate()
    }

    fun releaseMemory() {
        frameCacheBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        frameCacheBitmap = null
        frameCacheCanvas = null
    }
}
