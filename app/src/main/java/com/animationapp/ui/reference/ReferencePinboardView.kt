package com.animationapp.ui.reference

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Floating Draggable Reference Pinboard Window (PureRef-style for 2D Animators — Phase 10).
 *
 * Displays a draggable floating reference card on the studio workspace featuring:
 * 1. Built-in Character Head Proportions & Preston Blair Lip-Sync Mouth Chart Reference!
 * 2. Model Color Palette Swatches that the animator can tap to instantly pick colors!
 * 3. Drag the header bar to position it anywhere on the screen.
 */
class ReferencePinboardView(
    context: Context,
    private val onColorPicked: (Int) -> Unit
) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 28, 31, 42)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(95, 110, 145)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 200, 220)
        textSize = 18f
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 215, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }

    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cardRect = RectF()
    private val facePath = Path()

    // Character Model Sheet Swatches (Warm Skin, Shadow Skin, Anime Hair, Eye Highlight, Line-Art Brown, Sky Accent)
    private val modelSwatches = intArrayOf(
        Color.rgb(255, 221, 196),
        Color.rgb(224, 168, 138),
        Color.rgb(46, 52, 78),
        Color.rgb(255, 94, 126),
        Color.rgb(74, 44, 42),
        Color.rgb(84, 214, 255)
    )

    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var startTranslationX = 0f
    private var startTranslationY = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        cardRect.set(2f, 2f, w - 2f, h - 2f)

        canvas.drawRoundRect(cardRect, 18f, 18f, bgPaint)
        canvas.drawRoundRect(cardRect, 18f, 18f, borderPaint)

        // Header bar
        canvas.drawText("📌 2D Ref Sheet (Drag Me)", 18f, 30f, titlePaint)

        // Draw Loomis/Anime 2D Head Proportion Guide on left half
        val headCx = w * 0.28f
        val headCy = h * 0.46f
        val headR = minOf(w, h) * 0.20f

        canvas.drawCircle(headCx, headCy, headR, guidePaint)
        // Eye line & center line
        canvas.drawLine(headCx - headR * 1.1f, headCy + headR * 0.15f, headCx + headR * 1.1f, headCy + headR * 0.15f, guidePaint)
        canvas.drawLine(headCx, headCy - headR * 1.1f, headCx, headCy + headR * 1.45f, guidePaint)
        // Jawline
        facePath.reset()
        facePath.moveTo(headCx - headR * 0.88f, headCy + headR * 0.2f)
        facePath.lineTo(headCx - headR * 0.55f, headCy + headR * 1.15f)
        facePath.lineTo(headCx, headCy + headR * 1.42f)
        facePath.lineTo(headCx + headR * 0.55f, headCy + headR * 1.15f)
        facePath.lineTo(headCx + headR * 0.88f, headCy + headR * 0.2f)
        canvas.drawPath(facePath, guidePaint)

        // Draw Lip-Sync Phoneme Quick Guide on right half
        val rightX = w * 0.54f
        canvas.drawText("Mouth Shapes:", rightX, 62f, subTextPaint)
        canvas.drawText("• A/I: Wide Open", rightX, 86f, subTextPaint)
        canvas.drawText("• E: Wide Teeth", rightX, 108f, subTextPaint)
        canvas.drawText("• O/U: Round Circle", rightX, 130f, subTextPaint)
        canvas.drawText("• M/B/P: Closed Line", rightX, 152f, subTextPaint)

        // Draw clickable Character Sheet Color Swatches at bottom
        val swatchY = h - 26f
        val swatchRadius = 15f
        val startX = 28f
        val spacing = (w - 56f) / (modelSwatches.size - 1).coerceAtLeast(1)

        for (i in modelSwatches.indices) {
            val sx = startX + i * spacing
            swatchPaint.color = modelSwatches[i]
            canvas.drawCircle(sx, swatchY, swatchRadius, swatchPaint)
            canvas.drawCircle(sx, swatchY, swatchRadius, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check if user tapped one of the bottom character color swatches
                val h = height.toFloat()
                if (event.y >= h - 50f) {
                    val w = width.toFloat()
                    val startX = 28f
                    val spacing = (w - 56f) / (modelSwatches.size - 1).coerceAtLeast(1)
                    val index = ((event.x - startX + spacing * 0.5f) / spacing).toInt()
                    if (index in modelSwatches.indices) {
                        onColorPicked(modelSwatches[index])
                        return true
                    }
                }
                dragDownRawX = event.rawX
                dragDownRawY = event.rawY
                startTranslationX = translationX
                startTranslationY = translationY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                translationX = startTranslationX + (event.rawX - dragDownRawX)
                translationY = startTranslationY + (event.rawY - dragDownRawY)
                return true
            }
        }
        return true
    }
}
