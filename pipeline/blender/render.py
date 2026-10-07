"""Renders the mascot's moods and clips from one rigged 3D model into Brand/mascot/src/.

    blender -b path/to/mascot.blend --python tools/mascot/blender/render.py -- [options]

    --only idle,walk,upload.done   render just these moods / clips (default: everything)
    --theme light|dark             one outfit only (default: both)
    --preview                      one frame per entry, into Brand/mascot/model/preview/ (fast check)
    --list                         print what the .blend has (actions, materials, props) and exit

Then: node tools/mascot/pack.mjs --sync

What it does, per mood (tools/mascot/blender/actions.json says which action, face and props):
  * plays the named action on the armature and renders it at the pack's fps (12), at most maxFrames
    frames, or a single pose when the entry has "frame";
  * swaps the face texture (Brand/mascot/model/faces/<face>.png — tools/mascot/blender/faces.mjs);
  * shows only that mood's props (objects in the "Props" collection);
  * renders twice: black outfit → src/<mood>/light/, white outfit → src/<mood>/dark/;
  * same orthographic camera, light and scale for every frame, transparent background.
Clips go to src/moments/<moment.key>/<light|dark>/.

The .blend needs: one armature with the actions (e.g. Mixamo animations, renamed to the names in
actions.json), the outfit materials named in actions.json (two: black and white — every slot using
either is switched), a "Face" material whose Image Texture node is named "FaceTex", and the props.
Nothing in the .blend is saved; renders only.
"""

import json
import math
import os
import shutil
import sys

import bpy
import mathutils

HERE = os.path.dirname(os.path.abspath(__file__))
PIPELINE = os.path.abspath(os.path.join(HERE, ".."))
ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


def arg(name, default=None):
    if name in ARGS:
        i = ARGS.index(name)
        return ARGS[i + 1] if i + 1 < len(ARGS) and not ARGS[i + 1].startswith("--") else True
    return default


CONFIG = json.load(open(os.path.join(HERE, "actions.json")))
MODEL, CAM, REN = CONFIG["model"], CONFIG["camera"], CONFIG["render"]
OUT = os.path.join(PIPELINE, "src")
PREVIEW = arg("--preview", False)
if PREVIEW:
    OUT = os.path.join(PIPELINE, "model", "preview")
THEMES = [arg("--theme")] if arg("--theme") else ["light", "dark"]
ONLY = set(arg("--only").split(",")) if arg("--only") else None
scene = bpy.context.scene


def log(*a):
    print("[mascot]", *a, flush=True)


# ---- find the model ----------------------------------------------------------------------------
def armature():
    name = MODEL.get("armature")
    if name and name in bpy.data.objects:
        return bpy.data.objects[name]
    arms = [o for o in scene.objects if o.type == "ARMATURE"]
    if not arms:
        sys.exit("[mascot] no armature in this .blend")
    return arms[0]


def props_objects():
    col = bpy.data.collections.get(MODEL.get("propsCollection", "Props"))
    return list(col.all_objects) if col else []


def body_meshes():
    props = set(props_objects())
    return [o for o in scene.objects if o.type == "MESH" and o not in props and o.visible_get()
            and not getattr(o, "is_shadow_catcher", False)]


if arg("--list"):
    log("actions:", sorted(a.name for a in bpy.data.actions))
    log("materials:", sorted(m.name for m in bpy.data.materials))
    log("props:", sorted(o.name for o in props_objects()))
    log("armatures:", [o.name for o in scene.objects if o.type == "ARMATURE"])
    sys.exit(0)

ARM = armature()


def set_action(action):
    ad = ARM.animation_data or ARM.animation_data_create()
    ad.action = action
    # Blender 4.4+ slotted actions: bind the action's first slot
    if hasattr(ad, "action_slot") and getattr(action, "slots", None):
        ad.action_slot = action.slots[0]


