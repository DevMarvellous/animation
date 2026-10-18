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
import com.animationapp.core.storage.CrashSafeAutosave
import com.animationapp.engine.brush.InputStabilizer
import com.animationapp.engine.canvas.ViewportController
import com.animationapp.engine.onion.OnionSkinRenderer
import com.animationapp.engine.tools.SmartFloodFillEngine
import com.animationapp.engine.tools.StudioToolType
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerCel
import com.animationapp.model.Stroke
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Hardware-accelerated, zero-allocation Multi-Layer 2D Animation Stage & Viewport Engine.
 *
 * Capabilities:
 * - Multi-Layer Compositing (Visibility, Opacity, Blend Modes, Alpha Lock, Reference Layer Fill).
 * - Smart Scanline Flood-Fill Bucket (`FILL_BUCKET`) with Reference Layer boundary detection & 1px under-line color trapping.
 * - Instant Canvas Eyedropper (`EYEDROPPER`) color sampling.
 * - Symmetry Mirror Drawing Guide (Vertical axis mirror drawing).
 * - Exposure Hold playback (1s, 2s, 3s, 4s timing).
 * - Atomic Background Autosave (`CrashSafeAutosave`) after every stroke/action.
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

    // Symmetry Guide Mode
    var isVerticalSymmetryEnabled: Boolean = false

    // Playback loop state with Exposure Hold counter
    var isPlaying: Boolean = false
        private set
    private var lastPlaybackStepTimeMs = 0L
    private var currentHoldTick = 0

    // Offscreen Composite Bitmap Cache & Scratch Layer Bitmap
    private var compositeCacheBitmap: Bitmap? = null
    private var compositeCacheCanvas: Canvas? = null
    private var scratchLayerBitmap: Bitmap? = null
    private var scratchLayerCanvas: Canvas? = null

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
    private val symmetryGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val eraserXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val alphaLockXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

    private val stageRect = RectF(
        0f,
        0f,
        project.stageWidth.toFloat(),
        project.stageHeight.toFloat()
    )

    // Active stroke(s) in progress (primary + optional symmetry mirror stroke)
    private var activeStroke: Stroke? = null
    private var activeMirrorStroke: Stroke? = null

    // Multi-touch gesture tracking
    private var isMultiTouchGesture = false
    private var maxPointersInGesture = 0
    private var gestureDownTimeMs = 0L
    private var gestureMaxMoveDistancePx = 0f
    private var prevFocalX = 0f
    private var prevFocalY = 0f
    private var prevSpan = 0f
    private var prevAngleDeg = 0f

    init {
        ensureCachesAllocated()
        // Restore previous autosaved project if available
        CrashSafeAutosave.loadSavedProjectIfPresent(context, project)
    }

    private fun ensureCachesAllocated() {
        val w = project.stageWidth
        val h = project.stageHeight
        val comp = compositeCacheBitmap
        if (comp == null || comp.isRecycled || comp.width != w || comp.height != h) {
            comp?.let { if (!it.isRecycled) it.recycle() }
            val newComp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            compositeCacheBitmap = newComp
            compositeCacheCanvas = Canvas(newComp)
        }

        val scratch = scratchLayerBitmap
        if (scratch == null || scratch.isRecycled || scratch.width != w || scratch.height != h) {
            scratch?.let { if (!it.isRecycled) it.recycle() }
            val newScratch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            scratchLayerBitmap = newScratch
            scratchLayerCanvas = Canvas(newScratch)
        }
        project.currentFrame.isCacheDirty = true
    }

    private fun rebuildCompositeCacheIfNeeded() {
        val frame = project.currentFrame
        if (!frame.isCacheDirty) return

        ensureCachesAllocated()
        val compCanvas = compositeCacheCanvas ?: return
        val scratchCanvas = scratchLayerCanvas ?: return
        val scratchBmp = scratchLayerBitmap ?: return

        compCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val layers = project.layers
        for (lIdx in 0 until layers.size) {
            val layer = layers[lIdx]
            if (!layer.isVisible) continue

            val cel = frame.getCel(lIdx)
            if (cel.strokes.isEmpty() && cel.fillBitmap == null) continue

            // Render this single layer onto scratch bitmap first so Eraser & Alpha Lock only affect this layer!
            scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            renderCelOntoCanvas(scratchCanvas, cel, layer.isAlphaLocked)

            // Composite layer onto stage cache with layer Opacity & Blend Mode
            layer.syncCompositingPaint()
            compCanvas.drawBitmap(scratchBmp, 0f, 0f, layer.compositingPaint)
        }
        frame.isCacheDirty = false
    }

    private fun renderCelOntoCanvas(targetCanvas: Canvas, cel: LayerCel, isAlphaLocked: Boolean) {
        cel.fillBitmap?.let { fillBmp ->
            if (!fillBmp.isRecycled) {
                targetCanvas.drawBitmap(fillBmp, 0f, 0f, null)
            }
        }
        val strokes = cel.strokes
        for (i in 0 until strokes.size) {
            drawStrokeOntoCanvas(targetCanvas, strokes[i], isAlphaLocked && i > 0)
        }
    }

    private fun drawStrokeOntoCanvas(
        targetCanvas: Canvas,
        stroke: Stroke,
        useAlphaLock: Boolean = false
    ) {
        if (stroke.isEraser) {
            strokePaint.xfermode = eraserXfermode
            strokePaint.strokeWidth = stroke.baseSize
            strokePaint.color = Color.TRANSPARENT
        } else {
            strokePaint.xfermode = if (useAlphaLock) alphaLockXfermode else null
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
        // 1. Advance timeline playback if playing (respecting per-frame Exposure Holds 1s/2s/3s)
        if (isPlaying) {
            val now = SystemClock.uptimeMillis()
            val tickDurationMs = (1000L / project.fps.coerceAtLeast(1))
            if (now - lastPlaybackStepTimeMs >= tickDurationMs) {
                lastPlaybackStepTimeMs = now
                currentHoldTick++
                if (currentHoldTick >= project.currentFrame.exposureHold) {
                    currentHoldTick = 0
                    project.stepForwardLooping()
                    project.currentFrame.isCacheDirty = true
                    onStateChanged?.invoke()
                }
            }
        }

        // 2. Draw dark studio workspace background
        canvas.drawColor(workspaceBgColor)

        // 3. Apply camera viewport transform (Pan, Zoom, Rotate, Mirror)
        canvas.save()
        canvas.concat(viewport.viewMatrix)

        // 4. Draw animation stage paper & border
        stagePaperPaint.color = project.stageBackgroundColor
        canvas.drawRect(stageRect, stagePaperPaint)
        canvas.drawRect(stageRect, stageBorderPaint)

        canvas.clipRect(stageRect)

        // 5. Draw multi-frame tinted Onion Skins (when paused)
        if (!isPlaying && project.isOnionSkinEnabled) {
            onionSkinRenderer.drawOnionSkins(canvas, project)
        }

        // 6. Draw cached multi-layer composite bitmap in O(1)
        rebuildCompositeCacheIfNeeded()
        compositeCacheBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            }
        }

        // 7. Draw active stroke(s) currently being drawn
        activeStroke?.let { stroke ->
            previewActiveStroke(canvas, stroke)
        }
        activeMirrorStroke?.let { mirrorStroke ->
            previewActiveStroke(canvas, mirrorStroke)
        }

        // 8. Draw Vertical Symmetry Guide line if enabled
        if (isVerticalSymmetryEnabled) {
            val centerX = project.stageWidth * 0.5f
            canvas.drawLine(centerX, 0f, centerX, project.stageHeight.toFloat(), symmetryGuidePaint)
        }

        canvas.restore()

        // 9. Draw live Performance HUD in top-left
        performanceHud.recordFrameAndDraw(
            canvas = canvas,
            x = 16f,
            y = 60f,
            frameIndex = project.currentFrameIndex,
            totalFrames = project.frames.size,
            zoomPercent = (viewport.zoomScale * 100f).toInt()
        )

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    private fun previewActiveStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.isEraser) {
            strokePaint.xfermode = null
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = stroke.baseSize
            canvas.drawPath(stroke.renderPath, strokePaint)
        } else {
            drawStrokeOntoCanvas(canvas, stroke)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerCount = event.pointerCount

        if (action == MotionEvent.ACTION_DOWN && pointerCount == 1) {
            if (performanceHud.hitTest(event.x, event.y)) {
                onDiagnosticsRequested?.invoke()
                return true
            }
        }

        // Handle Multi-Touch (2+ fingers or Pan Tool)
        if (pointerCount >= 2 || activeTool == StudioToolType.HAND_PAN) {
            handleMultiTouchOrPanGesture(event)
            return true
        }

        if (isMultiTouchGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finishMultiTouchGesture()
            }
            return true
        }

        if (isPlaying) {
            pausePlayback()
        }

        // Block drawing if active layer is locked or hidden
        val currentLayer = project.activeLayer
        if (currentLayer.isLocked || !currentLayer.isVisible) {
            return true
        }

        val stageX = viewport.screenToStageX(event.x, event.y)
        val stageY = viewport.screenToStageY(event.x, event.y)
        val pressure = event.pressure.coerceIn(0.1f, 1.0f)

        // Handle Instant Single-Tap Tools: EYEDROPPER & FILL_BUCKET
        if (activeTool == StudioToolType.EYEDROPPER) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                sampleColorAtStagePoint(stageX.toInt(), stageY.toInt())
            }
            if (action == MotionEvent.ACTION_UP) {
                activeTool = StudioToolType.BRUSH
                onStateChanged?.invoke()
            }
            return true
        }

        if (activeTool == StudioToolType.FILL_BUCKET) {
            if (action == MotionEvent.ACTION_DOWN) {
                performSmartBucketFillAt(stageX.toInt(), stageY.toInt())
            }
            return true
        }

        // Handle BRUSH & ERASER
        val stageCenterX = project.stageWidth * 0.5f

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                stabilizer.reset(stageX, stageY)
                val isEraser = (activeTool == StudioToolType.ERASER)
                val size = if (isEraser) eraserSizePx else brushSizePx
                val stroke = Stroke(color = brushColor, baseSize = size, isEraser = isEraser)
                stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                activeStroke = stroke

                if (isVerticalSymmetryEnabled) {
                    val mirrorX = (2f * stageCenterX) - stabilizer.smoothedX
                    val mStroke = Stroke(color = brushColor, baseSize = size, isEraser = isEraser)
                    mStroke.addPoint(mirrorX, stabilizer.smoothedY, pressure)
                    activeMirrorStroke = mStroke
                } else {
                    activeMirrorStroke = null
                }
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
                    activeMirrorStroke?.addPoint((2f * stageCenterX) - stabilizer.smoothedX, stabilizer.smoothedY, hp)
                }
                stabilizer.filter(stageX, stageY)
                stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                activeMirrorStroke?.addPoint((2f * stageCenterX) - stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = activeStroke
                if (stroke != null) {
                    stabilizer.filter(stageX, stageY)
                    stroke.finishStroke(stabilizer.smoothedX, stabilizer.smoothedY)
                    project.currentFrame.addStrokeToLayer(project.activeLayerIndex, stroke)

                    activeMirrorStroke?.let { mStroke ->
                        mStroke.finishStroke((2f * stageCenterX) - stabilizer.smoothedX, stabilizer.smoothedY)
                        project.currentFrame.addStrokeToLayer(project.activeLayerIndex, mStroke)
                    }

                    activeStroke = null
                    activeMirrorStroke = null
                    project.currentFrame.isCacheDirty = true
                    triggerAutosave()
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
        }
        return true
    }

    private fun sampleColorAtStagePoint(x: Int, y: Int) {
        val bmp = compositeCacheBitmap ?: return
        if (x in 0 until bmp.width && y in 0 until bmp.height) {
            val pixel = bmp.getPixel(x, y)
            if (Color.alpha(pixel) > 10) {
                brushColor = Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                onStateChanged?.invoke()
            }
        }
    }

    private fun performSmartBucketFillAt(x: Int, y: Int) {
        if (x !in 0 until project.stageWidth || y !in 0 until project.stageHeight) return

        ensureCachesAllocated()
        val scratchCanvas = scratchLayerCanvas ?: return
        val scratchBmp = scratchLayerBitmap ?: return

        // Render the Reference Layer onto scratchBmp so Flood Fill respects reference line boundaries!
        val refLayerIdx = project.getReferenceLayerIndex()
        val refCel = project.currentFrame.getCel(refLayerIdx)
        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        renderCelOntoCanvas(scratchCanvas, refCel, isAlphaLocked = false)

        val targetCel = project.activeCel
        targetCel.fillBitmap = SmartFloodFillEngine.performFloodFill(
            referenceBitmap = scratchBmp,
            targetFillBitmap = targetCel.fillBitmap,
            stageWidth = project.stageWidth,
            stageHeight = project.stageHeight,
            seedX = x,
            seedY = y,
            fillColor = brushColor,
            expandUnderLinePx = 1
        )
        targetCel.isCacheDirty = true
        project.currentFrame.isCacheDirty = true
        CrashReporter.logInfo("FillBucket", "Filled at ($x, $y) using Reference Layer #${refLayerIdx + 1}")
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    private fun handleMultiTouchOrPanGesture(event: MotionEvent) {
        val action = event.actionMasked
        val pointerCount = event.pointerCount

        if (activeStroke != null) {
            activeStroke = null
            activeMirrorStroke = null
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
                finishMultiTouchGesture()
            }
        }
    }

    private fun finishMultiTouchGesture() {
        val duration = SystemClock.uptimeMillis() - gestureDownTimeMs
        if (duration <= StudioConfig.TWO_FINGER_TAP_MAX_DURATION_MS &&
            gestureMaxMoveDistancePx <= StudioConfig.TWO_FINGER_TAP_MAX_SLOP_PX
        ) {
            if (maxPointersInGesture == 2) {
                performUndo()
            } else if (maxPointersInGesture >= 3) {
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
        if (project.currentFrame.undoOnLayer(project.activeLayerIndex)) {
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun performRedo() {
        if (project.currentFrame.redoOnLayer(project.activeLayerIndex)) {
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun togglePlayback() {
        isPlaying = !isPlaying
        currentHoldTick = 0
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
            currentHoldTick = 0
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
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun duplicateCurrentFrame() {
        pausePlayback()
        project.duplicateCurrentFrame()
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun deleteCurrentFrame() {
        pausePlayback()
        project.deleteCurrentFrame()
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun cycleExposureHold() {
        pausePlayback()
        project.cycleCurrentFrameExposureHold()
        triggerAutosave()
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

    fun toggleVerticalSymmetry() {
        isVerticalSymmetryEnabled = !isVerticalSymmetryEnabled
        onStateChanged?.invoke()
        invalidate()
    }

    fun triggerAutosave() {
        CrashSafeAutosave.saveAsync(context, project)
    }

    fun releaseMemory() {
        compositeCacheBitmap?.let { if (!it.isRecycled) it.recycle() }
        scratchLayerBitmap?.let { if (!it.isRecycled) it.recycle() }
        compositeCacheBitmap = null
        compositeCacheCanvas = null
        scratchLayerBitmap = null
        scratchLayerCanvas = null
    }
}
