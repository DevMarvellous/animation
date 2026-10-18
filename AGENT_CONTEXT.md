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

## 2. Current Project Status & Completed Milestones
- **Current Phase**: Phase 2 & 3 Core Studio Engine Completed → Ready for Phase 4 (Multi-Layer Stack, Smart Gap-Closing Paint Bucket, Lasso/Transform, & Atomic Disk Autosave).
- **GitHub Actions Status**: Verified GREEN (`conclusion: success`) on `https://github.com/DevMarvellous/animation/actions`.
- **What Exists Right Now**:
  - `.github/workflows/build-apk.yml`: Cloud CI/CD building both `Debug` and R8-optimized `Release` APKs on every push to `main`.
  - `core/config/StudioConfig.kt`: Central tuning constants for stage resolution, zoom limits, stabilizer strength, onion skin colors, and undo caps.
  - `core/diagnostics/CrashReporter.kt`: In-App Crash Catcher & Live Diagnostics dialog with one-tap "Copy Report" to clipboard (eliminates need for local Android Studio Logcat).
  - `core/profiler/PerformanceHud.kt`: Zero-allocation real-time FPS, Heap RAM, Frame index, and Zoom % HUD overlay.
  - `model/Stroke.kt`: Primitive `FloatArray`-backed stroke model (zero `Point` object overhead) with cached hardware `Path`.
  - `model/AnimationFrame.kt`: Cel model with per-frame Undo/Redo stacks, dirty cache tracking, and frame duplication.
  - `model/AnimationProject.kt`: Stage container managing frames, FPS (8/12/24/30/60), and onion skin range.
  - `engine/brush/InputStabilizer.kt`: Real-time Lazy-Mouse / Streamline exponential smoothing filter for shaky budget touchscreens.
  - `engine/canvas/ViewportController.kt`: Pre-allocated forward/inverse `Matrix` camera controller for Pan, Pinch-Zoom (15%–3200%), Two-Finger Rotate, and Horizontal Mirror Flip.
  - `engine/onion/OnionSkinRenderer.kt`: Multi-frame tinted Onion Skin renderer (Ruby Red past frames, Cobalt Cyan future frames).
  - `engine/tools/StudioToolType.kt`: Pluggable tool enum (`BRUSH`, `ERASER`, `HAND_PAN`).
  - `engine/AnimationCanvasView.kt`: Full stage engine with 2-finger tap Undo, 3-finger tap Redo, offscreen bitmap frame caching, and real-time playback loop.
  - `ui/workspace/StudioWorkspaceLayout.kt`: Zero-bloat programmatic native Studio UI with Top Action Bar, Left/Right-Handed dockable Tool & Color Bar, Brush/Stabilizer Studio dialog, and Bottom Timeline Scrubber.
  - `MainActivity.kt`: Native `Activity` mounting `StudioWorkspaceLayout` with 90Hz/120Hz display refresh rate negotiation and `CrashReporter`.

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                  # App entry point, lifecycle, memory trim, crash recovery
├── core/
│   ├── config/StudioConfig.kt       # Central tuning constants (max undo, tile sizes, RAM thresholds)
│   ├── diagnostics/CrashReporter.kt # In-app crash catcher & live log viewer with clipboard copy
│   ├── profiler/PerformanceHud.kt   # FPS, frame-time (ms), RAM heap, frame & zoom counter
│   └── storage/CrashSafeAutosave.kt # (Next) Background atomic autosave after every stroke/action
├── model/
│   ├── AnimationProject.kt          # Resolution, FPS (8/12/24/30/60), background color, frame list
│   ├── AnimationFrame.kt            # Frame strokes, undo/redo stacks, exposure hold, dirty flag
│   ├── Layer.kt                     # (Next) VectorLayer, RasterLayer, AudioLayer, CameraLayer
│   └── Stroke.kt                    # Packed FloatArray coordinates (x, y, pressure)
├── engine/
│   ├── AnimationCanvasView.kt       # Hardware stage canvas, touch router, playback engine
│   ├── canvas/ViewportController.kt # Viewport matrix (pan/zoom/rotate/mirror), inverse mapping
│   ├── brush/InputStabilizer.kt     # Real-time Lazy-Mouse / Streamline stroke smoothing
│   ├── tools/StudioToolType.kt      # Pluggable Tool types (Pen, Eraser, Pan, Bucket, Lasso)
│   ├── onion/OnionSkinRenderer.kt   # Multi-frame tinted Onion Skinning (Past Red / Future Cyan)
│   ├── tween/                       # (Planned) Keyframes, Bézier Graph Editor, 2D IK/FK Bones
│   └── memory/                      # (Planned) LruTileCache, RGB_565 flattening, DiskSwapManager
└── ui/
    └── workspace/StudioWorkspaceLayout.kt # Dockable/collapsible studio panels, timeline scrubber, L/R flip
