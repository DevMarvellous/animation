package com.animationapp.core.storage

import android.content.Context
import android.graphics.Color
import com.animationapp.model.AnimationFrame
import com.animationapp.model.AnimationLayer
import com.animationapp.model.AnimationProject
import java.io.File

/**
 * 2D Studio Stage Resolution & Aspect Ratio Presets (Phase 23).
 */
enum class CanvasStagePreset(val icon: String, val label: String, val width: Int, val height: Int) {
    WIDESCREEN_16_9("📺", "16:9 Widescreen Anime / HD (1280×720)", 1280, 720),
    VERTICAL_9_16("📱", "9:16 Vertical Shorts / TikTok (720×1280)", 720, 1280),
    SQUARE_1_1("⬜", "1:1 Square Loop (960×960)", 960, 960),
    RETRO_4_3("👾", "4:3 Retro / Low-RAM Turbo (640×480)", 640, 480)
}

/**
 * 2D Stage Paper Background Color Presets (Phase 23).
 */
enum class StagePaperPreset(val label: String, val color: Int) {
    PURE_WHITE("📄 Pure White Paper (#FFFFFF)", Color.WHITE),
    CREAM_ANIME("📜 Warm Anime Cel Cream (#FFF9E8)", Color.rgb(255, 249, 232)),
    DARK_SLATE("🌑 Dark Storyboard Slate (#222634)", Color.rgb(34, 38, 52)),
    SKY_BLUE("🌤 Studio Sky Backdrop (#DDF2FF)", Color.rgb(221, 242, 255)),
    CHROMA_GREEN("🟩 Chroma Green Screen (#00D741)", Color.rgb(0, 215, 65))
}

/**
 * Multi-Project Scene Manager (Phase 23).
 *
 * Allows animators to save multiple named `.pas` projects (`filesDir/projects/*.pas`),
 * switch between scenes, or start a fresh scene with any 2D Stage Aspect Ratio Preset.
 */
object MultiProjectManager {

    private const val PROJECTS_DIR = "projects"

    private fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, PROJECTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSavedProjects(context: Context): List<File> {
        val dir = getProjectsDir(context)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".pas") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun saveProjectSlot(context: Context, project: AnimationProject, slotName: String): File {
        val cleanName = slotName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { "Scene_${System.currentTimeMillis() / 1000}" }
        project.projectName = cleanName
        val targetFile = File(getProjectsDir(context), "$cleanName.pas")
        CrashSafeAutosave.writeProjectToFile(targetFile, project)
        CrashSafeAutosave.writeProjectAtomically(context, project)
        return targetFile
    }

    fun loadProjectSlot(context: Context, sourceFile: File, targetProject: AnimationProject): Boolean {
        val ok = CrashSafeAutosave.readProjectFromFile(sourceFile, targetProject)
        if (ok) {
            CrashSafeAutosave.writeProjectAtomically(context, targetProject)
        }
        return ok
    }

    fun loadProjectSlot(context: Context, targetProject: AnimationProject, sourceFile: File): Boolean {
        return loadProjectSlot(context, sourceFile, targetProject)
    }

    fun loadProjectSlot(context: Context, targetProject: AnimationProject, fileName: String): Boolean {
        val file = File(getProjectsDir(context), fileName)
        return loadProjectSlot(context, targetProject, file)
    }

    fun resetToNewBlankProject(
        targetProject: AnimationProject,
        sceneName: String,
        preset: CanvasStagePreset
    ) {
        targetProject.projectName = sceneName
        targetProject.resizeStage(preset.width, preset.height)
        targetProject.stageBackgroundColor = Color.WHITE
        targetProject.clearLoopRange()

        targetProject.layers.clear()
        targetProject.layers.add(AnimationLayer(name = "Color Layer", isReferenceLayer = false))
        targetProject.layers.add(AnimationLayer(name = "Line Art (Ref)", isReferenceLayer = true))

        targetProject.frames.clear()
        targetProject.frames.add(AnimationFrame(layerCount = 2))

        targetProject.selectLayer(1)
        targetProject.selectFrame(0)
    }

    fun startNewBlankProject(
        context: Context,
        targetProject: AnimationProject,
        preset: CanvasStagePreset,
        sceneName: String = "Scene_${(System.currentTimeMillis() % 10000)}"
    ) {
        resetToNewBlankProject(targetProject, sceneName, preset)
        CrashSafeAutosave.writeProjectAtomically(context, targetProject)
    }
}

