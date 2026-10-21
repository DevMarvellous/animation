package com.animationapp.core.storage

import android.content.Context
import com.animationapp.audio.LipSyncPhoneme
import com.animationapp.core.diagnostics.CrashReporter
import com.animationapp.engine.brush.BrushPreset
import com.animationapp.model.AnimationFrame
import com.animationapp.model.AnimationLayer
import com.animationapp.model.AnimationProject
import com.animationapp.model.LayerBlendMode
import com.animationapp.model.Stroke
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Atomic Binary Crash-Safe Autosave & `.pas` Project Serializer (v3 — supports Stage Dimensions,
 * Project Name, BrushPreset, LipSyncPhoneme, and Non-Destructive 2D Layer VFX).
 */
object CrashSafeAutosave {

    private const val MAGIC_HEADER_V3 = 0x50415333 // "PAS3"
    private const val AUTOSAVE_FILE = "autosave.pas"
    private const val AUTOSAVE_TMP_FILE = "autosave.pas.tmp"

    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun saveAsync(context: Context, project: AnimationProject) {
        val appContext = context.applicationContext
        ioExecutor.execute {
            try {
                writeProjectAtomically(appContext, project)
            } catch (t: Throwable) {
                CrashReporter.logInfo("Autosave", "Autosave warning: ${t.message}")
            }
        }
    }

    @Synchronized
    fun writeProjectAtomically(context: Context, project: AnimationProject) {
        val tmpFile = File(context.filesDir, AUTOSAVE_TMP_FILE)
        val finalFile = File(context.filesDir, AUTOSAVE_FILE)
        writeProjectToFile(tmpFile, project)
        if (finalFile.exists()) {
            finalFile.delete()
        }
        tmpFile.renameTo(finalFile)
    }

