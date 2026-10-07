"""Builds the StudioShare mascot as a rigged 3D model, with an animation per mood, from code.

    blender -b --python tools/mascot/blender/build_model.py
    → Brand/mascot/model/mascot.blend   (then render.py renders it)

A chibi vinyl-toy figure after the turnaround in studioshare-mascot.png: a round hooded head with a glossy
black visor (the face is a swappable glowing texture), a hoodie with drawstrings and a pocket,
joggers and chunky sneakers. Rigid parts on a small skeleton — like a toy's joints — so it needs no
skinning. Props live in the "Props" collection; render.py shows the ones a mood asks for.

Everything is parameters here: change a proportion, a colour or an animation and run it again.
The character faces -Y, stands on z = 0, about 1 unit tall.
"""

import math
import os

import bmesh
import bpy
from mathutils import Euler, Matrix, Quaternion, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
PIPELINE = os.path.abspath(os.path.join(HERE, ".."))
OUT = os.path.join(PIPELINE, "model", "mascot.blend")
FACES = os.path.join(PIPELINE, "model", "faces")
FPS = 24

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS


# ---- materials ---------------------------------------------------------------------------------
def material(name, color, rough=0.5, emit=None, strength=0.0, metal=0.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if coat:
        b.inputs["Coat Weight"].default_value = coat
    if emit:
        b.inputs["Emission Color"].default_value = (*emit, 1)
        b.inputs["Emission Strength"].default_value = strength
    m.use_fake_user = True
    return m


def fabric(name, color, rough=0.88):
    """Cotton fleece: matte, a soft sheen at grazing angles, and a fine knit bump."""
    m = material(name, color, rough=rough)
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    b.inputs["Sheen Weight"].default_value = 0.12
    b.inputs["Sheen Roughness"].default_value = 0.6
    b.inputs["Specular IOR Level"].default_value = 0.12
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 380
    noise.inputs["Detail"].default_value = 6
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.12
    bump.inputs["Distance"].default_value = 0.002
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


def glass(name):
    m = material(name, (0.95, 0.97, 1.0), rough=0.04)
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Transmission Weight"].default_value = 1.0
    b.inputs["IOR"].default_value = 1.45
    return m


M = {
    "black": fabric("Outfit_Black", (0.007, 0.007, 0.008)),
    "white": fabric("Outfit_White", (0.7, 0.7, 0.71)),
    "glove": material("Glove", (0.9, 0.9, 0.89), rough=0.45, coat=0.25),
    "shoe": material("Shoe", (0.92, 0.92, 0.9), rough=0.42),
    "midsole": material("Midsole", (0.96, 0.95, 0.92), rough=0.55),
    "accent": material("ShoeAccent", (1.0, 0.62, 0.05), rough=0.45),
    "sole": material("Sole", (0.05, 0.05, 0.055), rough=0.75),
    "lace": material("Lace", (0.95, 0.95, 0.95), rough=0.7),
    "string": material("String", (0.88, 0.88, 0.88), rough=0.6),
    "rim": material("VisorRim", (0.62, 0.63, 0.66), rough=0.28, metal=1.0),
    "yellow": material("Yellow", (1.0, 0.72, 0.08), rough=0.35, emit=(1.0, 0.65, 0.05), strength=0.6),
    "cardboard": material("Cardboard", (0.72, 0.5, 0.27), rough=0.8),
    "metal": material("Metal", (0.72, 0.73, 0.76), rough=0.25, metal=1.0),
    "brass": material("Brass", (0.85, 0.65, 0.3), rough=0.3, metal=1.0),
    "dark": material("DarkPlastic", (0.03, 0.03, 0.035), rough=0.3),
    "glass": material("Lens", (0.05, 0.1, 0.18), rough=0.05, coat=1.0),
    "bulb": glass("BulbGlass"),
    "filament": material("Filament", (1.0, 0.6, 0.2), rough=0.4, emit=(1.0, 0.55, 0.15), strength=12.0),
    "paper": material("Paper", (0.96, 0.96, 0.95), rough=0.7),
    "pink": material("Pink", (1.0, 0.18, 0.62), rough=0.3, emit=(1.0, 0.2, 0.6), strength=2.0),
    "blue": material("Drop", (0.45, 0.8, 1.0), rough=0.1, emit=(0.4, 0.75, 1.0), strength=0.4),
    "screen": material("Screen", (0.3, 0.6, 1.0), rough=0.2, emit=(0.5, 0.75, 1.0), strength=1.5),
    "skin": material("PhotoFace", (0.95, 0.78, 0.66), rough=0.6),
    "green": material("Check", (0.2, 0.85, 0.45), rough=0.3, emit=(0.2, 0.85, 0.45), strength=0.8),
}
CONFETTI = [material(f"Confetti{i}", c, rough=0.4, emit=c, strength=0.5)
            for i, c in enumerate([(1, 0.72, 0.08), (1, 0.2, 0.6), (0.3, 0.75, 1), (0.4, 0.9, 0.5), (1, 0.5, 0.15)])]

# The face: black glossy visor; the texture's glowing shapes drive the emission.
face = material("Face", (0.0, 0.0, 0.0), rough=0.3, coat=0.5)
face.node_tree.nodes["Principled BSDF"].inputs["Specular IOR Level"].default_value = 0.2
nt = face.node_tree
bsdf = nt.nodes["Principled BSDF"]
tex = nt.nodes.new("ShaderNodeTexImage")
tex.name = "FaceTex"
tex.extension = "CLIP"
tex.image = bpy.data.images.load(os.path.join(FACES, "smile.png"))
coord = nt.nodes.new("ShaderNodeTexCoord")
sep = nt.nodes.new("ShaderNodeSeparateXYZ")
comb = nt.nodes.new("ShaderNodeCombineXYZ")
mapn = nt.nodes.new("ShaderNodeMapping")
nt.links.new(coord.outputs["Object"], sep.inputs[0])
nt.links.new(sep.outputs["X"], comb.inputs["X"])
nt.links.new(sep.outputs["Z"], comb.inputs["Y"])
nt.links.new(comb.outputs[0], mapn.inputs["Vector"])
# visor-local x, z ∈ [-1, 1] → the face square (a little zoomed so the features fill the visor)
mapn.inputs["Scale"].default_value = (0.5 * 0.86, 0.5 * 0.86, 1)
mapn.inputs["Location"].default_value = (0.5, 0.47, 0)
nt.links.new(mapn.outputs[0], tex.inputs["Vector"])
nt.links.new(tex.outputs["Color"], bsdf.inputs["Emission Color"])
bsdf.inputs["Emission Strength"].default_value = 2.2
face.use_fake_user = True


# ---- mesh helpers ------------------------------------------------------------------------------
def finish(o, mat, smooth=True, subsurf=2):
    o.data.materials.append(mat)
    if smooth:
        for p in o.data.polygons:
            p.use_smooth = True
    if subsurf:
        mod = o.modifiers.new("Subsurf", "SUBSURF")
        mod.levels, mod.render_levels = 1, subsurf
    return o


def sphere(name, loc, scale, mat, segs=32, subsurf=1):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segs, ring_count=segs // 2, radius=1, location=loc)
    o = bpy.context.active_object
    o.name, o.scale = name, scale
    return finish(o, mat, subsurf=subsurf)


def rbox(name, loc, size, mat, bevel=0.3, rot=(0, 0, 0)):
    """A rounded box: a cube bevelled, then smoothed."""
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name, o.scale = name, size
    bpy.ops.object.transform_apply(scale=True)
    m = o.modifiers.new("Bevel", "BEVEL")
    m.width, m.segments, m.limit_method = min(size) * bevel, 3, "NONE"
    return finish(o, mat, subsurf=1)


def cyl(name, loc, r, depth, mat, rot=(0, 0, 0), verts=32, subsurf=0):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    return finish(o, mat, subsurf=subsurf)


def capsule(name, a, b, r, mat):
    """A rounded limb from point a to point b."""
    a, b = Vector(a), Vector(b)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=1)
    o = bpy.context.active_object
    o.name = name
    bm = bmesh.new()
    bm.from_mesh(o.data)
    half = (b - a).length / 2
    for v in bm.verts:
        v.co *= r
        v.co.z += half if v.co.z > 0 else -half
    bm.to_mesh(o.data)
    bm.free()
    o.location = (a + b) / 2
    o.rotation_euler = (b - a).to_track_quat("Z", "Y").to_euler()
    return finish(o, mat, subsurf=1)


