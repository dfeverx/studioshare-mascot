// Imports generated mascot images into Brand/mascot/src/, keying out their background.
//   node tools/mascot/import.mjs <folder of images> [--dry]
//
// File names say where each image goes (see Brand/mascot/PROMPTS.md):
//   <mood>-black.png, <mood>-white.png              one pose: src/<mood>/<light|dark>/000.png
//   <mood>-black-1.png, <mood>-black-2.png …        keyframes, in order: 000.png, 001.png …
//   moment@<moment.key>-black[-N].png               a moment's own clip: src/moments/<key>/…
// The black outfit is drawn on light UI ("light"), the white outfit on dark UI ("dark").
//
// Background: a render that already has transparency is kept as it is. Otherwise the background is the
// colour around the image's border (ask for flat chroma green #00B140 — never white or grey: the white outfit and its shadows
// would key out with them). It is removed by a flood fill from the border, with a soft, colour-corrected
// edge so no fringe of the old background is left. Then `node tools/mascot/pack.mjs --sync`.
import fs from 'node:fs';
import path from 'node:path';
import { PIPELINE_DIR, MASCOT_SPEC, SRC, sharp } from './lib.mjs';

const dir = process.argv[2];
const DRY = process.argv.includes('--dry');
if (!dir || !fs.existsSync(dir)) { console.error('usage: import.mjs <folder of images> [--dry]'); process.exit(1); }
const def = JSON.parse(fs.readFileSync(MASCOT_SPEC, 'utf8'));
const NAME = /^(?:moment@([a-z][a-zA-Z0-9.]*)|([a-z][a-zA-Z0-9]*))-(black|white)(?:-(\d+))?\.(png|jpe?g|webp)$/i;

const HARD = 26; // within this distance of the background colour: background
const SOFT = 70; // beyond this: fully the mascot; in between, a partial edge

async function key(file) {
  const img = sharp(file).ensureAlpha();
  const { data, info } = await img.raw().toBuffer({ resolveWithObject: true });
  const { width: W, height: H } = info;
  const px = (x, y) => (y * W + x) * 4;
  // already transparent around the edge → nothing to key
  const corners = [px(0, 0), px(W - 1, 0), px(0, H - 1), px(W - 1, H - 1)];
  if (corners.every((i) => data[i + 3] < 10)) return sharp(data, { raw: info }).png().toBuffer();

  const border = [];
  for (let x = 0; x < W; x += 2) border.push(px(x, 0), px(x, H - 1));
  for (let y = 0; y < H; y += 2) border.push(px(0, y), px(W - 1, y));
  const med = (k) => border.map((i) => data[i + k]).sort((a, b) => a - b)[border.length >> 1];
  const bg = [med(0), med(1), med(2)];
  const dist = (i) => Math.max(Math.abs(data[i] - bg[0]), Math.abs(data[i + 1] - bg[1]), Math.abs(data[i + 2] - bg[2]));

  // flood from the border through everything close to the background colour (holes enclosed by the
  // figure — between an arm and the body — are reached through their own opening, or stay)
  const isBg = new Uint8Array(W * H);
  const stack = [];
  const push = (x, y) => {
    const k = y * W + x;
    if (!isBg[k] && dist(k * 4) < SOFT) { isBg[k] = 1; stack.push(k); }
  };
  for (let x = 0; x < W; x++) { push(x, 0); push(x, H - 1); }
  for (let y = 0; y < H; y++) { push(0, y); push(W - 1, y); }
  while (stack.length) {
    const k = stack.pop(); const x = k % W, y = (k / W) | 0;
    if (x > 0) push(x - 1, y); if (x < W - 1) push(x + 1, y); if (y > 0) push(x, y - 1); if (y < H - 1) push(x, y + 1);
  }
  const greenish = bg[1] > bg[0] + 30 && bg[1] > bg[2] + 30;
  for (let k = 0; k < W * H; k++) {
    if (!isBg[k]) continue;
    const i = k * 4, d = dist(i);
    if (d <= HARD) { data[i + 3] = 0; continue; }
    // edge pixel: part mascot, part background — un-mix the background out of its colour
    const a = (d - HARD) / (SOFT - HARD);
    for (let c = 0; c < 3; c++) data[i + c] = Math.max(0, Math.min(255, Math.round((data[i + c] - (1 - a) * bg[c]) / a)));
    data[i + 3] = Math.round(a * 255);
  }
  // green spill on the outline of a green-screen render
  if (greenish) {
    for (let k = 0; k < W * H; k++) {
      const i = k * 4;
      if (data[i + 3] === 0) continue;
      const cap = Math.max(data[i], data[i + 2]);
      if (data[i + 1] > cap) data[i + 1] = cap;
    }
  }
  return sharp(data, { raw: info }).trim({ threshold: 1 }).png().toBuffer();
}

const plan = new Map(); // target dir → [{ n, file }]
const skipped = [];
for (const f of fs.readdirSync(dir).sort()) {
  const m = f.match(NAME);
  if (!m) { if (!f.startsWith('.')) skipped.push(`${f}: name doesn't follow <mood>-black|white[-N]`); continue; }
  const [, moment, mood, outfit, n] = m;
  if (moment && !def.moments[moment]) { skipped.push(`${f}: no moment "${moment}"`); continue; }
  if (mood && !def.moods[mood]) { skipped.push(`${f}: no mood "${mood}"`); continue; }
  const theme = outfit.toLowerCase() === 'black' ? 'light' : 'dark';
  const target = moment ? path.join(MASCOT, 'src', 'moments', moment, theme) : path.join(MASCOT, 'src', mood, theme);
  if (!plan.has(target)) plan.set(target, []);
  plan.get(target).push({ n: Number(n ?? 0), file: path.join(dir, f) });
}

for (const [target, items] of plan) {
  items.sort((a, b) => a.n - b.n);
  if (!DRY) {
    // a re-import replaces the folder, so a pose with fewer keyframes doesn't keep stale ones
    fs.rmSync(target, { recursive: true, force: true });
    fs.mkdirSync(target, { recursive: true });
  }
  for (const [i, it] of items.entries()) {
    const out = path.join(target, `${String(i).padStart(3, '0')}.png`);
    if (!DRY) fs.writeFileSync(out, await key(it.file));
    console.log(`${path.basename(it.file)} → ${path.relative(PIPELINE_DIR, out)}`);
  }
}
for (const s of skipped) console.warn(`skipped ${s}`);
console.log(DRY ? 'dry run: nothing written' : 'next: npm run pack, then look at pipeline/contact-light.png and contact-dark.png');
