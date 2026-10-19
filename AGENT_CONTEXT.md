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

### 2. Current Project Status & Total 10-Phase Roadmap
- **Total Phases Planned**: **10 Phases** (Phases 1 through 8 are now **COMPLETED**!).
  - **Phase 1 (Done)**: Zero-bloat Kotlin setup & Cloud APK GitHub Actions pipeline.
  - **Phase 2 (Done)**: Multi-touch 2D Viewport (Pan/Zoom/Rotate/Flip), Streamline Stabilizer, & In-App Crash Catcher.
  - **Phase 3 (Done)**: Timeline Scrubber, FPS Selector (8–60 FPS), & Tinted Past/Future Onion Skinning.
  - **Phase 4 (Done)**: Multi-Layer Compositor (Visibility, Opacity, Alpha Lock, Reference Layer, 5 Blend Modes).
  - **Phase 5 (Done)**: Smart Flood-Fill Bucket (Reference Layer + 1px Color Trapping), Eyedropper, Vertical Symmetry, Exposure Holds (`1s–4s`), & Atomic `.pas` Autosave.
  - **Phase 6 (Done)**: Freehand 2D Lasso Selection & Interactive Move/Scale/Rotate Gizmo + Shift-and-Trace Virtual Light Table.
  - **Phase 7 (Done)**: Zero-Bloat Hardware-Accelerated 1080p H.264 `.mp4` Video Exporter (`MediaCodec`).
  - **Phase 8 (Done)**: 2D Camera Keyframe & Smoothstep Tweening Engine (`🎥 2D Cam`), 2D Vector Intersection Stroke Cutter (`✂ Trim`), & Live UX Comfort Tuner (`🎛 Tune UX`).
  - **Phase 9 (Next)**: Audio Track Import, Waveform Timeline Scrubbing (for 2D Lip-Sync), & Flipbook Quick-Roll Wheel.
  - **Phase 10 (Next)**: Sprite Sheet PNG/JSON Atlas Exporter (for 2D Game Engines) & Floating Reference Image Pinboard.