def text(name, body, loc, size, mat, rot=(math.radians(90), 0, 0)):
    cu = bpy.data.curves.new(name, "FONT")
    cu.body, cu.size, cu.extrude, cu.bevel_depth = body, size, size * 0.12, size * 0.03
    cu.align_x, cu.align_y = "CENTER", "CENTER"
    o = bpy.data.objects.new(name, cu)
    scene.collection.objects.link(o)
    o.location, o.rotation_euler = loc, rot
    o.data.materials.append(mat)
    return o


def join(name, objs):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        if o.type == "FONT":
            bpy.context.view_layer.objects.active = o
            o.select_set(True)
            bpy.ops.object.convert(target="MESH")
            o.select_set(False)
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.convert(target="MESH")  # applies modifiers so parts with different ones join cleanly
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    return o


# ---- the figure --------------------------------------------------------------------------------
BLACK = M["black"]  # every outfit part uses it; render.py swaps it for Outfit_White on the white outfit
HEAD_Z = 0.69
WITH_CAP = True  # the cap sits high on the crown, its peak above the visor
parts = {}

# head: hood, visor (glowing face), a thin metal rim round the visor, the hood's soft opening
parts["hood"] = sphere("hood", (0, 0.01, HEAD_Z), (0.285, 0.27, 0.265), BLACK)
parts["visor"] = sphere("visor", (0, -0.115, HEAD_Z - 0.012), (0.225, 0.17, 0.205), face, subsurf=2)


def ring(name, loc, scale, minor, mat):
    bpy.ops.mesh.primitive_torus_add(major_radius=1, minor_radius=minor, major_segments=64, minor_segments=16,
                                     location=loc, rotation=(math.radians(90), 0, 0))
    o = bpy.context.active_object
    o.name, o.scale = name, scale
    return finish(o, mat, subsurf=1)


parts["hoodrim"] = ring("hoodrim", (0, -0.16, HEAD_Z - 0.012), (0.24, 0.225, 0.12), 0.17, BLACK)
parts["visorrim"] = ring("visorrim", (0, -0.2, HEAD_Z - 0.012), (0.218, 0.2, 0.06), 0.05, M["rim"])

if WITH_CAP:
    # crown: a dome hugging the head, cut on a slant — high at the front (just above the visor), low at the back
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1, location=(0, 0.012, HEAD_Z + 0.008))
    crown = bpy.context.active_object
    crown.name, crown.scale = "cap", (0.298, 0.288, 0.285)
    bm = bmesh.new()
    bm.from_mesh(crown.data)
    # a clean slanted cut (deleting vertices would leave a stepped edge)
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=(0, 0, 0.42),
                           plane_no=(0, 0.36, 1), clear_inner=True)
    bm.to_mesh(crown.data)
    bm.free()
    crown.modifiers.new("Solidify", "SOLIDIFY").thickness = 0.04
    parts["cap"] = finish(crown, BLACK, subsurf=1)
    parts["capbutton"] = sphere("capbutton", (0, 0.03, HEAD_Z + 0.292), (0.024, 0.024, 0.014), BLACK)
    # peak: a curved half-disc in front of the crown
    bm = bmesh.new()
    bmesh.ops.create_circle(bm, cap_ends=True, segments=48, radius=1)
    for v in bm.verts:
        v.co.z -= 0.18 * v.co.x * v.co.x  # curves down at the sides, like a worn cap
    me = bpy.data.meshes.new("brim")
    bm.to_mesh(me)
    bm.free()
    brim = bpy.data.objects.new("brim", me)
    scene.collection.objects.link(brim)
    brim.location = (0, -0.235, HEAD_Z + 0.205)
    brim.scale = (0.19, 0.16, 0.2)
    brim.rotation_euler = (math.radians(10), 0, 0)
    sol = brim.modifiers.new("Solidify", "SOLIDIFY")
    sol.thickness, sol.offset = 0.12, 0
    bev = brim.modifiers.new("Bevel", "BEVEL")
    bev.width, bev.segments = 0.01, 3
    parts["brim"] = finish(brim, BLACK, subsurf=1)

# body: the hoodie — torso, ribbed hem, kangaroo pocket, the hood gathered at the neck, drawstrings
parts["torso"] = rbox("torso", (0, 0.0, 0.355), (0.36, 0.25, 0.25), BLACK, bevel=0.42)
hem = ring("hem", (0, 0.0, 0.245), (0.175, 0.125, 1), 0.12, BLACK)
hem.rotation_euler = (0, 0, 0)
hem.scale = (0.168, 0.12, 0.09)
parts["hem"] = hem
parts["pocket"] = rbox("pocket", (0, -0.124, 0.305), (0.2, 0.02, 0.085), BLACK, bevel=0.5)
parts["hoodfold"] = sphere("hoodfold", (0, 0.07, 0.475), (0.2, 0.13, 0.075), BLACK)
parts["collar"] = ring("collar", (0, 0.0, 0.47), (0.15, 0.13, 0.4), 0.2, BLACK)
parts["collar"].rotation_euler = (0, 0, 0)
for x in (-0.035, 0.035):
    parts[f"string{x}"] = capsule(f"string{x}", (x, -0.128, 0.455), (x * 1.25, -0.133, 0.375), 0.007, M["string"])
    parts[f"aglet{x}"] = capsule(f"aglet{x}", (x * 1.25, -0.133, 0.38), (x * 1.27, -0.134, 0.36), 0.0085, M["metal"])

