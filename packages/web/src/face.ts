// The StudioShare app-icon face as a character, drawn on a 2D canvas — a line-for-line port of
// packages/kmp/.../face/StudioFaceExpression.kt + StudioFaceDrawing.kt, so the web and the apps
// show the same face for the same mood. No images: nothing to download.

import { mascotMoodMotions, type HandGesture } from './moments';

export type EyeShape =
  | 'teardrop' | 'happy' | 'closed' | 'sleepy' | 'wide' | 'focused' | 'worried' | 'wink' | 'look' | 'scan';
export type MouthShape = 'smile' | 'small' | 'grin' | 'flat' | 'smallO' | 'open' | 'frown' | 'wavy';
export type Accent =
  | 'sparkles' | 'question' | 'exclaim' | 'sweat' | 'zzz' | 'check' | 'heart' | 'dots' | 'lock' | 'progress';

export interface FaceExpression {
  eyes: EyeShape;
  mouth: MouthShape;
  accent?: Accent;
}

const e = (eyes: EyeShape, mouth: MouthShape, accent?: Accent): FaceExpression => ({ eyes, mouth, accent });

/** Every mood in spec/mascot.json. Keep in step with StudioFaceExpressions.byMood in the KMP library. */
export const faceExpressions: Record<string, FaceExpression> = {
  idle: e('teardrop', 'smile'),
  waiting: e('look', 'small', 'dots'),
  shy: e('happy', 'small', 'heart'),
  happy: e('happy', 'grin', 'sparkles'),
  celebrating: e('happy', 'grin', 'sparkles'),
  proud: e('focused', 'smile', 'sparkles'),
  curious: e('look', 'smallO', 'question'),
  excited: e('wide', 'open', 'exclaim'),
  focused: e('focused', 'flat'),
  carrying: e('focused', 'small'),
  thinking: e('look', 'flat', 'question'),
  searching: e('scan', 'flat'),
  connecting: e('teardrop', 'small', 'dots'),
  connected: e('happy', 'grin', 'check'),
  disconnected: e('worried', 'frown'),
  sleepy: e('sleepy', 'smallO', 'zzz'),
  hot: e('worried', 'wavy', 'sweat'),
  patient: e('sleepy', 'small', 'dots'),
  oops: e('worried', 'wavy', 'sweat'),
  sad: e('worried', 'frown'),
  locked: e('worried', 'flat', 'lock'),
  careful: e('wide', 'smallO', 'exclaim'),
  goodbye: e('happy', 'smile', 'heart'),
  ok: e('wink', 'smile', 'check'),
  scanning: e('scan', 'flat'),
  walk: e('teardrop', 'smile'),
  glance: e('wink', 'smile'),
  uploading: e('focused', 'flat', 'progress'),
  sending: e('happy', 'small', 'progress'),
  capturing: e('wide', 'smile', 'sparkles'),
};

export const expressionFor = (mood: string): FaceExpression => faceExpressions[mood] ?? faceExpressions.idle;

// ---- continuous parameters (so mood changes tween) ------------------------------------------

export interface FaceParams {
  open: number; arc: number; tilt: number; eyeScale: number; lookX: number; lookY: number;
  lid: number; lidTilt: number; wink: number; scan: number;
  curve: number; width: number; thick: number; fill: number; oval: number; ovalSize: number; wavy: number;
}

const BASE: FaceParams = {
  open: 1, arc: 0, tilt: 0, eyeScale: 1, lookX: 0, lookY: 0, lid: 0, lidTilt: 0, wink: 0, scan: 0,
  curve: 1, width: 1, thick: 1, fill: 0, oval: 0, ovalSize: 1, wavy: 0,
};

export function paramsOf(x: FaceExpression): FaceParams {
  const eyes: Partial<FaceParams> = {
    teardrop: {}, happy: { arc: 1 }, closed: { arc: -1 }, sleepy: { lid: 0.55 }, wide: { eyeScale: 1.2 },
    focused: { lid: 0.36 }, worried: { lid: 0.3, lidTilt: 22 }, wink: { wink: 1 },
    look: { lookX: 0.8, lookY: -0.6 }, scan: { scan: 1 },
  }[x.eyes];
  const mouth: Partial<FaceParams> = {
    smile: {}, small: { curve: 0.75, width: 0.5, thick: 0.8 }, grin: { width: 1.04, fill: 1 },
    flat: { curve: 0.1, width: 0.5, thick: 0.75 }, smallO: { curve: 0.4, width: 0.4, oval: 1, ovalSize: 0.75 },
    open: { curve: 0.4, width: 0.4, oval: 1, ovalSize: 1.05 }, frown: { curve: -0.7, width: 0.55, thick: 0.8 },
    wavy: { curve: 0, width: 0.62, thick: 0.6, wavy: 1 },
  }[x.mouth];
  return { ...BASE, ...eyes, ...mouth };
}

