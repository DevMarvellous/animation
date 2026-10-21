package com.animationapp.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.core.storage.DiskSwapManager
import com.animationapp.engine.vfx.LayerVfxEngine
import com.animationapp.model.AnimationLayer
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerCel
import com.animationapp.model.Stroke
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Zero-Bloat Hardware-Accelerated 1080p MP4 Video Exporter (`android.media.MediaCodec`).
 *
 * Why this is critical for budget Android phones:
 * - Third-party FFmpeg libraries add ~30MB to the APK and use heavy software CPU encoding.
 * - Android's built-in `MediaCodec` + `MediaMuxer` uses the phone's dedicated hardware video chip,
 *   adds 0 KB to the APK, and exports crisp 1080p H.264 `.mp4` animations directly to the phone's
 *   Movies/Gallery folder!
 */
object HardwareVideoExporter {

    private const val MIME_TYPE = "video/avc" // H.264 Hardware Encoder
    private const val BIT_RATE = 8_000_000    // 8 Mbps crisp 2D line-art quality
    private const val I_FRAME_INTERVAL = 1

    private val exportExecutor = Executors.newSingleThreadExecutor()

    fun exportMp4Async(
        context: Context,
        project: AnimationProject,
        onComplete: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        exportExecutor.execute {
            try {
                val tempOutFile = File(appContext.cacheDir, "export_render.mp4")
                if (tempOutFile.exists()) tempOutFile.delete()

                encodeProjectToMp4(project, tempOutFile)
                val savedLocation = copyToGalleryMovies(appContext, tempOutFile)
                tempOutFile.delete()

                CrashReporter.logInfo("Exporter", "Exported MP4 successfully to $savedLocation")
                onComplete(true, savedLocation)
            } catch (t: Throwable) {
                CrashReporter.logInfo("Exporter", "MP4 Export failed: ${t.message}")
                onComplete(false, t.message ?: "Unknown encoding error")
            }
        }
    }

    private fun encodeProjectToMp4(project: AnimationProject, outputFile: File) {
        // Ensure dimensions are multiples of 16 for hardware H.264 encoders on budget MediaTek/Snapdragon chips
        val width = (project.stageWidth / 16) * 16
        val height = (project.stageHeight / 16) * 16
        val fps = project.fps.coerceIn(1, 60)

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false

        val scratchLayerBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchLayerBmp)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val eraserXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val alphaLockXfer = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

        var presentationTimeUs = 0L
        val frameDurationUs = 1_000_000L / fps

        try {
            // Loop at least 3 times if animation is very short (< 12 frames) so social media players play it cleanly
            val totalRawFrames = project.frames.size
            val repeatLoops = if (totalRawFrames in 1..8) 3 else 1
            val tweenScratch5 = FloatArray(5)

            for (loop in 0 until repeatLoops) {
                for (fIdx in 0 until totalRawFrames) {
                    val frame = project.frames[fIdx]
                    val holdCount = frame.exposureHold.coerceAtLeast(1)

                    for (hold in 0 until holdCount) {
                        val surfaceCanvas = inputSurface.lockCanvas(null)
                        try {
                            surfaceCanvas.drawColor(project.stageBackgroundColor)

                            for (lIdx in 0 until project.layers.size) {
                                val layer = project.layers[lIdx]
                                if (!layer.isVisible) continue
                                val cel = frame.getCel(lIdx)
                                if (!cel.hasContent()) continue

                                scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                scratchCanvas.save()
                                if (layer.tweenTrack.hasKeyframes()) {
                                    scratchCanvas.concat(
                                        layer.tweenTrack.buildLayerMatrix(fIdx, width, height, tweenScratch5)
                                    )
                                }
                                renderCel(
                                    scratchCanvas,
                                    cel,
                                    layer,
                                    fIdx * 10 + hold,
                                    strokePaint,
                                    eraserXfer,
                                    alphaLockXfer
                                )
                                scratchCanvas.restore()
                                layer.syncCompositingPaint()
                                surfaceCanvas.drawBitmap(scratchLayerBmp, 0f, 0f, layer.compositingPaint)
                            }
                        } finally {
                            inputSurface.unlockCanvasAndPost(surfaceCanvas)
                        }

                        drainEncoder(encoder, muxer, bufferInfo, endOfStream = false, trackIndexSetter = {
                            trackIndex = it
                            muxerStarted = true
                        }, currentTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted }, forcePtsUs = presentationTimeUs)

                        presentationTimeUs += frameDurationUs
                    }
                }
            }

            // Signal End of Stream
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true, trackIndexSetter = {
                trackIndex = it
                muxerStarted = true
            }, currentTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted }, forcePtsUs = presentationTimeUs)
        } finally {
            scratchLayerBmp.recycle()
            try {
                encoder.stop()
                encoder.release()
            } catch (_: Throwable) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Throwable) {}
            inputSurface.release()
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
            } else {
                s.brushPreset.applyToPaint(strokePaint, s.color, s.baseSize)
                strokePaint.xfermode = if (isAlphaLocked && i > 0) alphaLockXfer else null
            }
            targetCanvas.drawPath(effectivePath, strokePaint)
            strokePaint.xfermode = null
            strokePaint.maskFilter = null
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndexSetter: (Int) -> Unit,
        currentTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean,
        forcePtsUs: Long
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                val tIdx = muxer.addTrack(newFormat)
                muxer.start()
                trackIndexSetter(tIdx)
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status) ?: continue
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0 && isMuxerStarted()) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    bufferInfo.presentationTimeUs = forcePtsUs
                    muxer.writeSampleData(currentTrackIndex(), encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun copyToGalleryMovies(context: Context, sourceMp4: File): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ProAnimation_$stamp.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ProAnimationStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore video entry")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceMp4).use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Movies/ProAnimationStudio/$fileName"
        } else {
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Exports")
            outDir.mkdirs()
            val dest = File(outDir, fileName)
            sourceMp4.copyTo(dest, overwrite = true)
            return dest.absolutePath
        }
    }
}