# legs: cargo pants with side pockets and gathered cuffs; detailed sneakers
LEG_X = 0.078
legs = {}
for side, x in (("L", LEG_X), ("R", -LEG_X)):
    o = 1 if x > 0 else -1  # outward
    pieces = [
        capsule(f"leg.{side}", (x, 0, 0.23), (x, 0, 0.11), 0.066, BLACK),
        rbox(f"cargo.{side}", (x + o * 0.063, 0.0, 0.165), (0.022, 0.075, 0.07), BLACK, bevel=0.4),
        rbox(f"flap.{side}", (x + o * 0.074, 0.0, 0.2), (0.012, 0.078, 0.022), BLACK, bevel=0.4),
        ring(f"cuff.{side}", (x, 0, 0.105), (0.062, 0.062, 0.35), 0.22, BLACK),
        # sneaker: rubber outsole, midsole, leather upper, toe cap, yellow heel and side panels, laces, tongue
        rbox(f"sole.{side}", (x, -0.022, 0.014), (0.128, 0.215, 0.028), M["sole"], bevel=0.4),
        rbox(f"midsole.{side}", (x, -0.02, 0.038), (0.124, 0.208, 0.026), M["midsole"], bevel=0.45),
        rbox(f"upper.{side}", (x, -0.012, 0.078), (0.112, 0.18, 0.068), M["shoe"], bevel=0.5),
        sphere(f"toecap.{side}", (x, -0.085, 0.066), (0.056, 0.05, 0.036), M["shoe"]),
        rbox(f"heel.{side}", (x, 0.062, 0.078), (0.114, 0.05, 0.062), M["accent"], bevel=0.45),
        rbox(f"panel.{side}", (x + o * 0.055, -0.005, 0.07), (0.006, 0.075, 0.035), M["accent"], bevel=0.45),
        rbox(f"tongue.{side}", (x, -0.005, 0.115), (0.06, 0.06, 0.03), M["shoe"], bevel=0.5),
    ]
    for i, y in enumerate((-0.062, -0.044, -0.026, -0.008)):
        pieces.append(capsule(f"lace{i}.{side}", (x - 0.03, y, 0.113 + i * 0.003), (x + 0.03, y, 0.113 + i * 0.003), 0.0055, M["lace"]))
    legs[side] = pieces

# arms: hoodie sleeves with ribbed cuffs, white four-finger gloves with a flared cuff
SH_X, SH_Z, EL_Z, WR_Z = 0.17, 0.44, 0.335, 0.235
arms = {}
for side, s in (("L", 1), ("R", -1)):
    hx = s * (SH_X + 0.03)
    upper = capsule(f"upperarm.{side}", (s * SH_X, 0, SH_Z), (s * (SH_X + 0.025), 0, EL_Z), 0.058, BLACK)
    hand = [
        capsule(f"forearm.{side}", (s * (SH_X + 0.025), 0, EL_Z), (hx, 0, WR_Z + 0.045), 0.053, BLACK),
        ring(f"sleevecuff.{side}", (hx, 0, WR_Z + 0.04), (0.05, 0.05, 0.45), 0.2, BLACK),
        cyl(f"glovecuff.{side}", (hx, 0, WR_Z + 0.022), 0.048, 0.03, M["glove"], verts=32, subsurf=1),
        sphere(f"palm.{side}", (hx, -0.004, WR_Z - 0.012), (0.04, 0.05, 0.045), M["glove"]),
    ]
    for i, y in enumerate((-0.034, -0.012, 0.01, 0.03)):
        ln = (0.05, 0.055, 0.052, 0.042)[i]
        hand.append(capsule(f"finger{i}.{side}", (hx + s * 0.004, y, WR_Z - 0.035), (hx + s * 0.012, y - 0.004, WR_Z - 0.035 - ln), 0.0125, M["glove"]))
    hand.append(capsule(f"thumb.{side}", (hx - s * 0.02, -0.035, WR_Z - 0.005), (hx - s * 0.03, -0.062, WR_Z - 0.035), 0.0135, M["glove"]))
    arms[side] = (upper, hand)

# a soft contact shadow on the floor (Cycles shadow catcher: only the shadow reaches the transparent frame)
bpy.ops.mesh.primitive_circle_add(vertices=48, radius=0.32, fill_type="NGON", location=(0, -0.01, 0))
floor = bpy.context.active_object
floor.name = "MascotShadow"
floor.is_shadow_catcher = True

# ---- the skeleton ------------------------------------------------------------------------------
arm_data = bpy.data.armatures.new("Rig")
rig = bpy.data.objects.new("Rig", arm_data)
scene.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode="EDIT")


def bone(name, head, tail, parent=None):
    b = arm_data.edit_bones.new(name)
    b.head, b.tail = head, tail
    if parent:
        b.parent = arm_data.edit_bones[parent]
    return b


bone("root", (0, 0, 0), (0, 0, 0.1))
bone("hips", (0, 0, 0.23), (0, 0, 0.3), "root")
bone("spine", (0, 0, 0.3), (0, 0, 0.47), "hips")
bone("head", (0, 0, 0.47), (0, 0, 0.8), "spine")
for side, s in (("L", 1), ("R", -1)):
    bone(f"upperarm.{side}", (s * SH_X, 0, SH_Z), (s * (SH_X + 0.025), 0, EL_Z), "spine")
    bone(f"forearm.{side}", (s * (SH_X + 0.025), 0, EL_Z), (s * (SH_X + 0.03), 0, WR_Z - 0.03), f"upperarm.{side}")
    bone(f"thigh.{side}", (s * LEG_X, 0, 0.23), (s * LEG_X, 0, 0.02), "hips")
bpy.ops.object.mode_set(mode="OBJECT")


def attach(o, bone_name):
    bpy.context.view_layer.update()
    mw = o.matrix_world.copy()
    o.parent, o.parent_type, o.parent_bone = rig, "BONE", bone_name
    bpy.context.view_layer.update()
    o.matrix_world = mw


for k in ("hood", "visor", "hoodrim", "visorrim", "cap", "capbutton", "brim"):
    if k not in parts:
        continue
    attach(parts[k], "head")
