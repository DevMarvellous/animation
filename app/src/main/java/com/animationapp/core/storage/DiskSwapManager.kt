package com.animationapp.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerCel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Virtual Memory Frame Streaming Engine for Low-End 2GB RAM Phones (Phase 18).
 *
 * Keeps a hot window of active frames (`currentFrameIndex ± activeWindowRadius`) in RAM for
 * 120 FPS drawing and Onion Skinning, and swaps inactive `LayerCel.fillBitmap` rasters out to
 * `cacheDir/cel_swap/` so animators can create 200+ frame animations without OutOfMemoryError!
 */
object DiskSwapManager {

    private const val SWAP_DIR_NAME = "cel_swap"
    var swappedCelCount: Int = 0
        private set

    fun maintainHotFrameWindow(
        context: Context,
        project: AnimationProject,
        activeWindowRadius: Int = 4
    ) {
        val swapDir = File(context.cacheDir, SWAP_DIR_NAME)
        if (!swapDir.exists()) {
            swapDir.mkdirs()
        }

        val currentIdx = project.currentFrameIndex
        val frames = project.frames

        // 1. Collect all LayerCels that MUST stay in RAM (within activeWindowRadius)
        val hotCels = HashSet<LayerCel>()
        for (i in frames.indices) {
            if (abs(i - currentIdx) <= activeWindowRadius) {
                val f = frames[i]
                for (cIdx in 0 until f.cels.size) {
                    val cel = f.cels[cIdx]
                    hotCels.add(cel)
                    // Ensure hot cels are paged into RAM
                    restoreCelFromDiskIfNeeded(cel)
                }
            }
        }

        // 2. Swap out cold LayerCels that have an uncompressed fillBitmap sitting in RAM
        var newlySwapped = 0
        for (i in frames.indices) {
            if (abs(i - currentIdx) <= activeWindowRadius) continue
            val f = frames[i]
            for (cIdx in 0 until f.cels.size) {
                val cel = f.cels[cIdx]
                if (hotCels.contains(cel)) continue // Shared linked cel used by a hot frame
                val bmp = cel.fillBitmap
                if (bmp != null && !bmp.isRecycled) {
                    val swapFile = File(swapDir, "cel_${cel.celId}.png")
                    try {
                        FileOutputStream(swapFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        cel.swappedDiskPath = swapFile.absolutePath
                        bmp.recycle()
                        cel.fillBitmap = null
                        newlySwapped++
                    } catch (t: Throwable) {
                        CrashReporter.logWarning("DiskSwapManager", "Swap write skipped: ${t.message}")
                    }
                }
            }
        }

        // Recount total swapped cels
        var totalSwapped = 0
        for (f in frames) {
            for (cel in f.cels) {
                if (cel.fillBitmap == null && cel.swappedDiskPath != null) {
                    totalSwapped++
                }
            }
        }
        swappedCelCount = totalSwapped
    }

    fun restoreCelFromDiskIfNeeded(cel: LayerCel): Bitmap? {
        val existing = cel.fillBitmap
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        val path = cel.swappedDiskPath ?: return null
        val file = File(path)
        if (!file.exists()) {
            cel.swappedDiskPath = null
            return null
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
            cel.fillBitmap = decoded
            cel.isCacheDirty = true
            decoded
        } catch (t: Throwable) {
            CrashReporter.logError("DiskSwapManager", "Failed to restore swapped cel ${cel.celId}", t)
            null
        }
    }
}

