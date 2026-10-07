// One-off: cuts the v0 still poses out of the 3D render sheets into Brand/mascot/src/<mood>/light/0.png.
// Usage: node tools/mascot/extract-v0.mjs ~/Downloads/gray.png
// The sheet is a 6 x 3 grid of poses on light cards; the card is keyed out by a flood fill from its
// border. Works for the dark outfit only — the white outfit sits too close to the card colour, so its
// v0 art is derived at pack time (see pack.mjs) until transparent renders exist.
import fs from 'node:fs';
import path from 'node:path';
import { MASCOT, sharp } from './lib.mjs';

const sheet = process.argv[2];
if (!sheet) { console.error('usage: extract-v0.mjs <sheet.png>'); process.exit(1); }

const COLS = [[11, 266], [272, 519], [529, 768], [773, 1019], [1029, 1269], [1280, 1521]];
const ROWS = [[21, 327], [337, 642], [651, 986]];
// Sheet position (row-major, 1-based) → mood. Positions not listed are not used.
const POSES = {
  1: 'ok', 2: 'happy', 3: 'idle', 4: 'celebrating', 5: 'connected', 7: 'scanning', 8: 'thinking',
  9: 'focused', 10: 'goodbye', 11: 'connecting', 12: 'proud', 13: 'sleepy', 14: 'excited',
  15: 'patient', 16: 'shy', 17: 'waiting', 18: 'carrying',
};
const T = 10; // max channel distance from the card colour that still counts as card

const { data, info } = await sharp(sheet).removeAlpha().raw().toBuffer({ resolveWithObject: true });
const W = info.width;
let n = 0;
for (const [y0, y1] of ROWS) for (const [x0, x1] of COLS) {
  n++;
  const mood = POSES[n];
  if (!mood) continue;
  const ix0 = x0 + 4, iy0 = y0 + 4, w = x1 - x0 - 8, h = y1 - y0 - 8;
  const get = (x, y) => { const i = ((iy0 + y) * W + ix0 + x) * 3; return [data[i], data[i + 1], data[i + 2]]; };
  const border = [];
  for (let x = 0; x < w; x++) border.push(get(x, 0), get(x, h - 1));
  for (let y = 0; y < h; y++) border.push(get(0, y), get(w - 1, y));
  const med = (k) => border.map((p) => p[k]).sort((a, b) => a - b)[border.length >> 1];
  const bg = [med(0), med(1), med(2)];
  const dist = (p) => Math.max(Math.abs(p[0] - bg[0]), Math.abs(p[1] - bg[1]), Math.abs(p[2] - bg[2]));
  const isBg = new Uint8Array(w * h);
  const stack = [];
  const push = (x, y) => { const k = y * w + x; if (!isBg[k] && dist(get(x, y)) <= T) { isBg[k] = 1; stack.push(k); } };
  for (let x = 0; x < w; x++) { push(x, 0); push(x, h - 1); }
  for (let y = 0; y < h; y++) { push(0, y); push(w - 1, y); }
  while (stack.length) {
    const k = stack.pop(); const x = k % w, y = (k / w) | 0;
    if (x > 0) push(x - 1, y); if (x < w - 1) push(x + 1, y); if (y > 0) push(x, y - 1); if (y < h - 1) push(x, y + 1);
  }
  const rgba = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const k = y * w + x, p = get(x, y);
    let a = 255;
    if (isBg[k]) a = 0;
    else {
      let edge = false;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx >= 0 && yy >= 0 && xx < w && yy < h && isBg[yy * w + xx]) edge = true;
      }
      if (edge) a = Math.max(90, Math.min(255, Math.round((dist(p) / (T * 3)) * 255)));
    }
    rgba.set([p[0], p[1], p[2], a], k * 4);
  }
  const dir = path.join(MASCOT, 'src', mood, 'light');
  fs.mkdirSync(dir, { recursive: true });
  await sharp(rgba, { raw: { width: w, height: h, channels: 4 } }).trim({ threshold: 0 }).png().toFile(path.join(dir, '0.png'));
  console.log(`${mood} ← pose ${n}`);
}