for k, o in parts.items():
    if o.parent is None and o.name != "MascotShadow":
        attach(o, "spine")
for side in ("L", "R"):
    for o in legs[side]:
        attach(o, f"thigh.{side}")
    upper, fore = arms[side]
    attach(upper, f"upperarm.{side}")
    for o in fore:
        attach(o, f"forearm.{side}")

# ---- props ---------------------------------------------------------------------------------------
props = bpy.data.collections.new("Props")
scene.collection.children.link(props)


def prop(name, objs, bone_name):
    o = join(name, objs) if len(objs) > 1 else objs[0]
    o.name = name
    for c in o.users_collection:
        c.objects.unlink(o)
    props.objects.link(o)
    attach(o, bone_name)
    o.hide_render = o.hide_viewport = True
    return o


R90 = (math.radians(90), 0, 0)
HAND_R = Vector((-(SH_X + 0.03), -0.06, WR_Z - 0.04))  # the right hand at rest
CHEST = Vector((0, -0.25, 0.37))
ABOVE = Vector((0.27, -0.05, 0.98))  # symbols float beside the head

cloud = [sphere(f"c{i}", CHEST + Vector(o) + Vector((0, 0, 0.06)), (r, r * 0.8, r), M["yellow"])
         for i, (o, r) in enumerate([((-0.07, 0, 0), 0.07), ((0.0, 0, 0.035), 0.09), ((0.08, 0, 0), 0.07), ((0, 0, -0.02), 0.08)])]
arrow = [cyl("arrowshaft", CHEST + Vector((0, -0.075, 0.05)), 0.015, 0.07, M["paper"]),
         cyl("arrowhead", CHEST + Vector((0, -0.075, 0.1)), 0.04, 0.045, M["paper"], verts=3)]
arrow[1].scale = (1, 0.4, 1)
prop("prop_cloud", cloud + arrow, "spine")
# an idea: a glass bulb with a glowing filament and a brass screw base, held in the left hand
HAND_L = Vector((SH_X + 0.03, -0.06, WR_Z - 0.04))
bulb_c = HAND_L + Vector((0.01, -0.03, 0.1))
prop("prop_bulb", [sphere("bulbglass", bulb_c, (0.055, 0.055, 0.06), M["bulb"]),
                   cyl("bulbneck", bulb_c + Vector((0, 0, -0.06)), 0.028, 0.035, M["bulb"], subsurf=1),
                   cyl("bulbbase", bulb_c + Vector((0, 0, -0.088)), 0.026, 0.03, M["brass"]),
                   capsule("filament", bulb_c + Vector((-0.018, 0, 0.005)), bulb_c + Vector((0.018, 0, 0.005)), 0.004, M["filament"])],
     "forearm.L")
prop("prop_question", [text("q", "?", ABOVE, 0.16, M["yellow"])], "head")
prop("prop_exclaim", [text("ex", "!!", ABOVE, 0.16, M["yellow"])], "head")
prop("prop_zzz", [text("z1", "z", ABOVE + Vector((-0.04, 0, -0.02)), 0.08, M["yellow"]),
                  text("z2", "Z", ABOVE + Vector((0.04, 0, 0.07)), 0.12, M["yellow"])], "head")
sparks = []
for i, a in enumerate((-70, -30, 10, 200, 240)):
    v = Vector((math.cos(math.radians(a)) * 0.38, -0.05, HEAD_Z + 0.1 + math.sin(math.radians(a)) * 0.34))
    s = rbox(f"spark{i}", v, (0.012, 0.012, 0.06), M["yellow"], bevel=0.45)
    s.rotation_euler = (0, math.radians(90 - a), 0)
    sparks.append(s)
prop("prop_sparks", sparks, "head")
confetti = []
for i in range(26):
    rng = (i * 7919) % 1000 / 1000
    rng2 = (i * 104729) % 1000 / 1000
    v = Vector(((rng - 0.5) * 0.9, -0.15 - rng2 * 0.1, 0.55 + ((i * 31) % 17) / 17 * 0.6))
    c = rbox(f"conf{i}", v, (0.03, 0.006, 0.018), CONFETTI[i % len(CONFETTI)], bevel=0.2)
    c.rotation_euler = (rng * 3, rng2 * 3, (rng + rng2) * 3)
    confetti.append(c)
prop("prop_confetti", confetti, "root")
heart = [sphere("h1", ABOVE + Vector((-0.03, 0, 0.02)), (0.04, 0.025, 0.04), M["pink"]),
         sphere("h2", ABOVE + Vector((0.03, 0, 0.02)), (0.04, 0.025, 0.04), M["pink"])]
hb = cyl("h3", ABOVE + Vector((0, 0, -0.02)), 0.055, 0.05, M["pink"], rot=R90, verts=3)
hb.rotation_euler = (math.radians(90), math.radians(-90), 0)
prop("prop_heart", heart + [hb], "head")
drop = sphere("drop", Vector((0.2, -0.17, HEAD_Z + 0.08)), (0.025, 0.02, 0.03), M["blue"])
dtip = cyl("droptip", Vector((0.2, -0.17, HEAD_Z + 0.115)), 0.022, 0.035, M["blue"], verts=16)
dtip.scale = (1, 0.8, 1)
bpy.ops.object.select_all(action="DESELECT")
prop("prop_sweat", [drop, dtip], "head")
chk = [capsule("ck1", ABOVE + Vector((-0.06, 0, 0.0)), ABOVE + Vector((-0.02, 0, -0.045)), 0.018, M["green"]),
       capsule("ck2", ABOVE + Vector((-0.02, 0, -0.045)), ABOVE + Vector((0.07, 0, 0.06)), 0.018, M["green"])]
prop("prop_check", chk, "head")
# a five-point star
star = bpy.data.meshes.new("star")
verts = []
for i in range(10):
    r = 0.07 if i % 2 == 0 else 0.03
    a = math.pi / 2 + i * math.pi / 5
    verts.append((math.cos(a) * r, 0, math.sin(a) * r))
star.from_pydata(verts + [(0, 0, 0)], [], [(i, (i + 1) % 10, 10) for i in range(10)])
so = bpy.data.objects.new("star", star)
scene.collection.objects.link(so)
so.location = ABOVE + Vector((0, 0, 0.02))
so.modifiers.new("Solidify", "SOLIDIFY").thickness = 0.02
so.data.materials.append(M["yellow"])
prop("prop_star", [so], "head")
card = HAND_R + Vector((0.02, -0.06, 0.13))
frame = [rbox("card", card, (0.17, 0.008, 0.2), M["paper"], bevel=0.05),
         sphere("photoface", card + Vector((0, -0.006, 0.01)), (0.045, 0.003, 0.055), M["skin"], subsurf=0)]
