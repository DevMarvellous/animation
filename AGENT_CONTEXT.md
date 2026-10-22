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
- **Total Phases Planned**: **25 Phases across 3 Tiers** (ALL 25 PHASES — 100% — ARE NOW **COMPLETED**!).

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

#### Tier 2: Pro Drawing, Coloring & Cel Mastery (Phases 11–18 — ALL COMPLETED!)
  - **Phase 11 (Done)**: 5 Pluggable 2D Brush Presets (`✒ G-Pen`, `✏ Pencil Sketch`, `🖍 Chisel Marker`, `💨 Soft Airbrush`, `👾 Pixel Art 1-Bit`).
  - **Phase 12 (Done)**: Full HSV Color Wheel (`🎨 Color`), Brightness & Opacity Sliders, Hex `#RRGGBB` Input, & Custom Saved Character Swatch Palettes (`ColorPaletteStudio.kt`).
  - **Phase 13 (Done)**: Geometric 2D Shape Guides (`📏 Straight Line`, `▭ Rectangle`, `◯ Circle/Ellipse`, `📐 Perspective Vanishing Ray`) & Interactive 1/2-Point Perspective Horizon + Draggable `VP1` / `VP2` Rulers (`ShapeAndPerspectiveController.kt`).
  - **Phase 14 (Done)**: Morphological Gap-Closing Paint Bucket (`SmartFloodFillEngine.kt` with separable 1D dilation + margin reclaim so 2px–16px open line-art gaps are sealed automatically).
  - **Phase 15 (Done)**: Timeline Loop Play Ranges (`⟦ Set In` / `Set Out ⟧` Loop Brackets via `🔁 Loop`) & Vertical Exposure Sheet (X-Sheet) Table Inspector.
  - **Phase 16 (Done)**: Linked Cels / Reusable 2D Symbols (`🔗 Link` inside `⧉ Clone` menu — shares a master `LayerCel` across frames with 0 extra RAM, updating all linked frames live when edited, plus `🔓 Unlink`).
  - **Phase 17 (Done)**: 4-Point Perspective Corner-Pin & 3x3 Bilinear Mesh Cage Deform inside the 2D Lasso Tool (`LassoTransformController.kt` & `Stroke.applyCornerPinDeform`).
  - **Phase 18 (Done)**: `DiskSwapManager.kt` Virtual Memory Frame Streaming (maintains a hot window around `currentFrameIndex` and streams cold `fillBitmap`s to `cacheDir/cel_swap/` to prevent Out-Of-Memory crashes on 2GB RAM phones).

