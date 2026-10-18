package com.animationapp.ui.workspace

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.engine.AnimationCanvasView
import com.animationapp.engine.tools.StudioToolType
import com.animationapp.model.LayerBlendMode

/**
 * Modular, zero-bloat Native Pro Studio Workspace UI (Phase 4).
 *
 * Includes:
 * - Top Action Bar: Undo, Redo, Layers Manager (`🗂 Layers`), Onion Skin Toggle, Symmetry Guide (`🦋 Sym`),
 *   Mirror Flip (`⇄ Flip`), Fit Stage (`⛶ Fit`), FPS Selector, Brush Studio (`⚙ Brush`), Left/Right Hand Flip, Logs.
 * - Dockable Tool & Color Bar: Pen, Eraser, Smart Fill Bucket (`🪣 Fill`), Eyedropper (`💧 Pick`), Pan/Zoom,
 *   Active Color Indicator & Quick Color Swatches.
 * - Bottom Timeline Scrubber: Play/Pause, Step Back/Forward, Frame Pills (with Exposure Hold badge `x2`, `x3`),
 *   Hold Timing button (`⏱ 1s/2s/3s`), +Add Frame, Clone Frame, Delete Frame.
 */
class StudioWorkspaceLayout(private val activity: Activity) : FrameLayout(activity) {

    val canvasView = AnimationCanvasView(activity)

    private var isLeftHandedMode = false
    private lateinit var toolDockContainer: LinearLayout
    private lateinit var frameStripLayout: LinearLayout
    private lateinit var playPauseBtn: TextView
    private lateinit var layersBtn: TextView
    private lateinit var onionBtn: TextView
    private lateinit var symBtn: TextView
    private lateinit var mirrorBtn: TextView
    private lateinit var fpsBtn: TextView
    private lateinit var holdBtn: TextView
    private lateinit var sizeLabel: TextView
    private lateinit var activeColorDot: View
    private lateinit var toolButtons: Map<StudioToolType, TextView>

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

        // 2. Build Top Studio Action Bar
        addView(buildTopActionBar())

        // 3. Build Side Dockable Tool & Color Panel
        toolDockContainer = buildSideToolDock()
        addView(toolDockContainer)

        // 4. Build Bottom Animation Timeline Bar
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

        mirrorBtn = createStudioButton("⇄ Flip") {
            canvasView.toggleMirrorHorizontal()
        }

        val btnFit = createStudioButton("⛶ Fit") {
            canvasView.fitStageToScreen()
        }

        fpsBtn = createStudioButton("${canvasView.project.fps} FPS") {
            showFpsPickerDialog()
        }

        val btnSliders = createStudioButton("⚙ Brush") {
            showBrushSettingsDialog()
        }

        val btnHandMode = createStudioButton("✋ L/R") {
            toggleHandednessLayout()
        }

        val btnDiag = createStudioButton("🐞 Logs") {
            openDiagnosticsDialog()
        }

        listOf(
            btnUndo, btnRedo, layersBtn, onionBtn, symBtn, mirrorBtn,
            btnFit, fpsBtn, btnSliders, btnHandMode, btnDiag
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
        val btnBucket = createStudioButton("🪣 Fill") { selectTool(StudioToolType.FILL_BUCKET) }
        val btnPicker = createStudioButton("💧 Pick") { selectTool(StudioToolType.EYEDROPPER) }
        val btnPan = createStudioButton("✥ Pan") { selectTool(StudioToolType.HAND_PAN) }

        toolButtons = mapOf(
            StudioToolType.BRUSH to btnPen,
            StudioToolType.ERASER to btnEraser,
            StudioToolType.FILL_BUCKET to btnBucket,
            StudioToolType.EYEDROPPER to btnPicker,
            StudioToolType.HAND_PAN to btnPan
        )

        listOf(btnPen, btnEraser, btnBucket, btnPicker, btnPan).forEach { btn ->
            val lp = LinearLayout.LayoutParams(dp(72f), dp(32f)).apply {
                bottomMargin = dp(5f)
            }
            dock.addView(btn, lp)
        }

        sizeLabel = TextView(context).apply {
            text = "${canvasView.brushSizePx.toInt()}px"
            setTextColor(Color.rgb(180, 190, 210))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(1f), 0, dp(3f))
        }
        dock.addView(sizeLabel)

        // Active color indicator ring
        activeColorDot = View(context).apply {
            background = createColorOval(canvasView.brushColor, strokeWidthDp = 3f, strokeColor = Color.WHITE)
        }
        dock.addView(
            activeColorDot,
            LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { bottomMargin = dp(6f) }
        )

