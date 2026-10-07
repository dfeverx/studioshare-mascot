# StudioShare Mascot (`studioshare-mascot`)

A modular, standalone project hosting the StudioShare 3D mascot companion:
- **3D Asset & Animation Pipeline:** Blender source model, automated rendering, and WebP atlas packaging.
- **Compose Multiplatform Library (`:mascot-core`):** Frame player, procedural vector rig fallback, director, and stage placement for Android, iOS, macOS, and Windows.
- **Web Library (`@studioshare/mascot`):** High-performance HTML5 Canvas player and React component.
- **Desktop Background Agent (`:mascot-agent`):** Companion agent that lives in the macOS Menu Bar / Windows System Tray, provides floating companion HUDs, and sends native OS notifications for background tasks, system warnings, and milestones.
- **Interactive Testbench & Preview (`:mascot-preview`):** Standalone desktop GUI to preview all moods, test rig poses, and simulate agent triggers.

---

## Architecture & Project Structure

```
studioshare-mascot/
├── pipeline/                          # 3D character assets & pack generation
│   ├── model/mascot.blend             # Blender 3D rigged character
│   ├── blender/                       # Python Blender scripts (build_model.py, render.py)
│   ├── spec/mascot.json               # Master specification (moods, motions, props, moments)
│   ├── src/                           # Rendered PNG frames (moods & moments)
│   ├── dist/                          # Compiled distribution pack (manifest.json + WebP atlases)
│   ├── tools/                         # CLI tools (pack.mjs, import.mjs)
│   └── docs/                          # ART.md, SHOTLIST.md, PROMPTS.md
│
├── packages/
│   ├── kmp/                           # :mascot-core Compose Multiplatform SDK
│   │   ├── build.gradle.kts
│   │   └── src/                       # Common Kotlin, Android, and Skia decoders
│   │
│   ├── web/                           # @studioshare/mascot Web / React SDK
│   │   ├── package.json
│   │   ├── src/                       # MascotSprite.tsx, pack.ts, moments.ts, agent.ts
│   │   └── demo/index.html            # Standalone browser testbed
│   │
│   └── agent/                         # :mascot-agent Desktop Background Companion
│       ├── build.gradle.kts
│       └── src/                       # MascotAgent, MascotTrayCompanion, MascotMiniCompanionWindow
│
└── apps/
    └── desktop-preview/               # :mascot-preview Desktop GUI Testbench
        ├── build.gradle.kts
        └── src/                       # Standalone Compose Desktop preview app
```

---

## 1. How Character Updates Work

Adding or changing character moods, outfits, or animations is frictionless:

1. **Edit the Character / Animations:**
   - In Blender: `pipeline/model/mascot.blend` or automate via `pipeline/blender/build_model.py` and `pipeline/blender/render.py`.
   - In Spec: Update `pipeline/spec/mascot.json` (define moods, keyframes, or moment mappings).

2. **Rebuild the Pack & Generate Bindings:**
   ```bash
   npm run pack
   ```
   This single command:
   - Validates frames and trims bounds.
   - Generates optimized WebP atlases in `pipeline/dist/atlas/`.
   - Computes SHA-256 hashes and outputs versioned `manifest.json`.
   - Automatically generates `MascotMoments.kt` in `packages/kmp/` and `moments.ts` in `packages/web/`.

3. **Over-The-Air (OTA) Updates:**
   - When `pipeline/dist/` is deployed to `/mascot/` on web or CDN, running client apps automatically detect the new version within 6 hours and hot-reload the character without needing a binary app update.

---

## 2. Desktop Background Agent

The mascot functions as a proactive companion running in the background:
- **Menu Bar / System Tray (`MascotTrayCompanion`):** Shows live status ("StudioShare • Uploading: 45%"), with pause/cancel actions.
- **Floating Mini-Companion Window (`MascotMiniCompanionWindow`):** A draggable, borderless HUD that sits unobtrusively on desktop while the main app is minimized.
- **Native OS Notifications (`DesktopMascotNotifier`):** Sends macOS Notification Center or Windows Action Center alerts for warnings or milestones.

### Usage in Kotlin / KMP:
```kotlin
val agent = MascotAgent.default

// 1. Progress updates (e.g., photo upload or culling)
agent.reportProgress(
    taskId = "cull_wedding",
    title = "AuraFace Culling",
    detail = "340 / 800 photos analyzed",
    progress = 0.42f,
    mood = "searching"
)

// 2. System warnings
agent.postWarning(
    id = "warn_storage",
    title = "Low Storage Warning",
    message = "Only 1.2 GB remaining on disk",
    mood = "careful"
)

// 3. Milestones
agent.postAlert(
    id = "booking_1",
    title = "New Booking Confirmed!",
    message = "Wedding Gold session booked for Oct 24",
    mood = "celebrating"
)
```

---

## 3. Running & Testing Standalone

### Run the Desktop Preview App:
```bash
./gradlew :mascot-preview:run
```
Opens the interactive stage with:
- Live mascot viewport (switch between 25+ moods and Light/Dark themes).
- Background Agent simulator (test upload progress, post warnings, trigger booking alerts).
- Floating desktop companion toggle.

### Run KMP Unit Tests:
```bash
./gradlew :mascot-core:jvmTest
```

### Preview in Browser:
Open `packages/web/demo/index.html` in any browser or start a static server:
```bash
npx serve packages/web/demo
```
