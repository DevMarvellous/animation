package com.animationapp.engine.tools

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.model.AnimationProject
import com.animationapp.model.Stroke

enum class ComicTextStyle(val label: String) {
    COMIC_OUTLINE("💬 Comic Outline / Sticker"),
    SOLID_INK("✒ Solid Inked"),
    ACTION_SHADOW("💥 Action Pop Shadow")
}

/**
 * 2D Cartoon Text & Onomatopoeia Vector Tool (Phase 26).
 *
 * Converts text strings (e.g., "POW!", "BOOM!", dialogue titles, sound effects) into
 * first-class vector [Stroke]s using hardware [Paint.getTextPath] and [PathMeasure].
 *
 * Resulting text strokes:
 * - Can be selected with the Lasso tool
 * - Can be scaled, translated, and rotated
 * - Can be deformed into 2D perspective with 4-Point Corner-Pin and 3x3 Mesh Cage
 * - Automatically render across all 4 exporters (MP4, GIF, ZIP, Sprite Sheet)
 */
class TextToolController {

    private var lastEnteredText: String = "POW!"
    private var lastSelectedStyle: ComicTextStyle = ComicTextStyle.COMIC_OUTLINE
    private var lastFontSizeSp: Int = 48
    private var lastTypefaceIndex: Int = 0

    private val typefaces = arrayOf(
        "Comic Black (Sans-Serif)" to Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
        "Dramatic Serif" to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        "Cyberpunk Monospace" to Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        "Standard Sans" to Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    )