# ---- render setup -----------------------------------------------------------------------------
def setup_render():
    r = scene.render
    engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    want = REN.get("engine", "EEVEE")
    if want == "CYCLES":
        r.engine = "CYCLES"
        scene.cycles.samples = REN.get("samples", 64)
        scene.cycles.use_denoising = True
        try:  # the GPU (Metal on a Mac) when there is one
            prefs = bpy.context.preferences.addons["cycles"].preferences
            for kind in ("METAL", "OPTIX", "CUDA", "HIP"):
                try:
                    prefs.compute_device_type = kind
                    break
                except TypeError:
                    continue
            prefs.get_devices()
            for d in prefs.devices:
                d.use = True
            scene.cycles.device = "GPU"
        except Exception as e:  # noqa: BLE001
            log("GPU not available, rendering on the CPU:", e)
    else:
        r.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
        try:
            scene.eevee.taa_render_samples = REN.get("samples", 32)
        except AttributeError:
            pass
    r.resolution_x, r.resolution_y, r.resolution_percentage = REN["width"], REN["height"], 100
    r.film_transparent = True
    r.image_settings.file_format = "PNG"
    r.image_settings.color_mode = "RGBA"
    r.image_settings.color_depth = "8"
    # "Standard" keeps the face's neon pink → orange as designed (AgX/Filmic wash it out)
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"


def world_bbox(objs):
    lo = mathutils.Vector((1e9, 1e9, 1e9))
    hi = -lo
    deps = bpy.context.evaluated_depsgraph_get()
    for o in objs:
        ev = o.evaluated_get(deps)
        for c in ev.bound_box:
            p = ev.matrix_world @ mathutils.Vector(c)
            lo = mathutils.Vector(map(min, lo, p))
            hi = mathutils.Vector(map(max, hi, p))
    return lo, hi


def setup_camera_and_lights():
    """One camera for everything, framed on the rest pose with room for jumps and raised arms."""
    rest = bpy.data.actions.get(MODEL.get("restAction", "idle"))
    if rest:
        set_action(rest)
        scene.frame_set(int(rest.frame_range[0]))
    lo, hi = world_bbox(body_meshes())
    height = hi.z - lo.z
    centre = (lo + hi) / 2
    cam = bpy.data.objects.get("MascotCam")
    if not cam:
        cam = bpy.data.objects.new("MascotCam", bpy.data.cameras.new("MascotCam"))
        scene.collection.objects.link(cam)
    cam.data.type = "ORTHO"
    # portrait frame: ortho_scale spans the height
    scale = height * CAM.get("margin", 1.4)
    cam.data.ortho_scale = scale
    front = {"-Y": (0, -1, 0), "Y": (0, 1, 0), "X": (1, 0, 0), "-X": (-1, 0, 0)}[CAM.get("front", "-Y")]
    d = mathutils.Vector(front)
    d.rotate(mathutils.Euler((0, 0, math.radians(CAM.get("yawDegrees", 20)))))
    d.z = math.tan(math.radians(CAM.get("pitchDegrees", 6)))
    d.normalize()
    # feet a little above the bottom edge: the frame's centre sits above the feet by half the scale
    target = mathutils.Vector((centre.x, centre.y, lo.z + scale * (0.5 - CAM.get("footroom", 0.04))))
    cam.location = target + d * height * 10
    cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam.data.clip_end = height * 40
    scene.camera = cam

    if REN.get("lights", "auto") == "auto":
        for o in [o for o in scene.objects if o.name.startswith("MascotLight")]:
            bpy.data.objects.remove(o, do_unlink=True)

        def light(name, energy, offset, size):
            lamp = bpy.data.lights.new(name, "AREA")
            lamp.energy, lamp.size = energy * height * height * REN.get("lightScale", 1.0), size * height
            lamp.shape = "DISK"
            lamp.specular_factor = 0.0  # never mirrored in the glossy visor (they read as extra eyes)
            ob = bpy.data.objects.new(name, lamp)
            scene.collection.objects.link(ob)
            ob.location = centre + mathutils.Vector(offset) * height
            ob.rotation_euler = (centre - ob.location).to_track_quat("-Z", "Y").to_euler()
            ob.visible_glossy = False

        world = scene.world or bpy.data.worlds.new("MascotWorld")
        scene.world = world
        world.use_nodes = True
        world.node_tree.nodes["Background"].inputs[0].default_value = (0.06, 0.06, 0.065, 1)
        world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
        side = mathutils.Vector(front).cross(mathutils.Vector((0, 0, 1)))
        f = mathutils.Vector(front)
        light("MascotLightKey", 420, tuple(f * 2.2 + side * 1.6 + mathutils.Vector((0, 0, 1.8))), 1.2)
        light("MascotLightFill", 140, tuple(f * 2.4 - side * 2.0 + mathutils.Vector((0, 0, 0.6))), 1.6)
        light("MascotLightRim", 320, tuple(-f * 2.0 + mathutils.Vector((0, 0, 1.6))), 1.0)
    log(f"camera: height {height:.3f}, ortho {scale:.3f}")


