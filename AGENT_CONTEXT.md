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

### 2. Current Project Status & Completed Milestones
- **Current Phase**: Phases 1 through 7 Completed → Ready for Phase 8 (2D Camera Pan/Zoom Track, Vector Intersection Eraser, & Audio Track Scrubbing).
- **GitHub Actions Status**: Verified GREEN (`conclusion: success`) on `https://github.com/DevMarvellous/animation/actions`.
- **What Exists Right Now**:
  - `.github/workflows/build-apk.yml`: Cloud CI/CD building both `Debug` and R8-optimized `Release` APKs on every push to `main`.
  - `core/config/StudioConfig.kt`: Central tuning constants for stage resolution, zoom limits, stabilizer strength, onion skin colors, and undo caps.
  - `core/diagnostics/CrashReporter.kt`: In-App Crash Catcher & Live Diagnostics dialog with one-tap "Copy Report" to clipboard.
  - `core/profiler/PerformanceHud.kt`: Zero-allocation real-time FPS, Heap RAM, Frame index, and Zoom % HUD overlay.
  - `core/storage/CrashSafeAutosave.kt`: Atomic binary `.pas` background autosave & crash recovery (`autosave.pas.tmp` -> `autosave.pas`).
  - `model/Stroke.kt`: Primitive `FloatArray`-backed 2D stroke model with bounding box tracking and in-place 2D `Matrix` affine transformation (`applyTransform`).
  - `model/AnimationLayer.kt`: Multi-layer model (`LayerCel`, `AnimationLayer`, `LayerBlendMode` for Normal, Multiply, Screen, Add, Overlay, plus Reference Layer, Alpha Lock, Opacity, and Visibility).
  - `model/AnimationFrame.kt`: Multi-layer frame holding `LayerCel`s per layer and `exposureHold` (1s, 2s, 3s, 4s timing).
  - `model/AnimationProject.kt`: Stage container managing multi-layer stack, reference layer selection, exposure holds, FPS, and onion skinning.
  - `engine/brush/InputStabilizer.kt`: Real-time Lazy-Mouse / Streamline exponential smoothing filter for shaky budget touchscreens.
  - `engine/canvas/ViewportController.kt`: Pre-allocated forward/inverse `Matrix` camera controller for Pan, Pinch-Zoom (15%–3200%), Two-Finger Rotate, and Horizontal Mirror Flip.
  - `engine/onion/OnionSkinRenderer.kt`: Multi-frame & multi-layer tinted Onion Skin renderer + **Shift-and-Trace (Virtual Light Table)** offset support.
  - `engine/tools/StudioToolType.kt`: Pluggable tool enum (`BRUSH`, `ERASER`, `LASSO`, `FILL_BUCKET`, `EYEDROPPER`, `SHIFT_TRACE`, `HAND_PAN`).
  - `engine/tools/LassoTransformController.kt`: Freehand 2D Lasso selection polygon & interactive Move/Scale/Rotate bounding box gizmo.
  - `engine/tools/SmartFloodFillEngine.kt`: Zero-object-allocation Scanline Flood Fill engine with Reference Layer boundary detection and 1px under-line color trapping.
  - `export/HardwareVideoExporter.kt`: Zero-bloat 1080p H.264 `.mp4` hardware video exporter using Android's native `MediaCodec` and `MediaMuxer`.
  - `engine/AnimationCanvasView.kt`: Full stage engine integrating multi-layer compositing, Lasso Transform, Shift-and-Trace Light Table, Vertical Symmetry Mirror drawing, and Exposure Hold playback loop.
  - `ui/workspace/StudioWorkspaceLayout.kt`: Programmatic native Studio UI with Top Action Bar (`🎬 Export MP4`, `🗂 Layers`, `🦋 Sym`, etc.), Left/Right-Handed Tool Dock (`✏ Pen`, `⌫ Erase`, `⬠ Lasso`, `🪣 Fill`, `💧 Pick`, `💡 Shift`, `✥ Pan`), and Bottom Timeline Scrubber.
  - `MainActivity.kt`: Native `Activity` mounting `StudioWorkspaceLayout` with 90Hz/120Hz display refresh rate negotiation and `CrashReporter`.

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                        # App entry point, lifecycle, memory trim, crash recovery
├── core/
│   ├── config/StudioConfig.kt             # Central tuning constants (max undo, tile sizes, RAM thresholds)
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
│   ├── canvas/ViewportController.kt       # Viewport matrix (pan/zoom/rotate/mirror), inverse mapping
│   ├── brush/InputStabilizer.kt           # Real-time Lazy-Mouse / Streamline stroke smoothing
│   ├── tools/StudioToolType.kt            # Pluggable Tool types (Pen, Eraser, Lasso, Fill, Pick, Shift, Pan)
│   ├── tools/LassoTransformController.kt  # Freehand 2D Lasso selection & Move/Scale/Rotate gizmo
│   ├── tools/SmartFloodFillEngine.kt      # Scanline Flood Fill with Reference Layer & 1px color trapping
│   └── onion/OnionSkinRenderer.kt         # Multi-frame tinted Onion Skinning + Shift-and-Trace Light Table
├── export/
│   └── HardwareVideoExporter.kt           # Hardware-accelerated 1080p H.264 MP4 exporter (MediaCodec)
└── ui/
    └── workspace/StudioWorkspaceLayout.kt # Layer manager, tool dock, timeline scrubber, MP4 export