```

---

## 4. Exhaustive Master List of Features (Including Easy-to-Forget Pro Details)

### Category 1: Pro Studio UX & Ergonomics (Often Forgotten!)
1. **Palm Rejection & Stylus-Only Mode**: Ignore accidental palm/wrist touches when using a stylus or drawing with one finger while resting hand.
2. **Multi-Touch Gesture Shortcuts** *(Implemented)*:
   - Two-finger tap = Instant **Undo**.
   - Three-finger tap = Instant **Redo**.
   - Two-finger pinch/drag/twist = Smooth **Zoom, Pan, and Canvas Rotation**.
   - Long-press on canvas = Instant **Eyedropper (Color Picker)** loupe magnifier *(Next)*.
3. **Quick-Access Radial Menu / Floating Puck**: Customizable on-screen shortcut wheel for one-handed thumb access.
4. **Canvas Flip (Horizontal/Vertical Mirror Preview)** *(Implemented)*: Instant `⇄ Flip` button in top bar to check pose symmetry.
5. **Left-Handed & Right-Handed Workspace Flip** *(Implemented)*: Single `✋ L/R` toggle to mirror the tool dock.
6. **Reference Image Pinboard (PureRef-style)**: Floating, resizable reference image window.

### Category 2: Crash-Proofing, Diagnostics & Low-End Survival
7. **Atomic Stroke-by-Stroke Autosave & Crash Recovery**: Journal changes to disk asynchronously.
8. **In-App Debug Console & Diagnostics Overlay** *(Implemented)*: `CrashReporter.kt` catches uncaught exceptions and provides a live `🐞 Logs` viewer with 1-tap clipboard copy.
9. **Adaptive Low-Spec Mode ("Potato Phone Turbo Mode")**: Switches inactive layers to 16-bit color (`RGB_565`) and streams offscreen frames to disk.

### Category 3: Drawing, Inking & Coloring Engine
10. **Pluggable Brush Studio** *(Core Implemented, More Brushes Next)*: Adjustable Brush Size (1–120px), Eraser Size (4–180px), Color Swatches.
11. **Input Stabilizer (Streamline / Lazy-Mouse)** *(Implemented)*: Adjustable 0–90% smoothing slider in `⚙ Brush` dialog.
12. **Velocity & Pressure Simulation**: Simulate pen pressure based on finger speed or touch contact area.
13. **Vector + Raster Hybrid Layers**: Infinite scaling + Vector Eraser / Stroke Cutter.
14. **Smart Fill Bucket**: Gap Closure (1–15px), Reference Layer Fill, and Under-Line Color Trapping.
15. **Symmetry & Perspective Guides**: Mirror Ruler, 1/2/3-Point Perspective grids.
16. **Lasso, Transform & Mesh Warp**: Freehand selection, scale/rotate/skew, and lattice mesh deformation.

### Category 4: Pro Animation Timeline & Cel Workflow
17. **Exposure Holds (1s, 2s, 3s, N-frames)** *(Model Ready, UI Next)*: Hold a drawing across multiple frames without duplicating memory.
18. **Clone / Linked Cels** *(Frame Duplication Implemented)*: `⧉ Clone` button duplicates active cel onto next frame.
19. **Shift and Trace (Virtual Light Table)**: Temporarily move/rotate an onion-skin ghost frame to trace in-betweens.
20. **Flipbook Scrubbing Wheel ("Rolling" the frames)**: Rapidly flip between the last 3 drawings.
21. **Layer Folders, Clipping Masks & Alpha Lock**: Multi-layer stack management.
22. **Blend Modes**: Normal, Multiply, Screen/Add, Overlay.
23. **2D Camera Layer**: Dedicated camera frame with pan, zoom, and rotation keyframes.

### Category 5: Tweening, Rigging & Procedural FX
24. **Keyframe Interpolation & Graph Editor**: Animate layer position, scale, rotation, opacity with Cubic Bézier curves.
25. **2D Skeletal Rigging (FK / IK Bones)**: Bone hierarchies with Inverse Kinematics.
26. **Motion Paths**: Visible trajectory arcs on canvas.
27. **Non-Destructive VFX Modifiers**: Blur, Glow/Bloom, Shadow, Line Boil.

### Category 6: Audio, Lip-Sync & Export Pipeline
28. **Multi-Track Audio & Real-Time Scrubbing**: Import audio, view waveforms, scrub frames.
29. **Built-in Voice Recorder & Phoneme Markers**: Record voiceovers and place mouth chart markers.
30. **Rotoscoping Video Import**: Stream reference video frames from disk.
31. **Hardware-Accelerated Export**: MP4 via Android `MediaCodec`, GIF, Sprite Sheets, and `.pas` project files.

---

## 5. Change Log & Session History
- **2026-10-18 (Session 1)**:
  - Set up Pure Native Kotlin + GitHub Actions CI/CD (Zero local Android Studio/SDK bloat).
  - Verified GitHub Actions cloud APK builds (`conclusion: success`).
  - Created `AGENT_CONTEXT.md` as the permanent brain of the project.
- **2026-10-18 (Session 2)**:
  - Implemented modular package architecture (`core/config`, `core/diagnostics`, `core/profiler`, `model`, `engine/brush`, `engine/canvas`, `engine/onion`, `engine/tools`, `ui/workspace`).
  - Added `CrashReporter.kt` for in-app crash catching and live diagnostics without ADB Logcat.
  - Built `ViewportController.kt` (multi-touch pan, pinch-zoom, canvas rotation, horizontal mirror flip) and `InputStabilizer.kt` (Lazy-Mouse smoothing).
  - Built `OnionSkinRenderer.kt` (Ruby Red past frames, Cobalt Cyan future frames) and `StudioWorkspaceLayout.kt` (Top bar, L/R-handed tool dock, color swatches, Brush/Stabilizer dialog, and interactive Timeline Scrubber with Play/Pause, FPS selector, Add/Clone/Delete frame).

