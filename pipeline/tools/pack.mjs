// Builds the mascot pack: Brand/mascot/{mascot.json, src/} → Brand/mascot/dist/{manifest.json, atlas/}.
//   node tools/mascot/pack.mjs           build + validate
//   node tools/mascot/pack.mjs --sync    …then copy into the KMP app and the web app and regenerate
//                                         MascotMoments.kt / moments.ts from the moment keys.
// Art: src/<mood>/<light|dark>/*.png, one PNG per frame in name order. "light" is drawn on light UI
// (dark outfit), "dark" on dark UI (light outfit). A mood without a dark folder gets one derived from
// its light frames with a soft light rim, so it reads on a dark background.
// A moment can have its own clip — the doc's action for that one moment — in
// src/moments/<moment.key>/<light|dark>/*.png; it wins over its mood's art. SHOTLIST.md (written on
// every build) lists every moment, its action and which art it has, as the brief for the renders.
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { PIPELINE_DIR, PROJECT_ROOT, WORKSPACE_ROOT, MASCOT_SPEC, SRC, DIST, DOCS, sharp } from './lib.mjs';

const SYNC_APPS = process.argv.includes('--sync') || process.argv.includes('--sync-apps');
const KMP_KT = path.join(PROJECT_ROOT, 'packages/kmp/src/commonMain/kotlin/com/dfeverx/studioshare/mascot/MascotMoments.kt');
const WEB_TS = path.join(PROJECT_ROOT, 'packages/web/src/moments.ts');

const APPS_KMP_RES = path.join(WORKSPACE_ROOT, 'Projects/studioshare-kmp/composeApp/src/commonMain/composeResources/files/mascot');
const APPS_WEB_PUBLIC = path.join(WORKSPACE_ROOT, 'Projects/studioshare-web/public/mascot');

const BUDGET_ATLAS = 150 * 1024;
const BUDGET_PACK = 4 * 1024 * 1024;
const MAX_COLS = 8;
const MAX_FRAMES = 10;
const PAD = 8;
const THEMES = ['light', 'dark'];
const MOTIONS = new Set(['none', 'breathe', 'sway', 'hop', 'jump', 'pop', 'nod', 'shake', 'trot', 'droop', 'slow']);
// Must match mascot/src/commonMain/.../rig/MascotRig.kt — an unknown id draws the default.
const FACES = new Set(['smile', 'happy', 'wink', 'closed', 'sleepy', 'surprised', 'worried', 'sad', 'focused', 'thinking', 'cool', 'scan']);
const ARMS = new Set(['down', 'wave', 'cheer', 'thumbs', 'carry', 'camera', 'chin', 'shrug', 'cover', 'point', 'hips', 'stop']);
const PROPS = new Set(['none', 'box', 'cloud', 'laptop', 'magnifier', 'question', 'exclaim', 'zzz', 'sparks', 'confetti', 'heart',
  'plug', 'unplug', 'key', 'sweat', 'hourglass', 'check', 'phone', 'envelope', 'star', 'camera', 'faceframe']);
const KEY = /^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+$/;

const def = JSON.parse(fs.readFileSync(MASCOT_SPEC, 'utf8'));
const errors = [];
const fail = (m) => errors.push(m);