const l = (a: number, b: number, f: number) => a + (b - a) * f;

export function lerpParams(a: FaceParams, b: FaceParams, f: number): FaceParams {
  const out = { ...a };
  for (const k of Object.keys(a) as (keyof FaceParams)[]) out[k] = l(a[k], b[k], f);
  return out;
}

// ---- body motion (port of MascotMotion.kt) ---------------------------------------------------

interface Pose { dx: number; dy: number; sx: number; sy: number; rot: number }
const TAU = Math.PI * 2;
const still: Pose = { dx: 0, dy: 0, sx: 1, sy: 1, rot: 0 };

function breathe(t: number, period: number, depth: number): Pose {
  const s = depth * (0.5 + 0.5 * Math.sin((t * TAU) / period));
  return { ...still, sy: 1 + s, sx: 1 - s * 0.4 };
}
function hops(t: number, height: number, period: number, count: number): Pose {
  if (t >= period * count) return still;
  const phase = (t % period) / period;
  const squash = phase < 0.12 || phase > 0.88 ? 0.06 : 0;
  return { ...still, dy: -height * Math.sin(phase * Math.PI), sy: 1 - squash, sx: 1 + squash };
}

export function motionPose(motion: string, t: number): Pose {
  switch (motion) {
    case 'breathe': return breathe(t, 3.2, 0.025);
    case 'slow': return breathe(t, 5, 0.03);
    case 'sway': return { ...still, rot: 3.5 * Math.sin((t * TAU) / 3) };
    case 'hop': return hops(t, 0.08, 0.55, 2);
    case 'jump': return { ...hops(t, 0.16, 0.7, 2), rot: t < 1.4 ? 4 * Math.sin((t * TAU) / 0.7) : 0 };
    case 'pop': {
      const s = t > 1.2 ? 1 : 1 - 0.15 * Math.exp(-5 * t) * Math.cos(t * 14);
      return { ...still, sx: s, sy: s };
    }
    case 'nod': return { ...still, rot: t < 1.2 ? 6 * Math.sin((t * TAU) / 0.6) * Math.exp(-1.5 * t) : 0 };
    case 'shake': return { ...still, dx: t < 0.9 ? 0.04 * Math.sin(t * TAU * 6) * (1 - t / 0.9) : 0 };
    case 'trot': return { ...still, dy: -0.035 * Math.abs(Math.sin((t * TAU) / 0.5)), rot: 2 * Math.sin((t * TAU) / 0.5) };
    case 'droop': return { ...breathe(t, 4.5, 0.02), rot: -2 };
    default: return still;
  }
}

/** Where to look: x and y each −1..1, right and down positive. */
export interface Gaze { x: number; y: number }

/**
 * Which way to look at something `dx`, `dy` CSS pixels away: each axis on its own, turning hard for
 * a nearby offset and settling as it grows — so a pointer far below and a little to the side still
 * gets a real sideways look. Same curve as `gazeToward` in the KMP library.
 */
export const gazeToward = (dx: number, dy: number): Gaze => ({ x: Math.tanh(dx / 260), y: Math.tanh(dy / 200) });

const hash = (n: number, seed: number) => {
  const j = Math.sin(n * seed) * 43758.547;
  return j - Math.floor(j);
};

/** One blink, 0 open … 1 shut: down in 70 ms, back up in 130 ms. */
function blinkShape(dt: number): number {
  if (dt < 0 || dt > 0.2) return 0;
  if (dt < 0.07) {
    const u = dt / 0.07;
    return u * u * (3 - 2 * u);
  }
  const u = 1 - (dt - 0.07) / 0.13;
  return u * u;
}

/** Blink, 0 open … 1 shut: 2.2–5.4 s apart, and about one in five a quick double. Pure in t. */
export function blinkAt(t: number): number {
  const slot = 3.8;
  const n = Math.floor(t / slot);
  let b = 0;
  for (const k of [n - 1, n]) {
    const start = k * slot + hash(k, 12.9898) * 1.6;
    b = Math.max(b, blinkShape(t - start));
    if (hash(k, 78.233) < 0.22) b = Math.max(b, blinkShape(t - start - 0.23));
  }
  return b;
}

// ---- drawing ---------------------------------------------------------------------------------

export const studioFacePalette = {
  body: '#000000',
  glow: '#FF4FD8',
  eyeTop: '#FF9147',
  eyeBottom: '#FF0FA8',
  mouthTop: '#FFC21A',
  mouthBottom: '#FF00AE',
  mark: '#FFFFFF',
};
const P = studioFacePalette;

type Ctx = CanvasRenderingContext2D;
const deg = (d: number) => (d * Math.PI) / 180;