#### Tier 3: Cut-Out Rigging, Graph Editor, VFX & Pro Studio Exports (Phases 19–25 — ALL COMPLETED!)
  - **Phase 19 (Done)**: Per-Layer Keyframe Tweening (`📈 Tween`), 5 Velocity Easing Curves (`Linear`, `Smooth Ease In/Out`, `Snappy Ease-Out Anime`, `Heavy Ease-In`, `Cartoon Bounce Out`), & Stage Motion Path Trajectory Arc (`LayerTweenEngine.kt`).
  - **Phase 20 (Done)**: 2D Cut-Out Skeletal Bone Rigging (`🦴 Rig` — 10-Bone Humanoid Rig with Parent-Child Forward Kinematics + 2-Bone Law-of-Cosines Analytic Inverse Kinematics & Live Layer Stroke Deformation in `Skeleton2DController.kt`).
  - **Phase 21 (Done)**: Non-Destructive 2D Layer VFX Modifiers (`✨ VFX` in Layer Manager — Procedural Hand-Drawn Line-Boil Shimmer, Anime Glow/Bloom, and 2D Cel Drop Shadow in `LayerVfxEngine.kt`, synced with all Exporters).
  - **Phase 22 (Done)**: Voiceover Waveform Envelope & 1-Tap Auto-Lip-Sync Mouth Generator (`VoiceoverWaveformEngine.kt` — generates vocal syllable waveforms `▁▂▃▅▇` and maps amplitude peaks to Preston Blair 2D mouth phonemes across all frames).
  - **Phase 23 (Done)**: Multi-Project Slot Manager (`📁 Scene`), Custom 2D Stage Aspect Ratio Presets (`16:9 Widescreen`, `9:16 Vertical Shorts/TikTok`, `1:1 Square`, `4:3 Retro`), & 5 Stage Paper Color Presets (`MultiProjectManager.kt`).
  - **Phase 24 (Done)**: Zero-Bloat Pure-Kotlin Animated `.GIF` Loop Exporter (`GIF89a` LZW encoder) & PNG Frame Sequence `.ZIP` Archive Exporter (`GifAndZipSequenceExporter.kt`), 100% synced with Layer Tweens, Exposure Holds, Disk Swap, and 2D Layer VFX.
  - **Phase 25 (Done)**: Floating One-Handed Quick-Access 8-Slice Radial Pie Menu (`🎯 Pie` in `RadialPieMenuView.kt`).

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                        # App entry point, lifecycle, memory trim, crash recovery
├── audio/
│   ├── LipSyncAudioEngine.kt              # Preston Blair 2D mouth phonemes + PCM formant scrub synth
│   └── VoiceoverWaveformEngine.kt         # Vocal syllable waveform bars + 1-tap Auto-Lip-Sync generator
├── core/
│   ├── config/StudioConfig.kt             # Central compile-time tuning constants
│   ├── config/StudioPreferences.kt        # Live in-app UX comfort tuner (Palm Rejection, Turbo Mode, Grid)
│   ├── diagnostics/CrashReporter.kt       # In-app crash catcher & live log viewer with clipboard copy
│   ├── profiler/PerformanceHud.kt         # FPS, frame-time (ms), RAM heap, frame & zoom counter
│   ├── storage/CrashSafeAutosave.kt       # Background atomic binary .pas (PAS3) autosave & restoration
│   ├── storage/DiskSwapManager.kt         # Virtual Memory cold-cel disk swapper for 2GB RAM phones
│   └── storage/MultiProjectManager.kt     # Multi-slot .pas scene manager, 16:9/9:16/1:1/4:3 presets & paper colors
├── model/
│   ├── AnimationProject.kt                # Multi-layer stack, stage aspect ratio, FPS, paper color, frame list
│   ├── AnimationFrame.kt                  # Per-layer LayerCels, exposure hold (1s/2s/3s/4s), phoneme & waveform
│   ├── AnimationLayer.kt                  # LayerCel, AnimationLayer, LayerBlendMode, Reference, Tween & VFX
│   └── Stroke.kt                          # Packed FloatArray coordinates + BrushPreset + 2D Matrix & Cage Warp
├── engine/
│   ├── AnimationCanvasView.kt             # Hardware stage canvas, multi-layer compositor, touch router
│   ├── camera/Camera2DController.kt       # 2D Camera Keyframe Pan/Zoom/Rotate & Smoothstep Tweening
│   ├── canvas/ViewportController.kt       # Viewport matrix (pan/zoom/rotate/mirror), inverse mapping
│   ├── brush/BrushPreset.kt               # 5 2D Brushes (G-Pen, Pencil, Marker, Airbrush, Pixel Art)
│   ├── brush/InputStabilizer.kt           # Real-time Lazy-Mouse / Streamline stroke smoothing
│   ├── rigging/Skeleton2DController.kt    # 2D Cut-Out Skeletal Bone Rigging (FK + 2-Bone Law-of-Cosines IK)
│   ├── tween/LayerTweenEngine.kt          # Per-Layer Keyframe Tweening, 5 Easing Curves & Motion Path Arc
│   ├── vfx/LayerVfxEngine.kt              # Non-Destructive 2D VFX (Hand-Drawn Line-Boil, Anime Glow, Drop Shadow)
│   ├── tools/StudioToolType.kt            # Pluggable Tool types (Pen, Erase, Shapes, Trim, Lasso, Rig, Tween, Fill, Pick, Cam, Shift, Pan)
│   ├── tools/ShapeAndPerspectiveController.kt # 2D Geometric Shapes (Line, Rect, Circle) + 1/2-Point Perspective Rulers
│   ├── tools/LassoTransformController.kt  # Freehand 2D Lasso selection, Move/Scale/Rotate & 4-Point Mesh Cage Deform
│   ├── tools/VectorStrokeCutter.kt        # 2D Vector intersection line cutter (Trim tool)
│   ├── tools/SmartFloodFillEngine.kt      # Scanline Flood Fill + Ref Layer + 1px trap + Morphological Gap-Closing
│   └── onion/OnionSkinRenderer.kt         # Multi-frame tinted Onion Skinning + Shift-and-Trace Light Table
├── export/
│   ├── GifAndZipSequenceExporter.kt       # Pure-Kotlin Animated GIF89a (LZW) & PNG Sequence .ZIP archive exporter
│   ├── HardwareVideoExporter.kt           # Hardware-accelerated 1080p H.264 MP4 exporter (MediaCodec)
│   └── SpriteSheetExporter.kt             # Transparent 2D Sprite Sheet PNG + JSON Coordinate Atlas exporter
└── ui/
    ├── color/ColorPaletteStudio.kt        # Pre-rendered 2D HSV Color Wheel, Opacity, Hex & Custom Swatches
    ├── radial/RadialPieMenuView.kt        # One-handed floating 8-slice quick-access Radial Pie Menu (`🎯 Pie`)
    ├── reference/ReferencePinboardView.kt # PureRef-style draggable 2D character sheet & mouth chart pinboard
    └── workspace/StudioWorkspaceLayout.kt # Scene/Aspect studio, Layer manager, 2D Cam, Lip-Sync, Loop/X-Sheet, Roll wheel
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
- **2026-10-21 (Session 4 - Phases 12 through 18 — Tier 2 Completed!)**:
  - Built **Phase 12 (`ColorPaletteStudio.kt`)**: Pre-rendered 2D HSV Color Wheel (`🎨 Color`), Brightness & Opacity sliders, Hex `#RRGGBB` input, and persistent Custom Saved Character Swatches.
  - Built **Phase 13 (`ShapeAndPerspectiveController.kt`)**: 2D Geometric Shape Tool (`📏 Straight Line`, `▭ Rectangle`, `◯ Circle/Ellipse`, `📐 Perspective Vanishing Ray`) + Interactive 2-Point Perspective Horizon & Draggable `VP1` / `VP2` Rulers.
  - Built **Phase 14 (`SmartFloodFillEngine.kt`)**: Morphological Gap-Closing Paint Bucket (separable 1D horizontal/vertical dilation + margin reclaim so 2px–16px open line-art gaps are sealed automatically).
  - Built **Phase 15 (`AnimationProject.kt` + `StudioWorkspaceLayout.kt`)**: Timeline Loop Play Ranges (`⟦ Set In` / `Set Out ⟧` via `🔁 Loop`) & Vertical Exposure Sheet (X-Sheet) Table Inspector.
  - Built **Phase 16 (`AnimationLayer.kt` + `AnimationFrame.kt` + `AnimationProject.kt`)**: Linked Cels / Reusable 2D Symbols (`🔗 Link` inside `⧉ Clone` menu — shares a master `LayerCel` across frames with 0 extra RAM, updating all linked frames live when edited, plus `🔓 Unlink`).
  - Built **Phase 17 (`LassoTransformController.kt` + `Stroke.kt`)**: 4-Point Perspective Corner-Pin & 3x3 Bilinear Mesh Cage Deform inside the 2D Lasso Tool (`applyCornerPinDeform`).
  - Built **Phase 18 (`DiskSwapManager.kt`)**: Virtual Memory Frame Streaming (maintains a hot window around `currentFrameIndex` and streams cold `fillBitmap`s to `cacheDir/cel_swap/` to prevent Out-Of-Memory crashes on 2GB RAM phones).
