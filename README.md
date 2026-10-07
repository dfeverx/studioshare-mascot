# StudioShare Mascot (`studioshare-mascot`)

A small, standalone project for the StudioShare companion: the **StudioShare app-icon face** (a black
rounded square with gradient drop eyes and a gradient smile) as a living character, drawn entirely
in code. There is no image data, no art pipeline and no pack to download.
- **Compose Multiplatform library (`:mascot-core`):** the `StudioFace` composable, moods → expressions, moments → moods, the director and the floating stage, for Android, iOS, macOS and Windows.
- **Web library (`@studioshare/mascot`):** the same face on a 2D canvas (`StudioFace` for React, `StudioFacePlayer` for plain JS), plus the moments map.
- **Desktop background agent (`:mascot-agent`):** the notch-style companion bar, the floating HUD, the menu-bar/tray companion and native OS notifications for uploads, warnings and milestones.
- **Testbench (`:mascot-preview`):** a desktop app to browse every mood and moment and simulate agent events.

---

## Architecture & Project Structure

```
studioshare-mascot/
├── spec/mascot.json                   # The one file to edit: 30 moods (+ motion) and 470 moments → mood
├── tools/moments.mjs                  # Validates the spec, generates the Kotlin + TS bindings (no deps)
│
├── packages/
│   ├── kmp/                           # :mascot-core Compose Multiplatform SDK
│   │   └── src/commonMain/.../mascot/
│   │       ├── face/                  # StudioFace, expressions, drawing
│   │       ├── MascotMoments.kt       # GENERATED moment-key constants
│   │       ├── MascotMomentTable.kt   # GENERATED moment → mood, mood → motion
│   │       └── director/ stage/ render/MascotMotion.kt
│   │
│   ├── web/                           # @studioshare/mascot Web / React SDK
│   │   ├── src/                       # face.ts, StudioFace.tsx, moments.ts (GENERATED), agent.ts
│   │   └── demo/index.html            # Browser preview of every mood
│   │
│   └── agent/                         # :mascot-agent desktop companion
│       └── src/                       # MascotAgent, StudioFaceNotchCompanion, MascotMiniCompanionWindow, tray, notifier
│
└── apps/
    └── desktop-preview/               # :mascot-preview desktop testbench
```

---

## 1. How Character Updates Work

- **What the mascot does at a moment / a mood's motion:** edit `spec/mascot.json`, then run
  `npm run moments`. That regenerates `MascotMoments.kt`, `MascotMomentTable.kt` and the web's
  `moments.ts`. Moment keys are append-only and never renamed.
- **How the face looks in a mood:** edit `face/StudioFaceExpression.kt`, and the matching line in
  `packages/web/src/face.ts`. Tests fail if a mood has no expression or its motion disagrees with the spec.
- Everything is code, so a change ships with the app (and the web) like any other change. There is no
  pack and no over-the-air art update.

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

### The face and the notch companion
```kotlin
// Anywhere in Compose (Android, iOS, desktop):
StudioFace(mood = "uploading", progress = 0.45f, modifier = Modifier.size(64.dp))

// Desktop: a notch-style bar at the top centre of the screen, fed by the agent.
StudioFaceNotchCompanion(agent = agent, visible = true, onClose = {}, onOpenMainApp = {})
```

The bar is compact (face + one line, plus a percentage while working). It opens into a card on hover,
for a warning (with its action and Dismiss buttons), and for an alert.

On the web:
```tsx
<StudioFace moment={MascotMoments.uploadDone} size={64} />
```

---

## 3. Running & Testing Standalone

### Run the Desktop Preview App:
```bash
./gradlew :mascot-preview:run
```
Opens the interactive stage with:
- The face for every mood and moment.
- The background agent simulator (upload progress, warnings, booking alerts).
- Toggles for the floating HUD and the notch companion.

### Run the tests:
```bash
./gradlew :mascot-core:jvmTest :mascot-agent:jvmTest
```
These also render `packages/kmp/build/studio-face-preview/moods.png` (every mood) and
`packages/agent/build/studio-face-notch/states.png` (the notch bar states).

### Preview in Browser:
```bash
npm run build:web     # compiles packages/web/src → packages/web/dist
npx serve .           # then open /packages/web/demo/
```