    fun showTextDialog(
        activity: Activity,
        stageX: Float,
        stageY: Float,
        project: AnimationProject,
        primaryColor: Int,
        onComplete: () -> Unit
    ) {
        val context = activity
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val textInputLabel = TextView(context).apply {
            text = "Enter Animation Text / Sound Effect:"
            setTextColor(Color.rgb(180, 200, 230))
            textSize = 14f
        }
        val textInput = EditText(context).apply {
            setText(lastEnteredText)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "e.g. POW!, BOOM!, SWOOSH!, Ep. 1"
            selectAll()
        }
        layout.addView(textInputLabel)
        layout.addView(textInput)

        // Font Style Selector
        val fontLabel = TextView(context).apply {
            text = "Font Family: ${typefaces[lastTypefaceIndex].first}"
            setTextColor(Color.rgb(180, 200, 230))
            textSize = 13f
            setPadding(0, 16, 0, 6)
        }
        layout.addView(fontLabel)

        val fontBtnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        typefaces.forEachIndexed { index, (name, _) ->
            val shortName = when (index) {
                0 -> "Comic"
                1 -> "Serif"
                2 -> "Mono"
                else -> "Sans"
            }
            val btn = TextView(context).apply {
                text = shortName
                setTextColor(if (index == lastTypefaceIndex) Color.rgb(0, 210, 255) else Color.WHITE)
                setBackgroundColor(if (index == lastTypefaceIndex) Color.rgb(35, 45, 65) else Color.rgb(25, 28, 38))
                setPadding(20, 14, 20, 14)
                textSize = 12f
                gravity = Gravity.CENTER
                setOnClickListener {
                    lastTypefaceIndex = index
                    fontLabel.text = "Font Family: ${typefaces[index].first}"
                }
            }
            fontBtnRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            })
        }
        layout.addView(fontBtnRow)

        // Font Size Slider
        val sizeLabel = TextView(context).apply {
            text = "Text Size: ${lastFontSizeSp}sp"
            setTextColor(Color.rgb(180, 200, 230))
            textSize = 13f
            setPadding(0, 16, 0, 4)
        }
        val sizeSlider = SeekBar(context).apply {
            max = 120
            progress = (lastFontSizeSp - 16).coerceAtLeast(0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    lastFontSizeSp = prog + 16
                    sizeLabel.text = "Text Size: ${lastFontSizeSp}sp"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(sizeLabel)
        layout.addView(sizeSlider)

        // Style Selector (Sticker Outline vs Solid vs Action Shadow)
        val styleLabel = TextView(context).apply {
            text = "Style: ${lastSelectedStyle.label}"
            setTextColor(Color.rgb(180, 200, 230))
            textSize = 13f
            setPadding(0, 16, 0, 6)
        }
        layout.addView(styleLabel)

        val styleBtnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ComicTextStyle.values().forEach { style ->
            val sBtn = TextView(context).apply {
                text = when (style) {
                    ComicTextStyle.COMIC_OUTLINE -> "Outline"
                    ComicTextStyle.SOLID_INK -> "Solid"
                    ComicTextStyle.ACTION_SHADOW -> "Shadow"
                }
                setTextColor(if (style == lastSelectedStyle) Color.rgb(0, 210, 255) else Color.WHITE)
                setBackgroundColor(if (style == lastSelectedStyle) Color.rgb(35, 45, 65) else Color.rgb(25, 28, 38))
                setPadding(16, 14, 16, 14)
                textSize = 12f
                gravity = Gravity.CENTER
                setOnClickListener {
                    lastSelectedStyle = style
                    styleLabel.text = "Style: ${style.label}"
                }
            }
            styleBtnRow.addView(sBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            })
        }
        layout.addView(styleBtnRow)

        AlertDialog.Builder(context)
            .setTitle("2D Cartoon Text & Sound Effect")
            .setView(layout)
            .setPositiveButton("Stamp Text") { _, _ ->
                val entered = textInput.text.toString().trim()
                if (entered.isNotEmpty()) {
                    lastEnteredText = entered
                    stampTextOntoLayer(
                        text = entered,
                        stageX = stageX,
                        stageY = stageY,
                        project = project,
                        primaryColor = primaryColor,
                        style = lastSelectedStyle,
                        fontSizePx = lastFontSizeSp * 1.5f,
                        tf = typefaces[lastTypefaceIndex].second
                    )
                    onComplete()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stampTextOntoLayer(
        text: String,
        stageX: Float,
        stageY: Float,
        project: AnimationProject,
        primaryColor: Int,
        style: ComicTextStyle,
        fontSizePx: Float,
        tf: Typeface
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = fontSizePx
        }

        val rawPath = Path()
        paint.getTextPath(text, 0, text.length, stageX, stageY, rawPath)

        val bounds = RectF()
        rawPath.computeBounds(bounds, true)
        if (bounds.isEmpty) return

        val cel = project.currentFrame.getCel(project.activeLayerIndex)

        // Sample vector contour points along the path for Lasso & Mesh Cage deform compatibility
        val (sampledPoints, count) = samplePathPoints(rawPath)

        when (style) {
            ComicTextStyle.ACTION_SHADOW -> {
                // 1. Create Drop Shadow Stroke (+4px, +4px offset in black / dark tone)
                val shadowPath = Path(rawPath)
                shadowPath.offset(6f, 6f)
                val shadowBounds = RectF(bounds).apply { offset(6f, 6f) }
                val (shadowPoints, shadowCount) = samplePathPoints(shadowPath)
                val shadowStroke = Stroke(Color.argb(190, 15, 18, 28), 6f, false, BrushPreset.G_PEN)
                shadowStroke.assignPathWithPoints(shadowPath, shadowPoints, shadowCount, shadowBounds)
                cel.addStroke(shadowStroke)

                // 2. Main Text Stroke
                val mainStroke = Stroke(primaryColor, 3f, false, BrushPreset.G_PEN)
                mainStroke.assignPathWithPoints(rawPath, sampledPoints, count, bounds)
                cel.addStroke(mainStroke)
            }
            ComicTextStyle.COMIC_OUTLINE -> {
                // 1. White / Light comic sticker outline underlay
                val outlineStroke = Stroke(Color.WHITE, 10f, false, BrushPreset.G_PEN)
                outlineStroke.assignPathWithPoints(rawPath, sampledPoints, count, bounds)
                cel.addStroke(outlineStroke)

                // 2. Main Inked Text fill
                val fillStroke = Stroke(primaryColor, 3f, false, BrushPreset.G_PEN)
                fillStroke.assignPathWithPoints(rawPath, sampledPoints, count, bounds)
                cel.addStroke(fillStroke)
            }
            ComicTextStyle.SOLID_INK -> {
                val stroke = Stroke(primaryColor, 4f, false, BrushPreset.G_PEN)
                stroke.assignPathWithPoints(rawPath, sampledPoints, count, bounds)
                cel.addStroke(stroke)
            }
        }

        cel.isCacheDirty = true
        project.currentFrame.isCacheDirty = true
    }

    private fun samplePathPoints(path: Path): Pair<FloatArray, Int> {
        val pm = PathMeasure(path, false)
        val points = ArrayList<Float>(128)
        val pos = FloatArray(2)
        val tan = FloatArray(2)

        do {
            val length = pm.length
            val step = 6f // Sample every 6 pixels along contour
            var dist = 0f
            while (dist <= length) {
                if (pm.getPosTan(dist, pos, tan)) {
                    points.add(pos[0])
                    points.add(pos[1])
                    points.add(1.0f) // Pressure 1.0
                }
                dist += step
            }
        } while (pm.nextContour())

        val count = points.size / 3
        val arr = FloatArray(points.size)
        for (i in 0 until points.size) {
            arr[i] = points[i]
        }
        return Pair(arr, count)
    }
}
