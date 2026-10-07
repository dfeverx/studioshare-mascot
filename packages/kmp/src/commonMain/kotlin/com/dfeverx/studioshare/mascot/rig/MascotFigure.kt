package com.dfeverx.studioshare.mascot.rig

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.dfeverx.studioshare.mascot.pack.MascotPack
import com.dfeverx.studioshare.mascot.render.MascotMotion
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Draws a mood with the code rig, animating only in short bursts so the mascot costs nothing while
 * it simply stands there:
 *
 * - a **one-shot** plays for [MascotMotion.ONE_SHOT_SECONDS], then [onFinished];
 * - **walking** animates for as long as it walks;
 * - a **held** mood or idle is a still pose, with a ~1.5 s fidget (blink, breathe, the mood's
 *   gesture) every 6–10 s.
 *
 * Between bursts nothing ticks — no frames are requested, so a desktop window (which repaints whole)
 * is not repainted for the mascot. During a burst it updates at ~30 fps, and only the canvas draws:
 * the time is read in the draw and layer lambdas, never in composition.
 */
@Composable
fun MascotFigure(
    resolved: MascotPack.Resolved,
    colors: RigColors,
    oneShot: Boolean,
    walking: Boolean,
    mirrored: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    fidgetOnly: Boolean = false,
    onFinished: (() -> Unit)? = null,
) {
    val mood = resolved.mood
    val pose = remember(mood, walking, mirrored) {
        RigPose(
            face = if (walking) "smile" else mood.face,
            arms = if (walking) "down" else mood.arms,
            prop = if (walking && mood.prop != "box" && mood.prop != "cloud" && mood.prop != "envelope") "none" else mood.prop,
            walking = walking,
            mirrored = mirrored,
        )
    }
    val motion = if (walking) "trot" else mood.motion
    val time = remember { mutableFloatStateOf(0f) }
    val blink = remember { mutableFloatStateOf(0f) }
    val finished = rememberUpdatedState(onFinished)

    LaunchedEffect(resolved.momentKey, resolved.moodName, oneShot, walking, animate, fidgetOnly) {
        time.floatValue = 0f
        blink.floatValue = 0f
        if (!animate) {
            if (oneShot) {
                delay((MascotMotion.ONE_SHOT_SECONDS * 1000).toLong())
                finished.value?.invoke()
            }
            return@LaunchedEffect
        }
        suspend fun burst(seconds: Float, withBlink: Boolean) {
            var t = 0f
            while (t < seconds) {
                delay(FRAME_MS)
                t += FRAME_MS / 1000f
                time.floatValue = t
                if (withBlink) blink.floatValue = blinkAt(t)
            }
        }
        when {
            walking || (!oneShot && !fidgetOnly) -> {
                var t = 0f
                while (true) {
                    delay(FRAME_MS)
                    t += FRAME_MS / 1000f
                    time.floatValue = t
                    blink.floatValue = blinkAt(t)
                }
            }
            oneShot -> {
                burst(MascotMotion.ONE_SHOT_SECONDS, withBlink = false)
                time.floatValue = 0f
                finished.value?.invoke()
            }
            else -> while (true) {
                time.floatValue = 0f
                blink.floatValue = 0f
                delay(Random.nextLong(6_000, 10_000))
                burst(FIDGET_SECONDS, withBlink = true)
            }
        }
    }

    Canvas(
        modifier.graphicsLayer {
            val p = MascotMotion.pose(motion, time.floatValue)
            translationX = p.dx * size.width
            translationY = p.dy * size.height
            scaleX = p.scaleX
            scaleY = p.scaleY
            rotationZ = p.rotation
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        drawMascot(pose, colors, time.floatValue, blink.floatValue)
    }
}

/** A quick blink in the middle of a fidget. */
private fun blinkAt(t: Float): Float {
    val d = kotlin.math.abs(t - 0.7f)
    return if (d < 0.09f) 1f - d / 0.09f else 0f
}

private const val FRAME_MS = 33L
private const val FIDGET_SECONDS = 1.5f
