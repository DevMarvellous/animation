package com.animationapp.ui.workspace

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.animationapp.audio.LipSyncAudioEngine
import com.animationapp.audio.LipSyncPhoneme
import com.animationapp.core.config.StudioPreferences
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.engine.AnimationCanvasView
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.engine.tools.Shape2DMode
import com.animationapp.engine.tools.StudioToolType
import com.animationapp.export.HardwareVideoExporter
import com.animationapp.export.SpriteSheetExporter
import com.animationapp.model.LayerBlendMode
import com.animationapp.ui.color.ColorPaletteStudio
import com.animationapp.ui.reference.ReferencePinboardView

/**
 * Modular, zero-bloat Native Pro 2D Studio Workspace UI (Phases 12, 13, 14 & 15).
 */
class StudioWorkspaceLayout(private val activity: Activity) : FrameLayout(activity) {

    val canvasView = AnimationCanvasView(activity)
    private val referencePinboard = ReferencePinboardView(activity) { pickedColor ->
        canvasView.brushColor = pickedColor
        if (canvasView.activeTool == StudioToolType.ERASER || canvasView.activeTool == StudioToolType.HAND_PAN) {
            selectTool(StudioToolType.BRUSH)
        } else {
            refreshUiState()
        }
    }

    private var isLeftHandedMode = false
    private lateinit var toolDockContainer: LinearLayout
    private lateinit var frameStripLayout: LinearLayout
    private lateinit var playPauseBtn: TextView
    private lateinit var layersBtn: TextView
    private lateinit var onionBtn: TextView
    private lateinit var symBtn: TextView
    private lateinit var camBtn: TextView
    private lateinit var lipSyncBtn: TextView
    private lateinit var refPinBtn: TextView
    private lateinit var brushBtn: TextView
    private lateinit var colorWheelBtn: TextView
    private lateinit var mirrorBtn: TextView
    private lateinit var fpsBtn: TextView
    private lateinit var exportBtn: TextView
    private lateinit var holdBtn: TextView
    private lateinit var loopBtn: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var activeColorDot: View
    private lateinit var toolButtons: Map<StudioToolType, TextView>

    private var rollStartX = 0f
    private var rollAnchorFrameIdx = 0

    private val studioColors = intArrayOf(
        Color.rgb(20, 22, 28),    // Ink Black
        Color.rgb(235, 59, 90),   // Crimson Red
        Color.rgb(250, 130, 49),  // Vibrant Orange
        Color.rgb(247, 183, 49),  // Golden Yellow
        Color.rgb(32, 191, 107),  // Emerald Green
        Color.rgb(15, 185, 177),  // Teal Cyan
        Color.rgb(45, 152, 218),  // Sketch Blue
        Color.rgb(136, 84, 208),  // Royal Purple
        Color.rgb(255, 255, 255)  // Pure White
    )

