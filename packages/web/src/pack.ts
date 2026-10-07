// The mascot pack's manifest (built by tools/mascot/pack.mjs in the workspace, served from
// /mascot/manifest.json). Mirrors MascotPack.kt in the KMP library:
// moments → moods → per-theme frame atlases. Unknown keys are ignored; nothing here throws.

export interface MascotMood {
  code?: string;
  fps?: number;
  motion?: string;
  fallback?: string;
  frames?: number;
  cols?: number;
  loopFrom?: number;
  still?: number;
  atlas?: { light?: string; dark?: string };
}

export interface MascotPack {
  schema: number;
  version: number;
  frame: { w: number; h: number };
  moods: Record<string, MascotMood>;
  moments: Record<string, { mood: string; hold?: boolean; first?: string }>;
}

let customBaseUrl = '/mascot';

export function setMascotBaseUrl(url: string) {
  customBaseUrl = url.replace(/\/+$/, '');
  packPromise = null; // force reload if base URL changed
}

export function getMascotBaseUrl(): string {
  return customBaseUrl;
}

const SUPPORTED_SCHEMA = 1;
let packPromise: Promise<MascotPack | null> | null = null;

/** The published pack, fetched once per page load. Null when unavailable — draw nothing then. */
export function loadMascotPack(overrideUrl?: string): Promise<MascotPack | null> {
  const base = overrideUrl ? overrideUrl.replace(/\/+$/, '') : customBaseUrl;
  packPromise ??= fetch(`${base}/manifest.json`)
    .then((r) => (r.ok ? r.json() : null))
    .then((p: MascotPack | null) => (p && p.schema === SUPPORTED_SCHEMA && p.moods.idle ? p : null))
    .catch(() => null);
  return packPromise;
}

const hasArt = (m?: MascotMood) => !!m && (m.frames ?? 0) > 0 && !!m.atlas && Object.keys(m.atlas).length > 0;

export interface ResolvedMood {
  mood: MascotMood;
  art: MascotMood | null;
}

/** Same rules as the app: unknown moment or mood → idle; art from the first mood down the fallback chain that has some. */
export function resolveMoment(pack: MascotPack, moment: string): ResolvedMood {
  const moodName = pack.moments[moment]?.mood;
  return resolveMood(pack, moodName && pack.moods[moodName] ? moodName : 'idle');
}

export function resolveMood(pack: MascotPack, name: string): ResolvedMood {
  const mood = pack.moods[name] ?? pack.moods.idle;
  let artName = pack.moods[name] ? name : 'idle';
  const seen = new Set([artName]);
  while (!hasArt(pack.moods[artName])) {
    const next = pack.moods[artName]?.fallback;
    if (!next || seen.has(next)) { artName = 'idle'; break; }
    seen.add(next);
    artName = next;
  }
  const art = pack.moods[artName];
  return { mood, art: hasArt(art) ? art : null };
}
