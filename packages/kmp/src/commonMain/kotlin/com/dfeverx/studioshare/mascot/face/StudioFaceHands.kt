package com.dfeverx.studioshare.mascot.face

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Something the face does with its hands. The face has no hands most of the time: they grow out
 * from behind its lower corners for a gesture and tuck back in when it's done, the way Coucou's
 * Mochi only has hands while it waves. A moment opts in with `hands` in `spec/mascot.json`; the
 * [id]s are that field's values, and `packages/web/src/face.ts` draws the same gestures.
 *
 * @property seconds how long the gesture plays, hands out to hands in.
 * @property still the instant drawn when the face isn't animating.
 * @property inFront drawn over the face (hands on the cheeks) rather than behind it.
 */
enum class HandGesture(val id: String, internal val seconds: Float, internal val still: Float, internal val inFront: Boolean = false) {
    /** Hello and goodbye: the right hand swings up beside the face and waves; the left bobs. */
    Wave("wave", 1.9f, 0.62f),
    /** A milestone: both hands up high, pumping in turn. */
    Cheer("cheer", 1.8f, 0.55f),
    /** Nothing to do, or nothing there: both hands out to the sides, lifted once. */
    Shrug("shrug", 1.6f, 0.5f),
    /** Here it is: the right hand sweeps out to present what just went live. */
    TaDa("tada", 1.8f, 0.75f),
    /** Bashful: both hands up on the cheeks. */
    Shy("shy", 2.4f, 0.8f, inFront = true),
    ;

    companion object {
        /** The gesture a spec `hands` value names, or null. */
        fun of(id: String?): HandGesture? = entries.firstOrNull { it.id == id }
    }
}

/** A hand's centre in body sides from the body's centre (y down), and its tilt in degrees. */
internal data class HandPose(val x: Float, val y: Float, val rot: Float)

/** Hands at rest, as they first peek out: low on each side, mostly behind the body. */
private fun rest(side: Int) = HandPose(side * 0.54f, 0.35f, 0f)

private fun lerp(a: HandPose, b: HandPose, f: Float) =
    HandPose(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.rot + (b.rot - a.rot) * f)

/** 0 → 1 over [dur] seconds from [from], easing out (cubic). */
private fun rise(t: Float, from: Float, dur: Float): Float {
    val u = ((t - from) / dur).coerceIn(0f, 1f)
    return 1f - (1f - u) * (1f - u) * (1f - u)
}

private const val DEG = (180 / PI).toFloat()

/** How far out the hands are at [t] seconds into [g], 0..1: they grow in quickly and tuck away at the end. */
internal fun handsOut(g: HandGesture, t: Float): Float {
    if (t < 0f || t > g.seconds) return 0f
    val grow = rise(t, 0f, 0.28f)
    val shrink = ((g.seconds - t) / 0.22f).coerceAtMost(1f).let { it * it * (3 - 2 * it) }
    return min(grow, shrink)
}

/** Where hand [side] (−1 left, +1 right, as seen) is at [t] seconds into [g]. */
internal fun handPose(g: HandGesture, side: Int, t: Float): HandPose = when (g) {
    // Coucou's wave: the hand rides an arc beside the head, rocking on the wrist
    HandGesture.Wave -> if (side > 0) {
        val w = (t - 0.15f).coerceAtLeast(0f)
        val a = 13f * w
        lerp(
            rest(side),
            HandPose(0.55f + cos(a) * 0.06f, -0.075f - sin(a) * 0.14f, (-0.5f + sin(a) * 0.35f) * DEG),
            rise(t, 0.15f, 0.18f),
        )
    } else {
        rest(side).copy(y = 0.35f + sin(6f * t) * 0.04f)
    }
    HandGesture.Cheer -> {
        val pump = sin(16f * t + if (side > 0) 0f else PI.toFloat()) * 0.07f
        lerp(rest(side), HandPose(side * 0.58f, -0.40f + pump, -side * 28f), rise(t, 0.08f, 0.2f))
    }
    HandGesture.Shrug -> {
        val lift = sin(((t - 0.25f) / 0.5f).coerceIn(0f, 1f) * PI.toFloat()) * 0.06f
        lerp(rest(side), HandPose(side * 0.68f, 0.08f - lift, -side * 22f), rise(t, 0.05f, 0.2f))
    }
    HandGesture.TaDa -> if (side > 0) {
        val w = (t - 0.1f).coerceAtLeast(0f)
        val flourish = sin(11f * w) * 0.07f * exp(-3f * w)
        lerp(rest(side), HandPose(0.68f, -0.04f + flourish, -22f + flourish * 120f), rise(t, 0.1f, 0.25f))
    } else {
        rest(side).copy(y = 0.31f)
    }
    HandGesture.Shy ->
        lerp(rest(side), HandPose(side * 0.33f, 0.07f + sin(8f * t + side) * 0.012f, side * 20f), rise(t, 0.05f, 0.25f))
}

/**
 * Draws [g]'s hands at [t] seconds in, for a body of side [s] centred on the origin: two soft,
 * solid ovals in the eyes' warm gradient, lit from the top like Coucou's (a black hand would vanish
 * against the black notch). Behind the body unless [HandGesture.inFront], so
 * call it before the body (or after the face, for those).
 */
internal fun DrawScope.drawHands(s: Float, g: HandGesture, t: Float) {
    val out = handsOut(g, t)
    if (out < 0.01f) return
    val w = 0.30f * s * out
    val h = 0.26f * s * out
    for (side in intArrayOf(-1, 1)) {
        val p = handPose(g, side, t)
        translate(p.x * s, p.y * s) {
            rotate(p.rot, pivot = Offset.Zero) {
                val fill = Brush.linearGradient(
                    listOf(StudioFacePalette.eyeTop, StudioFacePalette.eyeBottom),
                    start = Offset(w * 0.35f, -h * 0.45f), end = Offset(-w * 0.4f, h * 0.45f),
                )
                drawOval(fill, Offset(-w / 2, -h / 2), Size(w, h))
                // a thin dark rim keeps a hand over the face (shy) from melting into the mouth
                drawOval(StudioFacePalette.body.copy(alpha = 0.55f), Offset(-w / 2, -h / 2), Size(w, h), style = Stroke(s * 0.012f))
            }
        }
    }
}
