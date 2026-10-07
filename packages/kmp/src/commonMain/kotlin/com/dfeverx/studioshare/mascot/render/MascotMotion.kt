package com.dfeverx.studioshare.mascot.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural body motion layered over the frames, so even a single-frame pose feels alive and an
 * animated one gets weight. Pure: a motion id and a time in seconds give a transform. Offsets are in
 * fractions of the mascot's size, so the same motion reads the same at 64 dp and 220 dp.
 */
data class MotionPose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
)

object MascotMotion {
    /** How long a one-shot plays before handing back to what was held. */
    const val ONE_SHOT_SECONDS = 2.2f

    private const val TAU = (2 * PI).toFloat()

    fun pose(motion: String, t: Float): MotionPose = when (motion) {
        "breathe" -> breathe(t, period = 3.2f, depth = 0.025f)
        "slow" -> breathe(t, period = 5f, depth = 0.03f)
        "sway" -> MotionPose(rotation = 3.5f * sin(t * TAU / 3f))
        "hop" -> hops(t, height = 0.08f, period = 0.55f, count = 2)
        "jump" -> hops(t, height = 0.16f, period = 0.7f, count = 2).let {
            it.copy(rotation = if (t < 1.4f) 4f * sin(t * TAU / 0.7f) else 0f)
        }
        "pop" -> {
            // overshoot to 1.1 and settle: a damped spring from 0.85
            val s = if (t > 1.2f) 1f else 1f - 0.15f * exp(-5f * t) * kotlin.math.cos(t * 14f)
            MotionPose(scaleX = s, scaleY = s)
        }
        "nod" -> MotionPose(rotation = if (t < 1.2f) 6f * sin(t * TAU / 0.6f) * exp(-1.5f * t) else 0f)
        "shake" -> MotionPose(dx = if (t < 0.9f) 0.04f * sin(t * TAU * 6f) * (1f - t / 0.9f) else 0f)
        "trot" -> MotionPose(dy = -0.035f * abs(sin(t * TAU / 0.5f)), rotation = 2f * sin(t * TAU / 0.5f))
        "droop" -> breathe(t, period = 4.5f, depth = 0.02f).copy(rotation = -2f)
        else -> MotionPose()
    }

    private fun breathe(t: Float, period: Float, depth: Float): MotionPose {
        val s = depth * (0.5f + 0.5f * sin(t * TAU / period))
        // squash and stretch around the feet: taller and a touch narrower on the in-breath
        return MotionPose(scaleY = 1f + s, scaleX = 1f - s * 0.4f)
    }

    private fun hops(t: Float, height: Float, period: Float, count: Int): MotionPose {
        if (t >= period * count) return MotionPose()
        val phase = (t % period) / period
        val lift = sin(phase * PI.toFloat())
        val squash = if (phase < 0.12f || phase > 0.88f) 0.06f else 0f
        return MotionPose(dy = -height * lift, scaleY = 1f - squash, scaleX = 1f + squash)
    }
}
