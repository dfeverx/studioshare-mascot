# Mascot art — how to make the real renders

The app draws a code version of the mascot until renders exist. Every render you drop in replaces the
drawing for that mood or moment — no code change, no app release (the web publishes the new pack).
`SHOTLIST.md` (generated) lists all 470 moments with the moment map's action and what art each has now.

## Why the current sheets can't be used as they are

`gray.png` / `white.png` are 18 stills per outfit; 15 of them are cut at the waist, none is animated,
and each sits on a rounded card. The companion is always full-body and moves, so it needs full-body,
transparent, front-facing frames. The two sheets also differ: in `white.png` pose 10 is the phone,
12 the wave and 16 a peek from behind a wall; in `gray.png` pose 10 is the wave, 12 the phone and 16
the shy blush. `studioshare-mascot.png` has the front / side / back turnaround — the reference for a
3D model.

## Chosen: one 3D model, rendered by Blender

Everything is rendered from one rigged model by `tools/mascot/blender/render.py`. Users never get the model or
Blender — only the rendered frames (WebP atlases, ≤ 150 KB each, ≤ 4 MB in all), played in short bursts.

**What the model (`.blend`) needs** — `blender -b mascot.blend --python tools/mascot/blender/render.py -- --list`
prints what it finds:

| Part | Name the script looks for (change in `tools/mascot/blender/actions.json`) |
| --- | --- |
| One armature with the animations as actions | an action per mood: `idle`, `walk`, `waiting`, `happy` … (the `action` of each entry) |
| Two outfit materials | `Outfit_Black` and `Outfit_White` — every slot using either is switched per outfit |
| The face | a material `Face` with an Image Texture node named `FaceTex` feeding colour and emission; the visor UVs cover the square face image |
| Props | objects in a collection `Props`, named `prop_box`, `prop_cloud`, `prop_camera` … (`model.props`) |

**The model we have** is built from code: `blender -b --python tools/mascot/blender/build_model.py` writes
`model/mascot.blend` — the figure (proportions, colours, cap, visor, hoodie, sneakers), its skeleton, all 22
props and an animation per mood, all as parameters in that one script. Change a pose or a proportion there and
run it again. To replace it with a sculpted model later: an image-to-3D tool (Meshy, Tripo, Rodin) from the
turnaround in `studioshare-mascot.png`, or a 3D artist → auto-rig and animations from Mixamo (each entry in `actions.json` has a `mixamo` hint;
download "without skin", import into the .blend, rename the action) → props as small meshes parented to a
hand bone. Faces: `node tools/mascot/blender/faces.mjs` draws the 12 expressions into `model/faces/`.

**Rendering:**

```
node tools/mascot/blender/actions.mjs                                  # adds new moods/clips to actions.json
blender -b mascot.blend --python tools/mascot/blender/render.py -- --preview   # one frame each → model/preview/
blender -b mascot.blend --python tools/mascot/blender/render.py               # everything → src/
blender -b mascot.blend --python tools/mascot/blender/render.py -- --only walk,idle
node tools/mascot/pack.mjs --sync                                      # atlases, contact sheets, into the apps
```

Same orthographic camera, lights and scale for every frame; transparent background; 12 fps; at most 16
frames per mood (`render` in actions.json). A rendered animation (4+ frames) plays as rendered — the app
adds no bob or hop on top. Frames of one animation share one crop, so jumps and steps keep their motion.

## Fallback: generated stills (`PROMPTS.md`)

If a model is not ready, `PROMPTS.md` has one image-generation prompt per pose (162: every mood,
keyframes, and own clips); `node tools/mascot/import.mjs <folder>` keys out the green background and files
them. Renders from the model later replace them mood by mood.

## Frame spec

| Setting | Value |
| --- | --- |
| Framing | Full body, cap to sneaker soles, feet on the bottom edge, centred |
| Frame | 200 × 280 px PNG with alpha (the pack fits and bottom-aligns larger renders) |
| Camera | Orthographic, front three-quarter, same height for every clip |
| Light | The sheets' soft studio key light; no floor, no shadow plate |
| Background | Transparent, or flat chroma green #00B140 (import.mjs keys it out) — never white, grey or a card |
| Outfits | `light/` = **black** outfit (shown on light UI), `dark/` = **white** outfit (shown on dark UI) |
| Frame rate | 12 fps (set `fps` per mood in `mascot.json` if different) |
| Length | Loops 8–24 frames (`loopFrom` = first frame of the loop); one-shots 12–24 frames |
| Still | `still` = the frame used with reduced motion |
| Budget | ≤ 150 KB per atlas (WebP), ≤ 4 MB whole pack — the tool fails the build above that |

## Where files go

```
Brand/mascot/src/<mood>/light/000.png, 001.png …        one mood, black outfit
Brand/mascot/src/<mood>/dark/000.png …                  same frames, white outfit (optional:
                                                         without it a rim-lit copy of light/ is used)
Brand/mascot/src/moments/<moment.key>/light/000.png …   one moment's own action (beats its mood)
```

Then: `node tools/mascot/pack.mjs --sync` — validates, builds atlases, copies the pack into the app and
the web, and rewrites `SHOTLIST.md`. Commit, deploy the web: apps pick the new pack up within 6 hours.

## Order that gives the most for the least

1. `walk` (it travels between places all the time) and `idle`.
2. The moods most moments use: `ok`, `careful`, `oops`, `carrying`, `curious`, `waiting`,
   `happy`, `thinking`, `patient`, `celebrating`.
3. The rest of the 30 moods.
4. Moment clips for the firsts (studio launched, first event live, first upload, first cull sent…)
   and the most-seen moments (uploading, culling, scanning faces).
