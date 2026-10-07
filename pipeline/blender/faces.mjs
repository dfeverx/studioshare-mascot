// Draws the mascot's expressions as face textures for the 3D model: Brand/mascot/model/faces/<face>.png.
//   node tools/mascot/blender/faces.mjs
// One 1024 × 1024 image per face id the moods use (mascot.json → moods.*.face): glowing pink → orange
// shapes on the black visor, centred, so the model's visor UVs map this square onto the face. The
// render script swaps the image per mood and uses it as both colour and emission, so the face glows.
import fs from 'node:fs';
import path from 'node:path';
import { MASCOT, sharp } from '../lib.mjs';

const OUT = path.join(MASCOT, 'model', 'faces');
const S = 1024;
const EX = 372, EX2 = 652, EY = 430; // eye centres

const oval = (x, y, rx = 58, ry = 92) => `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="url(#g)"/>`;
const stroke = (d, w = 62) => `<path d="${d}" fill="none" stroke="url(#g)" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`;
const archUp = (x, y, w = 62) => stroke(`M ${x - 62} ${y + 24} Q ${x} ${y - 70} ${x + 62} ${y + 24}`, w);   // ∩ happy-closed eye
const archDown = (x, y, w = 50) => stroke(`M ${x - 60} ${y - 10} Q ${x} ${y + 50} ${x + 60} ${y - 10}`, w); // ∪ relaxed-closed eye
const line = (x, y, w = 54, half = 62, tilt = 0) => stroke(`M ${x - half} ${y - tilt} L ${x + half} ${y + tilt}`, w);
const smile = (w = 360, depth = 170, y = 640) => stroke(`M ${512 - w / 2} ${y} Q 512 ${y + depth} ${512 + w / 2} ${y}`, 72);
const frown = () => stroke('M 380 760 Q 512 650 644 760', 64);
const flat = () => stroke('M 430 720 Q 512 700 594 720', 56);
const o = (r = 46) => `<ellipse cx="512" cy="720" rx="${r}" ry="${r * 1.2}" fill="url(#g)"/>`;

const FACES = {
  smile: oval(EX, EY) + oval(EX2, EY) + smile(),
  happy: archUp(EX, EY) + archUp(EX2, EY) + smile(400, 210),
  wink: oval(EX, EY) + archUp(EX2, EY) + smile(),
  closed: archDown(EX, EY + 10) + archDown(EX2, EY + 10) + smile(300, 120),
  sleepy: line(EX, EY + 30, 44, 58, -8) + line(EX2, EY + 30, 44, 58, 8) + o(30),
  surprised: oval(EX, EY, 74, 100) + oval(EX2, EY, 74, 100) + o(50),
  worried: oval(EX, EY + 20, 48, 76) + oval(EX2, EY + 20, 48, 76) +
    line(EX, EY - 110, 34, 54, 18) + line(EX2, EY - 110, 34, 54, -18) + flat(),
  sad: oval(EX, EY + 20, 50, 78) + oval(EX2, EY + 20, 50, 78) + frown() +
    `<path d="M ${EX2 + 40} ${EY + 120} q 26 50 0 76 q -26 -26 0 -76 z" fill="#7fd3ff" opacity="0.9"/>`,
  focused: line(EX, EY, 60, 60, 10) + line(EX2, EY, 60, 60, -10) + smile(300, 110),
  thinking: oval(EX + 26, EY - 36, 52, 84) + oval(EX2 + 26, EY - 36, 52, 84) + stroke('M 470 710 Q 540 740 610 700', 56),
  cool: `<rect x="250" y="350" width="524" height="160" fill="#111" stroke="#2a2a2a" stroke-width="10"/>` +
    `<path d="M 290 380 L 360 380" stroke="url(#g)" stroke-width="18" stroke-linecap="round"/>` + smile(340, 140),
  scan: oval(EX, EY) + oval(EX2, EY) + smile(300, 120) +
    `<rect x="190" y="${EY - 8}" width="644" height="16" fill="url(#g)" opacity="0.95"/>`,
};

fs.mkdirSync(OUT, { recursive: true });
for (const [name, shapes] of Object.entries(FACES)) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${S}" height="${S}" viewBox="0 0 ${S} ${S}">
  <defs>
    <linearGradient id="g" gradientUnits="userSpaceOnUse" x1="0" y1="320" x2="0" y2="820">
      <stop offset="0" stop-color="#ff0f9b"/><stop offset="0.55" stop-color="#ff1f86"/><stop offset="1" stop-color="#ff6a1a"/>
    </linearGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="16" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <rect width="${S}" height="${S}" fill="#000000"/>
  <g filter="url(#glow)">${shapes}</g>
</svg>`;
  await sharp(Buffer.from(svg)).png().toFile(path.join(OUT, `${name}.png`));
}
console.log(`${Object.keys(FACES).length} faces → ${path.relative(MASCOT, OUT)}/`);
