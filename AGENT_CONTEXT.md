# AGENT_CONTEXT.md — Persistent Memory & Engineering Bible
> **CRITICAL INSTRUCTION FOR ANY AI AGENT**:
> Read this file FIRST at the start of any conversation or task. Update this file after every completed feature, bug fix, or architectural change so zero context is ever lost between sessions.

---

## 1. Project Identity & Core Philosophy
- **Project Name**: ProAnimation Studio (`com.animationapp`)
- **Repository**: `https://github.com/DevMarvellous/animation.git` (Branch: `main`)
- **Vision**: An industry-grade, Adobe Animate / Toon Boom Harmony / Callipeg / TVPaint-caliber 2D Animation Studio for Android.
- **Non-Negotiable Constraints**:
  1. **Zero Heavy Laptop Bloat**: The user does NOT want Android Studio, local Android SDKs, or emulators installed on their laptop. All APK compilation happens in the cloud via **GitHub Actions** (`.github/workflows/build-apk.yml`).
  2. **Extreme Optimization for the Lowest-End Android Phones**: Must run at 60–120 FPS without lag or Out-Of-Memory (OOM) crashes even on \$70–\$100 budget phones (2GB RAM, weak MediaTek/Unisoc CPUs, low-res touch digitizers).
  3. **Zero Framework Bloat**: Pure Native Kotlin using direct Android OS Views (`android.view.View`, `android.app.Activity`) and Hardware-Accelerated Canvas/OpenGL/Bitmap pipelines—no Jetpack Compose or heavy third-party UI libraries that inflate RAM and APK size.
  4. **Modular, Plug-and-Play Architecture**: Every tool, brush, timeline action, and panel must be strictly decoupled into small, single-responsibility Kotlin files so adding new features or fixing bugs never breaks existing code.

---

### 2. Current Project Status & Total 25-Phase (3-Tier) Master Roadmap
- **Total Phases Planned**: **25 Phases across 3 Tiers** (Phases 1 through 11 are now **COMPLETED**!).

#### Tier 1: Core 2D Studio Engine (Phases 1–10 — ALL COMPLETED!)
  - **Phase 1 (Done)**: Zero-bloat Kotlin setup & Cloud APK GitHub Actions pipeline.
  - **Phase 2 (Done)**: Multi-touch 2D Viewport (Pan/Zoom/Rotate/Flip), Streamline Stabilizer, & In-App Crash Catcher.
  - **Phase 3 (Done)**: Timeline Scrubber, FPS Selector (8–60 FPS), & Tinted Past/Future Onion Skinning.
  - **Phase 4 (Done)**: Multi-Layer Compositor (Visibility, Opacity, Alpha Lock, Reference Layer, 5 Blend Modes).
  - **Phase 5 (Done)**: Smart Flood-Fill Bucket (Reference Layer + 1px Color Trapping), Eyedropper, Vertical Symmetry, Exposure Holds (`1s–4s`), & Atomic `.pas` Autosave.
  - **Phase 6 (Done)**: Freehand 2D Lasso Selection & Interactive Move/Scale/Rotate Gizmo + Shift-and-Trace Virtual Light Table.
  - **Phase 7 (Done)**: Zero-Bloat Hardware-Accelerated 1080p H.264 `.mp4` Video Exporter (`MediaCodec`).
  - **Phase 8 (Done)**: 2D Camera Keyframe & Smoothstep Tweening Engine (`🎥 2D Cam`), 2D Vector Intersection Stroke Cutter (`✂ Trim`), & Live UX Comfort Tuner (`🎛 Tune UX`).
  - **Phase 9 (Done)**: 2D Lip-Sync Phoneme Mouth Chart Markers (`A/I`, `E`, `O`, `U/W`, `M/B/P`, `F/V`, `L/TH`, `🥁 Beat`), Real-Time Formant PCM Audio Scrubbing (`👄 LipSync`), & Flipbook Quick-Roll Scrubber (`📖 Roll`).
  - **Phase 10 (Done)**: Transparent 2D Sprite Sheet PNG + JSON Coordinate Atlas Exporter (`🎬 Export` -> Sprite Sheet) & Floating Draggable 2D Reference Pinboard (`📌 Ref`).

