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
- **Current Phase**: Phase 1 Completed → Moving to Phase 2 (Modular Engine Architecture + Canvas Navigation + Core Tool System).
- **What Exists Right Now**:
  - `.github/workflows/build-apk.yml`: Cloud CI/CD building both `Debug` and R8-optimized `Release` APKs on every push to `main`.
  - `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`: Configured with AGP `8.7.3`, Kotlin `2.1.0`, Java 17, and `android.enableR8.fullMode=true`.
  - `app/build.gradle.kts` & `app/proguard-rules.pro`: Zero external library bloat, aggressive 5-pass R8 code shrinking, stripped Kotlin runtime assertions.
  - `app/src/main/AndroidManifest.xml`: Configured with `hardwareAccelerated="true"`, `largeHeap="true"`, and landscape immersive studio layout.
  - `app/src/main/java/com/animationapp/MainActivity.kt`: Native `Activity` with 90Hz/120Hz display refresh rate negotiation, sticky immersive fullscreen, and `onTrimMemory` handling.
  - `app/src/main/java/com/animationapp/engine/AnimationCanvasView.kt`: Initial hardware-accelerated drawing view with zero-allocation `onDraw`, historical touch smoothing (`getHistoricalX/Y`), offscreen `Bitmap` caching, and a real-time **FPS / RAM (MB) / Stroke Count HUD**.

---

## 3. Modular Codebase Architecture (For Easy Editing & Zero Regression)
To ensure we can add 100+ pro features without spaghetti code, all code under `app/src/main/java/com/animationapp/` must follow this strict package structure:

```text
com.animationapp/
├── MainActivity.kt                  # App entry point, lifecycle, memory trim, crash recovery
├── core/
│   ├── config/StudioConfig.kt       # Central tuning constants (max undo, tile sizes, RAM thresholds)
│   ├── profiler/PerformanceHud.kt   # FPS, frame-time (ms), RAM heap, native heap, thermal state
│   ├── command/CommandManager.kt    # Delta-based Undo/Redo command pattern (memory-capped)
│   └── storage/CrashSafeAutosave.kt # Background atomic autosave after every stroke/action
├── model/
│   ├── Project.kt                   # Resolution, FPS (12/24/30/60), background color, scenes
│   ├── Layer.kt                     # VectorLayer, RasterLayer, AudioLayer, CameraLayer, FolderGroup
│   ├── Frame.kt                     # Exposure holds (1s, 2s, 3s), cel links, disk cache key
│   └── Stroke.kt                    # Packed FloatArray coordinates (x, y, pressure, tilt, timestamp)
├── engine/
│   ├── canvas/                      # Viewport matrix (pan/zoom/rotate), tile cache, dirty-rect renderer
│   ├── brush/                       # Pluggable Brush interface, Stabilizer (Lazy-Mouse), Pressure Curve
│   ├── tools/                       # Pluggable Tool interface (Pen, Eraser, Bucket, Lasso, Mesh, Bone)
│   ├── onion/                       # Multi-frame tinted Onion Skinning & Shift-and-Trace (Light Table)
│   ├── tween/                       # Keyframes, Bézier Graph Editor, 2D IK/FK Bones, Camera paths
│   └── memory/                      # LruTileCache, RGB_565 flattening, DiskSwapManager (WebP/LZ4)
├── audio/
│   ├── AudioTrackEngine.kt          # Multi-track audio decoding, low-latency frame scrubbing, waveforms
├── export/
│   ├── HardwareVideoExporter.kt     # Android MediaCodec hardware MP4/WebM encoder (zero RAM spike)
│   └── ProjectSerializer.kt         # Custom binary/ZIP `.pas` project format + PSD/SVG/PNG export
└── ui/
    ├── workspace/                   # Dockable/collapsible studio panels, left-handed flip mode
    ├── timeline/                    # Virtualized timeline scrubber, exposure sheet (X-Sheet), layer controls
    └── dialogs/                     # Color wheel, brush studio, export settings, on-device diagnostics
```

---

## 4. Exhaustive Master List of Features (Including Easy-to-Forget Pro Details)

### Category 1: Pro Studio UX & Ergonomics (Often Forgotten!)
1. **Palm Rejection & Stylus-Only Mode**: Ignore accidental palm/wrist touches when using a stylus or drawing with one finger while resting hand.
2. **Multi-Touch Gesture Shortcuts**:
   - Two-finger tap = Instant **Undo**.
   - Three-finger tap = Instant **Redo**.
   - Two-finger pinch/drag/twist = Smooth **Zoom, Pan, and Canvas Rotation** (with quick snap back to 0°).
   - Long-press on canvas = Instant **Eyedropper (Color Picker)** loupe magnifier.
3. **Quick-Access Radial Menu / Floating Puck**: Customizable on-screen shortcut wheel for one-handed thumb access to brush size, opacity, flip canvas, and step 1 frame forward/back.
4. **Canvas Flip (Horizontal/Vertical Mirror Preview)**: Instant hot-button to flip the stage horizontally so animators can immediately spot proportion/pose symmetry mistakes.
5. **Left-Handed & Right-Handed Workspace Flip**: Single toggle to mirror all toolbars so the drawing hand never covers the UI controls.
6. **Reference Image Pinboard (PureRef-style)**: Floating, resizable reference image window with its own eyedropper and opacity slider so artists can look at character sheets while animating.