function withAlpha(ctx: Ctx, a: number, draw: () => void) {
  if (a <= 0.01) return;
  ctx.save();
  ctx.globalAlpha *= a;
  draw();
  ctx.restore();
}

function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

function vGrad(ctx: Ctx, top: string, bottom: string, y0: number, y1: number) {
  const g = ctx.createLinearGradient(0, y0, 0, y1);
  g.addColorStop(0, top);
  g.addColorStop(1, bottom);
  return g;
}

function teardrop(w: number, h: number): Path2D {
  const p = new Path2D();
  p.moveTo(0, -h / 2);
  p.bezierCurveTo(w * 0.3, -h / 2, w / 2, -h * 0.16, w / 2, h * 0.12);
  p.bezierCurveTo(w / 2, h * 0.36, w * 0.28, h / 2, 0, h / 2);
  p.bezierCurveTo(-w * 0.28, h / 2, -w / 2, h * 0.36, -w / 2, h * 0.12);
  p.bezierCurveTo(-w / 2, -h * 0.16, -w * 0.3, -h / 2, 0, -h / 2);
  p.closePath();
  return p;
}

function drawBody(ctx: Ctx, s: number, glow: boolean) {
  const r = s * 0.22;
  if (glow) {
    for (let i = 6; i >= 1; i--) {
      const g = s * 0.011 * i;
      ctx.strokeStyle = P.glow;
      ctx.lineWidth = s * 0.012;
      withAlpha(ctx, 0.07 * (1 - i / 7), () => {
        roundRect(ctx, -s / 2 - g, -s / 2 - g, s + 2 * g, s + 2 * g, r + g);
        ctx.stroke();
      });
    }
  }
  ctx.fillStyle = P.body;
  roundRect(ctx, -s / 2, -s / 2, s, s, r);
  ctx.fill();
  if (glow) {
    ctx.strokeStyle = P.glow;
    ctx.lineWidth = s * 0.008;
    withAlpha(ctx, 0.35, () => {
      roundRect(ctx, -s / 2, -s / 2, s, s, r);
      ctx.stroke();
    });
  }
}

function drawEyes(ctx: Ctx, s: number, p: FaceParams, t: number, gaze?: Gaze | null) {
  const blink = blinkAt(t);
  const scan = p.scan * Math.sin(t * 2.4) * 0.9;
  // a gaze replaces the mood's look and the idle drift; the eyes lead the turn a little (barely
  // when looking down, where the mouth sits right below them)
  const lookX = gaze ? scan + gaze.x * 0.8 * (1 - p.scan) : p.lookX + scan + 0.12 * Math.sin(t * 0.7) * (1 - p.scan);
  const lookY = gaze ? gaze.y * (gaze.y > 0 ? 0.2 : 0.9) : p.lookY + 0.08 * Math.sin(t * 0.53);
  const w = s * 0.16 * p.eyeScale;
  const h = s * 0.23 * p.eyeScale;
  for (const side of [-1, 1]) {
    const cx = side * s * 0.19 + lookX * s * 0.045;
    const cy = -s * 0.186 + lookY * s * 0.035;
    const arc = side > 0 ? l(p.arc, 1, p.wink) : p.arc;
    const lean = -side * (15 - p.tilt);
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(deg(lean));
    const grad = vGrad(ctx, P.eyeTop, P.eyeBottom, -h / 2, h / 2);
    const dropAlpha = 1 - Math.abs(arc);
    withAlpha(ctx, dropAlpha, () => {
      const open = Math.max(p.open * (1 - blink), 0.08);
      ctx.save();
      ctx.translate(0, h * 0.15);
      ctx.scale(1, open);
      ctx.translate(0, -h * 0.15);
      const drop = teardrop(w, h);
      if (p.lid > 0.01) {
        // keep only what is below the lid; level in the face, dropping outward when sad
        const ey = -h / 2 + p.lid * h;
        const ang = deg(-lean + side * p.lidTilt);
        const ax = Math.cos(ang) * 4 * w, ay = Math.sin(ang) * 4 * w;
        const dx = -Math.sin(ang) * 4 * h, dy = Math.cos(ang) * 4 * h;
        const below = new Path2D();
        below.moveTo(-ax, ey - ay);
        below.lineTo(ax, ey + ay);
        below.lineTo(ax + dx, ey + ay + dy);
        below.lineTo(-ax + dx, ey - ay + dy);
        below.closePath();
        ctx.clip(below);
      }
      ctx.fillStyle = grad;
      ctx.fill(drop);
      ctx.restore();
    });
    if (Math.abs(arc) > 0.01) {
      withAlpha(ctx, Math.abs(arc), () => {
        ctx.rotate(deg(-lean));
        ctx.beginPath();
        if (arc > 0) {
          ctx.moveTo(-w * 0.62, h * 0.12);
          ctx.quadraticCurveTo(0, -h * 0.42, w * 0.62, h * 0.12);
        } else {
          ctx.moveTo(-w * 0.62, -h * 0.05);
          ctx.quadraticCurveTo(0, h * 0.4, w * 0.62, -h * 0.05);
        }
        ctx.strokeStyle = grad;
        ctx.lineWidth = w * 0.42;
        ctx.lineCap = 'round';
        ctx.stroke();
      });
    }
    ctx.restore();
  }
}

