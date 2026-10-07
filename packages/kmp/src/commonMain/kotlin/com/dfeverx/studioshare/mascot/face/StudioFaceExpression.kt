package com.dfeverx.studioshare.mascot.face

/**
 * The StudioShare app-icon face as a character: what it does with its eyes and mouth for each of the
 * pack's moods. Separate from the 3D mascot — it shares only the mood names (so anything that drives
 * the mascot by mood, like `MascotAgent`, drives the face too) and [com.dfeverx.studioshare.mascot.render.MascotMotion]'s
 * body motions.
 */
enum class EyeShape {
    /** The icon's own eyes: two leaning drops. */
    Teardrop,
    /** Upward arcs (∩), eyes smiling. */
    Happy,
    /** Downward arcs (∪), eyes shut. */
    Closed,
    /** Heavy lids. */
    Sleepy,
    Wide,
    /** Lids lowered a little: concentrating. */
    Focused,
    /** Tilted in, lids slanted. */
    Worried,
    /** Right eye shut. */
    Wink,
    /** Eyes turned up and to the side. */
    Look,
    /** Eyes sweeping left and right. */
    Scan,
}

enum class MouthShape { Smile, Small, Grin, Flat, SmallO, Open, Frown, Wavy }

/** A small mark drawn at the face's top-right corner, echoing the mood's prop. */
enum class Accent { Sparkles, Question, Exclaim, Sweat, Zzz, Check, Heart, Dots, Lock, Progress }

data class FaceExpression(
    val eyes: EyeShape,
    val mouth: MouthShape,
    val accent: Accent? = null,
    /** One of `MascotMotion`'s ids. */
    val motion: String = "breathe",
)

object StudioFaceExpressions {
    val idle = FaceExpression(EyeShape.Teardrop, MouthShape.Smile)

    /** Every mood in `spec/mascot.json`; the motion matches the spec's (checked by MascotMomentMoodsTest). */
    val byMood: Map<String, FaceExpression> = mapOf(
        "idle" to idle,
        "waiting" to FaceExpression(EyeShape.Look, MouthShape.Small, Accent.Dots),
        "shy" to FaceExpression(EyeShape.Happy, MouthShape.Small, Accent.Heart, "sway"),
        "happy" to FaceExpression(EyeShape.Happy, MouthShape.Grin, Accent.Sparkles, "hop"),
        "celebrating" to FaceExpression(EyeShape.Happy, MouthShape.Grin, Accent.Sparkles, "jump"),
        "proud" to FaceExpression(EyeShape.Focused, MouthShape.Smile, Accent.Sparkles, "pop"),
        "curious" to FaceExpression(EyeShape.Look, MouthShape.SmallO, Accent.Question, "sway"),
        "excited" to FaceExpression(EyeShape.Wide, MouthShape.Open, Accent.Exclaim, "pop"),
        "focused" to FaceExpression(EyeShape.Focused, MouthShape.Flat),
        "carrying" to FaceExpression(EyeShape.Focused, MouthShape.Small, motion = "trot"),
        "thinking" to FaceExpression(EyeShape.Look, MouthShape.Flat, Accent.Question, "sway"),
        "searching" to FaceExpression(EyeShape.Scan, MouthShape.Flat, motion = "sway"),
        "connecting" to FaceExpression(EyeShape.Teardrop, MouthShape.Small, Accent.Dots),
        "connected" to FaceExpression(EyeShape.Happy, MouthShape.Grin, Accent.Check, "hop"),
        "disconnected" to FaceExpression(EyeShape.Worried, MouthShape.Frown, motion = "droop"),
        "sleepy" to FaceExpression(EyeShape.Sleepy, MouthShape.SmallO, Accent.Zzz, "slow"),
        "hot" to FaceExpression(EyeShape.Worried, MouthShape.Wavy, Accent.Sweat, "droop"),
        "patient" to FaceExpression(EyeShape.Sleepy, MouthShape.Small, Accent.Dots),
        "oops" to FaceExpression(EyeShape.Worried, MouthShape.Wavy, Accent.Sweat, "shake"),
        "sad" to FaceExpression(EyeShape.Worried, MouthShape.Frown, motion = "droop"),
        "locked" to FaceExpression(EyeShape.Worried, MouthShape.Flat, Accent.Lock, "droop"),
        "careful" to FaceExpression(EyeShape.Wide, MouthShape.SmallO, Accent.Exclaim, "pop"),
        "goodbye" to FaceExpression(EyeShape.Happy, MouthShape.Smile, Accent.Heart, "sway"),
        "ok" to FaceExpression(EyeShape.Wink, MouthShape.Smile, Accent.Check, "nod"),
        "scanning" to FaceExpression(EyeShape.Scan, MouthShape.Flat),
        "walk" to FaceExpression(EyeShape.Teardrop, MouthShape.Smile, motion = "trot"),
        "glance" to FaceExpression(EyeShape.Wink, MouthShape.Smile, motion = "nod"),
        "uploading" to FaceExpression(EyeShape.Focused, MouthShape.Flat, Accent.Progress, "trot"),
        "sending" to FaceExpression(EyeShape.Happy, MouthShape.Small, Accent.Progress, "trot"),
        "capturing" to FaceExpression(EyeShape.Wide, MouthShape.Smile, Accent.Sparkles),
    )

    /** Unknown moods read as idle, like `MascotPack.resolveMood`. */
    fun forMood(mood: String): FaceExpression = byMood[mood] ?: idle
}
