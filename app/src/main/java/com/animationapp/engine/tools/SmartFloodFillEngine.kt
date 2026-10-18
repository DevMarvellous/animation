package com.animationapp.engine.tools

import android.graphics.Bitmap
import android.graphics.Color

/**
 * High-speed, Zero-Object-Allocation Scanline Flood Fill Engine with Under-Line Color Trapping.
 *
 * Uses a single primitive IntArray ring/stack buffer for coordinates and reads reference layer
 * pixels in bulk via getPixels() so filling a 1080p region executes in milliseconds even on weak CPUs.
 *
 * Supports Reference Layer Coloring:
 * - Reads boundary lines from the Reference Layer (e.g., "Line Art" layer on top)
 * - Writes the solid color fill onto the target Color Layer underneath!
 */
object SmartFloodFillEngine {

    private const val ALPHA_BOUNDARY_THRESHOLD = 48

    fun performFloodFill(
        referenceBitmap: Bitmap,
        targetFillBitmap: Bitmap?,
        stageWidth: Int,
        stageHeight: Int,
        seedX: Int,
        seedY: Int,
        fillColor: Int,
        expandUnderLinePx: Int = 1
    ): Bitmap {
        val sx = seedX.coerceIn(0, stageWidth - 1)
        val sy = seedY.coerceIn(0, stageHeight - 1)

        val outBitmap = targetFillBitmap ?: Bitmap.createBitmap(
            stageWidth,
            stageHeight,
            Bitmap.Config.ARGB_8888
        )

        val totalPixels = stageWidth * stageHeight
        val refPixels = IntArray(totalPixels)
        val outPixels = IntArray(totalPixels)

        referenceBitmap.getPixels(refPixels, 0, stageWidth, 0, 0, stageWidth, stageHeight)
        outBitmap.getPixels(outPixels, 0, stageWidth, 0, 0, stageWidth, stageHeight)

        val seedIdx = sy * stageWidth + sx
        // If user tapped directly on a dark/opaque reference line, do nothing
        if (Color.alpha(refPixels[seedIdx]) >= 180) {
            return outBitmap
        }

        // Visited bitmask packed into a ByteArray (1 byte per pixel, very fast)
        val visited = ByteArray(totalPixels)

        // Primitive IntArray stack storing packed (y * stageWidth + x) indices
        val stack = IntArray(totalPixels / 2 + 1024)
        var stackPtr = 0

        stack[stackPtr++] = seedIdx
        visited[seedIdx] = 1

        while (stackPtr > 0) {
            val idx = stack[--stackPtr]
            val y = idx / stageWidth
            val x = idx - y * stageWidth

            // Move left to find scanline start
            var left = x
            var rowOffset = y * stageWidth
            while (left > 0) {
                val prevIdx = rowOffset + (left - 1)
                if (visited[prevIdx].toInt() != 0 || isBoundary(refPixels[prevIdx])) break
                left--
                visited[prevIdx] = 1
            }

            // Move right to find scanline end
            var right = x
            while (right < stageWidth - 1) {
                val nextIdx = rowOffset + (right + 1)
                if (visited[nextIdx].toInt() != 0 || isBoundary(refPixels[nextIdx])) break
                right++
                visited[nextIdx] = 1
            }

            // Fill scanline [left..right] and optionally trap 1px under boundary edges
            val trapLeft = (left - expandUnderLinePx).coerceAtLeast(0)
            val trapRight = (right + expandUnderLinePx).coerceAtMost(stageWidth - 1)
            for (fillX in trapLeft..trapRight) {
                outPixels[rowOffset + fillX] = fillColor
            }

            // Also trap 1px vertically above/below boundary ends
            if (expandUnderLinePx > 0) {
                if (y > 0) {
                    val upRow = (y - 1) * stageWidth
                    outPixels[upRow + left] = fillColor
                    outPixels[upRow + right] = fillColor
                }
                if (y < stageHeight - 1) {
                    val downRow = (y + 1) * stageWidth
                    outPixels[downRow + left] = fillColor
                    outPixels[downRow + right] = fillColor
                }
            }

            // Scan row above and row below for new seeds
            if (y > 0) {
                stackPtr = scanNeighborRow(
                    rowY = y - 1,
                    left = left,
                    right = right,
                    stageWidth = stageWidth,
                    refPixels = refPixels,
                    visited = visited,
                    stack = stack,
                    stackPtr = stackPtr
                )
            }
            if (y < stageHeight - 1) {
                stackPtr = scanNeighborRow(
                    rowY = y + 1,
                    left = left,
                    right = right,
                    stageWidth = stageWidth,
                    refPixels = refPixels,
                    visited = visited,
                    stack = stack,
                    stackPtr = stackPtr
                )
            }
        }

        outBitmap.setPixels(outPixels, 0, stageWidth, 0, 0, stageWidth, stageHeight)
        return outBitmap
    }

    private fun scanNeighborRow(
        rowY: Int,
        left: Int,
        right: Int,
        stageWidth: Int,
        refPixels: IntArray,
        visited: ByteArray,
        stack: IntArray,
        stackPtr: Int
    ): Int {
        var sp = stackPtr
        val rowOffset = rowY * stageWidth
        var inSegment = false

        for (x in left..right) {
            val idx = rowOffset + x
            val canFill = (visited[idx].toInt() == 0) && !isBoundary(refPixels[idx])
            if (canFill) {
                visited[idx] = 1
                if (!inSegment) {
                    if (sp < stack.size) {
                        stack[sp++] = idx
                    }
                    inSegment = true
                }
            } else {
                inSegment = false
            }
        }
        return sp
    }

    private fun isBoundary(pixel: Int): Boolean {
        return Color.alpha(pixel) >= ALPHA_BOUNDARY_THRESHOLD
    }
}
