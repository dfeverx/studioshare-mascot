# :mascot-core — the StudioShare floating companion

The StudioShare app-icon face as a small character that lives above every screen, reacts to what the
app is doing, can be dragged to any edge, walks over to empty states, and never covers the UI. It is
drawn entirely in code (Compose `Canvas`): no images, no pack to download, crisp at any size.

## Three layers, changed in three places

| Change | Where | Code change? |
|---|---|---|
| What the mascot does at a moment | `spec/mascot.json` → `moments` | No |
| A mood's body motion | `spec/mascot.json` → `moods` | No |
| How the face looks in a mood | `face/StudioFaceExpression.kt` (and `packages/web/src/face.ts`) | One line each |
| A new moment the app signals | add it to `spec/mascot.json`, then one `MascotCue(...)` / `play(...)` | One line |

After editing the spec, run `npm run moments` (repo root). It validates the spec and regenerates
`MascotMoments.kt` (the constants), `MascotMomentTable.kt` (moment → mood, mood → motion) and the
web's `moments.ts`. `MascotMomentMoodsTest` fails if a mood has no face expression, or if a face's
motion disagrees with the spec.

## Code

- `face/` — `StudioFace` (the composable: blinks, drift, ~250 ms tween between moods),
  `StudioFaceExpression` (mood → eyes, mouth, accent, motion), `StudioFaceDrawing` (the geometry and
  palette, sampled from the app icon).
- `render/MascotMotion` — procedural body motion (breathe, hop, nod, …), shared with the face.
- `MascotMomentMoods` — moment → mood resolution (unknown → idle; a repeated once-per-account
  celebration → ok), over the generated `MascotMomentTable.kt`.
- `director/` — `MascotDirector` (held cues by priority, one-shots), `MascotSettings` (on/off, dock, firsts).
- `stage/` — `MascotStage` overlay and `MascotPlacement` (pure geometry: dock, perch, avoid).
- `stage/MascotPose` — the mascot drawn in place (for dialogs, which cover the floating stage).
- `MascotController.kt` — `LocalMascot`, `MascotCue`, `Modifier.mascotAnchor` / `mascotAvoid`.

## Rules

- **Additive only.** The mascot reads app state and never changes it; every hook is a no-op while it is off.
- Screens only use `MascotCue`, `play`, `mascotAnchor`, `mascotAvoid` — never draw the mascot. The one
  exception is a dialog's unused `icon` slot, via `MascotPose`.
- Never on immersive viewers, never over a `mascotAvoid` element.
- Moment keys are append-only (shipped apps and the web use them).
