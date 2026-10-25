package com.animationapp.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.core.storage.DiskSwapManager
import com.animationapp.engine.brush.ScreentoneEngine
import com.animationapp.engine.brush.ScreentoneEngine.ScreentonePattern
import com.animationapp.engine.vfx.LayerVfxEngine
import com.animationapp.model.AnimationLayer
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerCel
import com.animationapp.model.Stroke
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zero-Bloat Animated `.GIF` Loop Exporter & PNG Frame Sequence `.ZIP` Archive Exporter (Phase 24).
 *
 * Completes the 4-Format Pro Studio Export Suite alongside 1080p H.264 MP4 and 2D Sprite Sheet PNG+JSON!
 */
object GifAndZipSequenceExporter {

    private val exportExecutor = Executors.newSingleThreadExecutor()

    fun exportAnimatedGifAsync(
        context: Context,
        project: AnimationProject,
        gifScaleFactor: Float = 0.5f,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        exportExecutor.execute {
            try {
                val gifW = (project.stageWidth * gifScaleFactor).toInt().coerceIn(160, 800)
                val gifH = (project.stageHeight * gifScaleFactor).toInt().coerceIn(120, 800)
                val tempGifFile = File(appContext.cacheDir, "export_loop.gif")
                if (tempGifFile.exists()) tempGifFile.delete()

                FileOutputStream(tempGifFile).use { fos ->
                    encodeProjectToGif89a(project, gifW, gifH, fos)
                }

                val savedPath = copyGifToGallery(appContext, tempGifFile)
                tempGifFile.delete()
                CrashReporter.logInfo("GifExporter", "Exported Animated GIF to $savedPath")
                onComplete(true, savedPath)
            } catch (t: Throwable) {
                CrashReporter.logError("GifExporter", "Animated GIF export failed", t)
                onComplete(false, t.message ?: "GIF Export error")
            }
        }
    }

    fun exportPngSequenceZipAsync(
        context: Context,
        project: AnimationProject,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        exportExecutor.execute {
            try {
                val tempZipFile = File(appContext.cacheDir, "export_frames.zip")
                if (tempZipFile.exists()) tempZipFile.delete()

                writePngSequenceToZip(project, tempZipFile)
                val savedPath = copyZipToDownloads(appContext, tempZipFile)
                tempZipFile.delete()
                CrashReporter.logInfo("ZipExporter", "Exported PNG Sequence ZIP to $savedPath")
                onComplete(true, savedPath)
            } catch (t: Throwable) {
                CrashReporter.logError("ZipExporter", "ZIP Sequence export failed", t)
                onComplete(false, t.message ?: "ZIP Sequence export error")
            }
        }
    }