```

---

## 4. Exhaustive Master List of Features (Including Easy-to-Forget Pro Details)

### Category 1: Pro Studio UX & Ergonomics (Often Forgotten!)
1. **Palm Rejection & Stylus-Only Mode**: Ignore accidental palm/wrist touches when using a stylus or drawing with one finger while resting hand.
2. **Multi-Touch Gesture Shortcuts** *(Implemented)*:
   - Two-finger tap = Instant **Undo**.
   - Three-finger tap = Instant **Redo**.
   - Two-finger pinch/drag/twist = Smooth **Zoom, Pan, and Canvas Rotation**.
   - Canvas **Eyedropper (`💧 Pick`)** *(Implemented)*.
3. **Quick-Access Radial Menu / Floating Puck**: Customizable on-screen shortcut wheel for one-handed thumb access.
4. **Canvas Flip (Horizontal/Vertical Mirror Preview)** *(Implemented)*: Instant `⇄ Flip` button in top bar to check pose symmetry.
5. **Left-Handed & Right-Handed Workspace Flip** *(Implemented)*: Single `✋ L/R` toggle to mirror the tool dock.
6. **Reference Image Pinboard (PureRef-style)**: Floating, resizable reference image window.

### Category 2: Crash-Proofing, Diagnostics & Low-End Survival
7. **Atomic Stroke-by-Stroke Autosave & Crash Recovery** *(Implemented)*: `CrashSafeAutosave.kt` writes binary `.pas.tmp` and renames atomically after every stroke/edit.
8. **In-App Debug Console & Diagnostics Overlay** *(Implemented)*: `CrashReporter.kt` catches uncaught exceptions and provides a live `🐞 Logs` viewer with 1-tap clipboard copy.
9. **Adaptive Low-Spec Mode ("Potato Phone Turbo Mode")**: Switches inactive layers to 16-bit color (`RGB_565`) and streams offscreen frames to disk.

### Category 3: Drawing, Inking & Coloring Engine
10. **Pluggable Brush Studio** *(Implemented)*: Adjustable Brush Size (1–120px), Eraser Size (4–180px), Color Swatches, Active Color Indicator.
11. **Input Stabilizer (Streamline / Lazy-Mouse)** *(Implemented)*: Adjustable 0–90% smoothing slider in `⚙ Brush` dialog.
12. **Velocity & Pressure Simulation**: Simulate pen pressure based on finger speed or touch contact area.
13. **Vector + Raster Hybrid Layers** *(Implemented)*: Each `LayerCel` combines vector `Stroke` lists with raster `fillBitmap` overlays.
14. **Smart Fill Bucket** *(Implemented)*: `SmartFloodFillEngine.kt` with **Reference Layer Fill** and **1px Under-Line Color Trapping**.
15. **Symmetry & Perspective Guides** *(Vertical Mirror Symmetry Implemented)*: `🦋 Sym` toggle for real-time symmetrical character drawing.
16. **Lasso, Transform & Bounding Box Gizmo** *(Implemented)*: `LassoTransformController.kt` (`⬠ Lasso`) for freehand selection, Move, Scale, and Rotate.

### Category 4: Pro Animation Timeline & Cel Workflow
17. **Exposure Holds (1s, 2s, 3s, 4s)** *(Implemented)*: `⏱ 1s` button in timeline cycles frame hold timing (`x2`, `x3`, `x4`) without duplicating memory.
18. **Clone / Linked Cels** *(Implemented)*: `⧉ Clone` button duplicates all layers of active frame onto next frame.
19. **Shift and Trace (Virtual Light Table)** *(Implemented)*: `💡 Shift` tool slides ghost onion skins to align in-between poses; `⛶ Fit` snaps them back to (0,0).
20. **Flipbook Scrubbing Wheel ("Rolling" the frames)**: Rapidly flip between the last 3 drawings.
21. **Multi-Layer Stack, Reference Layer & Alpha Lock** *(Implemented)*: `🗂 Layers` dialog with Add/Delete layer, Visibility (`👁`), Reference Layer (`⭐Ref`), Alpha Lock (`🔒α`), and Opacity slider.
22. **Blend Modes** *(Implemented)*: Normal, Multiply, Screen, Add (Glow), Overlay.
23. **2D Camera Layer**: Dedicated 2D camera frame with pan, zoom, and rotation keyframes.

### Category 5: Tweening, Rigging & Procedural FX
24. **Keyframe Interpolation & Graph Editor**: Animate 2D layer position, scale, rotation, opacity with Cubic Bézier curves.
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
- **2026-10-18 (Session 1)**:
  - Set up Pure Native Kotlin + GitHub Actions CI/CD (Zero local Android Studio/SDK bloat).
  - Verified GitHub Actions cloud APK builds (`conclusion: success`).
  - Created `AGENT_CONTEXT.md` as the permanent brain of the project.
- **2026-10-18 (Session 2 - Phases 2, 3, 4 & 5)**:
  - Implemented modular package architecture, `CrashReporter.kt`, `ViewportController.kt`, `InputStabilizer.kt`, `OnionSkinRenderer.kt`, and `StudioWorkspaceLayout.kt`.
  - Built Multi-Layer compositing engine (`AnimationLayer.kt`, `LayerCel`, `LayerBlendMode`), `SmartFloodFillEngine.kt` (Reference Layer + 1px under-line color trapping), Eyedropper, Vertical Symmetry, Exposure Holds (`1s/2s/3s/4s`), and `CrashSafeAutosave.kt`.
- **2026-10-19 (Session 3 - Phases 6 & 7)**:
  - Built `LassoTransformController.kt` (`⬠ Lasso`) for freehand 2D stroke selection and interactive Move, Scale, and Rotate bounding box gizmo.
  - Added **Shift-and-Trace Virtual Light Table** (`💡 Shift`) to `OnionSkinRenderer.kt` so animators can slide ghost onion skins to trace in-betweens and snap back via `⛶ Fit`.
  - Built `HardwareVideoExporter.kt` (`🎬 Export MP4`) using Android's native `MediaCodec` + `MediaMuxer` for zero-bloat 1080p H.264 `.mp4` video export to Gallery.

