package com.animationapp.engine.rigging

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import com.animationapp.model.Stroke
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents a single 2D Bone in a Cut-Out / Puppet Character Rig (Phase 20).
 */
class Bone2D(
    val id: Int,
    var name: String,
    val parentId: Int = -1,
    var localAngleDeg: Float = 0f,
    var lengthPx: Float = 90f,
    var rootX: Float = 640f,
    var rootY: Float = 360f,
    var useTwoBoneIk: Boolean = false
) {
    // Computed world-space joint coordinates
    var worldStartX: Float = rootX
    var worldStartY: Float = rootY
    var worldEndX: Float = rootX
    var worldEndY: Float = rootY
    var worldAngleDeg: Float = localAngleDeg
}

/**
 * 2D Cut-Out Skeletal Bone Rigging Engine with Parent-Child Forward Kinematics (FK)
 * and 2-Bone Law-of-Cosines Inverse Kinematics (IK) (Phase 20).
 */
class Skeleton2DController {

    val bones = ArrayList<Bone2D>(16)
    var isSkeletonVisible: Boolean = false
    var useIkMode: Boolean = true
    var deformLayerStrokesWithRig: Boolean = true

    private var activeDragBoneIndex: Int = -1
    private var isDraggingRootJoint: Boolean = false
    private val boneDiamondPath = Path()
    private val strokeTransformMatrix = Matrix()
    private val strokeScratchPt = FloatArray(2)