for dx, dz in ((-1, 1), (1, 1), (-1, -1), (1, -1)):
    c = card + Vector((dx * 0.06, -0.008, dz * 0.07))
    frame.append(rbox(f"brh{dx}{dz}", c + Vector((-dx * 0.012, 0, 0)), (0.026, 0.004, 0.006), M["yellow"], bevel=0.2))
    frame.append(rbox(f"brv{dx}{dz}", c + Vector((0, 0, -dz * 0.012)), (0.006, 0.004, 0.026), M["yellow"], bevel=0.2))
prop("prop_faceframe", frame, "forearm.R")
# the scan line sweeps the card (a driver on the frame number, so every mood that shows it animates it)
line = rbox("scanline", card + Vector((0, -0.012, 0)), (0.16, 0.003, 0.008), M["pink"], bevel=0.3)
sl = prop("prop_scanline", [line], "forearm.R")
d = sl.driver_add("delta_location", 2).driver
d.type, d.expression = "SCRIPTED", "0.08*sin(frame/3.0)"
props_for_faceframe = ["faceframe", "scanline"]


# ---- realistic props -----------------------------------------------------------------------------
def textured(name, color, rough, scale=200, strength=0.25, metal=0.0, kind="noise"):
    """A material with a fine surface texture (leatherette, cardboard fibre, brushed metal, wood)."""
    m = material(name, color, rough=rough, metal=metal)
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    if kind == "wood":
        t = nt.nodes.new("ShaderNodeTexWave")
        t.inputs["Scale"].default_value = scale
        t.inputs["Distortion"].default_value = 6
        ramp = nt.nodes.new("ShaderNodeValToRGB")
        ramp.color_ramp.elements[0].color = (color[0] * 0.6, color[1] * 0.6, color[2] * 0.6, 1)
        ramp.color_ramp.elements[1].color = (*color, 1)
        nt.links.new(t.outputs["Fac"], ramp.inputs["Fac"])
        nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
    else:
        t = nt.nodes.new("ShaderNodeTexNoise")
        t.inputs["Scale"].default_value = scale
        t.inputs["Detail"].default_value = 8
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = strength
    bump.inputs["Distance"].default_value = 0.001
    nt.links.new(t.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], b.inputs["Normal"])
    return m


def screen_mat(name, top, bottom, strength=1.4):
    """A lit display showing a soft photo-like gradient (sky → warm horizon)."""
    m = material(name, (0, 0, 0), rough=0.15, coat=1.0)
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    coord = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*bottom, 1)
    ramp.color_ramp.elements[1].color = (*top, 1)
    nt.links.new(coord.outputs["Generated"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], b.inputs["Emission Color"])
    b.inputs["Emission Strength"].default_value = strength
    return m


def cable(name, points, r, mat):
    """A real, smoothly bending cable through points."""
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions, cu.bevel_depth, cu.bevel_resolution = "3D", r, 6
    sp = cu.splines.new("BEZIER")
    sp.bezier_points.add(len(points) - 1)
    for bp, pt in zip(sp.bezier_points, points):
        bp.co = pt
        bp.handle_left_type = bp.handle_right_type = "AUTO"
    o = bpy.data.objects.new(name, cu)
    scene.collection.objects.link(o)
    o.data.materials.append(mat)
    return o


R = {
    "leatherette": textured("Leatherette", (0.02, 0.02, 0.022), 0.6, scale=900, strength=0.35),
    "rubber": textured("GripRubber", (0.015, 0.015, 0.016), 0.8, scale=600, strength=0.5),
    "alu": textured("Aluminium", (0.78, 0.79, 0.81), 0.32, scale=40, strength=0.05, metal=1.0),
    "chrome": material("Chrome", (0.9, 0.9, 0.92), rough=0.08, metal=1.0),
    "keys": material("Keycap", (0.03, 0.03, 0.035), rough=0.55),
    "bezel": material("Bezel", (0.01, 0.01, 0.012), rough=0.2, coat=1.0),
    "lens": glass("LensGlass"),
    "coating": material("LensCoating", (0.02, 0.05, 0.12), rough=0.02, coat=1.0, metal=0.6),
    "redring": material("LensRing", (0.8, 0.05, 0.08), rough=0.3),
    "card": textured("Corrugated", (0.62, 0.43, 0.24), 0.85, scale=120, strength=0.4),
    "tape": material("PackingTape", (0.72, 0.58, 0.38), rough=0.15, coat=0.8),
    "label": material("Label", (0.97, 0.97, 0.95), rough=0.6),
    "ink": material("Ink", (0.05, 0.05, 0.05), rough=0.5),
    "wood": textured("Wood", (0.42, 0.24, 0.11), 0.5, scale=6, strength=0.1, kind="wood"),
    "brasskey": textured("KeyBrass", (0.86, 0.66, 0.3), 0.3, scale=300, strength=0.05, metal=1.0),
    "sand": textured("Sand", (0.86, 0.68, 0.4), 0.9, scale=500, strength=0.3),
    "plastic": material("WhitePlastic", (0.92, 0.92, 0.9), rough=0.3, coat=0.3),
    "cord": material("Cord", (0.02, 0.02, 0.022), rough=0.35, coat=0.4),
    "envpaper": textured("EnvelopePaper", (0.95, 0.93, 0.88), 0.8, scale=300, strength=0.08),
    "seal": material("WaxSeal", (0.85, 0.08, 0.45), rough=0.35, coat=0.5),
    "display": screen_mat("Display", (0.35, 0.62, 1.0), (1.0, 0.55, 0.25)),
    "phonescreen": screen_mat("PhoneDisplay", (0.4, 0.7, 1.0), (1.0, 0.45, 0.6)),
}

