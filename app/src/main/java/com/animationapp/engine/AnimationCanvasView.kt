package com.animationapp.engine

import android.app.Activity
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
import com.animationapp.audio.ExternalAudioImportEngine
import com.animationapp.audio.LipSyncAudioEngine
import com.animationapp.core.config.StudioConfig
import com.animationapp.core.config.StudioPreferences
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.core.profiler.PerformanceHud
import com.animationapp.core.storage.CrashSafeAutosave
import com.animationapp.core.storage.DiskSwapManager
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.engine.brush.InputStabilizer
import com.animationapp.engine.brush.ScreentoneEngine
import com.animationapp.engine.brush.ScreentoneEngine.ScreentonePattern
import com.animationapp.engine.camera.Camera2DController
import com.animationapp.engine.canvas.ViewportController
import com.animationapp.engine.onion.OnionSkinRenderer
import com.animationapp.engine.rigging.Skeleton2DController
import com.animationapp.engine.tools.GradientToolController
import com.animationapp.engine.tools.LassoTransformController
import com.animationapp.engine.tools.ShapeAndPerspectiveController
import com.animationapp.engine.tools.SmartFloodFillEngine
import com.animationapp.engine.tools.StudioToolType
import com.animationapp.engine.tools.TextToolController
import com.animationapp.engine.tools.VectorStrokeCutter
import com.animationapp.engine.vfx.LayerVfxEngine
import com.animationapp.model.AnimationLayer
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerCel
import com.animationapp.model.Stroke
import com.animationapp.ui.color.ColorPaletteStudio
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Hardware-accelerated, zero-allocation Multi-Layer 2D Animation Stage & Viewport Engine (Phases 19–22).
 */