    private val boneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 0, 225, 255)
        style = Paint.Style.FILL
    }

    private val ikBoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 255, 175, 30)
        style = Paint.Style.FILL
    }

    private val boneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 75, 120)
        style = Paint.Style.FILL
    }

    fun hasRig(): Boolean = bones.isNotEmpty()

    fun clearRig() {
        bones.clear()
        activeDragBoneIndex = -1
        isSkeletonVisible = false
    }

    /**
     * Generates a ready-to-animate 2D Humanoid Cut-Out Character Rig (10 bones with 2-Bone IK limbs).
     */
    fun createHumanoid2DRig(stageWidth: Int, stageHeight: Int) {
        bones.clear()
        val cx = stageWidth * 0.5f
        val cy = stageHeight * 0.52f

        // 0: Pelvis / Root
        bones.add(Bone2D(0, "Pelvis", parentId = -1, localAngleDeg = -90f, lengthPx = 105f, rootX = cx, rootY = cy))
        // 1: Head
        bones.add(Bone2D(1, "Head", parentId = 0, localAngleDeg = 0f, lengthPx = 75f))

        // Left Arm (2 -> 3 IK)
        bones.add(Bone2D(2, "UpperArm_L", parentId = 0, localAngleDeg = -135f, lengthPx = 85f))
        bones.add(Bone2D(3, "Forearm_L", parentId = 2, localAngleDeg = -25f, lengthPx = 80f, useTwoBoneIk = true))

        // Right Arm (4 -> 5 IK)
        bones.add(Bone2D(4, "UpperArm_R", parentId = 0, localAngleDeg = 135f, lengthPx = 85f))
        bones.add(Bone2D(5, "Forearm_R", parentId = 4, localAngleDeg = 25f, lengthPx = 80f, useTwoBoneIk = true))

        // Left Leg (6 -> 7 IK)
        bones.add(Bone2D(6, "Thigh_L", parentId = -1, localAngleDeg = 105f, lengthPx = 105f, rootX = cx - 26f, rootY = cy))
        bones.add(Bone2D(7, "Shin_L", parentId = 6, localAngleDeg = 10f, lengthPx = 100f, useTwoBoneIk = true))

        // Right Leg (8 -> 9 IK)
        bones.add(Bone2D(8, "Thigh_R", parentId = -1, localAngleDeg = 75f, lengthPx = 105f, rootX = cx + 26f, rootY = cy))
        bones.add(Bone2D(9, "Shin_R", parentId = 8, localAngleDeg = -10f, lengthPx = 100f, useTwoBoneIk = true))

        isSkeletonVisible = true
        updateForwardKinematics()
    }

    fun updateForwardKinematics() {
        for (i in 0 until bones.size) {
            val b = bones[i]
            if (b.parentId >= 0 && b.parentId < bones.size) {
                val p = bones[b.parentId]
                b.worldStartX = p.worldEndX
                b.worldStartY = p.worldEndY
                b.worldAngleDeg = p.worldAngleDeg + b.localAngleDeg
            } else {
                b.worldStartX = b.rootX
                b.worldStartY = b.rootY
                b.worldAngleDeg = b.localAngleDeg
            }
            val rad = Math.toRadians(b.worldAngleDeg.toDouble())
            b.worldEndX = b.worldStartX + (cos(rad) * b.lengthPx).toFloat()
            b.worldEndY = b.worldStartY + (sin(rad) * b.lengthPx).toFloat()
        }
    }

    fun onTouchDown(stageX: Float, stageY: Float): Boolean {
        if (bones.isEmpty()) {
            createHumanoid2DRig(1280, 720)
        }
        updateForwardKinematics()

        // 1. Check if user grabbed a bone tip effector (for FK rotation or 2-Bone IK target)
        val hitRadius = 34f
        for (i in bones.lastIndex downTo 0) {
            val b = bones[i]
            if (hypot(stageX - b.worldEndX, stageY - b.worldEndY) <= hitRadius) {
                activeDragBoneIndex = i
                isDraggingRootJoint = false
                return true
            }
        }

        // 2. Check if user grabbed a root joint (to translate the rig pelvis)
        for (i in 0 until bones.size) {
            val b = bones[i]
            if (b.parentId == -1 && hypot(stageX - b.worldStartX, stageY - b.worldStartY) <= hitRadius) {
                activeDragBoneIndex = i
                isDraggingRootJoint = true
                return true
            }
        }

        activeDragBoneIndex = -1
        return false
    }

    fun onTouchMove(stageX: Float, stageY: Float, activeLayerStrokes: List<Stroke>): Boolean {
        if (activeDragBoneIndex !in bones.indices) return false
        val bone = bones[activeDragBoneIndex]

        val prevTipX = bone.worldEndX
        val prevTipY = bone.worldEndY
        val prevPivotX = bone.worldStartX
        val prevPivotY = bone.worldStartY
        val prevAngle = bone.worldAngleDeg

        if (isDraggingRootJoint) {
            val dx = stageX - bone.rootX
            val dy = stageY - bone.rootY
            for (b in bones) {
                if (b.parentId == -1) {
                    b.rootX += dx
                    b.rootY += dy
                }
            }
            updateForwardKinematics()
            if (deformLayerStrokesWithRig && activeLayerStrokes.isNotEmpty()) {
                strokeTransformMatrix.reset()
                strokeTransformMatrix.setTranslate(dx, dy)
                for (i in 0 until activeLayerStrokes.size) {
                    activeLayerStrokes[i].applyTransform(strokeTransformMatrix, strokeScratchPt)
                }
            }
            return true
        }

        if (useIkMode && bone.useTwoBoneIk && bone.parentId in bones.indices) {
            solveTwoBoneIk(bones[bone.parentId], bone, stageX, stageY)
        } else {
            // Standard Forward Kinematics (FK) rotation around bone start joint
            val desiredWorldAngle = Math.toDegrees(
                atan2((stageY - bone.worldStartY).toDouble(), (stageX - bone.worldStartX).toDouble())
            ).toFloat()

            if (bone.parentId in bones.indices) {
                val parentWorldAngle = bones[bone.parentId].worldAngleDeg
                bone.localAngleDeg = desiredWorldAngle - parentWorldAngle
            } else {
                bone.localAngleDeg = desiredWorldAngle
            }
            updateForwardKinematics()
        }

        // Deform nearby strokes on the active layer that belong to this bone segment
        if (deformLayerStrokesWithRig && activeLayerStrokes.isNotEmpty()) {
            val deltaDeg = bone.worldAngleDeg - prevAngle
            val influenceRadius = bone.lengthPx * 1.35f
            val midX = (prevPivotX + prevTipX) * 0.5f
            val midY = (prevPivotY + prevTipY) * 0.5f

            strokeTransformMatrix.reset()
            strokeTransformMatrix.setRotate(deltaDeg, bone.worldStartX, bone.worldStartY)

            for (i in 0 until activeLayerStrokes.size) {
                val s = activeLayerStrokes[i]
                val scx = s.bounds.centerX()
                val scy = s.bounds.centerY()
                if (hypot(scx - midX, scy - midY) <= influenceRadius) {
                    s.applyTransform(strokeTransformMatrix, strokeScratchPt)
                }
            }
        }
        return true
    }

    fun onTouchUp() {
        activeDragBoneIndex = -1
        isDraggingRootJoint = false
    }

    /**
     * Analytic 2-Bone Law of Cosines Inverse Kinematics (IK) Solver.
     */
    private fun solveTwoBoneIk(upper: Bone2D, lower: Bone2D, targetX: Float, targetY: Float) {
        val rootX = upper.worldStartX
        val rootY = upper.worldStartY
        val l1 = upper.lengthPx.coerceAtLeast(10f)
        val l2 = lower.lengthPx.coerceAtLeast(10f)

        val dx = targetX - rootX
        val dy = targetY - rootY
        val rawDist = hypot(dx, dy)
        val maxReach = (l1 + l2) - 0.5f
        val minReach = kotlin.math.abs(l1 - l2) + 0.5f
        val d = rawDist.coerceIn(minReach, maxReach)

        val baseAngleRad = atan2(dy.toDouble(), dx.toDouble())

        // Law of Cosines for shoulder/hip angle offset
        val cosAngle1 = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
        val angle1Rad = acos(cosAngle1.toDouble())

        // Law of Cosines for interior elbow/knee angle
        val cosAngle2 = ((l1 * l1 + l2 * l2 - d * d) / (2f * l1 * l2)).coerceIn(-1f, 1f)
        val interiorRad = acos(cosAngle2.toDouble())

        val upperWorldDeg = Math.toDegrees(baseAngleRad - angle1Rad).toFloat()
        val lowerLocalDeg = Math.toDegrees(Math.PI - interiorRad).toFloat()

        if (upper.parentId in bones.indices) {
            upper.localAngleDeg = upperWorldDeg - bones[upper.parentId].worldAngleDeg
        } else {
            upper.localAngleDeg = upperWorldDeg
        }
        lower.localAngleDeg = lowerLocalDeg
        updateForwardKinematics()
    }

    fun drawSkeletonOverlay(canvas: Canvas) {
        if (!isSkeletonVisible || bones.isEmpty()) return
        updateForwardKinematics()

        for (i in 0 until bones.size) {
            val b = bones[i]
            val sx = b.worldStartX
            val sy = b.worldStartY
            val ex = b.worldEndX
            val ey = b.worldEndY

            val dx = ex - sx
            val dy = ey - sy
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / len
            val ny = dx / len
            val halfWidth = (len * 0.14f).coerceIn(6f, 16f)

            val midX = sx + dx * 0.22f
            val midY = sy + dy * 0.22f

            boneDiamondPath.reset()
            boneDiamondPath.moveTo(sx, sy)
            boneDiamondPath.lineTo(midX + nx * halfWidth, midY + ny * halfWidth)
            boneDiamondPath.lineTo(ex, ey)
            boneDiamondPath.lineTo(midX - nx * halfWidth, midY - ny * halfWidth)
            boneDiamondPath.close()

            canvas.drawPath(boneDiamondPath, if (b.useTwoBoneIk) ikBoneFillPaint else boneFillPaint)
            canvas.drawPath(boneDiamondPath, boneStrokePaint)

            // Draw Joint & Effector handles
            canvas.drawCircle(sx, sy, 8f, jointPaint)
            canvas.drawCircle(ex, ey, if (b.useTwoBoneIk) 13f else 9f, jointPaint)
            canvas.drawCircle(ex, ey, if (b.useTwoBoneIk) 13f else 9f, boneStrokePaint)
        }
    }
}