# camera: a DSLR — leatherette body, prism hump, rubber grip, mode dial, shutter, hot shoe, strap lugs,
# a zoom lens with grip rings, a red ring and coated front glass. Held up to the face.
cam = Vector((0, -0.37, HEAD_Z - 0.01))
L = Vector((0.03, -0.0, -0.005))  # lens axis offset on the body
camparts = [
    rbox("cambody", cam, (0.2, 0.085, 0.12), R["leatherette"], bevel=0.18),
    rbox("camprism", cam + Vector((0.025, 0, 0.075)), (0.07, 0.07, 0.04), R["leatherette"], bevel=0.3),
    rbox("camgrip", cam + Vector((-0.083, -0.022, -0.008)), (0.045, 0.1, 0.115), R["rubber"], bevel=0.4),
    cyl("camdial", cam + Vector((-0.055, 0.0, 0.066)), 0.02, 0.014, R["chrome"], verts=24),
    cyl("camshutter", cam + Vector((-0.08, -0.03, 0.066)), 0.009, 0.01, R["chrome"]),
    rbox("hotshoe", cam + Vector((0.025, 0, 0.097)), (0.03, 0.025, 0.006), R["chrome"], bevel=0.2),
    rbox("lug1", cam + Vector((0.102, 0, 0.04)), (0.008, 0.012, 0.016), R["chrome"], bevel=0.3),
    rbox("lug2", cam + Vector((-0.102, 0, 0.04)), (0.008, 0.012, 0.016), R["chrome"], bevel=0.3),
    cyl("lensmount", cam + L + Vector((0, -0.05, 0)), 0.047, 0.014, R["chrome"], rot=R90, verts=40),
    cyl("lensbarrel", cam + L + Vector((0, -0.09, 0)), 0.044, 0.07, R["leatherette"], rot=R90, verts=40),
    cyl("lensgrip", cam + L + Vector((0, -0.1, 0)), 0.046, 0.03, R["rubber"], rot=R90, verts=40),
    cyl("lensred", cam + L + Vector((0, -0.125, 0)), 0.0445, 0.004, R["redring"], rot=R90, verts=40),
    cyl("lenshood", cam + L + Vector((0, -0.135, 0)), 0.042, 0.018, R["leatherette"], rot=R90, verts=40),
    cyl("lensglass", cam + L + Vector((0, -0.141, 0)), 0.034, 0.006, R["coating"], rot=R90, verts=40),
]
prop("prop_camera", camparts, "head")

# laptop: aluminium base with a keyboard and trackpad, a lid with a black bezel and a lit display
lap = CHEST + Vector((0, -0.02, -0.05))
lapparts = [
    rbox("lapbase", lap, (0.27, 0.18, 0.012), R["alu"], bevel=0.25),
    rbox("trackpad", lap + Vector((0, -0.055, 0.0062)), (0.08, 0.045, 0.001), R["alu"], bevel=0.2),
    rbox("lid", lap + Vector((0, 0.09, 0.085)), (0.27, 0.01, 0.17), R["alu"], bevel=0.2),
    rbox("bezel", lap + Vector((0, 0.084, 0.085)), (0.258, 0.002, 0.158), R["bezel"], bevel=0.1),
    rbox("display", lap + Vector((0, 0.0828, 0.088)), (0.24, 0.001, 0.14), R["display"], bevel=0.05),
]
for row in range(4):
    for col in range(11):
        lapparts.append(rbox(f"key{row}_{col}", lap + Vector((-0.11 + col * 0.022, 0.005 + row * 0.019, 0.007)),
                             (0.017, 0.015, 0.003), R["keys"], bevel=0.25))
lapparts.append(rbox("spacebar", lap + Vector((0, -0.014, 0.007)), (0.1, 0.015, 0.003), R["keys"], bevel=0.25))
prop("prop_laptop", lapparts, "spine")

# phone: rounded slab, glass display showing a photo, side button
ph = HAND_R + Vector((0, -0.03, 0.07))
prop("prop_phone", [rbox("phone", ph, (0.075, 0.011, 0.15), R["alu"], bevel=0.3),
                    rbox("phoneglass", ph + Vector((0, -0.0058, 0)), (0.068, 0.0012, 0.142), R["phonescreen"], bevel=0.12),
                    rbox("phonebutton", ph + Vector((0.038, 0, 0.03)), (0.004, 0.005, 0.02), R["alu"], bevel=0.3)], "forearm.R")

# box: corrugated cardboard, packing tape over the seam and down the sides, a shipping label
bx = CHEST
prop("prop_box", [rbox("box", bx, (0.2, 0.15, 0.14), R["card"], bevel=0.06),
                  rbox("tapetop", bx + Vector((0, 0, 0.0705)), (0.05, 0.152, 0.002), R["tape"], bevel=0.1),
                  rbox("tapefront", bx + Vector((0, -0.0755, 0.045)), (0.05, 0.002, 0.05), R["tape"], bevel=0.1),
                  rbox("label", bx + Vector((0.05, -0.0757, -0.02)), (0.07, 0.002, 0.045), R["label"], bevel=0.1)]
     + [rbox(f"bar{i}", bx + Vector((0.025 + i * 0.006, -0.0767, -0.03)), (0.002 + (i % 3) * 0.001, 0.001, 0.016), R["ink"], bevel=0.0)
        for i in range(9)], "spine")

# magnifier: chrome rim, real glass, a turned wooden handle with a metal ferrule
mg = HAND_R + Vector((0, -0.02, 0.12))
prop("prop_magnifier", [ring("lensrim", mg, (0.07, 0.07, 0.07), 0.14, R["chrome"]),
                        cyl("lens", mg, 0.066, 0.008, R["lens"], rot=R90, verts=48),
                        cyl("ferrule", mg + Vector((0, 0, -0.085)), 0.014, 0.025, R["chrome"]),
                        capsule("handle", mg + Vector((0, 0, -0.1)), mg + Vector((0, 0, -0.19)), 0.016, R["wood"])], "forearm.R")

# key: a brass key with a round bow and a toothed bit
k = HAND_R + Vector((0, -0.03, 0.06))
prop("prop_key", [ring("keybow", k, (0.032, 0.032, 0.032), 0.25, R["brasskey"]),
                  rbox("keyshaft", k + Vector((0, 0, -0.075)), (0.013, 0.008, 0.09), R["brasskey"], bevel=0.3),
                  rbox("keytooth1", k + Vector((0.013, 0, -0.105)), (0.016, 0.008, 0.01), R["brasskey"], bevel=0.3),
                  rbox("keytooth2", k + Vector((0.011, 0, -0.085)), (0.012, 0.008, 0.008), R["brasskey"], bevel=0.3)], "forearm.R")

# envelope: textured paper, a folded flap, a StudioShare-pink wax seal
ev = HAND_R + Vector((0, -0.04, 0.06))
flap = bpy.data.meshes.new("flap")
flap.from_pydata([(-0.08, 0, 0.05), (0.08, 0, 0.05), (0, 0, -0.005)], [], [(0, 1, 2)])
fo = bpy.data.objects.new("flap", flap)
scene.collection.objects.link(fo)
fo.location = ev + Vector((0, -0.0065, 0))
fo.modifiers.new("Solidify", "SOLIDIFY").thickness = 0.002
fo.data.materials.append(R["envpaper"])
prop("prop_envelope", [rbox("envelope", ev, (0.16, 0.011, 0.1), R["envpaper"], bevel=0.08), fo,
                       cyl("seal", ev + Vector((0, -0.009, 0.0)), 0.016, 0.006, R["seal"], rot=R90, subsurf=1)], "forearm.R")