- **2026-10-21 (Session 5 - Phases 19 through 25 & All-Green CI/CD Verification)**:
  - Built **Phase 19 (`LayerTweenEngine.kt`)**: Per-Layer Keyframe Tweening (`📈 Tween`), 5 Velocity Easing Curves (`Linear`, `Smooth Ease In/Out`, `Snappy Ease-Out Anime`, `Heavy Ease-In`, `Cartoon Bounce Out`), and Stage Motion Path Trajectory Arcs, synced across live stage and all exporters.
  - Built **Phase 20 (`Skeleton2DController.kt`)**: 2D Cut-Out Skeletal Bone Rigging (`🦴 Rig`) with a 10-Bone Humanoid Rig, Parent-Child Forward Kinematics (FK), 2-Bone Law-of-Cosines Analytic Inverse Kinematics (IK), and live layer stroke deformation.
  - Built **Phase 21 (`LayerVfxEngine.kt`)**: Non-Destructive 2D Layer VFX Modifiers (`✨ VFX` in Layer Manager) — Procedural Hand-Drawn Line-Boil Shimmer, Anime Glow/Bloom, and 2D Cel Drop Shadow, synced with all exporters.
  - Built **Phase 22 (`VoiceoverWaveformEngine.kt`)**: Voiceover Waveform Envelope (`▁▂▃▅▇`) & 1-Tap Auto-Lip-Sync Mouth Generator across all frames.
  - Built **Phase 23 (`MultiProjectManager.kt` + `CrashSafeAutosave.kt` PAS3)**: Multi-Project `.pas` Slot Manager (`📁 Scene`), Custom 2D Stage Aspect Ratio Presets (`16:9 Widescreen 1280×720`, `9:16 Vertical Shorts/TikTok 720×1280`, `1:1 Square 960×960`, `4:3 Retro 640×480`), and 5 Stage Paper Color Presets.
  - Built **Phase 24 (`GifAndZipSequenceExporter.kt`)**: Zero-Bloat Pure-Kotlin Animated `.GIF` Loop Exporter (`GIF89a` with 256-color 3-3-2 RGB Global Palette, Netscape 2.0 loop extension, and Clear-Code LZW encoder) and PNG Frame Sequence `.ZIP` Archive Exporter (with `project_manifest.json`).
  - Built **Phase 25 (`RadialPieMenuView.kt`)**: Floating One-Handed Quick-Access 8-Slice Radial Pie Menu (`🎯 Pie` for instant thumb-swipe access to `✏ Pen`, `⌫ Erase`, `🪣 Fill`, `⬠ Lasso`, `↶ Undo`, `↷ Redo`, `▶ Play`, and `＋ Frame`).
  - **CI/CD Cloud APK Verification (Run 39602777898 - Commit `c0b2e02`)**:
    - Fixed final Kotlin compile check in `StudioWorkspaceLayout.kt` (resolved `saveProjectSlot` return type check from `Boolean` to `File.exists()`).
    - Successfully compiled both `ProAnimationStudio-Release-APK` (99 KB R8-optimized) and `ProAnimationStudio-Debug-APK` (1009 KB) in GitHub Actions with 100% clean green passing status!