### Category 2: Crash-Proofing, Diagnostics & Low-End Survival
7. **Atomic Stroke-by-Stroke Autosave & Crash Recovery**: If a cheap phone's battery dies or the OS kills the app, zero strokes are lost—changes are journaled to disk asynchronously.
8. **In-App Debug Console & Diagnostics Overlay**: Since we build via GitHub Actions without local Android Studio Logcat, the app includes a toggleable **In-App Diagnostics & Crash Log Viewer** (with a "Copy Error Report" button) so any bug on your phone tells you the exact file and line number immediately!
9. **Adaptive Low-Spec Mode ("Potato Phone Turbo Mode")**: Automatically or manually switches inactive layers to 16-bit color (`RGB_565`), halves preview resolution during fast playback, and aggressively streams offscreen frames to disk.

### Category 3: Drawing, Inking & Coloring Engine
10. **Pluggable Brush Studio**: Pencil, Ballpoint, G-Pen (variable ink taper), Airbrush, Marker, Pixel Art (1px aliased), Textured Watercolor/Charcoal.
11. **Input Stabilizer (Streamline / Lazy-Mouse)**: Adjustable rope/spring smoothing (0–100%) to turn shaky finger drawing on cheap screens into clean studio line-art.
12. **Velocity & Pressure Simulation**: Budget phones without pressure-sensitive styluses can simulate pen pressure based on **finger speed** or **touch contact area (`event.size`)**.
13. **Vector + Raster Hybrid Layers**:
    - Vector layers allow infinite scaling, line-width tweaking after drawing, and **Stroke Cutter / Vector Eraser** (automatically trims intersecting lines at intersections!).
    - Raster layers allow textured shading, smudging, and pixel art.
14. **Smart Fill Bucket**:
    - **Close Gaps** (1px to 15px gap tolerance so paint doesn't leak out of open sketches).
    - **Reference Layer Fill**: Draw line-art on Layer 2, set it as "Reference", and tap-fill colors cleanly on Layer 1 underneath!
    - **Expand / Contract (Trapping)**: Automatically expands fill by 1–2px under the black line-art so there are no ugly white halos around edges.
15. **Symmetry & Perspective Guides**: Mirror Ruler, Kaleidoscope, 1-Point, 2-Point, 3-Point Perspective grids, and Fish-eye curvilinear guides with snap-to-guide toggle.
16. **Lasso, Transform & Mesh Warp**: Freehand selection, bounding box scale/rotate/skew, perspective 4-corner pin, and lattice mesh deformation.

### Category 4: Pro Animation Timeline & Cel Workflow
17. **Exposure Holds (1s, 2s, 3s, N-frames)**: Hold a drawing across multiple frames without duplicating memory.
18. **Clone / Linked Cels**: Reuse the exact same drawing at frame 10 and frame 45; editing one automatically updates all linked instances (ideal for blinking eyes or looping backgrounds).
19. **Shift and Trace (Virtual Light Table)**: Temporarily move/rotate an onion-skin ghost frame to trace in-betweens accurately, then snap it back into place (a signature Toon Boom / TVPaint pro feature).
20. **Flipbook Scrubbing Wheel ("Rolling" the frames)**: Hold a corner widget and slide left/right to rapidly flip between the last 3 drawings like flipping paper pages between your fingers.
21. **Layer Folders, Clipping Masks & Alpha Lock**: Group character parts into folders, lock transparent pixels for easy shading, and apply clipping masks.
22. **Blend Modes**: Normal, Multiply (for shadows), Screen/Add (for glow/lighting), Overlay, Color Dodge.
23. **2D Camera Layer**: Dedicated camera frame with pan, zoom, truck-in, rotation, and camera shake keyframes independent of the artwork layers.

### Category 5: Tweening, Rigging & Procedural FX
24. **Keyframe Interpolation & Graph Editor**: Animate layer position, scale, rotation, opacity, and pivot points with a full Cubic Bézier velocity curve editor (Ease-In, Ease-Out, Bounce, Stepped).
25. **2D Skeletal Rigging (FK / IK Bones)**: Create bone hierarchies for character limbs with Inverse Kinematics (pin feet to the ground, pull hand and elbow bends naturally).
26. **Motion Paths**: Visible trajectory arcs on the canvas with tick marks showing spacing (slow-in / slow-out visualization).
27. **Non-Destructive VFX Modifiers**: Blur, Drop Shadow, Chromatic Aberration, Glow/Bloom, Color Grading LUTs, and Line Boil (auto-wiggle effect).

### Category 6: Audio, Lip-Sync & Export Pipeline
28. **Multi-Track Audio & Real-Time Scrubbing**: Import MP3/WAV/OGG, view high-precision PCM waveforms on the timeline, and hear frame-by-frame audio slices while dragging the playhead.
29. **Built-in Voice Recorder & Phoneme Markers**: Record voiceovers directly inside the app and place lip-sync mouth chart markers (A, E, I, O, U, M, F, L) on the timeline.
30. **Rotoscoping Video Import**: Import a reference video clip onto a background layer (streamed frame-by-frame from disk so it never blows up RAM) to trace or study motion.
31. **Hardware-Accelerated Export**: MP4 (H.264/H.265 via Android `MediaCodec`), Transparent WebM/APNG/GIF, Sprite Sheets (with JSON atlas for game engines like Godot/Unity), and Layered ZIP/PSD project backup.

---

## 5. Change Log & Session History
- **2026-10-18 (Session 1)**:
  - Decided on Pure Native Kotlin + GitHub Actions CI/CD (Zero local Android Studio/SDK bloat).
  - Created repository structure, Gradle configuration, R8 optimization rules, and `.github/workflows/build-apk.yml`.
  - Built initial `MainActivity.kt` and `AnimationCanvasView.kt` with zero-allocation render loop and live FPS/RAM HUD.
  - Pushed initial commits to `https://github.com/DevMarvellous/animation.git`.
  - Created `AGENT_CONTEXT.md` as the permanent brain of the project.