# plug (connecting): a white plug with metal prongs on a real cable, held out in front
pl = CHEST + Vector((0, -0.02, 0.0))
prop("prop_plug", [rbox("plugbody", pl, (0.06, 0.05, 0.08), R["plastic"], bevel=0.35),
                   rbox("prong1", pl + Vector((-0.015, -0.04, 0.0)), (0.007, 0.035, 0.016), R["chrome"], bevel=0.2),
                   rbox("prong2", pl + Vector((0.015, -0.04, 0.0)), (0.007, 0.035, 0.016), R["chrome"], bevel=0.2),
                   cable("cord", [pl + Vector((0, 0.03, -0.02)), pl + Vector((0.06, 0.08, -0.12)), pl + Vector((0.02, 0.06, -0.3))], 0.008, R["cord"])],
     "spine")
# unplugged cable (disconnected): dangling from the right hand
cp = HAND_R + Vector((0, -0.02, -0.04))
prop("prop_cable", [rbox("cplug", cp + Vector((0, 0, -0.035)), (0.045, 0.04, 0.06), R["plastic"], bevel=0.35),
                    rbox("cprong1", cp + Vector((-0.012, 0, -0.08)), (0.006, 0.014, 0.03), R["chrome"], bevel=0.2),
                    rbox("cprong2", cp + Vector((0.012, 0, -0.08)), (0.006, 0.014, 0.03), R["chrome"], bevel=0.2),
                    cable("ccord", [cp + Vector((0, 0, 0.0)), cp + Vector((0.05, -0.02, 0.06)), cp + Vector((0.09, 0.0, -0.1)),
                                    cp + Vector((0.07, 0.02, -0.25))], 0.007, R["cord"])], "forearm.R")

# hourglass: turned wooden caps and posts, glass bulbs, sand running down
hg = Vector((0.3, -0.05, 0.2))
hgparts = [cyl("hgtop", hg + Vector((0, 0, 0.1)), 0.06, 0.016, R["wood"], verts=40, subsurf=1),
           cyl("hgbot", hg + Vector((0, 0, -0.1)), 0.06, 0.016, R["wood"], verts=40, subsurf=1),
           sphere("hgg1", hg + Vector((0, 0, 0.048)), (0.042, 0.042, 0.05), R["lens"]),
           sphere("hgg2", hg + Vector((0, 0, -0.048)), (0.042, 0.042, 0.05), R["lens"]),
           cyl("hgsand1", hg + Vector((0, 0, 0.03)), 0.028, 0.02, R["sand"], verts=24),
           cyl("hgsand2", hg + Vector((0, 0, -0.07)), 0.036, 0.03, R["sand"], verts=24)]
for i, a in enumerate((0, 120, 240)):
    v = Vector((math.cos(math.radians(a)) * 0.05, math.sin(math.radians(a)) * 0.05, 0))
    hgparts.append(capsule(f"hgpost{i}", hg + v + Vector((0, 0, -0.095)), hg + v + Vector((0, 0, 0.095)), 0.006, R["wood"]))
prop("prop_hourglass", hgparts, "root")

# ---- animation -----------------------------------------------------------------------------------
PB = rig.pose.bones


def rot(bone_name, wx=0.0, wy=0.0, wz=0.0):
    """Rotate a bone about the world axes of its rest pose (degrees) — the same meaning for every bone:
    x+ tips an upward bone forward / a downward bone backward, z+ turns to the character's left."""
    pb = PB[bone_name]
    rest = pb.bone.matrix_local.to_quaternion()
    q = Euler((math.radians(wx), math.radians(wy), math.radians(wz)), "XYZ").to_quaternion()
    pb.rotation_mode = "QUATERNION"
    pb.rotation_quaternion = rest.inverted() @ q @ rest


def move(dx=0.0, dy=0.0, dz=0.0):
    pb = PB["root"]
    rest = pb.bone.matrix_local.to_quaternion()
    pb.location = rest.inverted() @ Vector((dx, dy, dz))


def arm(side, out=8.0, fwd=0.0, elbow=0.0, twist=0.0):
    """out: away from the body (deg), fwd: forward (deg), elbow: bend (deg)."""
    s = 1 if side == "L" else -1
    rot(f"upperarm.{side}", -fwd, -s * out, s * twist)
    rot(f"forearm.{side}", -elbow, 0, 0)


def leg(side, fwd=0.0, out=0.0):
    s = 1 if side == "L" else -1
    rot(f"thigh.{side}", -fwd, -s * out, 0)


def rest_pose():
    for pb in PB:
        pb.rotation_mode = "QUATERNION"
        pb.rotation_quaternion = Quaternion()
        pb.location = (0, 0, 0)


S = math.sin
TAU = 2 * math.pi


def wave01(t):
    return 0.5 + 0.5 * S(t * TAU)


# Each mood: (frames at 24 fps, pose(t) for t in [0, 1)). Loops return to their start.
def idle(t):
    arm("L", 9 + 2 * S(t * TAU)); arm("R", 9 + 2 * S(t * TAU))
    rot("head", 2 * S(t * TAU), 0, 0); move(dz=0.006 * S(t * TAU))


def waiting(t):
    look = 16 * S(t * TAU)
    arm("L", 14, -28, 45); arm("R", 14, -28, 45)
    rot("spine", 5, 0, 0); rot("head", -2, 0, look); move(dz=0.004 * S(2 * t * TAU))


def shy(t):
    arm("L", 22, 118, 108); arm("R", 22, 118, 108)
    rot("head", 3, 6 * S(t * TAU), -14); rot("spine", 6, 0, 0)


def happy(t):
    arm("R", 112, 28, 55 + 25 * S(t * 2 * TAU)); arm("L", 10)
    rot("head", -4, -6, 0); move(dz=0.05 * abs(S(t * 2 * TAU)))


def celebrating(t):
    up = abs(S(t * TAU))
    arm("L", 118 + 8 * up, 22, 25); arm("R", 118 + 8 * up, 22, 25)
    leg("L", 10 * up, 6); leg("R", 10 * up, 6)
    rot("head", -10 * up, 0, 0); move(dz=0.18 * up)


def proud(t):
    k = min(1.0, t * 3)
    arm("R", 75 * k, 35 * k, 10); arm("L", 25, -10, 70)
    rot("spine", -6, 0, -8 * k); rot("head", -6, 6, 0)


def curious(t):
    arm("R", 120, 20, 115); arm("L", 10)
    rot("spine", 10, 0, 0); rot("head", 4, 16 + 3 * S(t * TAU), 0)


