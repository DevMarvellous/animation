package com.animationapp.engine.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.animationapp.core.storage.DiskSwapManager
import com.animationapp.model.AnimationProject
import kotlin.math.hypot

enum class GradientType(val label: String, val icon: String) {
    LINEAR("Linear Angle", "📏"),
    RADIAL("Radial Circle", "◯")
}

/**
 * Hardware-Accelerated 2D Linear & Radial Gradient Fill Tool (Phase 26).
 *
 * Supports:
 * - Direct GPU shader rendering via [LinearGradient] and [RadialGradient].
 * - Interactive on-stage drag handle preview with start/end color endpoints.
 * - Freehand Lasso Polygon Masking: if Lasso selection exists, gradient is strictly masked inside.
 * - Alpha-Lock Cel Shading: if layer is alpha-locked, only shades existing pixel art/ink without bleeding.
 * - Seamless integration with [DiskSwapManager] and all 4 video/GIF/ZIP/sheet exporters via LayerCel.fillBitmap.
 */
class GradientToolController {

    var gradientType: GradientType = GradientType.LINEAR
    var startColor: Int = Color.BLACK
    var endColor: Int = Color.TRANSPARENT

    var isDragging: Boolean = false
        private set

    var startX: Float = 0f
        private set
    var startY: Float = 0f
        private set
    var currentX: Float = 0f
        private set
    var currentY: Float = 0f
        private set

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.rgb(0, 210, 255) // Cyan studio accent
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    private val circlePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.rgb(255, 200, 0)
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }

    private val alphaLockXfer = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

    fun onTouchDown(stageX: Float, stageY: Float, primaryColor: Int) {
        isDragging = true
        startX = stageX
        startY = stageY
        currentX = stageX
        currentY = stageY
        startColor = primaryColor
        if (endColor == Color.TRANSPARENT || endColor == primaryColor) {
            // Default end color: 0% alpha transparent version of startColor for soft shading
            endColor = Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        }
    }

    fun onTouchMove(stageX: Float, stageY: Float) {
        if (!isDragging) return
        currentX = stageX
        currentY = stageY
    }

    fun onTouchUp(
        stageX: Float,
        stageY: Float,
        project: AnimationProject,
        lassoController: LassoTransformController? = null,
        onBaked: () -> Unit
    ) {
        if (!isDragging) return
        currentX = stageX
        currentY = stageY
        isDragging = false

        bakeGradientIntoCel(project, lassoController)
        onBaked()
    }

    fun cancelDrag() {
        isDragging = false
    }

    /**
     * Renders the interactive handle line and color dots on the stage viewport.
     */
    fun drawInteractiveOverlay(canvas: Canvas) {
        if (!isDragging) return

        val dx = currentX - startX
        val dy = currentY - startY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (gradientType == GradientType.LINEAR) {
            canvas.drawLine(startX, startY, currentX, currentY, linePaint)
        } else {
            // Radial mode: draw connecting line and circle bounds
            canvas.drawLine(startX, startY, currentX, currentY, linePaint)
            if (dist > 4f) {
                canvas.drawCircle(startX, startY, dist, circlePreviewPaint)
            }
        }

        // Draw Start Handle dot
        handleFillPaint.color = startColor
        canvas.drawCircle(startX, startY, 14f, handleFillPaint)
        canvas.drawCircle(startX, startY, 14f, handleStrokePaint)

        // Draw End Handle dot
        handleFillPaint.color = if (Color.alpha(endColor) == 0) Color.WHITE else endColor
        canvas.drawCircle(currentX, currentY, 14f, handleFillPaint)
        canvas.drawCircle(currentX, currentY, 14f, handleStrokePaint)
    }

    private fun bakeGradientIntoCel(
        project: AnimationProject,
        lassoController: LassoTransformController?
    ) {
        val frame = project.currentFrame
        val cel = frame.getCel(project.activeLayerIndex)
        val activeLayer = project.activeLayer

        // Ensure cel's fillBitmap is available (restore from disk swap if cold)
        var bmp = DiskSwapManager.restoreCelFromDiskIfNeeded(cel)
        if (bmp == null || bmp.isRecycled) {
            bmp = Bitmap.createBitmap(project.stageWidth, project.stageHeight, Bitmap.Config.ARGB_8888)
            cel.fillBitmap = bmp
        } else if (!bmp.isMutable) {
            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
            cel.fillBitmap = bmp
        }

        val bmpCanvas = Canvas(bmp)
        val dx = currentX - startX
        val dy = currentY - startY
        val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(10f)

        val shader: Shader = if (gradientType == GradientType.LINEAR) {
            val ex = if (hypot(dx.toDouble(), dy.toDouble()) < 1.0) startX + 10f else currentX
            val ey = if (hypot(dx.toDouble(), dy.toDouble()) < 1.0) startY + 10f else currentY
            LinearGradient(startX, startY, ex, ey, startColor, endColor, Shader.TileMode.CLAMP)
        } else {
            RadialGradient(startX, startY, radius, startColor, endColor, Shader.TileMode.CLAMP)
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            this.shader = shader
            if (activeLayer.isAlphaLocked) {
                xfermode = alphaLockXfer
            }
        }

        bmpCanvas.save()
        // If Lasso polygon exists, clip gradient strictly inside lasso area
        if (lassoController != null && lassoController.hasSelection()) {
            bmpCanvas.clipPath(lassoController.lassoPath)
        }
        bmpCanvas.drawRect(0f, 0f, project.stageWidth.toFloat(), project.stageHeight.toFloat(), fillPaint)
        bmpCanvas.restore()

        cel.isCacheDirty = true
        frame.isCacheDirty = true
    }
}