type Pt = [number, number];

/** A variable-width ribbon with round ends along `at`, as one simple polygon (alpha-safe). */
function ribbon(at: (u: number) => Pt, radius: (u: number) => number, n = 40): Path2D {
  const left: Pt[] = [], right: Pt[] = [], normals: Pt[] = [];
  for (let i = 0; i <= n; i++) {
    const u = i / n;
    const a = at(Math.max(u - 0.01, 0)), b = at(Math.min(u + 0.01, 1));
    const dx = b[0] - a[0], dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1;
    const nrm: Pt = [-dy / len, dx / len];
    const p = at(u), r = radius(u);
    left.push([p[0] + nrm[0] * r, p[1] + nrm[1] * r]);
    right.push([p[0] - nrm[0] * r, p[1] - nrm[1] * r]);
    normals.push(nrm);
  }
  const path = new Path2D();
  const cap = (c: Pt, nrm: Pt, r: number, forward: boolean) => {
    const start = Math.atan2(nrm[1], nrm[0]) + (forward ? 0 : Math.PI);
    for (let i = 1; i <= 10; i++) {
      const a = start - (Math.PI * i) / 10;
      path.lineTo(c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r);
    }
  };
  path.moveTo(...left[0]);
  for (let i = 1; i <= n; i++) path.lineTo(...left[i]);
  cap(at(1), normals[n], radius(1), true);
  for (let i = n; i >= 0; i--) path.lineTo(...right[i]);
  cap(at(0), normals[0], radius(0), false);
  path.closePath();
  return path;
}

function cubic(p0: Pt, p1: Pt, p2: Pt, p3: Pt, u: number): Pt {
  const v = 1 - u;
  const k0 = v * v * v, k1 = 3 * v * v * u, k2 = 3 * v * u * u, k3 = u * u * u;
  return [p0[0] * k0 + p1[0] * k1 + p2[0] * k2 + p3[0] * k3, p0[1] * k0 + p1[1] * k1 + p2[1] * k2 + p3[1] * k3];
}

function drawMouth(ctx: Ctx, s: number, p: FaceParams) {
  const mid = s * 0.13;
  const grad = vGrad(ctx, P.mouthTop, P.mouthBottom, 0, s * 0.3);
  const half = s * 0.228 * p.width;
  const depth = s * 0.16 * p.curve;
  const y0 = mid - depth / 2;
  const p0: Pt = [-half, y0], p3: Pt = [half, y0];
  const c1: Pt = [-half, y0 + depth * 1.33], c2: Pt = [half, y0 + depth * 1.33];
  const rEnd = s * 0.075 * p.thick;
  // the icon's smile swells at the bottom; a near-straight mouth stays even (or it creases)
  const rMid = l(rEnd, s * 0.092 * p.thick, Math.min(Math.abs(p.curve), 1));
  ctx.fillStyle = grad;

  const lineAlpha = (1 - p.oval) * (1 - p.wavy);
  if (p.fill > 0.01) {
    withAlpha(ctx, p.fill * lineAlpha, () => {
      const inside = new Path2D();
      inside.moveTo(...p0);
      inside.bezierCurveTo(c1[0], c1[1], c2[0], c2[1], p3[0], p3[1]);
      inside.closePath();
      ctx.fill(inside);
    });
  }
  withAlpha(ctx, lineAlpha, () =>
    ctx.fill(ribbon((u) => cubic(p0, c1, c2, p3, u), (u) => l(rEnd, rMid, Math.sin(Math.PI * u)))),
  );
  withAlpha(ctx, p.wavy, () =>
    ctx.fill(ribbon((u) => [-half + 2 * half * u, mid + s * 0.035 * Math.sin(u * 4 * Math.PI)], () => rEnd)),
  );
  withAlpha(ctx, p.oval, () => {
    const ow = s * 0.17 * p.ovalSize, oh = s * 0.21 * p.ovalSize;
    ctx.beginPath();
    ctx.ellipse(0, mid, ow / 2, oh / 2, 0, 0, TAU);
    ctx.fill();
  });
}