    private fun writePngSequenceToZip(project: AnimationProject, outZipFile: File) {
        val w = project.stageWidth
        val h = project.stageHeight
        val frameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBmp)
        val scratchBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBmp)
        val clipScratchBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val clipScratchCanvas = Canvas(clipScratchBmp)
        val clipMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
        }
        val eraserXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val alphaLockXfer = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        val tweenScratch5 = FloatArray(5)

        try {
            ZipOutputStream(FileOutputStream(outZipFile)).use { zos ->
                for (fIdx in 0 until project.frames.size) {
                    val frame = project.frames[fIdx]
                    frameCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    val layers = project.layers
                    var lIdx = 0
                    while (lIdx < layers.size) {
                        val baseIdx = lIdx
                        var chainEnd = baseIdx + 1
                        while (chainEnd < layers.size && layers[chainEnd].isClippingMask) {
                            chainEnd++
                        }

                        if (chainEnd == baseIdx + 1) {
                            val layer = layers[baseIdx]
                            if (layer.isVisible) {
                                val cel = frame.getCel(baseIdx)
                                if (cel.hasContent()) {
                                    scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    scratchCanvas.save()
                                    if (layer.tweenTrack.hasKeyframes()) {
                                        scratchCanvas.concat(layer.tweenTrack.buildLayerMatrix(fIdx, w, h, tweenScratch5))
                                    }
                                    renderCel(scratchCanvas, cel, layer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                    scratchCanvas.restore()
                                    layer.syncCompositingPaint()
                                    frameCanvas.drawBitmap(scratchBmp, 0f, 0f, layer.compositingPaint)
                                }
                            }
                            lIdx++
                        } else {
                            val baseLayer = layers[baseIdx]
                            if (baseLayer.isVisible) {
                                val baseCel = frame.getCel(baseIdx)
                                scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                if (baseCel.hasContent()) {
                                    scratchCanvas.save()
                                    if (baseLayer.tweenTrack.hasKeyframes()) {
                                        scratchCanvas.concat(baseLayer.tweenTrack.buildLayerMatrix(fIdx, w, h, tweenScratch5))
                                    }
                                    renderCel(scratchCanvas, baseCel, baseLayer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                    scratchCanvas.restore()
                                }

                                for (cIdx in (baseIdx + 1) until chainEnd) {
                                    val clipLayer = layers[cIdx]
                                    if (!clipLayer.isVisible) continue
                                    val clipCel = frame.getCel(cIdx)
                                    if (!clipCel.hasContent()) continue

                                    clipScratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    clipScratchCanvas.save()
                                    if (clipLayer.tweenTrack.hasKeyframes()) {
                                        clipScratchCanvas.concat(clipLayer.tweenTrack.buildLayerMatrix(fIdx, w, h, tweenScratch5))
                                    }
                                    renderCel(clipScratchCanvas, clipCel, clipLayer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                    clipScratchCanvas.restore()

                                    clipMaskPaint.alpha = (clipLayer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                                    scratchCanvas.drawBitmap(clipScratchBmp, 0f, 0f, clipMaskPaint)
                                }

                                baseLayer.syncCompositingPaint()
                                frameCanvas.drawBitmap(scratchBmp, 0f, 0f, baseLayer.compositingPaint)
                            }
                            lIdx = chainEnd
                        }
                    }

                    val entryName = String.format(Locale.US, "frame_%04d.png", fIdx + 1)
                    zos.putNextEntry(ZipEntry(entryName))
                    frameBmp.compress(Bitmap.CompressFormat.PNG, 100, zos)
                    zos.closeEntry()
                }

                // Write manifest JSON inside the ZIP
                zos.putNextEntry(ZipEntry("project_manifest.json"))
                val manifest = """
                    {
                      "projectName": "${project.projectName}",
                      "stageWidth": $w,
                      "stageHeight": $h,
                      "fps": ${project.fps},
                      "totalFrames": ${project.frames.size}
                    }
                """.trimIndent()
                zos.write(manifest.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        } finally {
            frameBmp.recycle()
            scratchBmp.recycle()
            clipScratchBmp.recycle()
        }
    }

    /**
     * Pure-Kotlin GIF89a + 3-3-2 RGB 256-Color Global Palette + Clear-Code LZW Stream Encoder.
     */
    private fun encodeProjectToGif89a(
        project: AnimationProject,
        gifW: Int,
        gifH: Int,
        out: OutputStream
    ) {
        val stageW = project.stageWidth
        val stageH = project.stageHeight
        val stageBmp = Bitmap.createBitmap(stageW, stageH, Bitmap.Config.ARGB_8888)
        val stageCanvas = Canvas(stageBmp)
        val scratchBmp = Bitmap.createBitmap(stageW, stageH, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBmp)
        val clipScratchBmp = Bitmap.createBitmap(stageW, stageH, Bitmap.Config.ARGB_8888)
        val clipScratchCanvas = Canvas(clipScratchBmp)
        val clipMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        val scaledBmp = Bitmap.createBitmap(gifW, gifH, Bitmap.Config.ARGB_8888)
        val scaledCanvas = Canvas(scaledBmp)
        val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
        }
        val eraserXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val alphaLockXfer = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        val tweenScratch5 = FloatArray(5)
        val pixels = IntArray(gifW * gifH)
        val indexedPixels = ByteArray(gifW * gifH)

        try {
            // 1. GIF89a Header
            out.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShortLE(out, gifW)
            writeShortLE(out, gifH)
            // Global Color Table Flag (1), Color Res (7), Sort (0), Size of GCT = 7 (256 colors) -> 0xF7
            out.write(0xF7)
            out.write(0) // Background color index
            out.write(0) // Pixel aspect ratio

            // 2. Write 256-color 3-3-2 RGB Global Color Table (768 bytes)
            for (i in 0 until 256) {
                val r = ((i shr 5) and 0x07) * 255 / 7
                val g = ((i shr 2) and 0x07) * 255 / 7
                val b = (i and 0x03) * 255 / 3
                out.write(r)
                out.write(g)
                out.write(b)
            }

            // 3. Netscape 2.0 Infinite Loop Application Extension
            out.write(
                byteArrayOf(
                    0x21, 0xFF.toByte(), 0x0B,
                    'N'.code.toByte(), 'E'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(),
                    'C'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(),
                    '2'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(),
                    0x03, 0x01, 0x00, 0x00, 0x00
                )
            )

            val baseDelayCentisec = (100 / project.fps.coerceAtLeast(1)).coerceAtLeast(2)

            for (fIdx in 0 until project.frames.size) {
                val frame = project.frames[fIdx]
                stageCanvas.drawColor(project.stageBackgroundColor)

                val layers = project.layers
                var lIdx = 0
                while (lIdx < layers.size) {
                    val baseIdx = lIdx
                    var chainEnd = baseIdx + 1
                    while (chainEnd < layers.size && layers[chainEnd].isClippingMask) {
                        chainEnd++
                    }

                    if (chainEnd == baseIdx + 1) {
                        val layer = layers[baseIdx]
                        if (layer.isVisible) {
                            val cel = frame.getCel(baseIdx)
                            if (cel.hasContent()) {
                                scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                scratchCanvas.save()
                                if (layer.tweenTrack.hasKeyframes()) {
                                    scratchCanvas.concat(layer.tweenTrack.buildLayerMatrix(fIdx, stageW, stageH, tweenScratch5))
                                }
                                renderCel(scratchCanvas, cel, layer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                scratchCanvas.restore()
                                layer.syncCompositingPaint()
                                stageCanvas.drawBitmap(scratchBmp, 0f, 0f, layer.compositingPaint)
                            }
                        }
                        lIdx++
                    } else {
                        val baseLayer = layers[baseIdx]
                        if (baseLayer.isVisible) {
                            val baseCel = frame.getCel(baseIdx)
                            scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                            if (baseCel.hasContent()) {
                                scratchCanvas.save()
                                if (baseLayer.tweenTrack.hasKeyframes()) {
                                    scratchCanvas.concat(baseLayer.tweenTrack.buildLayerMatrix(fIdx, stageW, stageH, tweenScratch5))
                                }
                                renderCel(scratchCanvas, baseCel, baseLayer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                scratchCanvas.restore()
                            }

                            for (cIdx in (baseIdx + 1) until chainEnd) {
                                val clipLayer = layers[cIdx]
                                if (!clipLayer.isVisible) continue
                                val clipCel = frame.getCel(cIdx)
                                if (!clipCel.hasContent()) continue

                                clipScratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                clipScratchCanvas.save()
                                if (clipLayer.tweenTrack.hasKeyframes()) {
                                    clipScratchCanvas.concat(clipLayer.tweenTrack.buildLayerMatrix(fIdx, stageW, stageH, tweenScratch5))
                                }
                                renderCel(clipScratchCanvas, clipCel, clipLayer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                clipScratchCanvas.restore()

                                clipMaskPaint.alpha = (clipLayer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                                scratchCanvas.drawBitmap(clipScratchBmp, 0f, 0f, clipMaskPaint)
                            }

                            baseLayer.syncCompositingPaint()
                            stageCanvas.drawBitmap(scratchBmp, 0f, 0f, baseLayer.compositingPaint)
                        }
                        lIdx = chainEnd
                    }
                }

                scaledCanvas.save()
                scaledCanvas.scale(gifW.toFloat() / stageW, gifH.toFloat() / stageH)
                scaledCanvas.drawBitmap(stageBmp, 0f, 0f, scalePaint)
                scaledCanvas.restore()

                scaledBmp.getPixels(pixels, 0, gifW, 0, 0, gifW, gifH)
                for (p in pixels.indices) {
                    val c = pixels[p]
                    val r3 = (Color.red(c) * 7 + 127) / 255
                    val g3 = (Color.green(c) * 7 + 127) / 255
                    val b2 = (Color.blue(c) * 3 + 127) / 255
                    indexedPixels[p] = ((r3 shl 5) or (g3 shl 2) or b2).toByte()
                }

                // Graphic Control Extension
                val frameDelayCs = baseDelayCentisec * frame.exposureHold.coerceAtLeast(1)
                out.write(0x21)
                out.write(0xF9)
                out.write(0x04)
                out.write(0x00) // No transparency disposal
                writeShortLE(out, frameDelayCs)
                out.write(0x00)
                out.write(0x00)

                // Image Descriptor
                out.write(0x2C)
                writeShortLE(out, 0)
                writeShortLE(out, 0)
                writeShortLE(out, gifW)
                writeShortLE(out, gifH)
                out.write(0x00) // Use Global Color Table

                // LZW Minimum Code Size = 8
                writeGifLzwImageData(out, indexedPixels)
            }

            // GIF Trailer
            out.write(0x3B)
            out.flush()
        } finally {
            stageBmp.recycle()
            scratchBmp.recycle()
            clipScratchBmp.recycle()
            scaledBmp.recycle()
        }
    }

    /**
     * Writes valid 9-bit Clear-Code reset LZW blocks for 8-bit indexed pixels.
     */
    private fun writeGifLzwImageData(out: OutputStream, indexedPixels: ByteArray) {
        out.write(8) // LZW Minimum Code Size
        val clearCode = 256
        val endOfInfoCode = 257

        val bitBuffer = ByteArrayOutputStream(indexedPixels.size + 1024)
        var curAccum = 0
        var curBits = 0

        fun emit9BitCode(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += 9
            while (curBits >= 8) {
                bitBuffer.write(curAccum and 0xFF)
                curAccum = curAccum ushr 8
                curBits -= 8
            }
        }

        emit9BitCode(clearCode)
        var symbolsSinceClear = 0
        for (i in indexedPixels.indices) {
            emit9BitCode(indexedPixels[i].toInt() and 0xFF)
            symbolsSinceClear++
            // Reset dictionary before 9-bit table fills (254 symbols)
            if (symbolsSinceClear >= 250) {
                emit9BitCode(clearCode)
                symbolsSinceClear = 0
            }
        }
        emit9BitCode(endOfInfoCode)
        if (curBits > 0) {
            bitBuffer.write(curAccum and 0xFF)
        }

        val rawBytes = bitBuffer.toByteArray()
        var offset = 0
        while (offset < rawBytes.size) {
            val blockLen = (rawBytes.size - offset).coerceAtMost(255)
            out.write(blockLen)
            out.write(rawBytes, offset, blockLen)
            offset += blockLen
        }
        out.write(0) // Block terminator
    }

    private fun writeShortLE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun renderCel(
        targetCanvas: Canvas,
        cel: LayerCel,
        layer: AnimationLayer,
        boilSeed: Int,
        strokePaint: Paint,
        eraserXfer: PorterDuffXfermode,
        alphaLockXfer: PorterDuffXfermode
    ) {
        DiskSwapManager.restoreCelFromDiskIfNeeded(cel)?.let { bmp ->
            if (!bmp.isRecycled) targetCanvas.drawBitmap(bmp, 0f, 0f, null)
        }
        val isAlphaLocked = layer.isAlphaLocked
        val strokes = cel.strokes
        for (i in 0 until strokes.size) {
            val s: Stroke = strokes[i]
            val effectivePath = if (layer.vfxLineBoilPx > 0.1f) {
                LayerVfxEngine.getEffectiveStrokePath(s, layer, boilSeed)
            } else {
                s.renderPath
            }
            if (layer.hasActiveVfx()) {
                LayerVfxEngine.drawStrokeUnderlayVfx(targetCanvas, s, effectivePath, layer)
            }
            if (s.isEraser) {
                strokePaint.xfermode = eraserXfer
                strokePaint.strokeWidth = s.baseSize
                strokePaint.color = Color.TRANSPARENT
                strokePaint.maskFilter = null
                strokePaint.shader = null
            } else {
                s.brushPreset.applyToPaint(strokePaint, s.color, s.baseSize)
                strokePaint.xfermode = if (isAlphaLocked && i > 0) alphaLockXfer else null
                if (s.screentonePattern != ScreentonePattern.NONE) {
                    strokePaint.shader = ScreentoneEngine.getShader(s.screentonePattern, s.color)
                } else {
                    strokePaint.shader = null
                }
            }
            targetCanvas.drawPath(effectivePath, strokePaint)
            strokePaint.xfermode = null
            strokePaint.maskFilter = null
            strokePaint.shader = null
        }
    }

    private fun copyGifToGallery(context: Context, sourceGif: File): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ProAnimation_$stamp.gif"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ProAnimationStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore GIF entry")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceGif).use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Pictures/ProAnimationStudio/$fileName"
        } else {
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GIFs")
            outDir.mkdirs()
            val dest = File(outDir, fileName)
            sourceGif.copyTo(dest, overwrite = true)
            return dest.absolutePath
        }
    }

    private fun copyZipToDownloads(context: Context, sourceZip: File): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ProAnimation_Frames_$stamp.zip"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ProAnimationStudio")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore Downloads ZIP entry")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceZip).use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/ProAnimationStudio/$fileName"
        } else {
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FrameSequences")
            outDir.mkdirs()
            val dest = File(outDir, fileName)
            sourceZip.copyTo(dest, overwrite = true)
            return dest.absolutePath
        }
    }
}

