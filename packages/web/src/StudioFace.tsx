'use client';

import { useEffect, useRef } from 'react';
import { StudioFacePlayer } from './face';
import { handsForMoment, moodForMoment, type HandGesture, type MascotMoment } from './moments';

export interface StudioFaceProps {
  /** A moment key from moments.ts (e.g. 'cull.running', 'upload.done'). */
  moment?: MascotMoment | string;
  /** Or a mood name directly (e.g. 'idle', 'uploading', 'happy', 'careful'). Ignored when `moment` is set. */
  mood?: string;
  /** 0..1 for the progress-ring accent (uploading/sending); omitted, the ring spins. */
  progress?: number;
  /** Rendered size in CSS pixels (square). Default 96. */
  size?: number;
  className?: string;
  /**
   * A gesture to make with the hands, played once whenever this or `moment` changes. Defaults to
   * the moment's own (spec `hands`); most moments have none, and the face has no hands then.
   */
  hands?: HandGesture | null;
  /** Turn to watch the pointer anywhere on the page. Off under prefers-reduced-motion. */
  followPointer?: boolean;
}

/**
 * The StudioShare app-icon face on the web: the same moods and moments as the apps, drawn in code
 * on a canvas (no images to load). Decorative (aria-hidden). Honours prefers-reduced-motion.
 */
export function StudioFace({ moment, mood, progress, size = 96, className, followPointer = false, hands }: StudioFaceProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const player = useRef<StudioFacePlayer | null>(null);
  const shown = moment ? moodForMoment(moment) : mood ?? 'idle';

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const dpr = window.devicePixelRatio || 1;
    el.width = size * dpr;
    el.height = size * dpr;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const p = new StudioFacePlayer(el, shown, !reduced);
    player.current = p;
    return () => p.dispose();
    // the player follows mood/progress below; only a new size needs a new canvas buffer
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [size]);

  useEffect(() => {
    player.current?.setMood(shown);
  }, [shown]);

  useEffect(() => {
    if (player.current) player.current.progress = progress ?? null;
  }, [progress]);

  const gesture = hands !== undefined ? hands : moment ? handsForMoment(moment) ?? null : null;
  useEffect(() => {
    player.current?.playHands(gesture);
  }, [gesture, moment]);

  useEffect(() => {
    const p = player.current;
    if (!p || !followPointer || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    return p.followPointer();
  }, [followPointer, size]);

  return <canvas ref={canvas} aria-hidden="true" className={className} style={{ width: size, height: size }} />;
}
