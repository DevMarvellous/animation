package com.animationapp.engine.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF

/**
 * 2D Animation Camera Keyframe & Cubic Smoothstep Tweening Engine.
 *
 * Just like Toon Boom Harmony and Adobe Animate, the 2D Camera lets animators create
 * camera pans across wide 2D backgrounds, dramatic anime zoom-ins (truck-in), and 2D camera tilts
 * across frames WITHOUT modifying or moving the actual 2D character/background drawings!
 */
class CameraKeyframe(
    val frameIndex: Int,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var zoom: Float = 1.0f,
    var rotationDeg: Float = 0f
)

class Camera2DController {

    val keyframes = ArrayList<CameraKeyframe>(8)
    var isCameraViewActive: Boolean = false // When true, playback/export renders through the 2D Camera lens!

    private val cameraMatrix = Matrix()
    private val frameRect = RectF()

    private val cameraBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 40) // Cinema Gold 2D camera viewfinder border
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 40)
        textSize = 26f
        isFakeBoldText = true
    }

    fun setOrUpdateKeyframe(
        frameIndex: Int,
        offsetX: Float,
        offsetY: Float,
        zoom: Float,
        rotationDeg: Float = 0f
    ) {
        val existing = keyframes.find { it.frameIndex == frameIndex }
        if (existing != null) {
            existing.offsetX = offsetX
            existing.offsetY = offsetY
            existing.zoom = zoom.coerceIn(0.25f, 6.0f)
            existing.rotationDeg = rotationDeg
        } else {
            keyframes.add(
                CameraKeyframe(
                    frameIndex = frameIndex,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    zoom = zoom.coerceIn(0.25f, 6.0f),
                    rotationDeg = rotationDeg
                )
            )
            keyframes.sortBy { it.frameIndex }
        }
    }

    fun removeKeyframeAt(frameIndex: Int): Boolean {
        return keyframes.removeAll { it.frameIndex == frameIndex }
    }

    fun hasKeyframeAt(frameIndex: Int): Boolean {
        for (i in 0 until keyframes.size) {
            if (keyframes[i].frameIndex == frameIndex) return true
        }
        return false
    }

    /**
     * Evaluates interpolated 2D camera state at any frame index using Smoothstep Ease-In-Out.
     */
    fun evaluateAtFrame(
        frameIndex: Int,
        outValues: FloatArray // [offsetX, offsetY, zoom, rotationDeg]
    ) {
        if (keyframes.isEmpty()) {
            outValues[0] = 0f
            outValues[1] = 0f
            outValues[2] = 1f
            outValues[3] = 0f
            return
        }

        if (frameIndex <= keyframes.first().frameIndex) {
            val k = keyframes.first()
            outValues[0] = k.offsetX
            outValues[1] = k.offsetY
            outValues[2] = k.zoom
            outValues[3] = k.rotationDeg
            return
        }

        if (frameIndex >= keyframes.last().frameIndex) {
            val k = keyframes.last()
            outValues[0] = k.offsetX
            outValues[1] = k.offsetY
            outValues[2] = k.zoom
            outValues[3] = k.rotationDeg
            return
        }

        // Find surrounding keyframes [prevK, nextK]
        var prevK = keyframes.first()
        var nextK = keyframes.last()
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (frameIndex in a.frameIndex..b.frameIndex) {
                prevK = a
                nextK = b
                break
            }
        }

        val span = (nextK.frameIndex - prevK.frameIndex).coerceAtLeast(1).toFloat()
        val rawT = ((frameIndex - prevK.frameIndex).toFloat() / span).coerceIn(0f, 1f)
        // Smoothstep Cubic Ease-In-Out: 3t^2 - 2t^3
        val smoothT = rawT * rawT * (3f - 2f * rawT)

        outValues[0] = prevK.offsetX + (nextK.offsetX - prevK.offsetX) * smoothT
        outValues[1] = prevK.offsetY + (nextK.offsetY - prevK.offsetY) * smoothT
        outValues[2] = prevK.zoom + (nextK.zoom - prevK.zoom) * smoothT
        outValues[3] = prevK.rotationDeg + (nextK.rotationDeg - prevK.rotationDeg) * smoothT
    }

    /**
     * Applies the 2D Camera lens transform to the stage canvas during Playback or MP4 Video Export.
     */
    fun buildCameraLensMatrix(
        frameIndex: Int,
        stageWidth: Int,
        stageHeight: Int,
        evalScratch: FloatArray
    ): Matrix {
        evaluateAtFrame(frameIndex, evalScratch)
        val camX = evalScratch[0]
        val camY = evalScratch[1]
        val camZoom = evalScratch[2].coerceAtLeast(0.1f)
        val camRot = evalScratch[3]

        val cx = stageWidth * 0.5f
        val cy = stageHeight * 0.5f

        cameraMatrix.reset()
        cameraMatrix.postTranslate(-camX, -camY)
        cameraMatrix.postScale(camZoom, camZoom, cx, cy)
        cameraMatrix.postRotate(-camRot, cx, cy)
        return cameraMatrix
    }

    /**
     * Draws the gold 2D Camera viewfinder frame on the stage when editing keyframes.
     */
    fun drawCameraViewfinder(
        canvas: Canvas,
        frameIndex: Int,
        stageWidth: Int,
        stageHeight: Int,
        evalScratch: FloatArray
    ) {
        if (keyframes.isEmpty()) return

        evaluateAtFrame(frameIndex, evalScratch)
        val camX = evalScratch[0]
        val camY = evalScratch[1]
        val camZoom = evalScratch[2].coerceAtLeast(0.1f)
        val camRot = evalScratch[3]

        val halfW = (stageWidth * 0.5f) / camZoom
        val halfH = (stageHeight * 0.5f) / camZoom
        val cx = (stageWidth * 0.5f) + camX
        val cy = (stageHeight * 0.5f) + camY

        frameRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        canvas.save()
        canvas.rotate(camRot, cx, cy)
        canvas.drawRect(frameRect, cameraBorderPaint)
        val keyBadge = if (hasKeyframeAt(frameIndex)) "🎥 2D CAM [KEY]" else "🎥 2D CAM (Tween)"
        canvas.drawText(keyBadge, frameRect.left + 14f, frameRect.top - 10f, labelPaint)
        canvas.restore()
    }
}