// ---- validate the definition ------------------------------------------------------------------
const { w: FW, h: FH } = def.frame;
const moodNames = Object.keys(def.moods);
// Optional frame art (3D renders): src/<mood>/light/*.png. Without it the rig draws the mood.
const hasArt = (m) => fs.existsSync(path.join(SRC, m, 'light'));
const CLIPS = path.join(SRC, 'moments');
const clipNames = fs.existsSync(CLIPS) ? fs.readdirSync(CLIPS).filter((d) => fs.existsSync(path.join(CLIPS, d, 'light'))) : [];
for (const c of clipNames) if (!def.moments[c]) fail(`clip src/moments/${c}: no moment has that key`);
for (const [name, m] of Object.entries(def.moods)) {
  if (!/^[a-z][a-zA-Z0-9]*$/.test(name)) fail(`mood "${name}": name must be lowerCamel`);
  if (!MOTIONS.has(m.motion ?? 'none')) fail(`mood "${name}": unknown motion "${m.motion}"`);
  if (m.fallback && !def.moods[m.fallback]) fail(`mood "${name}": fallback "${m.fallback}" is not a mood`);
  if (!FACES.has(m.face ?? 'smile')) fail(`mood "${name}": unknown face "${m.face}"`);
  if (!ARMS.has(m.arms ?? 'down')) fail(`mood "${name}": unknown arms "${m.arms}"`);
  if (!PROPS.has(m.prop ?? 'none')) fail(`mood "${name}": unknown prop "${m.prop}"`);
}
if (!def.moods.idle) fail('mood "idle" is required — it is the last fallback');
for (const [key, mo] of Object.entries(def.moments)) {
  if (!KEY.test(key)) fail(`moment "${key}": key must look like area.thing`);
  if (!def.moods[mo.mood]) fail(`moment "${key}": mood "${mo.mood}" is not defined`);
}

// ---- build atlases ----------------------------------------------------------------------------
// One scale for every frame in the pack, so the mascot is the same size in every pose: the largest
// that fits the tallest render in the frame (a pose too wide at that scale — arms flung out — is
// shrunk on its own). Each source is trimmed to its outline first, so a
// render's margins don't matter. A mood or clip can nudge it with "scale" (e.g. 0.95) in mascot.json.
// Frames of one animation (same canvas size, e.g. a Blender render) are cropped to the union of their
// outlines, so a jump still lifts off the floor and a step still moves; a lone image is cropped to its own.
const cropOf = new Map(); // file → { left, top, width, height }
async function outline(file) {
  const { info } = await sharp(file).ensureAlpha().trim({ threshold: 1 }).toBuffer({ resolveWithObject: true });
  return { left: -(info.trimOffsetLeft ?? 0), top: -(info.trimOffsetTop ?? 0), width: info.width, height: info.height };
}
async function prepareSet(folder) {
  const files = pngs(folder).sort();
  const sizes = await Promise.all(files.map((f) => sharp(f).metadata().then((m) => `${m.width}x${m.height}`)));
  const boxes = await Promise.all(files.map(outline));
  if (files.length > 1 && new Set(sizes).size === 1) {
    const l = Math.min(...boxes.map((b) => b.left)), t = Math.min(...boxes.map((b) => b.top));
    const r = Math.max(...boxes.map((b) => b.left + b.width)), b2 = Math.max(...boxes.map((b) => b.top + b.height));
    for (const f of files) cropOf.set(f, { left: l, top: t, width: r - l, height: b2 - t });
  } else {
    files.forEach((f, i) => cropOf.set(f, boxes[i]));
  }
}
const trimmed = new Map();
async function trimmedOf(file) {
  if (!trimmed.has(file)) {
    const buf = await sharp(file).ensureAlpha().extract(cropOf.get(file) ?? (await outline(file))).png().toBuffer();
    trimmed.set(file, { buf, ...(await sharp(buf).metadata()) });
  }
  return trimmed.get(file);
}
let SCALE = 1;
async function computeScale(files) {
  let s = Infinity;
  for (const f of files) {
    const { height } = await trimmedOf(f);
    s = Math.min(s, (FH - PAD * 2) / height);
  }
  SCALE = Number.isFinite(s) ? s : 1;
}

async function fitFrame(file, nudge = 1) {
  const t = await trimmedOf(file);
  const w = Math.max(1, Math.round(t.width * SCALE * nudge));
  const h = Math.max(1, Math.round(t.height * SCALE * nudge));
  const scaled = await sharp(t.buf).resize(w, h).png().toBuffer();
  const inner = await sharp(scaled).resize(FW - PAD * 2, FH - PAD * 2, { fit: 'inside', withoutEnlargement: true }).png().toBuffer();
  const meta = await sharp(inner).metadata();
  // bottom-centre: the mascot stands on the frame's floor so poses line up when they change
  return sharp({ create: { width: FW, height: FH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: inner, left: Math.round((FW - meta.width) / 2), top: FH - PAD - meta.height }])
    .png().toBuffer();
}