function star(cx: number, cy: number, outer: number, inner: number): Path2D {
  const p = new Path2D();
  for (let i = 0; i < 8; i++) {
    const r = i % 2 === 0 ? outer : inner;
    const a = -Math.PI / 2 + (i * Math.PI) / 4;
    if (i === 0) p.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
    else p.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
  }
  p.closePath();
  return p;
}

function drawAccent(ctx: Ctx, a: Accent, ax: number, ay: number, s: number, t: number, alpha: number, progress?: number | null) {
  const u = s * 0.13;
  const grad = ctx.createLinearGradient(ax - u, ay - u, ax + u, ay + u);
  grad.addColorStop(0, P.mouthTop);
  grad.addColorStop(1, P.eyeBottom);
  withAlpha(ctx, alpha, () => {
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.fillStyle = grad;
    ctx.strokeStyle = grad;
    ctx.lineWidth = u * 0.26;
    switch (a) {
      case 'sparkles':
        [[0, 0], [u * 0.95, u * 0.75]].forEach(([ox, oy], i) => {
          const k = (i === 0 ? 1 : 0.55) * (0.8 + 0.2 * Math.sin(t * 5 + i * 2));
          ctx.fill(star(ax + ox, ay + oy, u * 0.8 * k, u * 0.22 * k));
        });
        break;
      case 'question': {
        ctx.beginPath();
        ctx.moveTo(ax - u * 0.45, ay - u * 0.35);
        ctx.bezierCurveTo(ax - u * 0.45, ay - u, ax + u * 0.55, ay - u, ax + u * 0.5, ay - u * 0.35);
        ctx.bezierCurveTo(ax + u * 0.48, ay + u * 0.05, ax, ay, ax, ay + u * 0.4);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(ax, ay + u * 0.85, u * 0.16, 0, TAU);
        ctx.fill();
        break;
      }
      case 'exclaim': {
        const bob = Math.sin(t * 6) * u * 0.06;
        ctx.lineWidth = u * 0.3;
        ctx.beginPath();
        ctx.moveTo(ax, ay - u * 0.9 + bob);
        ctx.lineTo(ax, ay + u * 0.3 + bob);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(ax, ay + u * 0.85 + bob, u * 0.17, 0, TAU);
        ctx.fill();
        break;
      }
      case 'sweat': {
        const drip = (t * 0.6) % 1;
        ctx.save();
        ctx.translate(ax - u * 0.2, ay + u * 0.2 + drip * u * 0.6);
        ctx.globalAlpha *= 1 - drip * 0.7;
        ctx.fillStyle = P.mark;
        ctx.fill(teardrop(u * 0.75, u * 1.1));
        ctx.restore();
        break;
      }
      case 'zzz':
        ctx.strokeStyle = P.mark;
        ctx.lineWidth = u * 0.16;
        for (let i = 0; i <= 2; i++) {
          const ph = (t * 0.5 + i / 3) % 1;
          const z = u * (0.35 + 0.25 * i);
          const cx = ax + u * 0.6 * i - u * 0.4, cy = ay - ph * u * 1.2 + u * 0.4;
          withAlpha(ctx, 1 - ph, () => {
            ctx.beginPath();
            ctx.moveTo(cx - z / 2, cy - z / 2);
            ctx.lineTo(cx + z / 2, cy - z / 2);
            ctx.lineTo(cx - z / 2, cy + z / 2);
            ctx.lineTo(cx + z / 2, cy + z / 2);
            ctx.stroke();
          });
        }
        break;
      case 'check':
        ctx.lineWidth = u * 0.32;
        ctx.beginPath();
        ctx.moveTo(ax - u * 0.7, ay);
        ctx.lineTo(ax - u * 0.15, ay + u * 0.55);
        ctx.lineTo(ax + u * 0.8, ay - u * 0.6);
        ctx.stroke();
        break;
      case 'heart': {
        const r = u * 0.9 * (1 + 0.08 * Math.sin(t * 7));
        ctx.beginPath();
        ctx.moveTo(ax, ay + r * 0.75);
        ctx.bezierCurveTo(ax - r * 1.2, ay - r * 0.1, ax - r * 0.6, ay - r, ax, ay - r * 0.4);
        ctx.bezierCurveTo(ax + r * 0.6, ay - r, ax + r * 1.2, ay - r * 0.1, ax, ay + r * 0.75);
        ctx.fill();
        break;
      }
      case 'dots':
        ctx.fillStyle = P.mark;
        for (let i = 0; i <= 2; i++) {
          const ph = Math.max(Math.sin(t * 4 - i * 0.7), 0);
          withAlpha(ctx, 0.4 + 0.6 * ph, () => {
            ctx.beginPath();
            ctx.arc(ax + (i - 1) * u * 0.6, ay - ph * u * 0.35, u * 0.2, 0, TAU);
            ctx.fill();
          });
        }
        break;
      case 'lock':
        ctx.fillStyle = P.mark;
        ctx.strokeStyle = P.mark;
        ctx.fillRect(ax - u * 0.6, ay - u * 0.1, u * 1.2, u * 0.95);
        ctx.lineWidth = u * 0.2;
        ctx.lineCap = 'butt';
        ctx.beginPath();
        ctx.moveTo(ax - u * 0.38, ay - u * 0.1);
        ctx.lineTo(ax - u * 0.38, ay - u * 0.45);
        ctx.bezierCurveTo(ax - u * 0.38, ay - u, ax + u * 0.38, ay - u, ax + u * 0.38, ay - u * 0.45);
        ctx.lineTo(ax + u * 0.38, ay - u * 0.1);
        ctx.stroke();
        break;
      case 'progress': {
        const r = u * 0.85;
        ctx.fillStyle = P.body;
        ctx.beginPath();
        ctx.arc(ax, ay, r * 1.25, 0, TAU);
        ctx.fill();
        ctx.lineWidth = u * 0.24;
        ctx.strokeStyle = 'rgba(255,255,255,0.2)';
        ctx.beginPath();
        ctx.arc(ax, ay, r, 0, TAU);
        ctx.stroke();
        ctx.strokeStyle = grad;
        ctx.beginPath();
        if (progress != null) {
          const f = Math.min(Math.max(progress, 0), 1);
          ctx.arc(ax, ay, r, -Math.PI / 2, -Math.PI / 2 + TAU * f);
        } else {
          const a0 = deg((t * 300) % 360);
          ctx.arc(ax, ay, r, a0, a0 + Math.PI / 2);
        }
        ctx.stroke();
        break;
      }
    }
  });
}

