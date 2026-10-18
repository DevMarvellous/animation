# ProAnimation Studio 🎨⚡

An advanced, ultra-optimized 2D animation studio app for Android, engineered for smooth 60–120 FPS performance across all devices (including budget and low-end phones) with minimal memory footprint.

## 🚀 Key Architectural Pillars

- **Pure Native Kotlin & Hardware-Accelerated Canvas**: Direct hardware rendering using Android's native graphics pipeline with zero heavy third-party framework overhead.
- **Zero-Allocation Render Engine**: Pre-allocated path buffers, paints, and dirty-region caching to eliminate Dalvik/ART Garbage Collection frame stutters.
- **Low-Latency Touch Handling**: Uses `MotionEvent` historical coordinate batching (`getHistoricalX`/`getHistoricalY`) to deliver smooth Bézier curves on any touchscreen.
- **Cloud-Native CI/CD (Zero Local Tool Bloat)**: Automated APK compilation, R8 full-mode shrinking, and artifact delivery via GitHub Actions.
- **Real-Time Performance Telemetry**: Built-in HUD measuring live FPS, stroke count, and heap memory (MB).

## 🛠️ Automated Cloud Builds

Every push to `main` automatically triggers a GitHub Actions runner that compiles:
- `ProAnimationStudio-Release-APK` (Aggressive R8 full-mode optimization)
- `ProAnimationStudio-Debug-APK` (With debugging enabled)

You can download ready-to-install APKs directly from the **Actions** tab on GitHub!