async function rim(frame) {
  // a soft light halo behind the figure so the dark outfit reads on a dark background
  const { data, info } = await sharp(frame).extractChannel('alpha').blur(4).raw().toBuffer({ resolveWithObject: true });
  const halo = Buffer.alloc(info.width * info.height * 4);
  for (let i = 0; i < data.length; i++) halo.set([228, 228, 232, Math.min(255, data[i] * 2.2) * 0.55], i * 4);
  return sharp(halo, { raw: { width: info.width, height: info.height, channels: 4 } })
    .composite([{ input: frame }]).png().toBuffer();
}

const previous = fs.existsSync(path.join(DIST, 'manifest.json')) ? JSON.parse(fs.readFileSync(path.join(DIST, 'manifest.json'), 'utf8')) : null;
fs.rmSync(DIST, { recursive: true, force: true });
fs.mkdirSync(path.join(DIST, 'atlas'), { recursive: true });
// The app needs only mood / hold / first; the doc's action text stays in mascot.json and SHOTLIST.md.
const moments = Object.fromEntries(Object.entries(def.moments).map(([k, v]) => [k,
  { mood: v.mood, ...(v.hold ? { hold: true } : {}), ...(v.first ? { first: v.first } : {}) }]));
const manifest = { schema: def.schema, version: 0, hash: '', frame: def.frame, moods: {}, clips: {}, moments, assets: {} };
let total = 0;

const pngs = (dir) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.png')).map((f) => path.join(dir, f)) : []);
const artDirs = [...moodNames.filter(hasArt).map((n) => path.join(SRC, n)), ...clipNames.map((c) => path.join(CLIPS, c))];
for (const d of artDirs) for (const t of THEMES) await prepareSet(path.join(d, t));
await computeScale(artDirs.flatMap((d) => THEMES.flatMap((t) => pngs(path.join(d, t)))));
const sheet = { light: [], dark: [] };