def excited(t):
    arm("R", 20, 85, 5); arm("L", 30, 0, 30)
    rot("head", -8, 0, 0); move(dz=0.03 + 0.03 * abs(S(t * 2 * TAU)))


def focused(t):
    arm("L", 12, 50, 55); arm("R", 12, 50, 55)
    rot("spine", 12, 0, 0); rot("head", 4, 0, 2 * S(t * TAU))


def carrying(t):
    p = S(t * TAU)
    arm("L", 14, 62, 55); arm("R", 14, 62, 55)
    leg("L", 28 * p); leg("R", -28 * p)
    rot("spine", 4, 0, 4 * p); move(dz=0.02 * abs(S(t * 2 * TAU)))


def thinking(t):
    arm("R", 22, 112, 128); arm("L", 20, 45, 80)
    rot("head", -12, 8, 10 * S(t * TAU))


def searching(t):
    p = S(t * TAU)
    arm("R", 20, 72, 30, twist=0); arm("L", 10)
    rot("spine", 8, 0, 18 * p); rot("head", 4, 0, 14 * p)


def connecting(t):
    arm("L", 14, 72, 30); arm("R", 14, 72, 30)
    rot("spine", 6, 0, 0); rot("head", 2, 0, 0); move(dz=0.005 * S(t * TAU))


def connected(t):
    arm("R", 15, 60, 100); arm("L", 10)
    rot("head", 8 * max(0, S(t * 2 * TAU)), 0, 0)


def disconnected(t):
    arm("L", 45, 25, 75); arm("R", 45, 50, 40)
    rot("head", 6, 8, 0); rot("spine", 6, 0, 0); move(dz=0.004 * S(t * TAU))


def sleepy(t):
    arm("L", 5, 5); arm("R", 5, 5)
    rot("spine", 10, 0, 0); rot("head", 8 + 3 * S(t * TAU), 12, 0); move(dz=0.006 * S(t * TAU))


def hot(t):
    arm("R", 30, 95, 120 + 20 * S(t * 3 * TAU)); arm("L", 8)
    rot("head", 6, -6, 0); rot("spine", 5, 0, 0)


def patient(t):
    arm("L", 18, 75, 105); arm("R", 9)
    rot("head", 6, 0, -14); leg("R", 12 * max(0, S(t * 2 * TAU)))


def oops(t):
    arm("R", 118, 5, 95 + 10 * S(t * 2 * TAU)); arm("L", 12)
    rot("head", 4, -12, 0); move(dx=0.008 * S(t * 4 * TAU))


def sad(t):
    arm("L", 4, 4); arm("R", 4, 4)
    rot("spine", 10, 0, 0); rot("head", 7, 0, 0); move(dz=-0.01 + 0.003 * S(t * TAU))


def locked(t):
    arm("R", 15, 70, 25); arm("L", 6)
    rot("head", 4, 0, 0); move(dz=0.003 * S(t * TAU))


def careful(t):
    arm("L", 25, 80, 40); arm("R", 25, 80, 40)
    rot("spine", -6, 0, 0); rot("head", -4, 0, 0); move(dy=0.03, dz=0.004 * S(t * TAU))


def goodbye(t):
    arm("R", 112, 28, 55 + 25 * S(t * 2 * TAU)); arm("L", 10)
    rot("spine", 0, 0, -20 * t); rot("head", 0, 0, 15)


def ok(t):
    k = min(1.0, t * 4)
    arm("R", 15, 60 * k, 100 * k); arm("L", 10)
    rot("head", 6 * S(t * 2 * TAU), -8, 0)


def scanning(t):
    arm("R", 15, 80, 35); arm("L", 10)
    rot("head", 4, 0, 0); move(dz=0.004 * S(t * TAU))


def walk(t):
    p = S(t * TAU)
    leg("L", 32 * p); leg("R", -32 * p)
    arm("L", 10, -28 * p, 20); arm("R", 10, 28 * p, 20)
    rot("spine", 4, 0, -3 * p); move(dz=0.025 * abs(S(t * TAU)))


def glance(t):
    arm("L", 9); arm("R", 9)
    rot("head", -4, 0, 22 * S(t * math.pi))


def uploading(t):
    p = S(t * TAU)
    arm("L", 25, 78, 40); arm("R", 25, 78, 40)
    leg("L", 28 * p); leg("R", -28 * p); move(dz=0.02 * abs(S(t * 2 * TAU)))


def sending(t):
    k = t * 1.4
    fwd = -50 + 170 * min(1.0, max(0.0, (k - 0.3) / 0.4))
    arm("R", 20, fwd, 30); arm("L", 15, 20, 30)
    rot("spine", 8 * min(1.0, k), 0, -10 * min(1.0, k))


def capturing(t):
    arm("L", 30, 95, 105); arm("R", 30, 95, 105)
    rot("head", 2, 0, 0); move(dz=0.003 * S(t * TAU))


MOODS = {
    "idle": (48, idle), "waiting": (72, waiting), "shy": (48, shy), "happy": (36, happy),
    "celebrating": (24, celebrating), "proud": (36, proud), "curious": (48, curious), "excited": (24, excited),
    "focused": (48, focused), "carrying": (24, carrying), "thinking": (72, thinking), "searching": (48, searching),
    "connecting": (48, connecting), "connected": (36, connected), "disconnected": (48, disconnected),
    "sleepy": (72, sleepy), "hot": (24, hot), "patient": (48, patient), "oops": (24, oops), "sad": (72, sad),
    "locked": (48, locked), "careful": (48, careful), "goodbye": (36, goodbye), "ok": (36, ok),
    "scanning": (48, scanning), "walk": (24, walk), "glance": (24, glance), "uploading": (24, uploading),
    "sending": (24, sending), "capturing": (48, capturing),
}

rig.animation_data_create()
for name, (frames, fn) in MOODS.items():
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    rig.animation_data.action = act
    if hasattr(rig.animation_data, "action_slot") and getattr(act, "slots", None) is not None and len(act.slots):
        rig.animation_data.action_slot = act.slots[0]
    for f in range(0, frames + 1, 2):
        rest_pose()
        fn((f / frames) % 1.0 if f < frames else 0.0 if name not in ("sending", "proud", "ok", "goodbye") else 1.0)
        for pb in PB:
            pb.keyframe_insert("rotation_quaternion", frame=f)
            pb.keyframe_insert("location", frame=f)
    act.use_frame_range = True
    act.frame_range = (0, frames)
rig.animation_data.action = bpy.data.actions["idle"]

os.makedirs(os.path.dirname(OUT), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=OUT)
print("[mascot] model →", OUT, "with", len(MOODS), "actions and", len(props.objects), "props")