// ---- hands (port of StudioFaceHands.kt) -------------------------------------------------------

/**
 * Each gesture's life: how long it plays (hands out to hands in), the instant drawn when the face
 * isn't animating, and whether the hands go over the face rather than behind it.
 */
export const handGestures: Record<HandGesture, { seconds: number; still: number; inFront?: boolean }> = {
  wave: { seconds: 1.9, still: 0.62 },
  cheer: { seconds: 1.8, still: 0.55 },
  shrug: { seconds: 1.6, still: 0.5 },
  tada: { seconds: 1.8, still: 0.75 },
  shy: { seconds: 2.4, still: 0.8, inFront: true },
};

interface HandPose { x: number; y: number; rot: number }
const restHand = (side: number): HandPose => ({ x: side * 0.54, y: 0.35, rot: 0 });
const lerpHand = (a: HandPose, b: HandPose, f: number): HandPose =>
  ({ x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f, rot: a.rot + (b.rot - a.rot) * f });
/** 0 → 1 over `dur` seconds from `from`, easing out (cubic). */
function rise(t: number, from: number, dur: number) {
  const u = Math.min(Math.max((t - from) / dur, 0), 1);
  return 1 - (1 - u) ** 3;
}

/** How far out the hands are at `t` seconds into `g`, 0..1. */
export function handsOut(g: HandGesture, t: number): number {
  const { seconds } = handGestures[g];
  if (t < 0 || t > seconds) return 0;
  const shrink = Math.min((seconds - t) / 0.22, 1);
  return Math.min(rise(t, 0, 0.28), shrink * shrink * (3 - 2 * shrink));
}

/** Where hand `side` (−1 left, +1 right) is at `t` seconds into `g`: body sides from its centre, y down; rot in degrees. */
function handPose(g: HandGesture, side: number, t: number): HandPose {
  switch (g) {
    case 'wave': {
      if (side < 0) return { ...restHand(side), y: 0.35 + Math.sin(6 * t) * 0.04 };
      const a = 13 * Math.max(t - 0.15, 0);
      return lerpHand(restHand(side),
        { x: 0.55 + Math.cos(a) * 0.06, y: -0.075 - Math.sin(a) * 0.14, rot: (-0.5 + Math.sin(a) * 0.35) * (180 / Math.PI) },
        rise(t, 0.15, 0.18));
    }
    case 'cheer': {
      const pump = Math.sin(16 * t + (side > 0 ? 0 : Math.PI)) * 0.07;
      return lerpHand(restHand(side), { x: side * 0.58, y: -0.4 + pump, rot: -side * 28 }, rise(t, 0.08, 0.2));
    }
    case 'shrug': {
      const lift = Math.sin(Math.min(Math.max((t - 0.25) / 0.5, 0), 1) * Math.PI) * 0.06;
      return lerpHand(restHand(side), { x: side * 0.68, y: 0.08 - lift, rot: -side * 22 }, rise(t, 0.05, 0.2));
    }
    case 'tada': {
      if (side < 0) return { ...restHand(side), y: 0.31 };
      const w = Math.max(t - 0.1, 0);
      const flourish = Math.sin(11 * w) * 0.07 * Math.exp(-3 * w);
      return lerpHand(restHand(side), { x: 0.68, y: -0.04 + flourish, rot: -22 + flourish * 120 }, rise(t, 0.1, 0.25));
    }
    case 'shy':
      return lerpHand(restHand(side), { x: side * 0.33, y: 0.07 + Math.sin(8 * t + side) * 0.012, rot: side * 20 }, rise(t, 0.05, 0.25));
  }
}