#### Tier 2: Pro Drawing, Coloring & Cel Mastery (Phases 11–18)
  - **Phase 11 (Done)**: 5 Pluggable 2D Brush Presets (`✒ G-Pen`, `✏ Pencil Sketch`, `🖍 Chisel Marker`, `💨 Soft Airbrush`, `👾 Pixel Art 1-Bit`).
  - **Phase 12 (Next)**: Full HSV Color Wheel, Opacity Slider, Hex Input & Custom Saved Color Swatch Palettes.
  - **Phase 13 (Next)**: Geometric 2D Shape Guides (Line, Circle/Ellipse, Rectangle, Arc) & 1/2/3-Point Perspective Vanishing-Point Rulers.
  - **Phase 14 (Next)**: Morphological Gap-Closing Paint Bucket (fills line-art even with 2px–10px artist line gaps).
  - **Phase 15 (Next)**: Timeline Loop Play Ranges (Set In `[` / Out `]` Loop Brackets) & X-Sheet / Exposure Sheet Vertical View.
  - **Phase 16 (Next)**: Linked Cels / Reusable 2D Symbols (edit one master cel and update all linked instances).
  - **Phase 17 (Next)**: 4-Point Perspective Distort & 3x3 Mesh Warp Grid inside the 2D Lasso Tool.
  - **Phase 18 (Next)**: `DiskSwapManager` Virtual Memory Frame Streaming (allows 1,000+ frame movies on 2GB RAM phones by streaming compressed cels to disk).

#### Tier 3: Cut-Out Rigging, Graph Editor, VFX & Pro Studio Exports (Phases 19–25)
  - **Phase 19**: Per-Layer Keyframe Tweening & Custom Bezier Velocity Curve Graph Editor.
  - **Phase 20**: 2D Cut-Out Skeletal Bone Rigging (Parent-Child FK Bones + 2-Bone IK Chains).
  - **Phase 21**: Non-Destructive 2D Layer VFX Modifiers (Procedural Hand-Drawn Line-Boil, Anime Glow/Bloom, Drop Shadow).
  - **Phase 22**: Rotoscoping Reference Video Layer & Voiceover Microphone Recorder.
  - **Phase 23**: Multi-Project Home Gallery, Custom Canvas Resolution Presets (1080p, Square, Vertical 9:16, Pixel Art), & `.pas` Project File Share/Import.
  - **Phase 24**: Animated GIF Exporter & PNG Sequence `.zip` Archive Exporter.
  - **Phase 25**: Floating One-Handed Quick-Access Radial Pie Menu & Custom Gesture Shortcut Mapper.

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                        # App entry point, lifecycle, memory trim, crash recovery
├── audio/
│   └── LipSyncAudioEngine.kt              # Preston Blair 2D mouth phonemes + PCM formant scrub synth
├── core/
│   ├── config/StudioConfig.kt             # Central compile-time tuning constants
│   ├── config/StudioPreferences.kt        # Live in-app UX comfort tuner (Palm Rejection, Turbo Mode, Grid)
│   ├── diagnostics/CrashReporter.kt       # In-app crash catcher & live log viewer with clipboard copy
│   ├── profiler/PerformanceHud.kt         # FPS, frame-time (ms), RAM heap, frame & zoom counter
│   └── storage/CrashSafeAutosave.kt       # Background atomic binary .pas (PAS2) autosave & restoration
├── model/
│   ├── AnimationProject.kt                # Multi-layer stack, FPS, background color, frame list
│   ├── AnimationFrame.kt                  # Per-layer LayerCels, exposure hold (1s/2s/3s/4s), phoneme marker
│   ├── AnimationLayer.kt                  # LayerCel, AnimationLayer, LayerBlendMode, Reference & Alpha Lock
│   └── Stroke.kt                          # Packed FloatArray coordinates + BrushPreset + 2D Matrix transform
├── engine/
│   ├── AnimationCanvasView.kt             # Hardware stage canvas, multi-layer compositor, touch router
│   ├── camera/Camera2DController.kt       # 2D Camera Keyframe Pan/Zoom/Rotate & Smoothstep Tweening
│   ├── canvas/ViewportController.kt       # Viewport matrix (pan/zoom/rotate/mirror), inverse mapping
│   ├── brush/BrushPreset.kt               # 5 2D Brushes (G-Pen, Pencil, Marker, Airbrush, Pixel Art)
│   ├── brush/InputStabilizer.kt           # Real-time Lazy-Mouse / Streamline stroke smoothing
│   ├── tools/StudioToolType.kt            # Pluggable Tool types (Pen, Erase, Trim, Lasso, Fill, Pick, Cam, Shift, Pan)
│   ├── tools/LassoTransformController.kt  # Freehand 2D Lasso selection & Move/Scale/Rotate gizmo
│   ├── tools/VectorStrokeCutter.kt        # 2D Vector intersection line cutter (Trim tool)
│   ├── tools/SmartFloodFillEngine.kt      # Scanline Flood Fill with Reference Layer & 1px color trapping
│   └── onion/OnionSkinRenderer.kt         # Multi-frame tinted Onion Skinning + Shift-and-Trace Light Table
├── export/
│   ├── HardwareVideoExporter.kt           # Hardware-accelerated 1080p H.264 MP4 exporter (MediaCodec)
│   └── SpriteSheetExporter.kt             # Transparent 2D Sprite Sheet PNG + JSON Coordinate Atlas exporter
└── ui/
    ├── reference/ReferencePinboardView.kt # PureRef-style draggable 2D character sheet & mouth chart pinboard
    └── workspace/StudioWorkspaceLayout.kt # Layer manager, 2D Cam, Lip-Sync, Roll wheel, UX Tuner, timeline