- **What Exists Right Now**:
  - `.github/workflows/build-apk.yml` & `.vscode/settings.json`: Cloud CI/CD with zero offline editor schema warnings.
  - `core/config/StudioConfig.kt` & `StudioPreferences.kt`: Central tuning constants + live user-tunable UX preferences (Stylus-Only Palm Rejection, Potato Phone Turbo Mode, 2-Finger Tap Undo toggle, 2D Composition Grid).
  - `core/diagnostics/CrashReporter.kt`: In-App Crash Catcher & Live Diagnostics dialog with one-tap "Copy Report" to clipboard.
  - `core/profiler/PerformanceHud.kt`: Zero-allocation real-time FPS, Heap RAM, Frame index, and Zoom % HUD overlay.
  - `core/storage/CrashSafeAutosave.kt`: Atomic binary `.pas` background autosave & crash recovery (`autosave.pas.tmp` -> `autosave.pas`).
  - `model/Stroke.kt`: Primitive `FloatArray`-backed 2D stroke model with bounding box tracking and in-place 2D `Matrix` affine transformation.
  - `model/AnimationLayer.kt`: Multi-layer model (`LayerCel`, `AnimationLayer`, `LayerBlendMode` for Normal, Multiply, Screen, Add, Overlay, plus Reference Layer, Alpha Lock, Opacity, and Visibility).
  - `model/AnimationFrame.kt`: Multi-layer frame holding `LayerCel`s per layer and `exposureHold` (1s, 2s, 3s, 4s timing).
  - `model/AnimationProject.kt`: Stage container managing multi-layer stack, reference layer selection, exposure holds, FPS, and onion skinning.
  - `engine/brush/InputStabilizer.kt`: Real-time Lazy-Mouse / Streamline exponential smoothing filter for shaky budget touchscreens.
  - `engine/camera/Camera2DController.kt`: 2D Camera Keyframe & Smoothstep Cubic Ease-In-Out Tweening engine (Pan, Zoom/Truck-In, Rotate).
  - `engine/canvas/ViewportController.kt`: Pre-allocated forward/inverse `Matrix` camera controller for Pan, Pinch-Zoom (15%–3200%), Two-Finger Rotate, and Horizontal Mirror Flip.
  - `engine/onion/OnionSkinRenderer.kt`: Multi-frame & multi-layer tinted Onion Skin renderer + **Shift-and-Trace (Virtual Light Table)** offset support.
  - `engine/tools/StudioToolType.kt`: Pluggable tool enum (`BRUSH`, `ERASER`, `VECTOR_CUTTER`, `LASSO`, `FILL_BUCKET`, `EYEDROPPER`, `CAMERA_2D`, `SHIFT_TRACE`, `HAND_PAN`).
  - `engine/tools/LassoTransformController.kt`: Freehand 2D Lasso selection polygon & interactive Move/Scale/Rotate bounding box gizmo.
  - `engine/tools/VectorStrokeCutter.kt`: 2D Vector Stroke Intersection Cutter (`✂ Trim`) for instant line-art corner cleanup.
  - `engine/tools/SmartFloodFillEngine.kt`: Zero-object-allocation Scanline Flood Fill engine with Reference Layer boundary detection and 1px under-line color trapping.
  - `export/HardwareVideoExporter.kt`: Zero-bloat 1080p H.264 `.mp4` hardware video exporter using Android's native `MediaCodec` and `MediaMuxer`.
  - `engine/AnimationCanvasView.kt`: Full stage engine integrating multi-layer compositing, 2D Camera Keyframes, Vector Cutter, Lasso Transform, Shift-and-Trace Light Table, Vertical Symmetry Mirror drawing, and Exposure Hold playback loop.
  - `ui/workspace/StudioWorkspaceLayout.kt`: Programmatic native Studio UI with Top Action Bar (`🎥 2D Cam`, `🎛 Tune UX`, `🎬 Export MP4`, `🗂 Layers`, `🦋 Sym`, etc.), Left/Right-Handed Tool Dock (`✏ Pen`, `⌫ Erase`, `✂ Trim`, `⬠ Lasso`, `🪣 Fill`, `💧 Pick`, `🎥 Cam`, `💡 Shift`, `✥ Pan`), and Bottom Timeline Scrubber.
  - `MainActivity.kt`: Native `Activity` mounting `StudioWorkspaceLayout` with 90Hz/120Hz display refresh rate negotiation and `CrashReporter`.

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                        # App entry point, lifecycle, memory trim, crash recovery
├── core/
│   ├── config/StudioConfig.kt             # Central compile-time tuning constants
│   ├── config/StudioPreferences.kt        # Live in-app UX comfort tuner (Palm Rejection, Turbo Mode, Grid)
│   ├── diagnostics/CrashReporter.kt       # In-app crash catcher & live log viewer with clipboard copy
│   ├── profiler/PerformanceHud.kt         # FPS, frame-time (ms), RAM heap, frame & zoom counter
│   └── storage/CrashSafeAutosave.kt       # Background atomic binary .pas autosave & restoration
├── model/
│   ├── AnimationProject.kt                # Multi-layer stack, FPS, background color, frame list
│   ├── AnimationFrame.kt                  # Per-layer LayerCels, exposure hold (1s/2s/3s/4s), dirty flag
│   ├── AnimationLayer.kt                  # LayerCel, AnimationLayer, LayerBlendMode, Reference & Alpha Lock
│   └── Stroke.kt                          # Packed FloatArray coordinates + in-place 2D Matrix transform
├── engine/
│   ├── AnimationCanvasView.kt             # Hardware stage canvas, multi-layer compositor, touch router
│   ├── camera/Camera2DController.kt       # 2D Camera Keyframe Pan/Zoom/Rotate & Smoothstep Tweening
│   ├── canvas/ViewportController.kt       # Viewport matrix (pan/zoom/rotate/mirror), inverse mapping
│   ├── brush/InputStabilizer.kt           # Real-time Lazy-Mouse / Streamline stroke smoothing
│   ├── tools/StudioToolType.kt            # Pluggable Tool types (Pen, Erase, Trim, Lasso, Fill, Pick, Cam, Shift, Pan)
│   ├── tools/LassoTransformController.kt  # Freehand 2D Lasso selection & Move/Scale/Rotate gizmo
│   ├── tools/VectorStrokeCutter.kt        # 2D Vector intersection line cutter (Trim tool)
│   ├── tools/SmartFloodFillEngine.kt      # Scanline Flood Fill with Reference Layer & 1px color trapping
│   └── onion/OnionSkinRenderer.kt         # Multi-frame tinted Onion Skinning + Shift-and-Trace Light Table
├── export/
│   └── HardwareVideoExporter.kt           # Hardware-accelerated 1080p H.264 MP4 exporter (MediaCodec)
└── ui/
    └── workspace/StudioWorkspaceLayout.kt # Layer manager, 2D Cam dialog, UX Tuner, tool dock, timeline