- **2026-10-21 (Session 6 - Professional Studio Upgrades & Verified Green Build `39608689371`)**:
  - **8 Pro 2D Brushes (`BrushPreset.kt`)**: Added `CALLIGRAPHY_RIBBON` (🖋), `CHARCOAL_CHALK` (🪵), and `NEON_LASER` (⚡) with zero-allocation hardware shaders.
  - **8 Blend Modes (`LayerBlendMode.kt`)**: Expanded with `DARKEN`, `LIGHTEN`, and `XOR` alongside `NORMAL`, `MULTIPLY`, `SCREEN`, `ADD`, and `OVERLAY`.
  - **Frame Tagging & Color Markers (`AnimationFrame.kt` + `StudioWorkspaceLayout.kt`)**: Color-coded keyframe classification tags (`🔴 Keyframe`, `🟡 Breakdown`, `🟢 Inbetween`, `🔵 Action`) with visible colored pips on timeline thumbnails and 1-tap long-press picker.
  - **Timeline Frame Reordering (`AnimationProject.kt`)**: Added `moveCurrentFrameLeft()`, `moveCurrentFrameRight()`, and `reverseAllFrames()` for ping-pong loops and timeline re-timing.
  - **Ping-Pong Loop Playback (`AnimationCanvasView.kt` + `StudioPreferences.kt`)**: Smooth forward-backward oscillation for breathing, idle, and lip-sync cycles without pop jumps.
  - **Radial Pie Menu Auto-Z-Index (`RadialPieMenuView.kt`)**: Added automatic `bringToFront()` on expansion so the floating thumb puck and radial pie slices are never obscured.
  - **PAS4 Atomic Autosave (`CrashSafeAutosave.kt`)**: 100% backward and forward compatible with PAS3 and PAS4 formats.
  - **Cloud Build Verification**: GitHub Actions build run `39608689371` completed 100% **SUCCESS** with `ProAnimationStudio-Release-APK` (102 KB) and `ProAnimationStudio-Debug-APK` (1014 KB).