/** Two soft, solid ovals in the eyes' warm gradient, lit from the top. */
function drawHands(ctx: Ctx, s: number, g: HandGesture, t: number) {
  const out = handsOut(g, t);
  if (out < 0.01) return;
  const w = 0.3 * s * out, h = 0.26 * s * out;
  for (const side of [-1, 1]) {
    const p = handPose(g, side, t);
    ctx.save();
    ctx.translate(p.x * s, p.y * s);
    ctx.rotate(deg(p.rot));
    const fill = ctx.createLinearGradient(w * 0.35, -h * 0.45, -w * 0.4, h * 0.45);
    fill.addColorStop(0, P.eyeTop);
    fill.addColorStop(1, P.eyeBottom);
    ctx.beginPath();
    ctx.ellipse(0, 0, w / 2, h / 2, 0, 0, Math.PI * 2);
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.strokeStyle = P.body;
    ctx.lineWidth = s * 0.012;
    withAlpha(ctx, 0.55, () => ctx.stroke());
    ctx.restore();
  }
}

export interface DrawFaceOptions {
  params: FaceParams;
  motion: string;
  /** Seconds of life: blinks, drift, accent animation. */
  t: number;
  /** Seconds into the mood's body motion. */
  motionT: number;
  accent?: Accent;
  accentAlpha?: number;
  previousAccent?: Accent;
  previousAlpha?: number;
  progress?: number | null;
  glow?: boolean;
  /** Where to look ([gazeToward] makes one); omitted, the eyes drift on their own. */
  gaze?: Gaze | null;
  /** A gesture to make with the hands (it has none otherwise), `handsT` seconds into it. */
  hands?: HandGesture | null;
  handsT?: number;
}

/** Draws the face filling a `width × height` area at the canvas origin. */
export function drawStudioFace(ctx: Ctx, width: number, height: number, o: DrawFaceOptions) {
  const s = Math.min(width, height) * 0.78;
  const pose = motionPose(o.motion, o.motionT);
  ctx.save();
  const turn = o.gaze ?? { x: 0, y: 0 };
  ctx.translate(width / 2 + pose.dx * s + turn.x * s * 0.03, height / 2 + s * 0.03 + pose.dy * s + turn.y * s * 0.02);
  ctx.rotate(deg(pose.rot));
  ctx.translate(0, s / 2);
  ctx.scale(pose.sx, pose.sy);
  ctx.translate(0, -s / 2);
  // hands grow from behind the body, so they hop and lean with it
  if (o.hands && !handGestures[o.hands].inFront) drawHands(ctx, s, o.hands, o.handsT ?? 0);
  drawBody(ctx, s, o.glow ?? true);
  // the face turns toward what it looks at: eyes and mouth slide across the body and narrow a
  // little, like features on a head turning — readable even at a small size
  ctx.save();
  ctx.translate(turn.x * s * 0.1, turn.y * s * 0.07);
  ctx.scale(1 - 0.1 * Math.abs(turn.x), 1 - 0.06 * Math.abs(turn.y));
  drawEyes(ctx, s, o.params, o.t, o.gaze);
  drawMouth(ctx, s, o.params);
  ctx.restore();
  if (o.hands && handGestures[o.hands].inFront) drawHands(ctx, s, o.hands, o.handsT ?? 0);
  const ax = s * 0.43, ay = -s * 0.45;
  if (o.previousAccent && (o.previousAlpha ?? 0) > 0.01) drawAccent(ctx, o.previousAccent, ax, ay, s, o.t, o.previousAlpha ?? 0, o.progress);
  if (o.accent) drawAccent(ctx, o.accent, ax, ay, s, o.t, o.accentAlpha ?? 1, o.progress);
  ctx.restore();
}

/** The mood's body motion from the spec; `none` for an unknown mood. */
export const motionOf = (mood: string): string => mascotMoodMotions[mood] ?? 'none';

const TWEEN_SECONDS = 0.25;
/** How fast the face catches up with its gaze, per second: a soft follow, not a snap. */
const GAZE_RATE = 6;

/**
 * Animates the face on a canvas: blinks, motion, and a short tween on every mood change.
 * Framework-free; `StudioFace.tsx` wraps it for React. Call `dispose()` when done.
 */
