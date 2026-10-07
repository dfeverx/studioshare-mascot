'use client';

import { useEffect, useRef, useState } from 'react';
import { loadMascotPack, getMascotBaseUrl, resolveMoment, resolveMood, type MascotPack } from './pack';
import type { MascotMoment } from './moments';

export interface MascotSpriteProps {
  /** A moment key from moments.ts (e.g. 'cull.running', 'upload.done'). */
  moment?: MascotMoment | string;
  /** Or directly specify a mood name (e.g. 'idle', 'uploading', 'happy', 'careful'). */
  mood?: string;
  /** Rendered size in CSS pixels (square). Default 96. */
  size?: number;
  /** Which outfit: `light` for light pages (dark outfit), `dark` for dark pages. Defaults to the OS scheme. */
  theme?: 'light' | 'dark';
  /** Optional custom base URL for the mascot pack assets. */
  baseUrl?: string;
  className?: string;
}

/**
 * The StudioShare mascot on the web: the same pack, moods and moments as the apps, drawn from the
 * frame atlas on a high-performance canvas. Decorative (aria-hidden). Honours prefers-reduced-motion.
 */
export function MascotSprite({
  moment,
  mood,
  size = 96,
  theme,
  baseUrl,
  className
}: MascotSpriteProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [pack, setPack] = useState<MascotPack | null>(null);

  useEffect(() => {
    let alive = true;
    loadMascotPack(baseUrl).then((p) => {
      if (alive) setPack(p);
    });
    return () => {
      alive = false;
    };
  }, [baseUrl]);

  useEffect(() => {
    const el = canvas.current;
    if (!pack || !el) return;

    const resolved = moment
      ? resolveMoment(pack, moment)
      : mood
      ? resolveMood(pack, mood)
      : resolveMood(pack, 'idle');

    const { mood: resolvedMood, art } = resolved;
    if (!art?.atlas) return;

    const dark = theme ? theme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    const src = (dark ? art.atlas.dark : art.atlas.light) ?? art.atlas.light ?? art.atlas.dark;
    if (!src) return;

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const ctx = el.getContext('2d');
    if (!ctx) return;
    const dpr = window.devicePixelRatio || 1;
    el.width = size * dpr;
    el.height = size * dpr;

    const img = new Image();
    let raf = 0;
    const start = performance.now();
    const { w, h } = pack.frame;
    const frames = art.frames ?? 1;
    const cols = Math.max(1, art.cols ?? 1);
    const fps = Math.max(1, resolvedMood.fps ?? 12);
    const loopFrom = Math.min(art.loopFrom ?? 0, frames - 1);

    const draw = (now: number) => {
      const t = (now - start) / 1000;
      let i = art.still ?? 0;
      if (!reduced && frames > 1) {
        const raw = Math.floor(t * fps);
        i = raw < frames ? raw : loopFrom + ((raw - loopFrom) % (frames - loopFrom));
      }
      ctx.clearRect(0, 0, el.width, el.height);
      // a gentle breathe around the feet, so a single-frame pose is still alive
      const s = reduced ? 0 : 0.025 * (0.5 + 0.5 * Math.sin((t * 2 * Math.PI) / 3.2));
      ctx.save();
      ctx.translate(el.width / 2, el.height);
      ctx.scale(1 - s * 0.4, 1 + s);
      ctx.drawImage(img, (i % cols) * w, Math.floor(i / cols) * h, w, h, -el.width / 2, -el.height, el.width, el.height);
      ctx.restore();
      if (!reduced) raf = requestAnimationFrame(draw);
    };

    img.onload = () => {
      raf = requestAnimationFrame(draw);
    };
    const assetBase = baseUrl ? baseUrl.replace(/\/+$/, '') : getMascotBaseUrl();
    img.src = `${assetBase}/${src}`;

    return () => cancelAnimationFrame(raf);
  }, [pack, moment, mood, size, theme, baseUrl]);

  return <canvas ref={canvas} aria-hidden="true" className={className} style={{ width: size, height: size }} />;
}
