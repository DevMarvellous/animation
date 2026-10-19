package com.animationapp.engine.tools

import android.graphics.RectF
import com.animationapp.model.Stroke
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 2D Vector Stroke Intersection & Segment Cutter (`VECTOR_CUTTER` / `✂ Trim`).
 *
 * When animators sketch fast 2D line-art, lines often cross over each other at corners.
 * Instead of tediously erasing with a round eraser, swiping across any stroke with the
 * Trim tool cleanly removes the intersected stroke or trims overhanging segments!
 */
object VectorStrokeCutter {

    private val cutterBounds = RectF()

    /**
     * Removes any non-eraser stroke on the active layer that intersects the swipe segment
     * `(x0, y0) -> (x1, y1)` within tolerance `hitRadiusPx`.
     *
     * Returns true if one or more strokes were cut.
     */
    fun cutIntersectingStrokes(
        strokes: MutableList<Stroke>,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        hitRadiusPx: Float = 14f
    ): Boolean {
        if (strokes.isEmpty()) return false

        cutterBounds.set(
            min(x0, x1) - hitRadiusPx,
            min(y0, y1) - hitRadiusPx,
            max(x0, x1) + hitRadiusPx,
            max(y0, y1) + hitRadiusPx
        )

        var cutAny = false
        var i = strokes.lastIndex
        while (i >= 0) {
            val s = strokes[i]
            if (!s.isEraser && s.pointCount > 0 && RectF.intersects(cutterBounds, s.bounds)) {
                if (doesStrokeIntersectSegment(s, x0, y0, x1, y1, hitRadiusPx + s.baseSize * 0.5f)) {
                    strokes.removeAt(i)
                    cutAny = true
                }
            }
            i--
        }
        return cutAny
    }

    private fun doesStrokeIntersectSegment(
        stroke: Stroke,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        threshold: Float
    ): Boolean {
        val count = stroke.pointCount
        if (count == 1) {
            return distancePointToSegment(stroke.getX(0), stroke.getY(0), ax, ay, bx, by) <= threshold
        }

        var prevX = stroke.getX(0)
        var prevY = stroke.getY(0)
        for (p in 1 until count) {
            val currX = stroke.getX(p)
            val currY = stroke.getY(p)
            if (segmentsIntersect(ax, ay, bx, by, prevX, prevY, currX, currY) ||
                distancePointToSegment(currX, currY, ax, ay, bx, by) <= threshold
            ) {
                return true
            }
            prevX = currX
            prevY = currY
        }
        return false
    }

    private fun segmentsIntersect(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float
    ): Boolean {
        val d1 = crossProductDirection(cx, cy, dx, dy, ax, ay)
        val d2 = crossProductDirection(cx, cy, dx, dy, bx, by)
        val d3 = crossProductDirection(ax, ay, bx, by, cx, cy)
        val d4 = crossProductDirection(ax, ay, bx, by, dx, dy)
        return (((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
            ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f)))
    }

    private fun crossProductDirection(
        px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float
    ): Float {
        return (qx - px) * (ry - py) - (rx - px) * (qy - py)
    }

    private fun distancePointToSegment(
        px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.0001f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return hypot(px - projX, py - projY)
    }
}