```

---

## 4. Change Log & Session History
- **2026-10-18 (Session 1 - Phase 1)**:
  - Set up Pure Native Kotlin + GitHub Actions CI/CD (Zero local Android Studio/SDK bloat).
  - Created `AGENT_CONTEXT.md` as the permanent brain of the project.
- **2026-10-18 (Session 2 - Phases 2, 3, 4 & 5)**:
  - Built modular package architecture, `CrashReporter.kt`, `ViewportController.kt`, `InputStabilizer.kt`, `OnionSkinRenderer.kt`, `StudioWorkspaceLayout.kt`, Multi-Layer compositor (`AnimationLayer.kt`), `SmartFloodFillEngine.kt`, Eyedropper, Vertical Symmetry, Exposure Holds, and `CrashSafeAutosave.kt`.
- **2026-10-19 (Session 3 - Phases 6, 7, 8, 9, 10 & 11)**:
  - Built `LassoTransformController.kt` (`⬠ Lasso`), Shift-and-Trace Light Table (`💡 Shift`), and `HardwareVideoExporter.kt` (`🎬 Export MP4`).
  - Silenced all false-positive offline VS Code YAML/GitHub Actions diagnostics on `.github/workflows/build-apk.yml` via `.vscode/settings.json` (`"files.associations": { "*.yml": "plaintext" }`) and `shell: bash` workflow steps.
  - Built `Camera2DController.kt` (`🎥 2D Cam`), `VectorStrokeCutter.kt` (`✂ Trim`), and `StudioPreferences.kt` (`🎛 Tune UX`).
  - Built **Phase 9, 10 & 11**: `BrushPreset.kt` (5 pro 2D brushes), `LipSyncAudioEngine.kt` (Preston Blair 2D Lip-Sync Phoneme mouth markers + real-time PCM formant audio scrub), Flipbook Quick-Roll Wheel (`📖 Roll`), `SpriteSheetExporter.kt` (transparent PNG + JSON atlas exporter), and `ReferencePinboardView.kt` (`📌 Ref`).