    @Synchronized
    fun writeProjectToFile(targetFile: File, project: AnimationProject) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { out ->
            out.writeInt(MAGIC_HEADER_V3)
            out.writeUTF(project.projectName)
            out.writeInt(project.stageWidth)
            out.writeInt(project.stageHeight)
            out.writeInt(project.fps)
            out.writeInt(project.stageBackgroundColor)
            out.writeInt(project.activeLayerIndex)
            out.writeInt(project.currentFrameIndex)

            val layerCount = project.layers.size
            out.writeInt(layerCount)
            for (i in 0 until layerCount) {
                val layer = project.layers[i]
                out.writeUTF(layer.name)
                out.writeBoolean(layer.isVisible)
                out.writeBoolean(layer.isLocked)
                out.writeBoolean(layer.isAlphaLocked)
                out.writeBoolean(layer.isReferenceLayer)
                out.writeFloat(layer.opacity)
                out.writeInt(layer.blendMode.ordinal)
                out.writeFloat(layer.vfxLineBoilPx)
                out.writeFloat(layer.vfxGlowRadiusPx)
                out.writeFloat(layer.vfxDropShadowOffsetPx)
            }

            val frameCount = project.frames.size
            out.writeInt(frameCount)
            for (fIdx in 0 until frameCount) {
                val frame = project.frames[fIdx]
                out.writeInt(frame.exposureHold)
                out.writeInt(frame.phonemeMarker.ordinal)
                out.writeFloat(frame.audioAmplitude)
                for (lIdx in 0 until layerCount) {
                    val cel = frame.getCel(lIdx)
                    val strokes = cel.strokes
                    out.writeInt(strokes.size)
                    for (sIdx in 0 until strokes.size) {
                        val s = strokes[sIdx]
                        out.writeInt(s.color)
                        out.writeFloat(s.baseSize)
                        out.writeBoolean(s.isEraser)
                        out.writeInt(s.brushPreset.ordinal)
                        val ptCount = s.pointCount
                        out.writeInt(ptCount)
                        for (p in 0 until ptCount) {
                            out.writeFloat(s.getX(p))
                            out.writeFloat(s.getY(p))
                            out.writeFloat(s.getPressure(p))
                        }
                    }
                }
            }
            out.flush()
        }
    }

    @Synchronized
    fun loadSavedProjectIfPresent(context: Context, targetProject: AnimationProject): Boolean {
        val finalFile = File(context.filesDir, AUTOSAVE_FILE)
        return readProjectFromFile(finalFile, targetProject)
    }

    @Synchronized
    fun readProjectFromFile(sourceFile: File, targetProject: AnimationProject): Boolean {
        if (!sourceFile.exists() || sourceFile.length() < 16L) return false

        return try {
            DataInputStream(BufferedInputStream(FileInputStream(sourceFile))).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER_V3) return false

                targetProject.projectName = input.readUTF()
                val loadedW = input.readInt().coerceIn(128, 2560)
                val loadedH = input.readInt().coerceIn(128, 2560)
                targetProject.resizeStage(loadedW, loadedH)
                targetProject.fps = input.readInt().coerceIn(1, 120)
                targetProject.stageBackgroundColor = input.readInt()
                val savedActiveLayer = input.readInt()
                val savedFrameIdx = input.readInt()

                val layerCount = input.readInt().coerceIn(1, 32)
                targetProject.layers.clear()
                val blendModes = LayerBlendMode.values()
                for (i in 0 until layerCount) {
                    val name = input.readUTF()
                    val visible = input.readBoolean()
                    val locked = input.readBoolean()
                    val alphaLock = input.readBoolean()
                    val isRef = input.readBoolean()
                    val opacity = input.readFloat()
                    val blendOrd = input.readInt().coerceIn(0, blendModes.lastIndex)
                    val boilPx = input.readFloat()
                    val glowPx = input.readFloat()
                    val shadowPx = input.readFloat()
                    targetProject.layers.add(
                        AnimationLayer(
                            name = name,
                            isVisible = visible,
                            isLocked = locked,
                            isAlphaLocked = alphaLock,
                            isReferenceLayer = isRef,
                            opacity = opacity,
                            blendMode = blendModes[blendOrd],
                            vfxLineBoilPx = boilPx,
                            vfxGlowRadiusPx = glowPx,
                            vfxDropShadowOffsetPx = shadowPx
                        )
                    )
                }

                val frameCount = input.readInt().coerceIn(1, 2000)
                targetProject.frames.clear()
                val phonemes = LipSyncPhoneme.values()
                val presets = BrushPreset.values()
                for (fIdx in 0 until frameCount) {
                    val hold = input.readInt().coerceIn(1, 24)
                    val phOrd = input.readInt().coerceIn(0, phonemes.lastIndex)
                    val amp = input.readFloat()
                    val frame = AnimationFrame(
                        layerCount = layerCount,
                        exposureHold = hold,
                        phonemeMarker = phonemes[phOrd],
                        audioAmplitude = amp
                    )
                    for (lIdx in 0 until layerCount) {
                        val cel = frame.getCel(lIdx)
                        val strokeCount = input.readInt().coerceIn(0, 50000)
                        for (sIdx in 0 until strokeCount) {
                            val color = input.readInt()
                            val size = input.readFloat()
                            val isEraser = input.readBoolean()
                            val presetOrd = input.readInt().coerceIn(0, presets.lastIndex)
                            val ptCount = input.readInt().coerceIn(0, 50000)
                            val stroke = Stroke(color, size, isEraser, presets[presetOrd])
                            for (p in 0 until ptCount) {
                                val x = input.readFloat()
                                val y = input.readFloat()
                                val pr = input.readFloat()
                                stroke.addPoint(x, y, pr)
                            }
                            if (ptCount > 0) {
                                stroke.finishStroke(stroke.getX(ptCount - 1), stroke.getY(ptCount - 1))
                            }
                            cel.strokes.add(stroke)
                        }
                    }
                    frame.isCacheDirty = true
                    targetProject.frames.add(frame)
                }

                targetProject.selectLayer(savedActiveLayer)
                targetProject.selectFrame(savedFrameIdx)
                CrashReporter.logInfo(
                    "Autosave",
                    "Loaded '${targetProject.projectName}' (${targetProject.stageWidth}x${targetProject.stageHeight}, ${targetProject.frames.size} frames)"
                )
                true
            }
        } catch (t: Throwable) {
            CrashReporter.logInfo("Autosave", "Failed to load project file: ${t.message}")
            false
        }
    }
}
