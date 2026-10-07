# StudioShare Mascot (`studioshare-mascot`)

A small, standalone project for the StudioShare companion: the **StudioShare app-icon face** (a black
rounded square with gradient drop eyes and a gradient smile) as a living character, drawn entirely
in code. There is no image data, no art pipeline and no pack to download.
- **Compose Multiplatform library (`:mascot-core`):** the `StudioFace` composable, moods → expressions, moments → moods, the director and the floating stage, for Android, iOS, macOS and Windows.
- **Web library (`@studioshare/mascot`):** the same face on a 2D canvas (`StudioFace` for React, `StudioFacePlayer` for plain JS), plus the moments map.
- **Desktop background agent (`:mascot-agent`):** the notch companion (the face living in the MacBook notch), the floating HUD, the menu-bar/tray companion and native OS notifications for uploads, warnings and milestones.
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

// Watching something: gaze is read every frame, x/y each −1..1. gazeToward(dx, dy) makes one.
StudioFace(mood = "idle", gaze = { gazeToward(dx, dy) })

// Desktop: the face living in the MacBook notch, fed by the agent.
StudioFaceNotchCompanion(agent = agent, visible = true, onClose = {}, onOpenMainApp = {})
```

The notch companion follows [Coucou](https://github.com/Louis-CFM/coucou)'s Mochi.

At rest the island is the notch with a wide ear either side, so it is always wider than the camera
housing. The face sits in the left ear's outer corner and turns to watch the cursor anywhere on
screen. The right ear shows a progress ring while something runs, or an amber pulse while something
needs the user. There are no words at rest.

When something happens the notch opens downward into a card that is wide and short, so it only ever
covers a strip under the menu bar:
- A line like "You're signed in" is one row.
- A message with a detail is two rows, with its buttons beside it rather than under it.
- An upload shows its name over a bar the face rides along.

The card is washed with the mood's colour and the face slides in from the ear. The motion is the
Dynamic Island's: a soft spring open, a firmer one closed, and content fading in out of a slight
blur. Hovering keeps whatever is showing, and clicking the resting island opens it.

Clicking the island while nothing is going on doesn't open a card with words. The notch opens a
little, the face drops out of it with a wink, and waves.

### Hands

The face has no hands most of the time. Like Coucou's Mochi, two small soft ovals grow from behind
its lower corners for a gesture, then tuck away when it's done. They are filled with the eyes' warm
gradient, because black hands would vanish against the notch. A moment opts in with `hands` in
`spec/mascot.json`, and only the few where a gesture really fits have one:

| Gesture | What it does | Used for |
|---|---|---|
| `wave` | right hand waves beside the face | hello and goodbye: signed in/out, welcome back, a host joined, uploads going to the background, tapping the mascot, the notch's hello |
| `cheer` | both hands up, pumping | the big milestones: studio live, first photo, all uploaded, paid in full, quote accepted, selection complete, subscribed |
| `tada` | right hand sweeps out to present | something just went live: event published, gallery / wall / website live, a draft written, ready for review, job delivered |
| `shrug` | both hands out, lifted once | nothing there or nothing to do: no faces found, no results, already exported, nothing to restore, already invited |
| `shy` | hands on the cheeks | a thank-you (diagnostics sent), and the notch face blushing when the mouse rests on it |

A first-time celebration seen before shows "ok" and doesn't cheer. In code:
`StudioFace(mood, hands = MascotMomentMoods.handsFor(key), handsId = cueId)` (`MascotStage` and
`MascotPose` already do this, and so do `MascotAgent.moment` and the notch). On the web it is
`<StudioFace moment=… />`, or `hands="wave"` to gesture directly. `build/studio-face-preview/hands.png`
shows every gesture over time.

`MacNotch` reads the notch from the screen's safe area and logs what it found
(`notch: 185 × 38 (measured …)`).

Drive it with moments: every one-off moment in `spec/mascot.json` with a `say` line opens the notch
with that line, under its area's label. Moments without one only change the face.

```kotlin
agent.moment(MascotMoments.AuthSignedIn)                          // "You're signed in. Welcome back!"
agent.moment(MascotMoments.AuthSignedIn, "Welcome back, Priya!")  // your own words
agent.moment(MascotMoments.OrbitNewBooking, detail = "Priya booked a portrait session.",
    actionLabel = "Open", onAction = { /* … */ })
```

`MacNotch` lifts the window above the menu bar through the Objective-C runtime. Rounded corners
(the island, its cards, pill buttons) are the notch companion's exception to the no-radius rule:
the island has to read as part of the notch.

On the web:
```tsx
<StudioFace moment={MascotMoments.uploadDone} size={64} />
<StudioFace mood="idle" followPointer />   {/* watches the pointer, like the notch companion */}
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
These also render `packages/kmp/build/studio-face-preview/moods.png` (every mood),
`packages/kmp/build/studio-face-preview/gaze.png` (the face looking every way) and
`packages/agent/build/studio-face-notch/states.png` (the notch companion's states).

### Preview in Browser:
```bash
npm run build:web     # compiles packages/web/src → packages/web/dist
npx serve .           # then open /packages/web/demo/
```