# ---- per-entry state ----------------------------------------------------------------------------
_face_images = {}


def set_face(face):
    mat = bpy.data.materials.get(MODEL.get("faceMaterial", "Face"))
    if not mat or not mat.use_nodes:
        return
    node = mat.node_tree.nodes.get(MODEL.get("faceImageNode", "FaceTex"))
    if not node:
        return
    faces_dir = os.path.join(PIPELINE, "model", "faces")
    path = os.path.join(faces_dir, f"{face}.png")
    if not os.path.exists(path):
        log(f"  no face texture {face}.png — keeping the current face")
        return
    if path not in _face_images:
        _face_images[path] = bpy.data.images.load(path, check_existing=True)
    node.image = _face_images[path]


def set_outfit(theme):
    pair = MODEL["outfitMaterials"]
    want = bpy.data.materials.get(pair[theme])
    names = set(pair.values())
    if not want:
        log(f"  no material {pair[theme]} — outfit left as it is")
        return
    for o in scene.objects:
        for slot in getattr(o, "material_slots", []):
            if slot.material and slot.material.name in names:
                slot.material = want


def show_props(props):
    names = set()
    for p in props:
        mapped = MODEL["props"].get(p, p)
        names.update(mapped if isinstance(mapped, list) else [mapped])
    for o in props_objects():
        hidden = o.name not in names
        o.hide_render = hidden
        o.hide_viewport = hidden


def frames_of(entry, action):
    if "frame" in entry:
        return [int(entry["frame"])]
    start, end = int(action.frame_range[0]), int(action.frame_range[1])
    src_fps = scene.render.fps / scene.render.fps_base
    step = max(1, round(src_fps / entry.get("fps", REN.get("fps", 12))))
    # a loop leaves out its last frame: it is the first one again
    stop = end + (0 if entry.get("loop") else 1)
    frames = list(range(start, stop, step))
    cap = REN.get("maxFrames", 16)
    while len(frames) > cap:
        step += 1
        frames = list(range(start, stop, step))
    return frames[:1] if PREVIEW else frames


def render_entry(name, entry, outdir):
    action = bpy.data.actions.get(entry.get("action", name))
    if not action:
        log(f"skip {name}: no action \"{entry.get('action', name)}\" in this .blend")
        return False
    set_action(action)
    set_face(entry.get("face", "smile"))
    show_props(entry.get("props", []))
    frames = frames_of(entry, action)
    for theme in THEMES:
        set_outfit(theme)
        d = os.path.join(outdir, theme)
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)
        for i, f in enumerate(frames):
            scene.frame_set(f)
            scene.render.filepath = os.path.join(d, f"{i:03d}.png")
            bpy.ops.render.render(write_still=True)
    log(f"{name}: {len(frames)} frame(s) × {len(THEMES)} outfit(s)")
    return True


def main():
    setup_render()
    setup_camera_and_lights()
    done = skipped = 0
    for name, entry in CONFIG["moods"].items():
        if ONLY and name not in ONLY:
            continue
        ok = render_entry(name, entry, os.path.join(OUT, name))
        done, skipped = done + ok, skipped + (not ok)
    for key, entry in CONFIG["clips"].items():
        if ONLY and key not in ONLY:
            continue
        ok = render_entry(key, entry, os.path.join(OUT, "moments", key))
    log(f"rendered {done}, skipped {skipped} → {os.path.relpath(OUT, PIPELINE)}")
    if not PREVIEW:
        log("next: npm run pack")


main()