- **2026-10-21 (Session 7 - Native Branding, Cutout Notch Edge-to-Edge, & Safe Exit Polish)**:
  - **Custom Vector Launcher Icons (`ic_launcher.xml` & `ic_launcher_round.xml`)**: Midnight studio canvas `#151824`, cyan halo `#00D2FF`, onion-skin trajectory dots, pro G-pen nib, and gold animation star sparkle `#FFD700`.
  - **Manifest Branding & Permissions (`AndroidManifest.xml`)**: Added `android:icon="@drawable/ic_launcher"`, `android:roundIcon="@drawable/ic_launcher_round"`, and scoped storage maxSdkVersion="28" permissions for legacy Android 8/9 devices.
  - **Display Cutout Notch Support (`MainActivity.kt`)**: `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` for true edge-to-edge drawing past camera punch-holes and notches.
  - **Screen Keep-Awake (`MainActivity.kt`)**: `FLAG_KEEP_SCREEN_ON` so screen never dims or locks during drawing or animating sessions.
  - **Lifecycle Safe Exit & Immediate Autosave (`MainActivity.kt`)**: Immediate atomic autosave in `onPause()` and safe exit confirmation dialog in `onBackPressed()`.
  - **Verified Clean Cloud Build (`39615474065` - Commit `18b609e`)**: Completed 100% **SUCCESS** with `ProAnimationStudio-Release-APK` (102 KB, ID: `12350974604`) and `ProAnimationStudio-Debug-APK` (1,000 KB, ID: `12351279439`).
- **2026-10-21 (Session 8 - Phase 26 Expansion Trio & Usability Polish)**:
  - **Linear & Radial 2D Gradient Tool (`GradientToolController.kt`)**: Hardware GPU shader tool (`🌈 Grad`) supporting 2-point angle linear and center-radius radial gradients. Supports Lasso polygon boundary clipping, Alpha-Lock cel shading (`SRC_ATOP`), and bakes directly into `LayerCel.fillBitmap` with zero heap bloat and full exporter sync.
  - **Real External Audio Import & Dialogue Sync (`ExternalAudioImportEngine.kt`)**: Uses Android's native `MediaExtractor` and `MediaCodec` to decode `.mp3`, `.wav`, `.m4a`, `.aac` into a downsampled acoustic amplitude envelope (~800 bytes RAM for 200 frames). Plugs directly into `AnimationFrame.audioAmplitude` so timeline pills display true voice waveforms, auto-assigns Preston Blair mouth phonemes, and synchronizes real-time `MediaPlayer` audio playback and frame scrubbing.
  - **2D Cartoon Text & Onomatopoeia Tool (`TextToolController.kt`)**: Converts text ("POW!", "BOOM!", dialogue subtitles, titles) into first-class vector `Stroke`s via `Paint.getTextPath` and dense `PathMeasure` contour sampling. Fully compatible with Lasso transforms, 4-Point Perspective Corner-Pin, and 3x3 Mesh Cage deform.
  - **Feature Detoggling & Timeline Polish (`StudioWorkspaceLayout.kt` + `AnimationCanvasView.kt`)**:
    - **Symmetry Detoggle**: Visible `🦋 Sym: ON` / `🦋 Sym: OFF` state indicator with instant Toast confirmation.
    - **Loop Range Detoggle**: 1-tap `🚫 Detoggle Loop` in dialog and quick-prompt on `🔁 [1-8]` button to immediately revert to playing all frames.
    - **First/Last Frame Fast Jump**: Long-press `⏮` jumps straight to Frame 1 (Start); long-press `⏭` jumps straight to Last Frame (End).
    - **Exposure Hold Reset**: Long-press `⏱` immediately resets hold back to 1s.
    - **Cel Clearing Without Breaking Timing**: Added `clearActiveCel()` and `clearAllCelsOnCurrentFrame()` in `AnimationCanvasView.kt` and wired into long-press `🗑 Del` and `⧉ Clone` menu, letting animators blank out drawings without shifting downstream frames.
    - **1-Tap Lasso Deselect**: Tapping `⬠ Lasso` while active deselects the selection without having to switch tools.
    - **Light Table Reset**: Tapping `💡 Shift` while active immediately resets light-table offset back to center.
  - **Verified Clean Cloud Build (`39688178134` - Commit `43b123f`)**: Completed 100% **SUCCESS** with `ProAnimationStudio-Release-APK` (116 KB, ID: `12387560834`) and `ProAnimationStudio-Debug-APK` (1.04 MB, ID: `12387600772`).
