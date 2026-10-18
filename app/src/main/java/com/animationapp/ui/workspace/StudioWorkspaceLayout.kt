package com.animationapp.ui.workspace

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.engine.AnimationCanvasView
import com.animationapp.engine.tools.StudioToolType

/**
 * Modular, zero-bloat Native Pro Studio Workspace UI.
 *
 * Built programmatically using lightweight native Android Views (0 XML inflation overhead,
 * 0 Compose/AppCompat dependencies) for instant startup and minimal RAM footprint on budget phones.
 *
 * Includes:
 * - Top Action Bar: Undo, Redo, Onion Skin Toggle, Mirror Flip, Reset View, FPS Selector, Diagnostics, Left/Right Hand Flip.
 * - Dockable Tool & Color Bar: Pen, Eraser, Pan/Zoom, Quick Color Swatches, Size & Stabilizer Sliders.
 * - Bottom Timeline Scrubber: Play/Pause, Step Back/Forward, Frame Pills, +Add Frame, Duplicate, Delete.
 */
class StudioWorkspaceLayout(private val activity: Activity) : FrameLayout(activity) {

    val canvasView = AnimationCanvasView(activity)

    private var isLeftHandedMode = false
    private lateinit var toolDockContainer: LinearLayout
    private lateinit var frameStripLayout: LinearLayout
    private lateinit var playPauseBtn: TextView
    private lateinit var onionBtn: TextView
    private lateinit var mirrorBtn: TextView
    private lateinit var fpsBtn: TextView
    private lateinit var sizeLabel: TextView
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

        // 1. Add Hardware Animation Canvas (Full Screen Background Layer)
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
            background = createRoundedBg(Color.argb(225, 26, 28, 36), dp(12f))
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        }

        val btnUndo = createStudioButton("↶ Undo") { canvasView.performUndo() }
        val btnRedo = createStudioButton("↷ Redo") { canvasView.performRedo() }

        onionBtn = createStudioButton("Onion: ON") {
            canvasView.project.isOnionSkinEnabled = !canvasView.project.isOnionSkinEnabled
            refreshUiState()
            canvasView.invalidate()
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
            btnUndo, btnRedo, onionBtn, mirrorBtn, btnFit,
            fpsBtn, btnSliders, btnHandMode, btnDiag
        ).forEach { btn ->
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36f)
            ).apply { marginEnd = dp(6f) }
            topBar.addView(btn, lp)
        }

        val containerLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(10f) }

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
            background = createRoundedBg(Color.argb(225, 26, 28, 36), dp(14f))
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
        }

        val btnPen = createStudioButton("✏ Pen") { selectTool(StudioToolType.BRUSH) }
        val btnEraser = createStudioButton("⌫ Erase") { selectTool(StudioToolType.ERASER) }
        val btnPan = createStudioButton("✥ Pan") { selectTool(StudioToolType.HAND_PAN) }

        toolButtons = mapOf(
            StudioToolType.BRUSH to btnPen,
            StudioToolType.ERASER to btnEraser,
            StudioToolType.HAND_PAN to btnPan
        )

        listOf(btnPen, btnEraser, btnPan).forEach { btn ->
            val lp = LinearLayout.LayoutParams(dp(74f), dp(36f)).apply {
                bottomMargin = dp(6f)
            }
            dock.addView(btn, lp)
        }

        sizeLabel = TextView(context).apply {
            text = "${canvasView.brushSizePx.toInt()}px"
            setTextColor(Color.rgb(180, 190, 210))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, dp(4f))
        }
        dock.addView(sizeLabel)

        // Swatch Strip inside a compact vertical scroll
        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        for (c in studioColors) {
            val swatch = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(2f), Color.rgb(90, 95, 110))
                }
                setOnClickListener {
                    canvasView.brushColor = c
                    selectTool(StudioToolType.BRUSH)
                }
            }
            val lp = LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
                bottomMargin = dp(5f)
            }
            colorRow.addView(swatch, lp)
        }
        dock.addView(colorRow)

        dock.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END
        ).apply {
            marginEnd = dp(12f)
            marginStart = dp(12f)
        }
        return dock
    }

    private fun buildBottomTimelineBar(): View {
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg(Color.argb(235, 22, 24, 32), dp(14f))
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
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

        val controlMargin = dp(6f)
        listOf(btnPrev, playPauseBtn, btnNext).forEach { btn ->
            bottomContainer.addView(
                btn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38f)).apply {
                    marginEnd = controlMargin
                }
            )
        }

        bottomContainer.addView(
            frameScroll,
            LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
                marginEnd = controlMargin
            }
        )

        listOf(btnAddFrame, btnDupFrame, btnDelFrame).forEach { btn ->
            bottomContainer.addView(
                btn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38f)).apply {
                    marginStart = controlMargin
                }
            )
        }

        bottomContainer.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            leftMargin = dp(12f)
            rightMargin = dp(12f)
            bottomMargin = dp(10f)
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
        // Update Play/Pause button
        playPauseBtn.text = if (canvasView.isPlaying) "⏸ Pause" else "▶ Play"
        styleButton(playPauseBtn, isHighlighted = canvasView.isPlaying, accentColor = Color.rgb(0, 200, 130))

        // Update Onion Skin button
        val onionOn = canvasView.project.isOnionSkinEnabled
        onionBtn.text = if (onionOn) "Onion: ON" else "Onion: OFF"
        styleButton(onionBtn, isHighlighted = onionOn, accentColor = Color.rgb(255, 75, 110))

        // Update Mirror Flip button
        val mirrored = canvasView.viewport.isMirroredHorizontally
        styleButton(mirrorBtn, isHighlighted = mirrored, accentColor = Color.rgb(0, 180, 255))

        // Update FPS label
        fpsBtn.text = "${canvasView.project.fps} FPS"

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

        // Rebuild timeline frame pills
        rebuildTimelinePills()
    }

    private fun rebuildTimelinePills() {
        frameStripLayout.removeAllViews()
        val frames = canvasView.project.frames
        val activeIdx = canvasView.project.currentFrameIndex

        for (i in 0 until frames.size) {
            val isCurrent = (i == activeIdx)
            val hasInk = frames[i].strokes.isNotEmpty()
            val pillLabel = if (hasInk) "${i + 1} •" else "${i + 1}"

            val pill = TextView(context).apply {
                text = pillLabel
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
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
                dp(34f)
            ).apply { marginEnd = dp(5f) }
            frameStripLayout.addView(pill, lp)
        }
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
        val options = arrayOf("8 FPS (Anime on 3s)", "12 FPS (Standard 2D)", "24 FPS (Cinema)", "30 FPS (Smooth)", "60 FPS (Ultra)")
        val values = intArrayOf(8, 12, 24, 30, 60)

        AlertDialog.Builder(activity)
            .setTitle("Select Playback Frame Rate")
            .setItems(options) { _, which ->
                canvasView.project.fps = values[which]
                refreshUiState()
            }
            .show()
    }

    private fun openDiagnosticsDialog() {
        val hud = canvasView.performanceHud
        val stats = buildString {
            appendLine("Live FPS: ${hud.currentFps}")
            appendLine("Heap Memory: ${hud.usedRamMb} MB / ${hud.maxRamMb} MB")
            appendLine("Total Frames: ${canvasView.project.frames.size}")
            appendLine("Active Frame Strokes: ${canvasView.project.currentFrame.strokes.size}")
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
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
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

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }
}
