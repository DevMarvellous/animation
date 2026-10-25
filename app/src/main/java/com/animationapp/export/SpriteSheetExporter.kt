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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 2D Game Engine Sprite Sheet PNG & JSON Atlas Exporter (Phase 10).
 *
 * Packs all animation frames into a single grid Sprite Sheet `.png` image (plus a Godot/Unity-ready
 * `.json` frame coordinate atlas) and saves it directly to the phone's `Pictures/ProAnimationStudio/` gallery!
 */
object SpriteSheetExporter {

    private val exportExecutor = Executors.newSingleThreadExecutor()

    fun exportSpriteSheetAsync(
        context: Context,
        project: AnimationProject,
        cellScaleFactor: Float = 0.25f, // Default 480x270 per sprite cell so 30+ frames fit inside a 4096x4096 GPU texture
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        exportExecutor.execute {
            try {
                val totalFrames = project.frames.size.coerceAtLeast(1)
                val cols = ceil(sqrt(totalFrames.toDouble())).toInt().coerceAtLeast(1)
                val rows = ceil(totalFrames.toDouble() / cols).toInt().coerceAtLeast(1)

                val cellW = (project.stageWidth * cellScaleFactor).toInt().coerceAtLeast(64)
                val cellH = (project.stageHeight * cellScaleFactor).toInt().coerceAtLeast(64)
                val sheetW = (cols * cellW).coerceAtMost(4096)
                val sheetH = (rows * cellH).coerceAtMost(4096)

                val sheetBitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
                val sheetCanvas = Canvas(sheetBitmap)

                val stageTempBmp = Bitmap.createBitmap(project.stageWidth, project.stageHeight, Bitmap.Config.ARGB_8888)
                val stageTempCanvas = Canvas(stageTempBmp)

                val scratchBmp = Bitmap.createBitmap(project.stageWidth, project.stageHeight, Bitmap.Config.ARGB_8888)
                val scratchCanvas = Canvas(scratchBmp)

                val clipScratchBmp = Bitmap.createBitmap(project.stageWidth, project.stageHeight, Bitmap.Config.ARGB_8888)
                val clipScratchCanvas = Canvas(clipScratchBmp)
                val clipMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                }

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                }
                val eraserXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                val alphaLockXfer = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

                val jsonBuilder = StringBuilder()
                jsonBuilder.appendLine("{")
                jsonBuilder.appendLine("  \"app\": \"ProAnimation Studio 2D\",")
                jsonBuilder.appendLine("  \"fps\": ${project.fps},")
                jsonBuilder.appendLine("  \"cellWidth\": $cellW,")
                jsonBuilder.appendLine("  \"cellHeight\": $cellH,")
                jsonBuilder.appendLine("  \"columns\": $cols,")
                jsonBuilder.appendLine("  \"rows\": $rows,")
                jsonBuilder.appendLine("  \"frames\": [")

                val tweenScratch5 = FloatArray(5)

                try {
                    for (fIdx in 0 until totalFrames) {
                        val col = fIdx % cols
                        val row = fIdx / cols
                        val destX = col * cellW
                        val destY = row * cellH

                        stageTempCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        val frame = project.frames[fIdx]

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
                                            scratchCanvas.concat(
                                                layer.tweenTrack.buildLayerMatrix(
                                                    fIdx,
                                                    project.stageWidth,
                                                    project.stageHeight,
                                                    tweenScratch5
                                                )
                                            )
                                        }
                                        renderCel(scratchCanvas, cel, layer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                        scratchCanvas.restore()
                                        layer.syncCompositingPaint()
                                        stageTempCanvas.drawBitmap(scratchBmp, 0f, 0f, layer.compositingPaint)
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
                                            scratchCanvas.concat(
                                                baseLayer.tweenTrack.buildLayerMatrix(
                                                    fIdx,
                                                    project.stageWidth,
                                                    project.stageHeight,
                                                    tweenScratch5
                                                )
                                            )
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
                                            clipScratchCanvas.concat(
                                                clipLayer.tweenTrack.buildLayerMatrix(
                                                    fIdx,
                                                    project.stageWidth,
                                                    project.stageHeight,
                                                    tweenScratch5
                                                )
                                            )
                                        }
                                        renderCel(clipScratchCanvas, clipCel, clipLayer, fIdx, strokePaint, eraserXfer, alphaLockXfer)
                                        clipScratchCanvas.restore()

                                        clipMaskPaint.alpha = (clipLayer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                                        scratchCanvas.drawBitmap(clipScratchBmp, 0f, 0f, clipMaskPaint)
                                    }

                                    baseLayer.syncCompositingPaint()
                                    stageTempCanvas.drawBitmap(scratchBmp, 0f, 0f, baseLayer.compositingPaint)
                                }
                                lIdx = chainEnd
                            }
                        }

                        sheetCanvas.save()
                        sheetCanvas.translate(destX.toFloat(), destY.toFloat())
                        sheetCanvas.scale(cellScaleFactor, cellScaleFactor)
                        sheetCanvas.drawBitmap(stageTempBmp, 0f, 0f, scalePaint)
                        sheetCanvas.restore()

                        val comma = if (fIdx < totalFrames - 1) "," else ""
                        jsonBuilder.appendLine(
                            "    {\"frame\": ${fIdx + 1}, \"x\": $destX, \"y\": $destY, \"w\": $cellW, \"h\": $cellH, \"hold\": ${frame.exposureHold}}$comma"
                        )
                    }
                    jsonBuilder.appendLine("  ]")
                    jsonBuilder.appendLine("}")
                } finally {
                    stageTempBmp.recycle()
                    scratchBmp.recycle()
                    clipScratchBmp.recycle()
                }

                val savedPath = saveSpriteSheetToGallery(appContext, sheetBitmap, jsonBuilder.toString())
                sheetBitmap.recycle()

                CrashReporter.logInfo("SpriteSheet", "Saved Sprite Sheet ($cols x $rows) to $savedPath")
                onComplete(true, savedPath)
            } catch (t: Throwable) {
                CrashReporter.logInfo("SpriteSheet", "Sprite Sheet Export error: ${t.message}")
                onComplete(false, t.message ?: "Unknown Sprite Sheet export error")
            }
        }
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

    private fun saveSpriteSheetToGallery(context: Context, bitmap: Bitmap, atlasJson: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pngName = "SpriteSheet_$stamp.png"
        val jsonName = "SpriteSheet_$stamp.json"

        // Save JSON atlas to internal app files dir as well
        File(context.filesDir, jsonName).writeText(atlasJson)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, pngName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ProAnimationStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore PNG entry")

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Pictures/ProAnimationStudio/$pngName"
        } else {
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SpriteSheets")
            outDir.mkdirs()
            val destPng = File(outDir, pngName)
            destPng.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            File(outDir, jsonName).writeText(atlasJson)
            return destPng.absolutePath
        }
    }
}

