# :mascot — the StudioShare floating companion

A small 3D-rendered character that lives above every screen, reacts to what the app is doing, can be
dragged to any edge, walks over to empty states, and never covers the UI. Spec: the
"StudioShare Mascot — Expression & Moment Map" doc (moods M01–M25, moments per feature).

## Three layers, changed in three places

| Change | Where | Code change? |
|---|---|---|
| What the mascot does at a moment | `Brand/mascot/mascot.json` → `moments` | No |
| How a mood moves (fps, motion, fallback art) | `Brand/mascot/mascot.json` → `moods` | No |
| The character art | `Brand/mascot/src/<mood>/<light\|dark>/*.png` (one PNG per frame) | No |
| One moment's own action (the doc's "Action detail") | `Brand/mascot/src/moments/<moment.key>/<light\|dark>/*.png` | No |
| A new moment the app signals | add it to `mascot.json`, then one `MascotCue(...)` / `play(...)` | One line |

Then `node tools/mascot/pack.mjs --sync` (workspace root): validates, builds WebP atlases + `manifest.json`
into `Brand/mascot/dist/`, copies the pack into `composeApp/.../composeResources/files/mascot/` and
`studioshare-web/public/mascot/`, and regenerates `MascotMoments.kt` and the web's `lib/mascot/moments.ts`.
Deploying the web publishes the pack; apps pick it up within 6 h without a release (sha256-checked).

`light` art is drawn on light UI (dark outfit), `dark` on dark UI (light outfit). A mood without a `dark`
folder gets one derived with a soft rim. Until renders exist, the rig (`rig/`) draws every mood in code,
full-body. `Brand/mascot/ART.md` is the render spec; `Brand/mascot/SHOTLIST.md` (generated on every
build) lists every moment with the doc's action and the art it has. Art resolves clip → mood → fallback
chain → the drawn rig.

## Code

- `pack/` — manifest model + resolution (moment → mood → art, fallback to idle), the repository (bundled pack,
  verified remote override), SHA-256.
- `render/` — `MascotSprite` (atlas frame player + procedural `MascotMotion`), `AtlasCache` (LRU 3).
- `director/` — `MascotDirector` (held cues by priority, one-shots), `MascotSettings` (on/off, dock, firsts).
- `stage/` — `MascotStage` overlay and `MascotPlacement` (pure geometry: dock, perch, avoid).
- `stage/MascotPose` — the mascot drawn in place (the app's `mascotDialogIcon` uses it for confirms,
  which cover the floating stage).
- `MascotController.kt` — `LocalMascot`, `MascotCue`, `Modifier.mascotAnchor` / `mascotAvoid`.

The app wires it in `core/mascot/AppMascot.kt` (storage, cache, fetch), `ui/mascot/AppMascotStage.kt`
(stage in the shell + global signals) and Profile → Appearance → Mascot (the off switch).

## Rules

- **Additive only.** The mascot reads app state and never changes it; every hook is a no-op while it is off
  (`MascotFeature.ENABLED` compiles it out entirely).
- Screens only use `MascotCue`, `play`, `mascotAnchor`, `mascotAvoid` — never draw the mascot. The one
  exception is a dialog's unused `icon` slot: `icon = mascotDialogIcon(moment)`, which is null while the
  mascot is off, so the dialog is unchanged then.
- Never in the cull loupe/grid/viewer per key, never on immersive viewers, never over a `mascotAvoid` element.
- Moment keys are append-only (shipped apps and the web use them).