// Frame folders → one atlas per theme, recorded on [out].
async function buildAtlases(name, dir, m, out, prefix) {
  {
    // longer renders are thinned evenly to MAX_FRAMES (and played slower), to keep the pack small
    const thin = (list) => (list.length <= MAX_FRAMES ? list
      : Array.from({ length: MAX_FRAMES }, (_, i) => list[Math.round((i * list.length) / MAX_FRAMES)]));
    const allLight = fs.readdirSync(path.join(dir, 'light')).filter((f) => f.endsWith('.png')).sort();
    const lightFiles = thin(allLight);
    const darkDir = path.join(dir, 'dark');
    const darkFiles = fs.existsSync(darkDir) ? thin(fs.readdirSync(darkDir).filter((f) => f.endsWith('.png')).sort()) : null;
    out.thinned = allLight.length / lightFiles.length;
    if (darkFiles && darkFiles.length !== lightFiles.length) fail(`mood "${name}": light and dark have different frame counts`);
    const frames = lightFiles.length;
    const cols = Math.min(frames, MAX_COLS);
    const rows = Math.ceil(frames / cols);
    out.frames = frames; out.cols = cols; out.loopFrom = m.loopFrom ?? 0; out.still = m.still ?? 0;
    out.atlas = {};
    for (const theme of THEMES) {
      const tiles = [];
      for (let i = 0; i < frames; i++) {
        let tile;
        if (theme === 'dark' && !darkFiles) tile = await rim(await fitFrame(path.join(dir, 'light', lightFiles[i]), m.scale ?? 1));
        else tile = await fitFrame(path.join(dir, theme, (theme === 'dark' ? darkFiles : lightFiles)[i]), m.scale ?? 1);
        sheet[theme].push({ name: lightFiles.length > 1 ? `${prefix}${name} ${i}` : `${prefix}${name}`, tile });
        tiles.push({ input: tile, left: (i % cols) * FW, top: Math.floor(i / cols) * FH });
      }
      const rel = `atlas/${prefix}${name}-${theme}.webp`;
      const buf = await sharp({ create: { width: cols * FW, height: rows * FH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
        .composite(tiles).webp({ quality: 70, alphaQuality: 80, effort: 6, smartSubsample: true }).toBuffer();
      if (buf.length > BUDGET_ATLAS) fail(`${rel}: ${(buf.length / 1024).toFixed(0)} KB is over the ${BUDGET_ATLAS / 1024} KB budget`);
      fs.writeFileSync(path.join(DIST, rel), buf);
      manifest.assets[rel] = crypto.createHash('sha256').update(buf).digest('hex');
      out.atlas[theme] = rel;
      total += buf.length;
    }
  }
}

for (const name of moodNames) {
  const m = def.moods[name];
  const out = { code: m.code, fps: m.fps ?? 12, motion: m.motion ?? 'none', face: m.face ?? 'smile', arms: m.arms ?? 'down', prop: m.prop ?? 'none' };
  if (m.fallback) out.fallback = m.fallback;
  if (hasArt(name)) {
    await buildAtlases(name, path.join(SRC, name), m, out, '');
    // a rendered animation (Blender: 4+ frames) carries its own movement and plays at render speed;
    // the procedural bob/hop and the slow keyframe fps are for stills and generated keyframes
    if (out.frames >= 4) { out.motion = m.artMotion ?? 'none'; out.fps = Math.max(1, Math.round((m.renderFps ?? 12) / out.thinned)); }
    delete out.thinned;
  }
  manifest.moods[name] = out;
}
// A clip's animation is baked into its frames, so no body motion is added on top.
const clipDefs = def.clips ?? {};
for (const key of clipNames) {
  const c = clipDefs[key] ?? {};
  const out = { fps: c.fps ?? 12, motion: c.motion ?? 'none' };
  await buildAtlases(key, path.join(CLIPS, key), c, out, 'moment-');
  if (out.frames >= 4) out.fps = Math.max(1, Math.round((c.renderFps ?? 12) / out.thinned));
  delete out.thinned;
  manifest.clips[key] = out;
}
// Contact sheets (not shipped): every frame side by side on its UI colour, to spot a pose that
// drifted from the character or sits at the wrong size.
async function contactSheet(theme, bg) {
  const tiles = sheet[theme];
  if (!tiles.length) return;
  const cols = 10, LABEL = 18;
  const rows = Math.ceil(tiles.length / cols);
  const parts = [];
  tiles.forEach((t, i) => {
    const x = (i % cols) * FW, y = Math.floor(i / cols) * (FH + LABEL);
    parts.push({ input: t.tile, left: x, top: y });
    const label = t.name.replace(/&/g, '&amp;').replace(/</g, '&lt;');
    parts.push({ input: Buffer.from(`<svg width="${FW}" height="${LABEL}"><text x="4" y="13" font-size="11" font-family="Helvetica" fill="${theme === 'light' ? '#333' : '#ccc'}">${label}</text></svg>`), left: x, top: y + FH });
  });
  await sharp({ create: { width: cols * FW, height: rows * (FH + LABEL), channels: 4, background: bg } })
    .composite(parts).png().toFile(path.join(PIPELINE_DIR, `contact-${theme}.png`));
}
await contactSheet('light', { r: 245, g: 245, b: 247, alpha: 1 });
await contactSheet('dark', { r: 18, g: 18, b: 20, alpha: 1 });

if (total > BUDGET_PACK) fail(`pack is ${(total / 1048576).toFixed(2)} MB, over the 4 MB budget`);

if (errors.length) { console.error(errors.map((e) => `✗ ${e}`).join('\n')); process.exit(1); }

// Version = UTC yyyymmddHHMM, so a newer pack always compares greater; hash says whether anything changed.
const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
manifest.version = Number(`${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}`);
manifest.hash = crypto.createHash('sha256')
  .update(JSON.stringify({ moods: manifest.moods, clips: manifest.clips, moments: manifest.moments, assets: manifest.assets })).digest('hex').slice(0, 16);
// an unchanged pack keeps its version, so a rebuild never makes every app re-download it
if (previous?.hash === manifest.hash) manifest.version = previous.version;
fs.writeFileSync(path.join(DIST, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
writeShotList();
writePrompts();
console.log(`✓ ${Object.keys(manifest.moods).length} moods, ${Object.keys(manifest.clips).length} clips, ${Object.keys(manifest.moments).length} moments, ` +
  `${Object.keys(manifest.assets).length} atlases, ${(total / 1024).toFixed(0)} KB · v${manifest.version} · ${manifest.hash}`);

// The render brief: every moment with the doc's action and the art it has today.
function writeShotList() {
  const rows = Object.entries(def.moments).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => {
    const art = manifest.clips[k] ? 'own clip' : manifest.moods[v.mood]?.atlas ? `${v.mood} art` : 'drawn';
    const cell = (t) => String(t ?? '').replace(/\|/g, '\\|');
    return `| \`${k}\` | ${def.moods[v.mood].code} ${v.mood} | ${v.hold ? 'loop while it lasts' : 'one-shot'} | ${cell(v.action)} | ${art} |`;
  });
  const moods = moodNames.map((n) => `| ${def.moods[n].code ?? ''} | ${n} | ${manifest.moods[n].atlas ? 'yes' : '—'} |`);
  fs.mkdirSync(DOCS, { recursive: true });
  fs.writeFileSync(path.join(DOCS, 'SHOTLIST.md'), `<!-- GENERATED by pipeline/tools/pack.mjs — edit mascot.json, not this file. -->
# Mascot shot list

Each row is one moment in the app. Render the **action** as a short full-body clip into
\`src/moments/<moment>/<light|dark>/000.png…\` (or, to cover every moment of a mood at once, into
\`src/<mood>/<light|dark>/\`). See ART.md for the frame spec. Art: *own clip* = this moment has its
own render; *<mood> art* = it borrows its mood's render; *drawn* = the app draws it in code for now.

## Moods (${moodNames.length})

| Code | Mood | Rendered |
| --- | --- | --- |
${moods.join('\n')}

## Moments (${rows.length})

| Moment | Mood | Plays | Action (from the moment map) | Art |
| --- | --- | --- | --- | --- |
${rows.join('\n')}
`);
}

// The image-generation brief: one prompt per image, named the way import.mjs files them.
function writePrompts() {
  const CHARACTER = 'a small chibi 3D mascot, glossy soft-clay / vinyl-toy render: a big round head whose ' +
    'face is a glossy black visor with glowing pink-to-orange gradient eyes (tall rounded ovals) and a wide ' +
    'glowing smile, a baseball cap, a hoodie with drawstrings, joggers, chunky white sneakers with yellow ' +
    'accents; about 2.5 heads tall';
  const FRAMING = 'Full body from the top of the cap to the soles of the sneakers, nothing cropped, feet on ' +
    'the bottom edge, centred, front three-quarter view, the same camera distance and height in every image. ' +
    'Soft studio key light. Background: flat solid chroma green #00B140 — no floor, no shadow, no gradient, ' +
    'no text, no card or frame. Portrait, 1024 × 1440.';
  const entries = [];
  const add = (file, what, pose, kfs, note) => entries.push({ file, what, pose, kfs, note });
  for (const n of moodNames) {
    const m = def.moods[n];
    if (m.pose) add(n, `${m.code ?? ''} ${n}`.trim(), m.pose, m.keyframes ?? [], m.keyframes ? `${m.fps ?? 12} fps` : '');
  }
  for (const [k, c] of Object.entries(def.clips ?? {})) add(`moment@${k}`, `moment ${k}`, c.pose, c.keyframes ?? [], `${c.fps ?? 12} fps`);
  const count = entries.reduce((a, e) => a + (1 + e.kfs.length) * 2, 0);
  const block = (e) => {
    const lines = [`### ${e.what}${e.note ? ` · ${e.note}` : ''}`, '',
      `**${e.file}-black.png**`, '', '```', `Same character as the reference image, exactly: ${CHARACTER}. All-black outfit (cap, hoodie, joggers). Pose: ${e.pose}. ${FRAMING}`, '```', ''];
    e.kfs.forEach((k, i) => lines.push(`**${e.file}-black-${i + 1}.png**`, '', '```', `The previous image again — same character, outfit, framing, light and background — changing only this: ${k}.`, '```', ''));
    lines.push(`**${e.file}-white.png${e.kfs.length ? ` … ${e.file}-white-${e.kfs.length}.png` : ''}** — for each black image above:`, '', '```',
      'The same image exactly — same pose, framing, props, face and sneakers — with only the outfit (cap, hoodie, joggers) changed to white.', '```', '');
    return lines.join('\n');
  };
  fs.mkdirSync(DOCS, { recursive: true });
  fs.writeFileSync(path.join(DOCS, 'PROMPTS.md'), `<!-- GENERATED by pipeline/tools/pack.mjs from the poses in mascot.json — edit those, not this file. -->
# Mascot image prompts

${count} images: every mood (${moodNames.length}), keyframes for the most-seen ones, and own clips for the firsts and the
busiest moments. Each file name is where \`tools/mascot/import.mjs\` puts the image.

## How to run it

1. Start one chat/session in your image tool and attach **studioshare-mascot.png** (the turnaround) as the
   character reference. Generate everything in that one session — drift grows across sessions.
2. Do the **black** image of a pose first, then its keyframes ("the previous image again…"), then the white
   versions ("the same image exactly…") — editing the image you already have keeps them matched.
3. Reject and regenerate an image whose face, cap or sneakers changed, or that is cropped. Keep the feet on
   the bottom edge and the camera distance the same; the pack scales every pose by one shared factor.
4. Save with the exact file names below into one folder, then:
   \`node tools/mascot/import.mjs <folder>\` → \`node tools/mascot/pack.mjs --sync\` → open
   \`Brand/mascot/contact-light.png\` / \`contact-dark.png\` and check every pose side by side.
   A pose that sits too big or small: add \`"scale": 0.95\` (or 1.05) to that mood in mascot.json.

Until every mood has its image, a mood without one shows the idle image — so do **idle** and **walk**
first, then the moods in the order below.

## Prompts

${entries.map(block).join('\n')}`);
}

// ---- generate language bindings for KMP and Web ---------------------------------------
const camel = (key) => key.split('.').map((p, i) => (i === 0 ? p : p[0].toUpperCase() + p.slice(1))).join('');
const keys = Object.keys(def.moments).sort();

// Kotlin (KMP)
const kt = `// GENERATED by pipeline/tools/pack.mjs from pipeline/spec/mascot.json — do not edit by hand.
// Moment keys are the contract between code and the mascot pack: append-only, never renamed.
package com.dfeverx.studioshare.mascot

object MascotMoments {
${keys.map((k) => `    const val ${camel(k).replace(/^./, (c) => c.toUpperCase())} = "${k}"`).join('\n')}

    val all: List<String> = listOf(
${keys.map((k) => `        "${k}",`).join('\n')}
    )
}
`;
fs.mkdirSync(path.dirname(KMP_KT), { recursive: true });
fs.writeFileSync(KMP_KT, kt);
console.log(`✓ Generated ${path.relative(PROJECT_ROOT, KMP_KT)}`);

// TypeScript (Web)
const ts = `// GENERATED by pipeline/tools/pack.mjs from pipeline/spec/mascot.json — do not edit by hand.
export const MascotMoments = {
${keys.map((k) => `  ${camel(k)}: '${k}',`).join('\n')}
} as const;

export type MascotMoment = (typeof MascotMoments)[keyof typeof MascotMoments];
`;
fs.mkdirSync(path.dirname(WEB_TS), { recursive: true });
fs.writeFileSync(WEB_TS, ts);
console.log(`✓ Generated ${path.relative(PROJECT_ROOT, WEB_TS)}`);

// Optional sync to external workspace apps if requested
if (SYNC_APPS) {
  function copyPack(dest) {
    fs.rmSync(dest, { recursive: true, force: true });
    fs.cpSync(DIST, dest, { recursive: true });
    console.log(`→ Synced to ${path.relative(WORKSPACE_ROOT, dest)}`);
  }
  if (fs.existsSync(path.dirname(APPS_KMP_RES))) copyPack(APPS_KMP_RES);
  if (fs.existsSync(path.dirname(APPS_WEB_PUBLIC))) copyPack(APPS_WEB_PUBLIC);
}
