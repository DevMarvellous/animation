package com.animationapp.engine.tween

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 5 Studio Easing Velocity Curves for Phase 19 Per-Layer Keyframe Tweening.
 */
enum class TweenEasingType(val label: String) {
    LINEAR("Linear (Constant)"),
    EASE_IN_OUT_SMOOTH("Smooth Ease In/Out"),
    EASE_OUT_SNAP("Snappy Ease-Out (Anime)"),
    EASE_IN_HEAVY("Heavy Ease-In"),
    BOUNCE_ELASTIC("Cartoon Bounce Out");

    fun evaluate(rawT: Float): Float {
        val t = rawT.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            EASE_IN_OUT_SMOOTH -> t * t * (3f - 2f * t)
            EASE_OUT_SNAP -> 1f - (1f - t).pow(3)
            EASE_IN_HEAVY -> t * t * t
            BOUNCE_ELASTIC -> {
                if (t == 0f || t == 1f) t
                else (2.0.pow(-8.0 * t) * sin((t * 8.0 - 0.75) * (2.0 * PI / 3.0)) + 1.0).toFloat()
                    .coerceIn(0f, 1.15f)
            }
        }
    }
}

/**
 * Represents a single 2D Transform Keyframe on an [com.animationapp.model.AnimationLayer].
 */
data class LayerKeyframe(
    val frameIndex: Int,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var rotationDeg: Float = 0f,
    var easing: TweenEasingType = TweenEasingType.EASE_IN_OUT_SMOOTH
)

/**
 * Per-Layer 2D Keyframe Tweening, Velocity Curve Evaluator & Motion Path Renderer (Phase 19).
 *
 * Pre-allocates its [Matrix] and [Path] objects so evaluating layer tweens during 120 FPS playback
 * or MP4/SpriteSheet export causes zero Garbage Collection allocations.
 */
class LayerTweenTrack {

    val keyframes = ArrayList<LayerKeyframe>(8)
    private val evalMatrix = Matrix()
    private val motionArcPath = Path()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 230, 180)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val keyDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 200, 0)
        style = Paint.Style.FILL
    }

    private val tickDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 180)
        style = Paint.Style.FILL
    }

    fun hasKeyframes(): Boolean = keyframes.isNotEmpty()

    fun hasKeyframeAt(frameIndex: Int): Boolean {
        for (i in 0 until keyframes.size) {
            if (keyframes[i].frameIndex == frameIndex) return true
        }
        return false
    }

    fun getOrCreateKeyframeAt(frameIndex: Int, evalScratch: FloatArray): LayerKeyframe {
        for (i in 0 until keyframes.size) {
            if (keyframes[i].frameIndex == frameIndex) return keyframes[i]
        }
        evaluateAtFrame(frameIndex, evalScratch)
        val created = LayerKeyframe(
            frameIndex = frameIndex,
            offsetX = evalScratch[0],
            offsetY = evalScratch[1],
            scaleX = evalScratch[2],
            scaleY = evalScratch[3],
            rotationDeg = evalScratch[4]
        )
        keyframes.add(created)
        keyframes.sortBy { it.frameIndex }
        return created
    }

    fun removeKeyframeAt(frameIndex: Int) {
        keyframes.removeAll { it.frameIndex == frameIndex }
    }

    fun clearAllKeyframes() {
        keyframes.clear()
    }

    /**
     * Evaluates `[offsetX, offsetY, scaleX, scaleY, rotationDeg]` at [frameIndex] into [out5].
     */
    fun evaluateAtFrame(frameIndex: Int, out5: FloatArray) {
        if (keyframes.isEmpty()) {
            out5[0] = 0f
            out5[1] = 0f
            out5[2] = 1f
            out5[3] = 1f
            out5[4] = 0f
            return
        }

        if (frameIndex <= keyframes.first().frameIndex) {
            val k = keyframes.first()
            out5[0] = k.offsetX
            out5[1] = k.offsetY
            out5[2] = k.scaleX
            out5[3] = k.scaleY
            out5[4] = k.rotationDeg
            return
        }

        if (frameIndex >= keyframes.last().frameIndex) {
            val k = keyframes.last()
            out5[0] = k.offsetX
            out5[1] = k.offsetY
            out5[2] = k.scaleX
            out5[3] = k.scaleY
            out5[4] = k.rotationDeg
            return
        }

        var prev = keyframes.first()
        var next = keyframes.last()
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (frameIndex in a.frameIndex..b.frameIndex) {
                prev = a
                next = b
                break
            }
        }

        val span = (next.frameIndex - prev.frameIndex).coerceAtLeast(1).toFloat()
        val rawT = ((frameIndex - prev.frameIndex) / span).coerceIn(0f, 1f)
        val easedT = prev.easing.evaluate(rawT)

        out5[0] = prev.offsetX + (next.offsetX - prev.offsetX) * easedT
        out5[1] = prev.offsetY + (next.offsetY - prev.offsetY) * easedT
        out5[2] = prev.scaleX + (next.scaleX - prev.scaleX) * easedT
        out5[3] = prev.scaleY + (next.scaleY - prev.scaleY) * easedT
        out5[4] = prev.rotationDeg + (next.rotationDeg - prev.rotationDeg) * easedT
    }

    /**
     * Builds the 2D transformation [Matrix] for this layer at [frameIndex] pivoted around stage center.
     */
    fun buildLayerMatrix(
        frameIndex: Int,
        stageWidth: Int,
        stageHeight: Int,
        evalScratch5: FloatArray
    ): Matrix {
        evalMatrix.reset()
        if (keyframes.isEmpty()) return evalMatrix

        evaluateAtFrame(frameIndex, evalScratch5)
        val ox = evalScratch5[0]
        val oy = evalScratch5[1]
        val sx = evalScratch5[2]
        val sy = evalScratch5[3]
        val rot = evalScratch5[4]

        val cx = stageWidth * 0.5f
        val cy = stageHeight * 0.5f

        evalMatrix.postScale(sx, sy, cx, cy)
        evalMatrix.postRotate(rot, cx, cy)
        evalMatrix.postTranslate(ox, oy)
        return evalMatrix
    }

    /**
     * Renders the 2D Motion Path Trajectory Arc on the stage so the animator can see spacing & easing ticks.
     */
    fun drawMotionPathOverlay(
        canvas: Canvas,
        stageWidth: Int,
        stageHeight: Int,
        evalScratch5: FloatArray
    ) {
        if (keyframes.size < 2) return
        val cx = stageWidth * 0.5f
        val cy = stageHeight * 0.5f

        val firstFrame = keyframes.first().frameIndex
        val lastFrame = keyframes.last().frameIndex
        motionArcPath.reset()

        for (f in firstFrame..lastFrame) {
            evaluateAtFrame(f, evalScratch5)
            val px = cx + evalScratch5[0]
            val py = cy + evalScratch5[1]
            if (f == firstFrame) {
                motionArcPath.moveTo(px, py)
            } else {
                motionArcPath.lineTo(px, py)
            }
            canvas.drawCircle(px, py, 4.5f, tickDotPaint)
        }
        canvas.drawPath(motionArcPath, arcPaint)

        for (i in 0 until keyframes.size) {
            val k = keyframes[i]
            canvas.drawCircle(cx + k.offsetX, cy + k.offsetY, 9f, keyDotPaint)
        }
    }
}

