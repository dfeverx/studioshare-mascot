// Writes (or tops up) tools/mascot/blender/actions.json — what render.py renders for each mood and clip.
//   node tools/mascot/blender/actions.mjs
// Existing entries are kept as edited; new moods/clips from mascot.json are added with defaults.
// Each entry names the model's Blender action (animation) to play, the face texture, the props to show,
// and either a frame range or a single pose frame. "mixamo" is only a hint: an animation to search for on
// mixamo.com, download "without skin", and import into the model's .blend under the name in "action".
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MASCOT } from '../lib.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FILE = path.join(HERE, 'actions.json');
const def = JSON.parse(fs.readFileSync(path.join(MASCOT, 'mascot.json'), 'utf8'));
const cur = fs.existsSync(FILE) ? JSON.parse(fs.readFileSync(FILE, 'utf8')) : {};

const MIXAMO = {
  idle: 'Breathing Idle', waiting: 'Idle (looking around)', shy: 'Bashful', happy: 'Waving',
  celebrating: 'Jumping / Victory', proud: 'Presenting / Standing Arguing (one arm sweep)', curious: 'Looking Around',
  excited: 'Excited', focused: 'Typing (seated) or Looking Down', carrying: 'Walking (carrying) / Box Walk',
  thinking: 'Thinking', searching: 'Looking Around', connecting: 'Standing Idle (arms forward)', connected: 'Thumbs Up',
  disconnected: 'Shrugging', sleepy: 'Sleeping Idle / Sitting Idle', hot: 'Tired / Wiping Sweat', patient: 'Checking Watch',
  oops: 'Head Scratch / Embarrassed', sad: 'Sad Idle', locked: 'Standing Idle', careful: 'Stop Gesture / Defeated (palms up)',
  goodbye: 'Waving (walking)', ok: 'Thumbs Up', scanning: 'Idle (holding item)', walk: 'Walking / Running',
  glance: 'Head Turn', uploading: 'Walking (carrying)', sending: 'Throw', capturing: 'Taking a Photo / Idle (holding item)',
};
// mascot.json prop id → object name in the model's "Props" collection
const PROP_OBJECTS = {
  box: 'prop_box', cloud: 'prop_cloud', laptop: 'prop_laptop', magnifier: 'prop_magnifier', question: 'prop_question',
  exclaim: 'prop_exclaim', zzz: 'prop_zzz', sparks: 'prop_sparks', confetti: 'prop_confetti', heart: 'prop_heart',
  plug: 'prop_plug', unplug: 'prop_cable', key: 'prop_key', sweat: 'prop_sweat', hourglass: 'prop_hourglass',
  check: 'prop_check', phone: 'prop_phone', envelope: 'prop_envelope', star: 'prop_star', camera: 'prop_camera',
  faceframe: 'prop_faceframe',
};

const out = {
  $comment: 'Edit freely: render.py reads this. Run actions.mjs again to add new moods/clips without touching these.',
  model: cur.model ?? {
    armature: '',
    outfitMaterials: { light: 'Outfit_Black', dark: 'Outfit_White' },
    faceMaterial: 'Face', faceImageNode: 'FaceTex', facesDir: 'Brand/mascot/model/faces',
    propsCollection: 'Props', props: PROP_OBJECTS, restAction: 'idle',
  },
  camera: cur.camera ?? { front: '-Y', yawDegrees: 20, pitchDegrees: 6, margin: 1.4, footroom: 0.04 },
  render: cur.render ?? { width: 400, height: 560, engine: 'EEVEE', samples: 32, fps: 12, maxFrames: 16, lights: 'auto' },
  moods: {},
  clips: {},
};
const entry = (name, base, kind) => ({
  action: name,
  mixamo: MIXAMO[base] ?? '',
  face: def.moods[base]?.face ?? 'smile',
  props: (def.moods[base]?.prop && def.moods[base].prop !== 'none') ? [def.moods[base].prop] : [],
  // a held mood loops; a one-shot plays once. Set "frame": N instead to render a single pose.
  loop: kind === 'loop',
});
for (const [n, m] of Object.entries(def.moods)) {
  const hold = Object.values(def.moments).some((mo) => mo.mood === n && mo.hold) || ['idle', 'walk'].includes(n);
  out.moods[n] = cur.moods?.[n] ?? entry(n, n, hold ? 'loop' : 'once');
}
for (const k of Object.keys(def.clips ?? {})) {
  const mood = def.moments[k]?.mood ?? 'idle';
  out.clips[k] = cur.clips?.[k] ?? { ...entry(k.replace(/\./g, '_'), mood, def.moments[k]?.hold ? 'loop' : 'once'), mixamo: '' };
}
fs.writeFileSync(FILE, JSON.stringify(out, null, 2) + '\n');
console.log(`${Object.keys(out.moods).length} moods, ${Object.keys(out.clips).length} clips → ${path.relative(process.cwd(), FILE)}`);