```

---

## 4. Exhaustive Master List of Features (Including Easy-to-Forget Pro Details)

### Category 1: Pro Studio UX & Ergonomics (Often Forgotten!)
1. **Palm Rejection & Stylus-Only Mode** *(Implemented)*: Toggleable inside `🎛 Tune UX` (`StudioPreferences.stylusOnlyPalmRejection`).
2. **Multi-Touch Gesture Shortcuts** *(Implemented)*:
   - Two-finger tap = Instant **Undo** (can be toggled in `🎛 Tune UX`).
   - Three-finger tap = Instant **Redo**.
   - Two-finger pinch/drag/twist = Smooth **Zoom, Pan, and Canvas Rotation**.
   - Canvas **Eyedropper (`💧 Pick`)** *(Implemented)*.
3. **Quick-Access Radial Menu / Floating Puck**: Customizable on-screen shortcut wheel for one-handed thumb access.
4. **Canvas Flip (Horizontal/Vertical Mirror Preview)** *(Implemented)*: Instant `⇄ Flip` button in top bar to check pose symmetry.
5. **Left-Handed & Right-Handed Workspace Flip** *(Implemented)*: Single `✋ L/R` toggle to mirror the tool dock.
6. **Reference Image Pinboard (PureRef-style)**: Floating, resizable reference image window.

### Category 2: Crash-Proofing, Diagnostics & Low-End Survival
7. **Atomic Stroke-by-Stroke Autosave & Crash Recovery** *(Implemented)*: `CrashSafeAutosave.kt` writes binary `.pas.tmp` and renames atomically after every stroke/edit.
8. **In-App Debug Console & Live UX Tuner** *(Implemented)*: `CrashReporter.kt` (`🐞 Logs`) + `StudioPreferences.kt` (`🎛 Tune UX`) let the user fix UX feel issues on the fly and copy full diagnostic + preference snapshots in 1 tap.
9. **Adaptive Low-Spec Mode ("Potato Phone Turbo Mode")** *(Implemented)*: Toggleable inside `🎛 Tune UX` to disable costly anti-aliasing passes for maximum FPS on weak phones.

### Category 3: Drawing, Inking & Coloring Engine
10. **Pluggable Brush Studio** *(Implemented)*: Adjustable Brush Size (1–120px), Eraser Size (4–180px), Color Swatches, Active Color Indicator.
11. **Input Stabilizer (Streamline / Lazy-Mouse)** *(Implemented)*: Adjustable 0–90% smoothing slider in `⚙ Brush` dialog.
12. **Velocity & Pressure Simulation**: Simulate pen pressure based on finger speed or touch contact area.
13. **Vector + Raster Hybrid Layers & Vector Stroke Cutter** *(Implemented)*: Each `LayerCel` combines vector `Stroke` lists with raster `fillBitmap` overlays, plus `VectorStrokeCutter.kt` (`✂ Trim`) to cut intersecting strokes cleanly.
14. **Smart Fill Bucket** *(Implemented)*: `SmartFloodFillEngine.kt` with **Reference Layer Fill** and **1px Under-Line Color Trapping**.
15. **Symmetry & 2D Composition Grid Guides** *(Implemented)*: `🦋 Sym` vertical mirror drawing + 2D Alignment Grid in `🎛 Tune UX`.
16. **Lasso, Transform & Bounding Box Gizmo** *(Implemented)*: `LassoTransformController.kt` (`⬠ Lasso`) for freehand selection, Move, Scale, and Rotate.

### Category 4: Pro Animation Timeline & Cel Workflow
17. **Exposure Holds (1s, 2s, 3s, 4s)** *(Implemented)*: `⏱ 1s` button in timeline cycles frame hold timing (`x2`, `x3`, `x4`) without duplicating memory.
18. **Clone / Linked Cels** *(Implemented)*: `⧉ Clone` button duplicates all layers of active frame onto next frame.
19. **Shift and Trace (Virtual Light Table)** *(Implemented)*: `💡 Shift` tool slides ghost onion skins to align in-between poses; `⛶ Fit` snaps them back to (0,0).
20. **Flipbook Scrubbing Wheel ("Rolling" the frames)**: Rapidly flip between the last 3 drawings.
21. **Multi-Layer Stack, Reference Layer & Alpha Lock** *(Implemented)*: `🗂 Layers` dialog with Add/Delete layer, Visibility (`👁`), Reference Layer (`⭐Ref`), Alpha Lock (`🔒α`), and Opacity slider.
22. **Blend Modes** *(Implemented)*: Normal, Multiply, Screen, Add (Glow), Overlay.
23. **2D Camera Layer & Keyframe Tweening** *(Implemented)*: `Camera2DController.kt` (`🎥 2D Cam` / `🎥 Cam`) with Pan, Truck-In/Out Zoom, and Smoothstep Cubic Easing across frames.

### Category 5: Tweening, Rigging & Procedural FX
24. **Keyframe Interpolation & Graph Editor**: Smoothstep easing implemented for 2D Camera; custom layer tweening next.
25. **2D Cut-Out Skeletal Rigging (FK / IK Bones)**: 2D bone hierarchies with Inverse Kinematics.
26. **Motion Paths**: Visible trajectory arcs on canvas.
27. **Non-Destructive VFX Modifiers**: Blur, Glow/Bloom, Shadow, Line Boil.

### Category 6: Audio, Lip-Sync & Export Pipeline
28. **Multi-Track Audio & Real-Time Scrubbing**: Import audio, view waveforms, scrub frames.
29. **Built-in Voice Recorder & Phoneme Markers**: Record voiceovers and place mouth chart markers.
30. **Rotoscoping Video Import**: Stream reference video frames from disk.
31. **Hardware-Accelerated Export** *(1080p MP4 Implemented)*: `HardwareVideoExporter.kt` (`🎬 Export MP4`) encodes H.264 `.mp4` directly to `Movies/ProAnimationStudio/` via Android `MediaCodec`.

---

## 5. Change Log & Session History
- **2026-10-18 (Session 1 - Phase 1)**:
  - Set up Pure Native Kotlin + GitHub Actions CI/CD (Zero local Android Studio/SDK bloat).
  - Created `AGENT_CONTEXT.md` as the permanent brain of the project.
- **2026-10-18 (Session 2 - Phases 2, 3, 4 & 5)**:
  - Built modular package architecture, `CrashReporter.kt`, `ViewportController.kt`, `InputStabilizer.kt`, `OnionSkinRenderer.kt`, `StudioWorkspaceLayout.kt`, Multi-Layer compositor (`AnimationLayer.kt`), `SmartFloodFillEngine.kt`, Eyedropper, Vertical Symmetry, Exposure Holds, and `CrashSafeAutosave.kt`.
- **2026-10-19 (Session 3 - Phases 6, 7 & 8)**:
  - Built `LassoTransformController.kt` (`⬠ Lasso`), Shift-and-Trace Light Table (`💡 Shift`), and `HardwareVideoExporter.kt` (`🎬 Export MP4`).
  - Fixed false-positive VS Code GitHub Actions extension warning on `actions/checkout@v4` in `.github/workflows/build-apk.yml` + `.vscode/settings.json`.
  - Built `Camera2DController.kt` (`🎥 2D Cam`), `VectorStrokeCutter.kt` (`✂ Trim`), and `StudioPreferences.kt` (`🎛 Tune UX` for live UX comfort tuning, Palm Rejection, Potato Phone Turbo Mode, and 2D Composition Grid).