    init {
        setBackgroundColor(Color.rgb(18, 19, 24))

        // 1. Add Hardware Animation Canvas
        addView(
            canvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // 2. Add Floating Draggable Reference Pinboard (Hidden by default until `📌 Ref` tapped)
        referencePinboard.visibility = View.GONE
        addView(
            referencePinboard,
            LayoutParams(dp(250f), dp(195f), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(24f)
                topMargin = dp(95f)
            }
        )

        // 3. Build Top Studio Action Bar
        addView(buildTopActionBar())

        // 4. Build Side Dockable Tool & Color Panel
        toolDockContainer = buildSideToolDock()
        addView(toolDockContainer)

        // 5. Build Bottom Animation Timeline Bar
        addView(buildBottomTimelineBar())

        // Wire callbacks
        canvasView.onStateChanged = { refreshUiState() }
        canvasView.onDiagnosticsRequested = { openDiagnosticsDialog() }

        refreshUiState()
    }

    private fun buildTopActionBar(): View {
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg(Color.argb(230, 26, 28, 36), dp(12f))
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }

        val btnUndo = createStudioButton("↶ Undo") { canvasView.performUndo() }
        val btnRedo = createStudioButton("↷ Redo") { canvasView.performRedo() }

        layersBtn = createStudioButton("🗂 Layers") {
            showLayerManagerDialog()
        }

        onionBtn = createStudioButton("Onion: ON") {
            canvasView.project.isOnionSkinEnabled = !canvasView.project.isOnionSkinEnabled
            refreshUiState()
            canvasView.invalidate()
        }

        symBtn = createStudioButton("🦋 Sym") {
            canvasView.toggleVerticalSymmetry()
        }

        camBtn = createStudioButton("🎥 2D Cam") {
            show2DCameraKeyframeDialog()
        }

        lipSyncBtn = createStudioButton("👄 LipSync") {
            showLipSyncPhonemeDialog()
        }

        refPinBtn = createStudioButton("📌 Ref") {
            val nowVisible = (referencePinboard.visibility != View.VISIBLE)
            referencePinboard.visibility = if (nowVisible) View.VISIBLE else View.GONE
            refreshUiState()
        }

        mirrorBtn = createStudioButton("⇄ Flip") {
            canvasView.toggleMirrorHorizontal()
        }

        val btnFit = createStudioButton("⛶ Fit") {
            canvasView.fitStageToScreen()
        }

        fpsBtn = createStudioButton("${canvasView.project.fps} FPS") {
            showFpsPickerDialog()
        }

        brushBtn = createStudioButton("✒ G-Pen") {
            showBrushSettingsDialog()
        }

        colorWheelBtn = createStudioButton("🎨 Color") {
            openColorPaletteStudio()
        }

        val btnTuneUx = createStudioButton("🎛 Tune UX") {
            showUxComfortTunerDialog()
        }

        exportBtn = createStudioButton("🎬 Export") {
            showExportSuiteDialog()
        }

        val btnHandMode = createStudioButton("✋ L/R") {
            toggleHandednessLayout()
        }

        val btnDiag = createStudioButton("🐞 Logs") {
            openDiagnosticsDialog()
        }

        listOf(
            btnUndo, btnRedo, layersBtn, onionBtn, symBtn, camBtn, lipSyncBtn, refPinBtn,
            mirrorBtn, btnFit, fpsBtn, brushBtn, colorWheelBtn, btnTuneUx, exportBtn, btnHandMode, btnDiag
        ).forEach { btn ->
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34f)
            ).apply { marginEnd = dp(6f) }
            topBar.addView(btn, lp)
        }

        val containerLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(8f) }

        val scrollWrapper = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(topBar)
        }
        scrollWrapper.layoutParams = containerLp
        return scrollWrapper
    }

    private fun buildSideToolDock(): LinearLayout {
        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createRoundedBg(Color.argb(230, 26, 28, 36), dp(14f))
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }

        val btnPen = createStudioButton("✏ Pen") { selectTool(StudioToolType.BRUSH) }
        val btnEraser = createStudioButton("⌫ Erase") { selectTool(StudioToolType.ERASER) }
        val btnShapes = createStudioButton("📐 Shapes") {
            if (canvasView.activeTool == StudioToolType.SHAPE_GUIDE) {
                showShapeAndPerspectiveDialog()
            } else {
                selectTool(StudioToolType.SHAPE_GUIDE)
                showShapeAndPerspectiveDialog()
            }
        }
        val btnCutter = createStudioButton("✂ Trim") { selectTool(StudioToolType.VECTOR_CUTTER) }
        val btnLasso = createStudioButton("⬠ Lasso") { selectTool(StudioToolType.LASSO) }
        val btnBucket = createStudioButton("🪣 Fill") { selectTool(StudioToolType.FILL_BUCKET) }
        val btnPicker = createStudioButton("💧 Pick") { selectTool(StudioToolType.EYEDROPPER) }
        val btnCamTool = createStudioButton("🎥 Cam") { selectTool(StudioToolType.CAMERA_2D) }
        val btnShift = createStudioButton("💡 Shift") { selectTool(StudioToolType.SHIFT_TRACE) }
        val btnPan = createStudioButton("✥ Pan") { selectTool(StudioToolType.HAND_PAN) }

        toolButtons = mapOf(
            StudioToolType.BRUSH to btnPen,
            StudioToolType.ERASER to btnEraser,
            StudioToolType.SHAPE_GUIDE to btnShapes,
            StudioToolType.VECTOR_CUTTER to btnCutter,
            StudioToolType.LASSO to btnLasso,
            StudioToolType.FILL_BUCKET to btnBucket,
            StudioToolType.EYEDROPPER to btnPicker,
            StudioToolType.CAMERA_2D to btnCamTool,
            StudioToolType.SHIFT_TRACE to btnShift,
            StudioToolType.HAND_PAN to btnPan
        )

        val toolsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        listOf(
            btnPen, btnEraser, btnShapes, btnCutter, btnLasso, btnBucket,
            btnPicker, btnCamTool, btnShift, btnPan
        ).forEach { btn ->
            val lp = LinearLayout.LayoutParams(dp(74f), dp(29f)).apply {
                bottomMargin = dp(4f)
            }
            toolsColumn.addView(btn, lp)
        }

        val toolsScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(toolsColumn)
        }
        dock.addView(toolsScroll, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(155f)))

        sizeLabel = TextView(context).apply {
            text = "${canvasView.brushSizePx.toInt()}px"
            setTextColor(Color.rgb(180, 190, 210))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, dp(2f))
        }
        dock.addView(sizeLabel)

        activeColorDot = View(context).apply {
            background = createColorOval(canvasView.brushColor, strokeWidthDp = 3f, strokeColor = Color.WHITE)
            setOnClickListener {
                openColorPaletteStudio()
            }
        }
        dock.addView(
            activeColorDot,
            LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { bottomMargin = dp(4f) }
        )

        val colorColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        for (c in studioColors) {
            val swatch = View(context).apply {
                background = createColorOval(c, strokeWidthDp = 1.5f, strokeColor = Color.rgb(90, 95, 110))
                setOnClickListener {
                    canvasView.brushColor = c
                    if (canvasView.activeTool == StudioToolType.ERASER || canvasView.activeTool == StudioToolType.HAND_PAN) {
                        selectTool(StudioToolType.BRUSH)
                    } else {
                        refreshUiState()
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(dp(21f), dp(21f)).apply {
                bottomMargin = dp(4f)
            }
            colorColumn.addView(swatch, lp)
        }

        val swatchScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(colorColumn)
        }
        dock.addView(swatchScroll, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(72f)))

        dock.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END
        ).apply {
            marginEnd = dp(10f)
            marginStart = dp(10f)
        }
        return dock
    }

    private fun buildBottomTimelineBar(): View {
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg(Color.argb(235, 22, 24, 32), dp(14f))
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        }

        val btnPrev = createStudioButton("⏮") {
            canvasView.pausePlayback()
            canvasView.project.stepBackwardLooping()
            canvasView.project.currentFrame.isCacheDirty = true
            LipSyncAudioEngine.playFrameScrubSlice(canvasView.project.currentFrame.phonemeMarker)
            refreshUiState()
            canvasView.invalidate()
        }

        playPauseBtn = createStudioButton("▶ Play") {
            canvasView.togglePlayback()
        }

        val btnNext = createStudioButton("⏭") {
            canvasView.pausePlayback()
            canvasView.project.stepForwardLooping()
            canvasView.project.currentFrame.isCacheDirty = true
            LipSyncAudioEngine.playFrameScrubSlice(canvasView.project.currentFrame.phonemeMarker)
            refreshUiState()
            canvasView.invalidate()
        }

        // Flipbook Quick-Roll Widget (`📖 Roll`): Slide finger left/right to rapidly flip neighboring drawings!
        val btnFlipbookRoll = TextView(context).apply {
            text = "📖 Roll"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
            background = createRoundedBg(Color.rgb(65, 85, 130), dp(8f))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        canvasView.pausePlayback()
                        rollStartX = event.rawX
                        rollAnchorFrameIdx = canvasView.project.currentFrameIndex
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaFrames = ((event.rawX - rollStartX) / dp(22f)).toInt()
                        val targetIdx = (rollAnchorFrameIdx + deltaFrames)
                            .coerceIn(0, canvasView.project.frames.lastIndex)
                        if (targetIdx != canvasView.project.currentFrameIndex) {
                            canvasView.selectFrame(targetIdx)
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        holdBtn = createStudioButton("⏱ 1s") {
            canvasView.cycleExposureHold()
        }

        loopBtn = createStudioButton("🔁 Loop") {
            showLoopAndXSheetDialog()
        }

        val btnAddFrame = createStudioButton("＋ Frame") {
            canvasView.addBlankFrame()
        }

        val btnDupFrame = createStudioButton("⧉ Clone") {
            canvasView.duplicateCurrentFrame()
        }

        val btnDelFrame = createStudioButton("🗑 Del") {
            canvasView.deleteCurrentFrame()
        }

        frameStripLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val frameScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(frameStripLayout)
        }

        val controlMargin = dp(5f)
        listOf(btnPrev, playPauseBtn, btnNext, btnFlipbookRoll, holdBtn, loopBtn).forEach { btn ->
            bottomContainer.addView(
                btn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36f)).apply {
                    marginEnd = controlMargin
                }
            )
        }

        bottomContainer.addView(
            frameScroll,
            LinearLayout.LayoutParams(0, dp(38f), 1f).apply {
                marginEnd = controlMargin
            }
        )

        listOf(btnAddFrame, btnDupFrame, btnDelFrame).forEach { btn ->
            bottomContainer.addView(
                btn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36f)).apply {
                    marginStart = controlMargin
                }
            )
        }

        bottomContainer.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            leftMargin = dp(10f)
            rightMargin = dp(10f)
            bottomMargin = dp(8f)
        }

        return bottomContainer
    }

    private fun selectTool(tool: StudioToolType) {
        canvasView.activeTool = tool
        refreshUiState()
    }

    private fun toggleHandednessLayout() {
        isLeftHandedMode = !isLeftHandedMode
        val lp = toolDockContainer.layoutParams as LayoutParams
        lp.gravity = Gravity.CENTER_VERTICAL or (if (isLeftHandedMode) Gravity.START else Gravity.END)
        toolDockContainer.layoutParams = lp
    }

    private fun showExportSuiteDialog() {
        val options = arrayOf(
            "🎬 Export 1080p MP4 Video (Hardware H.264 to Movies/)",
            "👾 Export 2D Sprite Sheet PNG + JSON Atlas (to Pictures/)"
        )
        AlertDialog.Builder(activity)
            .setTitle("Pro Export Suite")
            .setItems(options) { _, which ->
                if (which == 0) {
                    triggerHardwareMp4Export()
                } else {
                    triggerSpriteSheetExport()
                }
            }
            .show()
    }

    private fun triggerHardwareMp4Export() {
        canvasView.pausePlayback()
        exportBtn.text = "⏳ MP4..."
        exportBtn.isEnabled = false
        styleButton(exportBtn, isHighlighted = true, accentColor = Color.rgb(245, 160, 35))

        HardwareVideoExporter.exportMp4Async(activity, canvasView.project) { success, message ->
            post {
                exportBtn.text = "🎬 Export"
                exportBtn.isEnabled = true
                styleButton(exportBtn, isHighlighted = false, accentColor = Color.rgb(42, 46, 58))

                if (success) {
                    Toast.makeText(activity, "Saved MP4 to $message", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("Export Error")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun triggerSpriteSheetExport() {
        canvasView.pausePlayback()
        exportBtn.text = "⏳ Sheet..."
        exportBtn.isEnabled = false
        styleButton(exportBtn, isHighlighted = true, accentColor = Color.rgb(0, 195, 255))

        SpriteSheetExporter.exportSpriteSheetAsync(activity, canvasView.project) { success, message ->
            post {
                exportBtn.text = "🎬 Export"
                exportBtn.isEnabled = true
                styleButton(exportBtn, isHighlighted = false, accentColor = Color.rgb(42, 46, 58))

                if (success) {
                    Toast.makeText(activity, "Saved Sprite Sheet to $message", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("Sprite Sheet Export Error")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    fun refreshUiState() {
        val proj = canvasView.project

        playPauseBtn.text = if (canvasView.isPlaying) "⏸ Pause" else "▶ Play"
        styleButton(playPauseBtn, isHighlighted = canvasView.isPlaying, accentColor = Color.rgb(0, 200, 130))

        val activeLayer = proj.activeLayer
        val refBadge = if (activeLayer.isReferenceLayer) " [Ref]" else ""
        layersBtn.text = "🗂 ${activeLayer.name}$refBadge"
        styleButton(layersBtn, isHighlighted = true, accentColor = Color.rgb(95, 80, 210))

        val onionOn = proj.isOnionSkinEnabled
        val shiftTag = if (canvasView.onionSkinRenderer.isShifted()) " (Shifted)" else ""
        onionBtn.text = if (onionOn) "Onion: ON$shiftTag" else "Onion: OFF"
        styleButton(onionBtn, isHighlighted = onionOn, accentColor = Color.rgb(255, 75, 110))

        styleButton(symBtn, isHighlighted = canvasView.isVerticalSymmetryEnabled, accentColor = Color.rgb(0, 195, 255))

        val hasCamKey = canvasView.camera2D.hasKeyframeAt(proj.currentFrameIndex)
        camBtn.text = if (hasCamKey) "🎥 Cam [KEY]" else "🎥 2D Cam"
        styleButton(camBtn, isHighlighted = hasCamKey, accentColor = Color.rgb(230, 155, 20))

        val phoneme = proj.currentFrame.phonemeMarker
        lipSyncBtn.text = if (phoneme != LipSyncPhoneme.NONE) "👄 ${phoneme.code}" else "👄 LipSync"
        styleButton(lipSyncBtn, isHighlighted = (phoneme != LipSyncPhoneme.NONE), accentColor = Color.rgb(235, 70, 150))

        styleButton(
            refPinBtn,
            isHighlighted = (referencePinboard.visibility == View.VISIBLE),
            accentColor = Color.rgb(0, 185, 165)
        )

        val preset = canvasView.activeBrushPreset
        brushBtn.text = "${preset.icon} ${preset.label}"

        styleButton(mirrorBtn, isHighlighted = canvasView.viewport.isMirroredHorizontally, accentColor = Color.rgb(0, 180, 255))

        fpsBtn.text = "${proj.fps} FPS"
        val hold = proj.currentFrame.exposureHold
        holdBtn.text = "⏱ ${hold}s"
        styleButton(holdBtn, isHighlighted = (hold > 1), accentColor = Color.rgb(245, 160, 35))

        if (proj.isLoopRangeEnabled) {
            loopBtn.text = "🔁 [${proj.loopStartFrame + 1}-${proj.loopEndFrame + 1}]"
            styleButton(loopBtn, isHighlighted = true, accentColor = Color.rgb(32, 191, 107))
        } else {
            loopBtn.text = "🔁 Loop"
            styleButton(loopBtn, isHighlighted = false, accentColor = Color.rgb(42, 46, 58))
        }

        toolButtons.forEach { (tool, btn) ->
            if (tool == StudioToolType.SHAPE_GUIDE) {
                btn.text = "${canvasView.shapeController.activeShapeMode.icon} Shapes"
            }
            styleButton(btn, isHighlighted = (canvasView.activeTool == tool), accentColor = Color.rgb(75, 123, 236))
        }

        val currentSize = if (canvasView.activeTool == StudioToolType.ERASER) {
            canvasView.eraserSizePx
        } else {
            canvasView.brushSizePx
        }
        sizeLabel.text = "${currentSize.toInt()}px"
        activeColorDot.background = createColorOval(canvasView.brushColor, strokeWidthDp = 3f, strokeColor = Color.WHITE)

        rebuildTimelinePills()
    }

    private fun rebuildTimelinePills() {
        frameStripLayout.removeAllViews()
        val proj = canvasView.project
        val frames = proj.frames
        val activeIdx = proj.currentFrameIndex

        for (i in 0 until frames.size) {
            val frame = frames[i]
            val isCurrent = (i == activeIdx)
            val hasInk = frame.hasAnyContent()
            val hasCam = canvasView.camera2D.hasKeyframeAt(i)
            val phoneme = frame.phonemeMarker
            val loopInPrefix = if (proj.isLoopRangeEnabled && i == proj.loopStartFrame) "⟦" else ""
            val loopOutSuffix = if (proj.isLoopRangeEnabled && i == proj.loopEndFrame) "⟧" else ""
            val holdSuffix = if (frame.exposureHold > 1) " (x${frame.exposureHold})" else ""
            val dotSuffix = if (hasInk) "•" else ""
            val camSuffix = if (hasCam) "🎥" else ""
            val phSuffix = if (phoneme != LipSyncPhoneme.NONE) "[${phoneme.code}]" else ""
            val pillLabel = "$loopInPrefix${i + 1}$dotSuffix$camSuffix$phSuffix$holdSuffix$loopOutSuffix"

            val pill = TextView(context).apply {
                text = pillLabel
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(11f), dp(4f), dp(11f), dp(4f))
                setTextColor(if (isCurrent) Color.WHITE else Color.rgb(185, 190, 205))
                background = createRoundedBg(
                    if (isCurrent) Color.rgb(75, 123, 236) else Color.rgb(38, 42, 54),
                    dp(8f)
                )
                setOnClickListener {
                    canvasView.selectFrame(i)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32f)
            ).apply { marginEnd = dp(5f) }
            frameStripLayout.addView(pill, lp)
        }
    }

    private fun showLipSyncPhonemeDialog() {
        val phonemes = LipSyncPhoneme.values()
        val labels = Array(phonemes.size) { idx ->
            val p = phonemes[idx]
            if (p == LipSyncPhoneme.NONE) "🚫 Clear Mouth Marker" else "👄 [${p.code}] — ${p.description}"
        }

        AlertDialog.Builder(activity)
            .setTitle("2D Lip-Sync Mouth Chart (Frame ${canvasView.project.currentFrameIndex + 1})")
            .setItems(labels) { _, which ->
                val chosen = phonemes[which]
                canvasView.project.currentFrame.phonemeMarker = chosen
                LipSyncAudioEngine.playFrameScrubSlice(chosen)
                canvasView.triggerAutosave()
                refreshUiState()
            }
            .show()
    }

    private fun show2DCameraKeyframeDialog() {
        val frameIdx = canvasView.project.currentFrameIndex
        val eval = FloatArray(4)
        canvasView.camera2D.evaluateAtFrame(frameIdx, eval)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(10f))
        }

        val zoomPercent = (eval[2] * 100).toInt()
        val zoomTitle = TextView(context).apply {
            text = "2D Camera Zoom (Truck-In/Out) at Frame ${frameIdx + 1}: $zoomPercent%"
            setTextColor(Color.WHITE)
        }

        val zoomSeek = SeekBar(context).apply {
            max = 300
            progress = zoomPercent.coerceIn(50, 300)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val z = p.coerceAtLeast(40) / 100f
                    canvasView.camera2D.evaluateAtFrame(frameIdx, eval)
                    canvasView.camera2D.setOrUpdateKeyframe(frameIdx, eval[0], eval[1], z, eval[3])
                    zoomTitle.text = "2D Camera Zoom (Truck-In/Out) at Frame ${frameIdx + 1}: ${p.coerceAtLeast(40)}%"
                    refreshUiState()
                    canvasView.invalidate()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val lensCheck = CheckBox(context).apply {
            text = "Preview Stage Through 2D Camera Lens Now"
            setTextColor(Color.WHITE)
            isChecked = canvasView.camera2D.isCameraViewActive
            setOnCheckedChangeListener { _, checked ->
                canvasView.camera2D.isCameraViewActive = checked
                canvasView.invalidate()
            }
        }

        val btnClearKey = createStudioButton("🗑 Remove Camera Keyframe on Frame ${frameIdx + 1}") {
            canvasView.camera2D.removeKeyframeAt(frameIdx)
            refreshUiState()
            canvasView.invalidate()
        }

        layout.addView(zoomTitle)
        layout.addView(zoomSeek)
        layout.addView(lensCheck)
        layout.addView(
            btnClearKey,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36f)).apply { topMargin = dp(10f) }
        )

        AlertDialog.Builder(activity)
            .setTitle("2D Camera Keyframe & Tweening")
            .setView(layout)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showUxComfortTunerDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(10f))
        }

        val cbStylusOnly = CheckBox(context).apply {
            text = "Stylus-Only Mode (Palm Rejection — Ignore finger drawing)"
            setTextColor(Color.WHITE)
            isChecked = StudioPreferences.stylusOnlyPalmRejection
            setOnCheckedChangeListener { _, checked ->
                StudioPreferences.stylusOnlyPalmRejection = checked
                StudioPreferences.save(context)
            }
        }

        val cbTapUndo = CheckBox(context).apply {
            text = "Enable 2-Finger Tap Undo & 3-Finger Tap Redo"
            setTextColor(Color.WHITE)
            isChecked = StudioPreferences.enableMultiTouchTapUndoRedo
            setOnCheckedChangeListener { _, checked ->
                StudioPreferences.enableMultiTouchTapUndoRedo = checked
                StudioPreferences.save(context)
            }
        }

        val cbTurbo = CheckBox(context).apply {
            text = "Potato Phone Turbo Mode (Max FPS boost for weak budget devices)"
            setTextColor(Color.WHITE)
            isChecked = StudioPreferences.lowSpecTurboMode
            setOnCheckedChangeListener { _, checked ->
                StudioPreferences.lowSpecTurboMode = checked
                StudioPreferences.save(context)
                canvasView.project.currentFrame.isCacheDirty = true
                canvasView.invalidate()
            }
        }

        val cbGrid = CheckBox(context).apply {
            text = "Show 2D Composition & Perspective Alignment Grid"
            setTextColor(Color.WHITE)
            isChecked = StudioPreferences.showGridOverlay
            setOnCheckedChangeListener { _, checked ->
                StudioPreferences.showGridOverlay = checked
                StudioPreferences.save(context)
                canvasView.invalidate()
            }
        }

        val cbAudioScrub = CheckBox(context).apply {
            text = "Play Lip-Sync Phoneme Audio Scrub Slices"
            setTextColor(Color.WHITE)
            isChecked = LipSyncAudioEngine.isAudioScrubEnabled
            setOnCheckedChangeListener { _, checked ->
                LipSyncAudioEngine.isAudioScrubEnabled = checked
            }
        }

        val gapLabel = TextView(context).apply {
            text = "Paint Bucket Gap-Closing Radius: ${StudioPreferences.fillGapClosingRadiusPx} px (Seals open line gaps)"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10f), 0, 0)
        }
        val gapSeek = SeekBar(context).apply {
            max = 8
            progress = StudioPreferences.fillGapClosingRadiusPx.coerceIn(0, 8)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    StudioPreferences.fillGapClosingRadiusPx = p
                    StudioPreferences.save(context)
                    gapLabel.text = "Paint Bucket Gap-Closing Radius: $p px (Seals open line gaps)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        layout.addView(cbStylusOnly)
        layout.addView(cbTapUndo)
        layout.addView(cbTurbo)
        layout.addView(cbGrid)
        layout.addView(cbAudioScrub)
        layout.addView(gapLabel)
        layout.addView(gapSeek)

        AlertDialog.Builder(activity)
            .setTitle("Live UX & Device Comfort Tuner")
            .setView(layout)
            .setPositiveButton("Save & Close", null)
            .show()
    }

    private fun openColorPaletteStudio() {
        ColorPaletteStudio.showDialog(activity, canvasView.brushColor) { selectedColor ->
            canvasView.brushColor = selectedColor
            if (canvasView.activeTool == StudioToolType.ERASER || canvasView.activeTool == StudioToolType.HAND_PAN) {
                selectTool(StudioToolType.BRUSH)
            } else {
                refreshUiState()
            }
        }
    }

    private fun showShapeAndPerspectiveDialog() {
        val shapeModes = Shape2DMode.values()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(10f))
        }

        for (mode in shapeModes) {
            val isCurrent = (canvasView.shapeController.activeShapeMode == mode)
            val btn = createStudioButton("${mode.icon} ${mode.label}") {
                canvasView.shapeController.activeShapeMode = mode
                selectTool(StudioToolType.SHAPE_GUIDE)
                canvasView.invalidate()
            }
            styleButton(btn, isHighlighted = isCurrent, accentColor = Color.rgb(75, 123, 236))
            root.addView(
                btn,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36f)).apply { bottomMargin = dp(6f) }
            )
        }

        val cbPerspectiveRuler = CheckBox(context).apply {
            text = "Show 2-Point Perspective Horizon & Draggable VP1 / VP2 Rulers"
            setTextColor(Color.WHITE)
            isChecked = canvasView.shapeController.showPerspectiveRuler
            setOnCheckedChangeListener { _, checked ->
                canvasView.shapeController.showPerspectiveRuler = checked
                canvasView.invalidate()
            }
        }
        root.addView(cbPerspectiveRuler)

        val btnResetVp = createStudioButton("🎯 Reset Vanishing Points (VP1 & VP2) to Stage Center") {
            canvasView.shapeController.resetVanishingPoints(
                canvasView.project.stageWidth,
                canvasView.project.stageHeight
            )
            canvasView.invalidate()
        }
        root.addView(
            btnResetVp,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34f)).apply { topMargin = dp(8f) }
        )

        AlertDialog.Builder(activity)
            .setTitle("📐 2D Geometric Shapes & Perspective Rulers")
            .setView(root)
            .setPositiveButton("Done") { _, _ -> refreshUiState() }
            .show()
    }

    private fun showLoopAndXSheetDialog() {
        val proj = canvasView.project
        val cur = proj.currentFrameIndex + 1
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(10f))
        }

        val loopRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSetIn = createStudioButton("⟦ Set In (#$cur)") {
            proj.setLoopInAtCurrent()
            refreshUiState()
        }
        val btnSetOut = createStudioButton("Set Out (#$cur) ⟧") {
            proj.setLoopOutAtCurrent()
            refreshUiState()
        }
        val btnClearLoop = createStudioButton("🚫 Clear Loop") {
            proj.clearLoopRange()
            refreshUiState()
        }

        listOf(btnSetIn, btnSetOut, btnClearLoop).forEach { b ->
            loopRow.addView(
                b,
                LinearLayout.LayoutParams(0, dp(34f), 1f).apply { marginEnd = dp(4f) }
            )
        }
        root.addView(loopRow, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10f) })

        val xSheetHeader = TextView(context).apply {
            text = "📋 Vertical Exposure Sheet (X-Sheet) — Tap Row to Jump:"
            setTextColor(Color.rgb(190, 205, 230))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(6f))
        }
        root.addView(xSheetHeader)

        val tableColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (i in 0 until proj.frames.size) {
            val f = proj.frames[i]
            val isCur = (i == proj.currentFrameIndex)
            val inLoop = proj.isLoopRangeEnabled && (i in proj.loopStartFrame..proj.loopEndFrame)
            val camTag = if (canvasView.camera2D.hasKeyframeAt(i)) "🎥Cam " else ""
            val phTag = if (f.phonemeMarker != LipSyncPhoneme.NONE) "👄[${f.phonemeMarker.code}] " else ""
            val celsSummary = proj.layers.indices.joinToString(" | ") { lIdx ->
                val cel = f.getCel(lIdx)
                val count = cel.strokes.size + (if (cel.fillBitmap != null) 1 else 0)
                "L${lIdx + 1}:${if (count > 0) "${count}✎" else "—"}"
            }

            val rowTv = TextView(context).apply {
                text = "Frame #${i + 1} (Hold ${f.exposureHold}s)  $phTag$camTag  [$celsSummary]"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                background = createRoundedBg(
                    when {
                        isCur -> Color.rgb(75, 123, 236)
                        inLoop -> Color.rgb(34, 72, 58)
                        else -> Color.rgb(36, 40, 52)
                    },
                    dp(7f)
                )
                setOnClickListener {
                    canvasView.selectFrame(i)
                }
            }
            tableColumn.addView(
                rowTv,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(4f)
                }
            )
        }

        val scroll = ScrollView(context).apply {
            addView(tableColumn)
        }
        root.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(190f)))

        AlertDialog.Builder(activity)
            .setTitle("🔁 Loop Play Range & Studio X-Sheet")
            .setView(root)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showLayerManagerDialog() {
        val proj = canvasView.project
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10f))
        }

        val btnAddLayer = createStudioButton("＋ New Layer") {
            proj.addLayer()
            proj.currentFrame.isCacheDirty = true
            canvasView.triggerAutosave()
            refreshUiState()
            canvasView.invalidate()
        }

        val btnDelLayer = createStudioButton("🗑 Delete Active") {
            if (proj.deleteLayer(proj.activeLayerIndex)) {
                canvasView.triggerAutosave()
                refreshUiState()
                canvasView.invalidate()
            }
        }

        headerRow.addView(btnAddLayer, LinearLayout.LayoutParams(0, dp(36f), 1f).apply { marginEnd = dp(8f) })
        headerRow.addView(btnDelLayer, LinearLayout.LayoutParams(0, dp(36f), 1f))
        root.addView(headerRow)

        for (lIdx in proj.layers.lastIndex downTo 0) {
            val layer = proj.layers[lIdx]
            val isSelected = (lIdx == proj.activeLayerIndex)

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedBg(
                    if (isSelected) Color.rgb(48, 62, 105) else Color.rgb(34, 38, 48),
                    dp(10f)
                )
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            }

            val topLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(context).apply {
                val refTag = if (layer.isReferenceLayer) " ⭐Ref" else ""
                val alphaTag = if (layer.isAlphaLocked) " 🔒Alpha" else ""
                text = "${lIdx + 1}. ${layer.name}$refTag$alphaTag (${layer.blendMode.label})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setOnClickListener {
                    proj.selectLayer(lIdx)
                    refreshUiState()
                    canvasView.invalidate()
                }
            }
            topLine.addView(titleTv, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            val btnVis = createStudioButton(if (layer.isVisible) "👁" else "🚫") {
                layer.isVisible = !layer.isVisible
                proj.currentFrame.isCacheDirty = true
                canvasView.triggerAutosave()
                refreshUiState()
                canvasView.invalidate()
            }

            val btnRef = createStudioButton(if (layer.isReferenceLayer) "⭐Ref" else "Set Ref") {
                proj.setReferenceLayer(lIdx)
                canvasView.triggerAutosave()
                refreshUiState()
            }

            val btnAlpha = createStudioButton(if (layer.isAlphaLocked) "🔒α" else "🔓α") {
                layer.isAlphaLocked = !layer.isAlphaLocked
                proj.currentFrame.isCacheDirty = true
                canvasView.triggerAutosave()
                canvasView.invalidate()
            }

            val btnBlend = createStudioButton(layer.blendMode.label) {
                val modes = LayerBlendMode.values()
                layer.blendMode = modes[(layer.blendMode.ordinal + 1) % modes.size]
                proj.currentFrame.isCacheDirty = true
                canvasView.triggerAutosave()
                canvasView.invalidate()
            }

            listOf(btnVis, btnRef, btnAlpha, btnBlend).forEach { b ->
                topLine.addView(
                    b,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(30f)).apply { marginStart = dp(4f) }
                )
            }
            row.addView(topLine)

            val opacitySeek = SeekBar(context).apply {
                max = 100
                progress = (layer.opacity * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        layer.opacity = p / 100f
                        proj.currentFrame.isCacheDirty = true
                        canvasView.invalidate()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {
                        canvasView.triggerAutosave()
                    }
                })
            }
            row.addView(opacitySeek)

            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            root.addView(row, rowLp)
        }

        val scroll = ScrollView(context).apply { addView(root) }

        AlertDialog.Builder(activity)
            .setTitle("Studio Layers (Tap Layer Name to Select)")
            .setView(scroll)
            .setPositiveButton("Done") { _, _ -> refreshUiState() }
            .show()
    }

    private fun showBrushSettingsDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(8f))
        }

        val presetLabel = TextView(context).apply {
            text = "Select 2D Brush Preset:"
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6f))
        }
        layout.addView(presetLabel)

        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (preset in BrushPreset.values()) {
            val btn = createStudioButton("${preset.icon} ${preset.label}") {
                canvasView.activeBrushPreset = preset
                selectTool(StudioToolType.BRUSH)
            }
            styleButton(btn, isHighlighted = (canvasView.activeBrushPreset == preset), accentColor = Color.rgb(75, 123, 236))
            presetRow.addView(
                btn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f) }
            )
        }
        val presetScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetRow)
        }
        layout.addView(presetScroll)

        val brushTitle = TextView(context).apply {
            text = "Brush Size: ${canvasView.brushSizePx.toInt()} px"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10f), 0, 0)
        }
        val brushSeek = SeekBar(context).apply {
            max = 120
            progress = canvasView.brushSizePx.toInt().coerceIn(1, 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p.coerceAtLeast(1).toFloat()
                    canvasView.brushSizePx = v
                    brushTitle.text = "Brush Size: ${v.toInt()} px"
                    refreshUiState()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val eraserTitle = TextView(context).apply {
            text = "Eraser Size: ${canvasView.eraserSizePx.toInt()} px"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10f), 0, 0)
        }
        val eraserSeek = SeekBar(context).apply {
            max = 180
            progress = canvasView.eraserSizePx.toInt().coerceIn(4, 180)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p.coerceAtLeast(4).toFloat()
                    canvasView.eraserSizePx = v
                    eraserTitle.text = "Eraser Size: ${v.toInt()} px"
                    refreshUiState()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val stabPercent = (canvasView.stabilizer.strength * 100).toInt()
        val stabTitle = TextView(context).apply {
            text = "Streamline Stabilizer (Smoothness): $stabPercent%"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10f), 0, 0)
        }
        val stabSeek = SeekBar(context).apply {
            max = 90
            progress = stabPercent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    canvasView.stabilizer.strength = p / 100f
                    StudioPreferences.defaultStabilizerPercent = p
                    StudioPreferences.save(context)
                    stabTitle.text = "Streamline Stabilizer (Smoothness): $p%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        layout.addView(brushTitle)
        layout.addView(brushSeek)
        layout.addView(eraserTitle)
        layout.addView(eraserSeek)
        layout.addView(stabTitle)
        layout.addView(stabSeek)

        AlertDialog.Builder(activity)
            .setTitle("2D Brush Presets & Stabilizer Studio")
            .setView(layout)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showFpsPickerDialog() {
        val options = arrayOf(
            "8 FPS (Anime on 3s)",
            "12 FPS (Standard 2D)",
            "24 FPS (Cinema)",
            "30 FPS (Smooth)",
            "60 FPS (Ultra)"
        )
        val values = intArrayOf(8, 12, 24, 30, 60)

        AlertDialog.Builder(activity)
            .setTitle("Select Playback Frame Rate")
            .setItems(options) { _, which ->
                canvasView.project.fps = values[which]
                canvasView.triggerAutosave()
                refreshUiState()
            }
            .show()
    }

    private fun openDiagnosticsDialog() {
        val hud = canvasView.performanceHud
        val proj = canvasView.project
        val stats = buildString {
            appendLine("Live FPS: ${hud.currentFps}")
            appendLine("Heap Memory: ${hud.usedRamMb} MB / ${hud.maxRamMb} MB")
            appendLine("Layers: ${proj.layers.size} (Active: ${proj.activeLayer.name})")
            appendLine("Total Frames: ${proj.frames.size} (Active Hold: ${proj.currentFrame.exposureHold}s)")
            appendLine("Active Brush: ${canvasView.activeBrushPreset.label}")
            appendLine("Active Cel Strokes: ${proj.activeCel.strokes.size}")
            appendLine("2D Camera Keyframes: ${canvasView.camera2D.keyframes.size}")
            appendLine("Viewport Zoom: ${(canvasView.viewport.zoomScale * 100).toInt()}%")
            appendLine(StudioPreferences.toDiagnosticSummary())
        }
        CrashReporter.showLiveDiagnosticsDialog(activity, stats)
    }

    private fun createStudioButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setPadding(dp(9f), dp(4f), dp(9f), dp(4f))
            background = createRoundedBg(Color.rgb(42, 46, 58), dp(8f))
            setOnClickListener { onClick() }
        }
    }

    private fun styleButton(btn: TextView, isHighlighted: Boolean, accentColor: Int) {
        btn.background = createRoundedBg(
            if (isHighlighted) accentColor else Color.rgb(42, 46, 58),
            dp(8f)
        )
    }

    private fun createRoundedBg(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(color)
        }
    }

    private fun createColorOval(color: Int, strokeWidthDp: Float, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }
}