class AnimationCanvasView(
    context: Context,
    val project: AnimationProject = AnimationProject()
) : View(context) {

    var onStateChanged: (() -> Unit)? = null
    var onDiagnosticsRequested: (() -> Unit)? = null

    val viewport = ViewportController()
    val stabilizer = InputStabilizer()
    val onionSkinRenderer = OnionSkinRenderer()
    val lassoController = LassoTransformController()
    val shapeController = ShapeAndPerspectiveController(project.stageWidth, project.stageHeight)
    val skeletonController = Skeleton2DController()
    val camera2D = Camera2DController()
    val gradientController = GradientToolController()
    val textController = TextToolController()
    val performanceHud = PerformanceHud()

    // Active Tool & Brush Settings
    var activeTool: StudioToolType = StudioToolType.BRUSH
        set(value) {
            if (field != value) {
                lassoController.clearSelection()
                field = value
                invalidate()
            }
        }
    var activeBrushPreset: BrushPreset = BrushPreset.G_PEN
    var activeScreentonePattern: ScreentonePattern = ScreentonePattern.NONE
    var brushColor: Int = Color.rgb(20, 22, 28)
    var brushSizePx: Float = StudioConfig.DEFAULT_BRUSH_SIZE_PX
    var eraserSizePx: Float = StudioConfig.DEFAULT_ERASER_SIZE_PX

    // Symmetry Guide Mode
    var isVerticalSymmetryEnabled: Boolean = false

    // Playback loop state with Exposure Hold counter
    var isPlaying: Boolean = false
        private set
    var isPingPongPlayback: Boolean = StudioPreferences.pingPongPlaybackMode
    private var isPingPongReversing: Boolean = false
    private var lastPlaybackStepTimeMs = 0L
    private var currentHoldTick = 0

    // Flipbook 3-Drawing Inbetween Rocker (Phase 30)
    var isRocking: Boolean = false
        private set
    var rockerBaseFrame: Int = 0
        private set
    var rockerFrames: IntArray = intArrayOf()
        private set
    private var rockerPos: Int = 0
    private var rockerDirection: Int = 1
    var rockerIntervalMs: Long = 125L // ~8 FPS default rocker cadence
    private var lastRockerStepMs: Long = 0L

    // Tool drag tracking
    private var lastToolDragX = 0f
    private var lastToolDragY = 0f
    private val camEvalScratch = FloatArray(4)
    private val layerTweenScratch5 = FloatArray(5)
    private var boilTickCounter = 0

    // Offscreen Composite Bitmap Cache & Scratch Layer Bitmap
    private var compositeCacheBitmap: Bitmap? = null
    private var compositeCacheCanvas: Canvas? = null
    private var scratchLayerBitmap: Bitmap? = null
    private var scratchLayerCanvas: Canvas? = null
    private var clipScratchBitmap: Bitmap? = null
    private var clipScratchCanvas: Canvas? = null
    private val clipMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

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
    private val gridGuidePaint = Paint().apply {
        color = Color.argb(45, 90, 110, 145)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
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

    // Active stroke(s) in progress
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
        StudioPreferences.load(context)
        stabilizer.strength = StudioPreferences.defaultStabilizerPercent / 100f
        ensureCachesAllocated()
        CrashSafeAutosave.loadSavedProjectIfPresent(context, project)
    }

    private fun ensureCachesAllocated() {
        val w = project.stageWidth
        val h = project.stageHeight
        stageRect.set(0f, 0f, w.toFloat(), h.toFloat())
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

        val clip = clipScratchBitmap
        if (clip == null || clip.isRecycled || clip.width != w || clip.height != h) {
            clip?.let { if (!it.isRecycled) it.recycle() }
            val newClip = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            clipScratchBitmap = newClip
            clipScratchCanvas = Canvas(newClip)
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
        val fIdx = project.currentFrameIndex
        var lIdx = 0
        while (lIdx < layers.size) {
            val baseIdx = lIdx
            var chainEnd = baseIdx + 1
            while (chainEnd < layers.size && layers[chainEnd].isClippingMask) {
                chainEnd++
            }

            if (chainEnd == baseIdx + 1) {
                // Standalone unclipped layer fast-path
                val layer = layers[baseIdx]
                if (layer.isVisible) {
                    val cel = frame.getCel(baseIdx)
                    if (cel.hasContent()) {
                        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        scratchCanvas.save()
                        if (layer.tweenTrack.hasKeyframes()) {
                            val tweenMat = layer.tweenTrack.buildLayerMatrix(
                                frameIndex = fIdx,
                                stageWidth = project.stageWidth,
                                stageHeight = project.stageHeight,
                                evalScratch5 = layerTweenScratch5
                            )
                            scratchCanvas.concat(tweenMat)
                        }
                        renderCelOntoCanvas(scratchCanvas, cel, layer, fIdx + boilTickCounter)
                        scratchCanvas.restore()

                        layer.syncCompositingPaint()
                        compCanvas.drawBitmap(scratchBmp, 0f, 0f, layer.compositingPaint)
                    }
                }
                lIdx++
            } else {
                // Clipping mask chain: layers[baseIdx] is base silhouette, layers[baseIdx + 1 until chainEnd] clip to it
                val baseLayer = layers[baseIdx]
                if (baseLayer.isVisible) {
                    val baseCel = frame.getCel(baseIdx)
                    scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    if (baseCel.hasContent()) {
                        scratchCanvas.save()
                        if (baseLayer.tweenTrack.hasKeyframes()) {
                            val tweenMat = baseLayer.tweenTrack.buildLayerMatrix(
                                frameIndex = fIdx,
                                stageWidth = project.stageWidth,
                                stageHeight = project.stageHeight,
                                evalScratch5 = layerTweenScratch5
                            )
                            scratchCanvas.concat(tweenMat)
                        }
                        renderCelOntoCanvas(scratchCanvas, baseCel, baseLayer, fIdx + boilTickCounter)
                        scratchCanvas.restore()
                    }

                    val clipCanvas = clipScratchCanvas
                    val clipBmp = clipScratchBitmap
                    if (clipCanvas != null && clipBmp != null) {
                        for (cIdx in (baseIdx + 1) until chainEnd) {
                            val clipLayer = layers[cIdx]
                            if (!clipLayer.isVisible) continue
                            val clipCel = frame.getCel(cIdx)
                            if (!clipCel.hasContent()) continue

                            clipCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                            clipCanvas.save()
                            if (clipLayer.tweenTrack.hasKeyframes()) {
                                val tweenMat = clipLayer.tweenTrack.buildLayerMatrix(
                                    frameIndex = fIdx,
                                    stageWidth = project.stageWidth,
                                    stageHeight = project.stageHeight,
                                    evalScratch5 = layerTweenScratch5
                                )
                                clipCanvas.concat(tweenMat)
                            }
                            renderCelOntoCanvas(clipCanvas, clipCel, clipLayer, fIdx + boilTickCounter)
                            clipCanvas.restore()

                            clipMaskPaint.alpha = (clipLayer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                            scratchCanvas.drawBitmap(clipBmp, 0f, 0f, clipMaskPaint)
                        }
                    }

                    baseLayer.syncCompositingPaint()
                    compCanvas.drawBitmap(scratchBmp, 0f, 0f, baseLayer.compositingPaint)
                }
                lIdx = chainEnd
            }
        }
        frame.isCacheDirty = false
    }

    private fun renderCelOntoCanvas(
        targetCanvas: Canvas,
        cel: LayerCel,
        layer: AnimationLayer? = null,
        boilSeedFrame: Int = 0
    ) {
        DiskSwapManager.restoreCelFromDiskIfNeeded(cel)?.let { fillBmp ->
            if (!fillBmp.isRecycled) {
                targetCanvas.drawBitmap(fillBmp, 0f, 0f, null)
            }
        }
        val isAlphaLocked = layer?.isAlphaLocked ?: false
        val strokes = cel.strokes
        for (i in 0 until strokes.size) {
            drawStrokeOntoCanvas(targetCanvas, strokes[i], isAlphaLocked && i > 0, layer, boilSeedFrame)
        }
    }

    private fun drawStrokeOntoCanvas(
        targetCanvas: Canvas,
        stroke: Stroke,
        useAlphaLock: Boolean = false,
        layer: AnimationLayer? = null,
        boilSeedFrame: Int = 0
    ) {
        val effectivePath = if (layer != null && layer.vfxLineBoilPx > 0.1f) {
            LayerVfxEngine.getEffectiveStrokePath(stroke, layer, boilSeedFrame)
        } else {
            stroke.renderPath
        }

        if (layer != null && layer.hasActiveVfx()) {
            LayerVfxEngine.drawStrokeUnderlayVfx(targetCanvas, stroke, effectivePath, layer)
        }

        if (stroke.isEraser) {
            strokePaint.xfermode = eraserXfermode
            strokePaint.strokeWidth = stroke.baseSize
            strokePaint.color = Color.TRANSPARENT
            strokePaint.maskFilter = null
            strokePaint.shader = null
        } else {
            stroke.brushPreset.applyToPaint(strokePaint, stroke.color, stroke.baseSize)
            if (StudioPreferences.lowSpecTurboMode) {
                strokePaint.isAntiAlias = false
                strokePaint.maskFilter = null
            }
            strokePaint.xfermode = if (useAlphaLock) alphaLockXfermode else null
            if (stroke.screentonePattern != ScreentonePattern.NONE) {
                strokePaint.shader = ScreentoneEngine.getShader(stroke.screentonePattern, stroke.color)
            } else {
                strokePaint.shader = null
            }
        }
        targetCanvas.drawPath(effectivePath, strokePaint)
        strokePaint.xfermode = null
        strokePaint.maskFilter = null
        strokePaint.shader = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            viewport.fitStageToViewport(w, h, project.stageWidth, project.stageHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Advance timeline playback if playing (respecting per-frame Exposure Holds & Lip-Sync Audio)
        if (isPlaying) {
            val now = SystemClock.uptimeMillis()
            val tickDurationMs = (1000L / project.fps.coerceAtLeast(1))
            if (now - lastPlaybackStepTimeMs >= tickDurationMs) {
                lastPlaybackStepTimeMs = now
                boilTickCounter++
                currentHoldTick++
                if (currentHoldTick >= project.currentFrame.exposureHold) {
                    currentHoldTick = 0
                    val prevFrameIdx = project.currentFrameIndex
                    if (isPingPongPlayback) {
                        stepPlaybackPingPong()
                    } else {
                        project.stepForwardLooping()
                    }
                    val newFrameIdx = project.currentFrameIndex
                    if (newFrameIdx < prevFrameIdx || (project.isLoopRangeEnabled && newFrameIdx == project.loopStartFrame && prevFrameIdx != newFrameIdx)) {
                        // Animation looped back to start: resynchronize dual audio stems
                        ExternalAudioImportEngine.startPlayback(newFrameIdx, project.fps)
                    }
                    project.currentFrame.isCacheDirty = true
                    LipSyncAudioEngine.playFrameScrubSlice(project.currentFrame.phonemeMarker)
                    onStateChanged?.invoke()
                } else {
                    // If any visible layer has Line-Boil VFX enabled, refresh composite cache even on held ticks!
                    for (i in 0 until project.layers.size) {
                        val l = project.layers[i]
                        if (l.isVisible && l.vfxLineBoilPx > 0.1f) {
                            project.currentFrame.isCacheDirty = true
                            break
                        }
                    }
                }
            }
        } else if (isRocking && rockerFrames.size >= 2) {
            val now = SystemClock.uptimeMillis()
            if (now - lastRockerStepMs >= rockerIntervalMs) {
                lastRockerStepMs = now
                rockerPos += rockerDirection
                if (rockerPos >= rockerFrames.size - 1) {
                    rockerPos = rockerFrames.size - 1
                    rockerDirection = -1
                } else if (rockerPos <= 0) {
                    rockerPos = 0
                    rockerDirection = 1
                }
                val targetFrame = rockerFrames[rockerPos]
                if (targetFrame != project.currentFrameIndex) {
                    project.selectFrame(targetFrame)
                    DiskSwapManager.maintainHotFrameWindow(context, project)
                    project.currentFrame.isCacheDirty = true
                    LipSyncAudioEngine.playFrameScrubSlice(project.currentFrame.phonemeMarker)
                    onStateChanged?.invoke()
                }
            }
            postInvalidateOnAnimation()
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

        // Optional: If playing or previewing through 2D Camera lens, apply 2D Camera Keyframe Matrix
        if ((isPlaying || camera2D.isCameraViewActive) && camera2D.keyframes.isNotEmpty()) {
            val camMatrix = camera2D.buildCameraLensMatrix(
                frameIndex = project.currentFrameIndex,
                stageWidth = project.stageWidth,
                stageHeight = project.stageHeight,
                evalScratch = camEvalScratch
            )
            canvas.concat(camMatrix)
        }

        // 5. Draw 2D Grid Overlay if enabled in StudioPreferences
        if (StudioPreferences.showGridOverlay && !isPlaying) {
            draw2DCompositionGrid(canvas)
        }

        // 6. Draw multi-frame tinted Onion Skins
        if (!isPlaying && project.isOnionSkinEnabled) {
            onionSkinRenderer.drawOnionSkins(canvas, project)
        }

        // 7. Draw cached multi-layer composite bitmap in O(1)
        rebuildCompositeCacheIfNeeded()
        compositeCacheBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            }
        }

        // 8. Draw active stroke(s) or Geometric 2D Shape preview currently being drawn
        activeStroke?.let { stroke ->
            previewActiveStroke(canvas, stroke)
        }
        activeMirrorStroke?.let { mirrorStroke ->
            previewActiveStroke(canvas, mirrorStroke)
        }
        if (activeTool == StudioToolType.SHAPE_GUIDE) {
            shapeController.buildPreviewStroke(brushColor, brushSizePx, activeBrushPreset)?.let { shapePreview ->
                previewActiveStroke(canvas, shapePreview)
            }
        }

        // 8b. Draw 1/2-Point Perspective Horizon & Vanishing Point Rulers if enabled
        if (!isPlaying && (shapeController.showPerspectiveRuler || activeTool == StudioToolType.SHAPE_GUIDE)) {
            shapeController.drawPerspectiveGuides(canvas, project.stageWidth, project.stageHeight)
        }

        // 9. Draw Freehand Lasso & Transform Bounding Box Gizmo if active
        if (activeTool == StudioToolType.LASSO) {
            lassoController.drawOverlay(canvas)
        }

        // 9b. Draw Per-Layer Keyframe Motion Path Arc if editing Tween or active layer has keyframes
        if (!isPlaying && (activeTool == StudioToolType.LAYER_TWEEN || project.activeLayer.tweenTrack.hasKeyframes())) {
            project.activeLayer.tweenTrack.drawMotionPathOverlay(
                canvas = canvas,
                stageWidth = project.stageWidth,
                stageHeight = project.stageHeight,
                evalScratch5 = layerTweenScratch5
            )
        }

        // 9c. Draw 2D Cut-Out Skeleton Rig if active or visible
        if (!isPlaying && (activeTool == StudioToolType.BONE_RIG || skeletonController.isSkeletonVisible)) {
            skeletonController.drawSkeletonOverlay(canvas)
        }

        // 10. Draw 2D Camera Viewfinder frame when editing in 2D Camera mode
        if (!isPlaying && !camera2D.isCameraViewActive &&
            (activeTool == StudioToolType.CAMERA_2D || camera2D.keyframes.isNotEmpty())
        ) {
            camera2D.drawCameraViewfinder(
                canvas = canvas,
                frameIndex = project.currentFrameIndex,
                stageWidth = project.stageWidth,
                stageHeight = project.stageHeight,
                evalScratch = camEvalScratch
            )
        }

        // 11. Draw Vertical Symmetry Guide line if enabled
        if (isVerticalSymmetryEnabled) {
            val centerX = project.stageWidth * 0.5f
            canvas.drawLine(centerX, 0f, centerX, project.stageHeight.toFloat(), symmetryGuidePaint)
        }

        // 11b. Draw Phase 26 2D Gradient Tool interactive drag overlay
        if (activeTool == StudioToolType.GRADIENT && gradientController.isDragging) {
            gradientController.drawInteractiveOverlay(canvas)
        }

        canvas.restore()

        // 12. Draw live Performance HUD in top-left
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

    private fun draw2DCompositionGrid(canvas: Canvas) {
        val step = StudioPreferences.gridSpacingPx.coerceAtLeast(40)
        var x = step
        while (x < project.stageWidth) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), project.stageHeight.toFloat(), gridGuidePaint)
            x += step
        }
        var y = step
        while (y < project.stageHeight) {
            canvas.drawLine(0f, y.toFloat(), project.stageWidth.toFloat(), y.toFloat(), gridGuidePaint)
            y += step
        }
    }

    private fun previewActiveStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.isEraser) {
            strokePaint.xfermode = null
            strokePaint.maskFilter = null
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

        // Stylus-Only Palm Rejection Check
        if (StudioPreferences.stylusOnlyPalmRejection) {
            val toolType = event.getToolType(0)
            if (toolType != MotionEvent.TOOL_TYPE_STYLUS && toolType != MotionEvent.TOOL_TYPE_ERASER) {
                return true
            }
        }

        if (isPlaying) {
            pausePlayback()
        }
        if (isRocking) {
            stopInbetweenRocker()
        }

        val stageX = viewport.screenToStageX(event.x, event.y)
        val stageY = viewport.screenToStageY(event.x, event.y)

        // Handle 2D Camera Keyframe Tool
        if (activeTool == StudioToolType.CAMERA_2D) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    if (!camera2D.hasKeyframeAt(project.currentFrameIndex)) {
                        camera2D.evaluateAtFrame(project.currentFrameIndex, camEvalScratch)
                        camera2D.setOrUpdateKeyframe(
                            project.currentFrameIndex,
                            camEvalScratch[0],
                            camEvalScratch[1],
                            camEvalScratch[2],
                            camEvalScratch[3]
                        )
                    }
                    onStateChanged?.invoke()
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = stageX - lastToolDragX
                    val dy = stageY - lastToolDragY
                    camera2D.evaluateAtFrame(project.currentFrameIndex, camEvalScratch)
                    camera2D.setOrUpdateKeyframe(
                        project.currentFrameIndex,
                        camEvalScratch[0] + dx,
                        camEvalScratch[1] + dy,
                        camEvalScratch[2],
                        camEvalScratch[3]
                    )
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    invalidate()
                }
            }
            return true
        }

        // Handle Shift-and-Trace Virtual Light Table Tool
        if (activeTool == StudioToolType.SHIFT_TRACE) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                }
                MotionEvent.ACTION_MOVE -> {
                    onionSkinRenderer.addShiftOffset(stageX - lastToolDragX, stageY - lastToolDragY)
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Block editing if active layer is locked or hidden
        val currentLayer = project.activeLayer
        if (currentLayer.isLocked || !currentLayer.isVisible) {
            return true
        }

        // Handle 2D Vector Stroke Intersection Cutter (`✂ Trim`)
        if (activeTool == StudioToolType.VECTOR_CUTTER) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                }
                MotionEvent.ACTION_MOVE -> {
                    val cut = VectorStrokeCutter.cutIntersectingStrokes(
                        strokes = project.activeCel.strokes,
                        x0 = lastToolDragX,
                        y0 = lastToolDragY,
                        x1 = stageX,
                        y1 = stageY,
                        hitRadiusPx = 14f
                    )
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    if (cut) {
                        project.currentFrame.isCacheDirty = true
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    triggerAutosave()
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Handle 2D Freehand Lasso Selection & Transform Tool
        if (activeTool == StudioToolType.LASSO) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    lassoController.onTouchDown(stageX, stageY)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val modifiedStrokes = lassoController.onTouchMove(stageX, stageY)
                    if (modifiedStrokes) {
                        project.currentFrame.isCacheDirty = true
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lassoController.onTouchUp(stageX, stageY, project.activeCel.strokes)
                    project.currentFrame.isCacheDirty = true
                    triggerAutosave()
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Handle Phase 20: 2D Cut-Out Skeletal Bone Rigging Tool (`🦴 Rig`)
        if (activeTool == StudioToolType.BONE_RIG) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    skeletonController.onTouchDown(stageX, stageY)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (skeletonController.onTouchMove(stageX, stageY, project.activeCel.strokes)) {
                        project.currentFrame.isCacheDirty = true
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    skeletonController.onTouchUp()
                    project.currentFrame.isCacheDirty = true
                    triggerAutosave()
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Handle Phase 19: Per-Layer Keyframe Tweening Drag Tool (`📈 Tween`)
        if (activeTool == StudioToolType.LAYER_TWEEN) {
            val track = currentLayer.tweenTrack
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    track.getOrCreateKeyframeAt(project.currentFrameIndex, layerTweenScratch5)
                    project.currentFrame.isCacheDirty = true
                    onStateChanged?.invoke()
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = stageX - lastToolDragX
                    val dy = stageY - lastToolDragY
                    val kf = track.getOrCreateKeyframeAt(project.currentFrameIndex, layerTweenScratch5)
                    kf.offsetX += dx
                    kf.offsetY += dy
                    lastToolDragX = stageX
                    lastToolDragY = stageY
                    project.currentFrame.isCacheDirty = true
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    triggerAutosave()
                    onStateChanged?.invoke()
                    invalidate()
                }
            }
            return true
        }

        // Handle Phase 26: 2D Linear & Radial Gradient Tool (`🌈 Gradient`)
        if (activeTool == StudioToolType.GRADIENT) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    gradientController.onTouchDown(stageX, stageY, brushColor)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    gradientController.onTouchMove(stageX, stageY)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    gradientController.onTouchUp(stageX, stageY, project, lassoController) {
                        triggerAutosave()
                        onStateChanged?.invoke()
                        invalidate()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    gradientController.cancelDrag()
                    invalidate()
                }
            }
            return true
        }

        // Handle Phase 26: 2D Cartoon Text & Onomatopoeia Tool (`🔤 Text`)
        if (activeTool == StudioToolType.TEXT) {
            if (action == MotionEvent.ACTION_UP) {
                val act = context as? Activity
                if (act != null) {
                    textController.showTextDialog(
                        activity = act,
                        stageX = stageX,
                        stageY = stageY,
                        project = project,
                        primaryColor = brushColor
                    ) {
                        triggerAutosave()
                        onStateChanged?.invoke()
                        invalidate()
                    }
                }
            }
            return true
        }

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

        // Handle 2D Geometric Shape & Perspective Vanishing-Point Ruler Tool (`📐 Shapes`)
        if (activeTool == StudioToolType.SHAPE_GUIDE) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    shapeController.onTouchDown(stageX, stageY)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    shapeController.onTouchMove(stageX, stageY)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val finishedShape = shapeController.onTouchUp(
                        stageX = stageX,
                        stageY = stageY,
                        color = brushColor,
                        sizePx = brushSizePx,
                        preset = activeBrushPreset
                    )
                    if (finishedShape != null) {
                        project.currentFrame.addStrokeToLayer(project.activeLayerIndex, finishedShape)
                        ColorPaletteStudio.recordRecentColor(context, brushColor)
                        project.currentFrame.isCacheDirty = true
                        triggerAutosave()
                        onStateChanged?.invoke()
                    }
                    invalidate()
                }
            }
            return true
        }

        // Handle BRUSH & ERASER
        val pressure = event.pressure.coerceIn(0.1f, 1.0f)
        val stageCenterX = project.stageWidth * 0.5f

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                stabilizer.reset(stageX, stageY)
                val isEraser = (activeTool == StudioToolType.ERASER)
                val size = if (isEraser) eraserSizePx else brushSizePx
                val effectiveTone = if (activeBrushPreset == BrushPreset.MANGA_SCREENTONE && activeScreentonePattern == ScreentonePattern.NONE) {
                    ScreentonePattern.MEDIUM_DOTS_30
                } else {
                    activeScreentonePattern
                }
                val stroke = Stroke(
                    color = brushColor,
                    baseSize = size,
                    isEraser = isEraser,
                    brushPreset = activeBrushPreset,
                    screentonePattern = if (isEraser) ScreentonePattern.NONE else effectiveTone
                )
                stroke.addPoint(stabilizer.smoothedX, stabilizer.smoothedY, pressure)
                activeStroke = stroke

                if (isVerticalSymmetryEnabled) {
                    val mirrorX = (2f * stageCenterX) - stabilizer.smoothedX
                    val mStroke = Stroke(
                        color = brushColor,
                        baseSize = size,
                        isEraser = isEraser,
                        brushPreset = activeBrushPreset,
                        screentonePattern = if (isEraser) ScreentonePattern.NONE else effectiveTone
                    )
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

                    if (!stroke.isEraser) {
                        ColorPaletteStudio.recordRecentColor(context, stroke.color)
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
                ColorPaletteStudio.recordRecentColor(context, brushColor)
                onStateChanged?.invoke()
            }
        }
    }

    private fun performSmartBucketFillAt(x: Int, y: Int) {
        if (x !in 0 until project.stageWidth || y !in 0 until project.stageHeight) return

        ensureCachesAllocated()
        val scratchCanvas = scratchLayerCanvas ?: return
        val scratchBmp = scratchLayerBitmap ?: return

        val refLayerIdx = project.getReferenceLayerIndex()
        val refCel = project.currentFrame.getCel(refLayerIdx)
        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        renderCelOntoCanvas(scratchCanvas, refCel, layer = null, boilSeedFrame = 0)

        val targetCel = project.activeCel
        targetCel.fillBitmap = SmartFloodFillEngine.performFloodFill(
            referenceBitmap = scratchBmp,
            targetFillBitmap = targetCel.fillBitmap,
            stageWidth = project.stageWidth,
            stageHeight = project.stageHeight,
            seedX = x,
            seedY = y,
            fillColor = brushColor,
            expandUnderLinePx = 1,
            gapClosingRadiusPx = StudioPreferences.fillGapClosingRadiusPx,
            screentonePattern = activeScreentonePattern
        )
        ColorPaletteStudio.recordRecentColor(context, brushColor)
        targetCel.isCacheDirty = true
        project.currentFrame.isCacheDirty = true
        CrashReporter.logInfo(
            "FillBucket",
            "Filled at ($x, $y) using Ref Layer #${refLayerIdx + 1} (GapClose=${StudioPreferences.fillGapClosingRadiusPx}px)"
        )
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
        if (StudioPreferences.enableMultiTouchTapUndoRedo &&
            duration <= StudioPreferences.twoFingerTapMaxMs &&
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
        lassoController.clearSelection()
        if (project.currentFrame.undoOnLayer(project.activeLayerIndex)) {
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun performRedo() {
        lassoController.clearSelection()
        if (project.currentFrame.redoOnLayer(project.activeLayerIndex)) {
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun togglePlayback() {
        lassoController.clearSelection()
        if (isRocking) {
            isRocking = false
        }
        isPlaying = !isPlaying
        currentHoldTick = 0
        if (isPlaying) {
            lastPlaybackStepTimeMs = SystemClock.uptimeMillis()
            ExternalAudioImportEngine.startPlayback(project.currentFrameIndex, project.fps)
            LipSyncAudioEngine.playFrameScrubSlice(project.currentFrame.phonemeMarker)
            postInvalidateOnAnimation()
        } else {
            ExternalAudioImportEngine.pausePlayback()
            project.currentFrame.isCacheDirty = true
            invalidate()
        }
        onStateChanged?.invoke()
    }

    /**
     * Starts continuous 3-drawing inbetween rocking around the current frame.
     * Oscillates ping-pong between [N-1, N, N+1].
     */
    fun startInbetweenRocker(speedFps: Int = 8, useFiveFrames: Boolean = false) {
        if (project.frames.size < 2) return
        pausePlayback()
        lassoController.clearSelection()

        val cur = project.currentFrameIndex
        rockerBaseFrame = cur
        rockerIntervalMs = (1000L / speedFps.coerceIn(4, 30))

        rockerFrames = if (useFiveFrames) {
            val f0 = (cur - 2).coerceAtLeast(0)
            val f1 = (cur - 1).coerceAtLeast(0)
            val f2 = cur
            val f3 = (cur + 1).coerceAtMost(project.frames.lastIndex)
            val f4 = (cur + 2).coerceAtMost(project.frames.lastIndex)
            intArrayOf(f0, f1, f2, f3, f4).distinct().toIntArray()
        } else {
            val prev = (cur - 1).coerceAtLeast(0)
            val next = (cur + 1).coerceAtMost(project.frames.lastIndex)
            intArrayOf(prev, cur, next).distinct().toIntArray()
        }

        rockerPos = rockerFrames.indexOf(cur).coerceAtLeast(0)
        rockerDirection = 1
        isRocking = true
        lastRockerStepMs = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
        onStateChanged?.invoke()
    }

    /**
     * Halts inbetween rocking and returns playhead to the original working cel.
     */
    fun stopInbetweenRocker() {
        if (isRocking) {
            isRocking = false
            selectFrame(rockerBaseFrame)
            onStateChanged?.invoke()
            invalidate()
        }
    }

    /**
     * Toggles 3-drawing inbetween rocking on/off.
     */
    fun toggleInbetweenRocker(speedFps: Int = 8) {
        if (isRocking) {
            stopInbetweenRocker()
        } else {
            startInbetweenRocker(speedFps)
        }
    }

    /**
     * Tactile Rocker: Jump directly to the previous neighbor frame in the rocker set.
     */
    fun jumpToRockerPrev() {
        val cur = project.currentFrameIndex
        val prev = (cur - 1).coerceAtLeast(0)
        selectFrame(prev)
    }

    /**
     * Tactile Rocker: Jump directly to the center inbetween drawing.
     */
    fun jumpToRockerCur() {
        selectFrame(rockerBaseFrame.coerceIn(0, project.frames.lastIndex))
    }

    /**
     * Tactile Rocker: Jump directly to the next neighbor frame in the rocker set.
     */
    fun jumpToRockerNext() {
        val cur = project.currentFrameIndex
        val next = (cur + 1).coerceAtMost(project.frames.lastIndex)
        selectFrame(next)
    }

    fun pausePlayback() {
        if (isPlaying) {
            isPlaying = false
            currentHoldTick = 0
            ExternalAudioImportEngine.pausePlayback()
            project.currentFrame.isCacheDirty = true
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun selectFrame(index: Int) {
        pausePlayback()
        if (isRocking && index !in rockerFrames) {
            isRocking = false
        }
        lassoController.clearSelection()
        project.selectFrame(index)
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        ExternalAudioImportEngine.scrubToFrame(index, project.fps)
        LipSyncAudioEngine.playFrameScrubSlice(project.currentFrame.phonemeMarker)
        onStateChanged?.invoke()
        invalidate()
    }

    fun addBlankFrame() {
        pausePlayback()
        lassoController.clearSelection()
        project.addBlankFrameAfterCurrent()
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun addBlankFrameBeforeCurrent() {
        pausePlayback()
        lassoController.clearSelection()
        project.addBlankFrameBeforeCurrent()
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun insertMultipleFrames(count: Int) {
        pausePlayback()
        lassoController.clearSelection()
        project.insertMultipleBlankFramesAfterCurrent(count)
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun setAllFramesExposureHold(hold: Int) {
        pausePlayback()
        project.setAllFramesExposureHold(hold)
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun toggleReferenceLayer(index: Int) {
        project.toggleReferenceLayer(index)
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun duplicateCurrentFrame() {
        pausePlayback()
        lassoController.clearSelection()
        project.duplicateCurrentFrame()
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun linkActiveCelToNextFrame() {
        pausePlayback()
        lassoController.clearSelection()
        project.linkActiveLayerToNextFrame()
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun unlinkActiveCelOnCurrentFrame() {
        pausePlayback()
        lassoController.clearSelection()
        project.unlinkActiveLayerOnCurrentFrame()
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun clearActiveCel() {
        lassoController.clearSelection()
        project.activeCel.clear()
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun clearAllCelsOnCurrentFrame() {
        lassoController.clearSelection()
        for (i in 0 until project.layers.size) {
            project.currentFrame.getCel(i).clear()
        }
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun deleteCurrentFrame() {
        pausePlayback()
        lassoController.clearSelection()
        project.deleteCurrentFrame()
        DiskSwapManager.maintainHotFrameWindow(context, project)
        project.currentFrame.isCacheDirty = true
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    private fun stepPlaybackPingPong() {
        val frames = project.frames
        if (frames.size <= 1) return
        val start = if (project.isLoopRangeEnabled) project.loopStartFrame.coerceIn(0, frames.lastIndex) else 0
        val end = if (project.isLoopRangeEnabled) project.loopEndFrame.coerceIn(start, frames.lastIndex) else frames.lastIndex

        var cur = project.currentFrameIndex
        if (!isPingPongReversing) {
            if (cur >= end) {
                isPingPongReversing = true
                cur = (cur - 1).coerceAtLeast(start)
            } else {
                cur++
            }
        } else {
            if (cur <= start) {
                isPingPongReversing = false
                cur = (cur + 1).coerceAtMost(end)
            } else {
                cur--
            }
        }
        project.selectFrame(cur)
    }

    fun moveCurrentFrameLeft() {
        pausePlayback()
        lassoController.clearSelection()
        if (project.moveCurrentFrameLeft()) {
            DiskSwapManager.maintainHotFrameWindow(context, project)
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun moveCurrentFrameRight() {
        pausePlayback()
        lassoController.clearSelection()
        if (project.moveCurrentFrameRight()) {
            DiskSwapManager.maintainHotFrameWindow(context, project)
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun reverseAllFrames() {
        pausePlayback()
        lassoController.clearSelection()
        if (project.reverseAllFrames()) {
            DiskSwapManager.maintainHotFrameWindow(context, project)
            triggerAutosave()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    fun cycleExposureHold() {
        pausePlayback()
        project.cycleCurrentFrameExposureHold()
        triggerAutosave()
        onStateChanged?.invoke()
        invalidate()
    }

    fun fitStageToScreen() {
        ensureCachesAllocated()
        onionSkinRenderer.resetShiftOffset()
        viewport.fitStageToViewport(width, height, project.stageWidth, project.stageHeight)
        onStateChanged?.invoke()
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
        project.invalidateFramesSharingCel(project.activeCel)
        CrashSafeAutosave.saveAsync(context, project)
    }

    fun releaseMemory() {
        ExternalAudioImportEngine.release()
        ScreentoneEngine.clearCache()
        compositeCacheBitmap?.let { if (!it.isRecycled) it.recycle() }
        scratchLayerBitmap?.let { if (!it.isRecycled) it.recycle() }
        clipScratchBitmap?.let { if (!it.isRecycled) it.recycle() }
        compositeCacheBitmap = null
        compositeCacheCanvas = null
        scratchLayerBitmap = null
        scratchLayerCanvas = null
        clipScratchBitmap = null
        clipScratchCanvas = null
    }
}