export class StudioFacePlayer {
  private mood: string;
  private from: FaceParams;
  private to: FaceParams;
  private drawn: FaceParams;
  private fromAccent?: Accent;
  private changedAt = 0;
  private raf = 0;
  private start = performance.now();
  private lookedAt = 0;
  private looking: Gaze | null = null;
  private stopFollowing?: () => void;
  private hands: HandGesture | null = null;
  private handsAt = 0;
  progress: number | null = null;
  /** Where to look, x and y each −1..1; null lets the eyes drift. The face eases toward it. */
  gaze: Gaze | null = null;

  constructor(private canvas: HTMLCanvasElement, mood = 'idle', private animate = true) {
    this.mood = mood;
    this.from = this.to = this.drawn = paramsOf(expressionFor(mood));
    this.fromAccent = expressionFor(mood).accent;
    this.frame = this.frame.bind(this);
    this.raf = requestAnimationFrame(this.frame);
  }

  setMood(mood: string) {
    if (mood === this.mood) return;
    this.fromAccent = expressionFor(this.mood).accent;
    this.mood = mood;
    this.from = this.drawn;
    this.to = paramsOf(expressionFor(mood));
    this.changedAt = this.now();
    if (!this.animate) this.render();
  }

  /**
   * Makes a gesture with the hands, once from now; they tuck away when it ends. null puts them away.
   * (A moment's gesture: `handsForMoment` in moments.ts.)
   */
  playHands(gesture: HandGesture | null) {
    this.hands = gesture;
    this.handsAt = this.now();
    if (!this.animate) this.render();
  }

  setAnimate(animate: boolean) {
    this.animate = animate;
    cancelAnimationFrame(this.raf);
    this.raf = requestAnimationFrame(this.frame);
  }

  /**
   * Turns the face to watch the pointer anywhere on the page (like the notch companion watches the
   * cursor). Returns a function that stops it; `dispose()` stops it too.
   */
  followPointer(): () => void {
    this.stopFollowing?.();
    let px: number | null = null, py = 0;
    const look = () => {
      if (px == null) return;
      const r = this.canvas.getBoundingClientRect();
      this.gaze = gazeToward(px - (r.left + r.width / 2), py - (r.top + r.height / 2));
    };
    const move = (e: PointerEvent) => { px = e.clientX; py = e.clientY; look(); };
    // scrolling moves the face under a still pointer, so look again then too
    window.addEventListener('pointermove', move, { passive: true });
    window.addEventListener('scroll', look, { passive: true });
    const stop = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('scroll', look);
      this.gaze = null;
      if (this.stopFollowing === stop) this.stopFollowing = undefined;
    };
    this.stopFollowing = stop;
    return stop;
  }

  dispose() {
    cancelAnimationFrame(this.raf);
    this.stopFollowing?.();
  }

  private now() {
    return this.animate ? 0.4 + (performance.now() - this.start) / 1000 : 0.4;
  }

  private frame() {
    this.render();
    if (this.animate) this.raf = requestAnimationFrame(this.frame);
  }

  render() {
    const ctx = this.canvas.getContext('2d');
    if (!ctx) return;
    const t = this.now();
    const since = t - this.changedAt;
    const f = this.animate ? Math.min(Math.max(since / TWEEN_SECONDS, 0), 1) : 1;
    const eased = f * f * (3 - 2 * f);
    const params = lerpParams(this.from, this.to, eased);
    this.drawn = params;
    const accent = expressionFor(this.mood).accent;
    const same = this.fromAccent === accent;
    const g = this.gaze ? { x: Math.max(-1, Math.min(1, this.gaze.x)), y: Math.max(-1, Math.min(1, this.gaze.y)) } : null;
    if (!g) this.looking = null;
    else if (!this.animate || !this.looking) this.looking = g;
    else {
      const k = 1 - Math.exp(-GAZE_RATE * Math.min(Math.max(t - this.lookedAt, 0), 0.1));
      this.looking = { x: this.looking.x + (g.x - this.looking.x) * k, y: this.looking.y + (g.y - this.looking.y) * k };
    }
    this.lookedAt = t;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    drawStudioFace(ctx, this.canvas.width, this.canvas.height, {
      params,
      motion: this.animate ? motionOf(this.mood) : 'none',
      t,
      motionT: this.animate ? since % 6 : 0.4,
      accent,
      accentAlpha: same ? 1 : eased,
      previousAccent: same ? undefined : this.fromAccent,
      previousAlpha: 1 - eased,
      progress: this.progress,
      gaze: this.looking,
      hands: this.hands,
      handsT: this.hands ? (this.animate ? t - this.handsAt : handGestures[this.hands].still) : 0,
    });
  }
}