        // Scrollable Swatch Strip so it fits even on compact 4.7" screens
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
            val lp = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                bottomMargin = dp(4f)
            }
            colorColumn.addView(swatch, lp)
        }

        val swatchScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(colorColumn)
        }
        dock.addView(swatchScroll, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(115f)))

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
            refreshUiState()
            canvasView.invalidate()
        }

        holdBtn = createStudioButton("⏱ 1s") {
            canvasView.cycleExposureHold()
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
        listOf(btnPrev, playPauseBtn, btnNext, holdBtn).forEach { btn ->
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

    fun refreshUiState() {
        val proj = canvasView.project

        // Update Play/Pause button
        playPauseBtn.text = if (canvasView.isPlaying) "⏸ Pause" else "▶ Play"
        styleButton(playPauseBtn, isHighlighted = canvasView.isPlaying, accentColor = Color.rgb(0, 200, 130))

        // Update Active Layer button label
        val activeLayer = proj.activeLayer
        val refBadge = if (activeLayer.isReferenceLayer) " [Ref]" else ""
        layersBtn.text = "🗂 ${activeLayer.name}$refBadge"
        styleButton(layersBtn, isHighlighted = true, accentColor = Color.rgb(95, 80, 210))

        // Update Onion Skin button
        val onionOn = proj.isOnionSkinEnabled
        onionBtn.text = if (onionOn) "Onion: ON" else "Onion: OFF"
        styleButton(onionBtn, isHighlighted = onionOn, accentColor = Color.rgb(255, 75, 110))

        // Update Symmetry button
        styleButton(symBtn, isHighlighted = canvasView.isVerticalSymmetryEnabled, accentColor = Color.rgb(0, 195, 255))

        // Update Mirror Flip button
        styleButton(mirrorBtn, isHighlighted = canvasView.viewport.isMirroredHorizontally, accentColor = Color.rgb(0, 180, 255))

        // Update FPS & Exposure Hold labels
        fpsBtn.text = "${proj.fps} FPS"
        val hold = proj.currentFrame.exposureHold
        holdBtn.text = "⏱ ${hold}s"
        styleButton(holdBtn, isHighlighted = (hold > 1), accentColor = Color.rgb(245, 160, 35))

        // Update Active Tool highlight
        toolButtons.forEach { (tool, btn) ->
            styleButton(btn, isHighlighted = (canvasView.activeTool == tool), accentColor = Color.rgb(75, 123, 236))
        }

        val currentSize = if (canvasView.activeTool == StudioToolType.ERASER) {
            canvasView.eraserSizePx
        } else {
            canvasView.brushSizePx
        }
        sizeLabel.text = "${currentSize.toInt()}px"
        activeColorDot.background = createColorOval(canvasView.brushColor, strokeWidthDp = 3f, strokeColor = Color.WHITE)

        // Rebuild timeline frame pills
        rebuildTimelinePills()
    }

    private fun rebuildTimelinePills() {
        frameStripLayout.removeAllViews()
        val frames = canvasView.project.frames
        val activeIdx = canvasView.project.currentFrameIndex

        for (i in 0 until frames.size) {
            val frame = frames[i]
            val isCurrent = (i == activeIdx)
            val hasInk = frame.hasAnyContent()
            val holdSuffix = if (frame.exposureHold > 1) " (x${frame.exposureHold})" else ""
            val dotSuffix = if (hasInk) "•" else ""
            val pillLabel = "${i + 1}$dotSuffix$holdSuffix"

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

    private fun showLayerManagerDialog() {
        val proj = canvasView.project
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        }

        // Top row: + Add Layer & Delete Active Layer
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

        // List layers from Top (lastIndex) to Bottom (0) like Adobe/ToonBoom/Procreate
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

            // Visibility Eye Button
            val btnVis = createStudioButton(if (layer.isVisible) "👁" else "🚫") {
                layer.isVisible = !layer.isVisible
                proj.currentFrame.isCacheDirty = true
                canvasView.triggerAutosave()
                refreshUiState()
                canvasView.invalidate()
            }

            // Set as Reference Layer Button (for Smart Fill Bucket)
            val btnRef = createStudioButton(if (layer.isReferenceLayer) "⭐Ref" else "Set Ref") {
                proj.setReferenceLayer(lIdx)
                canvasView.triggerAutosave()
                refreshUiState()
            }

            // Alpha Lock Toggle
            val btnAlpha = createStudioButton(if (layer.isAlphaLocked) "🔒α" else "🔓α") {
                layer.isAlphaLocked = !layer.isAlphaLocked
                proj.currentFrame.isCacheDirty = true
                canvasView.triggerAutosave()
                canvasView.invalidate()
            }

            // Blend Mode Cycle
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

            // Opacity Slider for this layer
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
            setPadding(dp(20f), dp(16f), dp(20f), dp(8f))
        }

        val brushTitle = TextView(context).apply {
            text = "Brush Size: ${canvasView.brushSizePx.toInt()} px"
            setTextColor(Color.WHITE)
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
            .setTitle("Brush & Stabilizer Studio")
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
            appendLine("Active Cel Strokes: ${proj.activeCel.strokes.size}")
            appendLine("Viewport Zoom: ${(canvasView.viewport.zoomScale * 100).toInt()}%")
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