- **2026-10-22 (Session 9 - Comprehensive Feature Detoggling & Time Frames Studio Engine)**:
  - **1-Tap Tool Detoggling Across All Utilities**: Tapping any active utility tool (`⌫ Erase`, `📐 Shapes`, `✂ Trim`, `⬠ Lasso`, `🦴 Rig`, `📈 Tween`, `🪣 Fill`, `🌈 Grad`, `🔤 Text`, `💧 Pick`, `🎥 Cam`, `💡 Shift`, `✥ Pan`) immediately detoggles it and returns to `✏ Pen`, preventing accidental lock-in.
  - **Dedicated Time Frames & Timing Studio Dialog (`showTimeFramesStudioDialog()`)**:
    - Complete inspection and control of timeline time: displays current frame / total frames, elapsed seconds, total duration seconds, FPS, and loop status.
    - 1-tap batch exposure holds: `Animate on 1s (Fluid 24 FPS)`, `Animate on 2s (Classic 12 FPS Anime / Disney)`, `Animate on 3s (Snappy 8 FPS)`.
    - Time frame insertion: Insert blank frame before current, after current, +5 blank frames, +10 blank frames.
    - Timeline sliding: Slide frame earlier (left), slide later (right), reverse entire animation order.
    - Loop brackets: 1-tap `Set In` / `Set Out` and 1-tap loop detoggle.
    - Cel clearing without shifting timeline timing: active cel or all cels on current frame.
  - **Dynamic Time Frame Badge (`timeFrameBadge`)**: Placed on timeline bar next to loop and hold controls. Displays live `⏱ CurrentFrame/TotalCount [LoopTag] (CurTime_s / TotalTime_s)`. Tapping opens the Time Frames Studio.
  - **Top Bar Quick Detoggling & Shortcuts**:
    - Long-press `🎥 2D Cam`: 1-tap toggles Camera Viewfinder Preview lens mode vs Raw Stage (`[LENS]` badge).
    - Long-press `Onion: ON/OFF`: Quick dialog to detoggle onion skin or reset light table shift.
    - Long-press `⛶ Fit`: 1-tap toggles background Grid overlay.
    - Reference Layer Detoggle: Tapping `⭐Ref` on an active reference layer detoggles it back to normal drawing layer.
    - Non-Destructive 2D VFX Detoggle: Added `🚫 Detoggle / Turn Off All VFX on Layer` button in `showLayerVfxDialog()`.
    - Exit buttons in `Shapes`, `Rig`, `Tween`, and `Gradient` dialogs to detoggle directly back to `✏ Pen`.
  - **Long-Press Timeline Shortcuts**:
    - Long-press `⏱ 1s`: Quick menu to set current frame to 1s/2s, or set ALL frames to 2s/1s.
    - Long-press `🔁 Loop`: Instantly detoggles loop range back to playing all frames without opening a dialog.
    - Long-press `＋ Frame`: Quick menu to insert blank frame before current, after current, or insert 5/10 blank frames.
  - **Verified Clean Cloud Build (`39731817256` - Commit `1e7f04c`)**: Completed 100% **SUCCESS** with `ProAnimationStudio-Release-APK` (119 KB, ID: `12405781038`) and `ProAnimationStudio-Debug-APK` (1.05 MB, ID: `12405805813`).




