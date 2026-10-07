package com.dfeverx.studioshare.mascot.face

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.exp

private const val TWEEN_SECONDS = 0.25f

/** How fast the face catches up with [StudioFace]'s gaze, per second: a soft follow, not a snap. */
private const val GAZE_RATE = 6f

/** Tween bookkeeping that must not trigger recomposition; it is read and written while drawing. */
private class FaceTween {
    var from = FaceParams()
    var to = FaceParams()
    var drawn = FaceParams()
    var fromAccent: Accent? = null
    var changedAt = 0f
    var mood: String? = null
    var gaze: Offset? = null
    var gazeAt = 0f
}

/**
 * The StudioShare app-icon face as a living character, drawn in code: no images, so it is crisp at
 * any size and costs nothing to ship. It takes the same mood names as the 3D mascot, so anything
 * that drives the mascot by mood (`MascotAgent.state.currentMood`) drives the face too.
 *
 * @param progress 0..1 for the progress-ring accent (uploading/sending); null spins it.
 * @param animate false draws one still frame — for reduced motion and for screenshots.
 * @param gaze where the face should look, read every frame (so a moving cursor never recomposes):
 *   x and y each −1..1, right and down positive ([gazeToward] makes one from an offset); null lets
 *   the eyes drift on their own. The whole face turns toward it; scanning moods keep scanning.
 */
@Composable
fun StudioFace(
    mood: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    progress: Float? = null,
    glow: Boolean = true,
    gaze: (() -> Offset?)? = null,
) {
    var time by remember { mutableFloatStateOf(0.4f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = 0.4f + (it - start) / 1_000_000_000f }
        }
    }
    val tween = remember { FaceTween() }
    val expression = StudioFaceExpressions.forMood(mood)

    Canvas(modifier) {
        val t = time
        if (tween.mood != mood) {
            val first = tween.mood == null
            tween.from = if (first) FaceParams.of(expression) else tween.drawn
            tween.to = FaceParams.of(expression)
            tween.fromAccent = if (first) expression.accent else tween.mood?.let { StudioFaceExpressions.forMood(it).accent }
            tween.changedAt = t
            tween.mood = mood
        }
        val since = t - tween.changedAt
        val f = if (animate) (since / TWEEN_SECONDS).coerceIn(0f, 1f) else 1f
        val eased = f * f * (3 - 2 * f)
        val params = tween.from.lerp(tween.to, eased)
        tween.drawn = params
        val look = gaze?.invoke()?.let { Offset(it.x.coerceIn(-1f, 1f), it.y.coerceIn(-1f, 1f)) }
        tween.gaze = when {
            look == null -> null
            !animate || tween.gaze == null -> look
            else -> {
                val k = 1f - exp(-GAZE_RATE * (t - tween.gazeAt).coerceIn(0f, 0.1f))
                tween.gaze!! + (look - tween.gaze!!) * k
            }
        }
        tween.gazeAt = t
        val sameAccent = tween.fromAccent == expression.accent
        // One-shot motions (hop, pop, nod…) replay every few seconds while the mood is held.
        val motionT = if (animate) since % 6f else 0.4f
        drawStudioFace(
            p = params,
            motion = if (animate) expression.motion else "none",
            t = t,
            motionT = motionT,
            accent = expression.accent,
            accentAlpha = if (sameAccent) 1f else eased,
            previousAccent = if (sameAccent) null else tween.fromAccent,
            previousAlpha = 1f - eased,
            progress = progress,
            glow = glow,
            gaze = tween.gaze,
        )
    }
}
