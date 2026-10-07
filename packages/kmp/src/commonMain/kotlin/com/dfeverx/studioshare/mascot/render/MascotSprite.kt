package com.dfeverx.studioshare.mascot.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dfeverx.studioshare.mascot.pack.LoadedPack
import com.dfeverx.studioshare.mascot.pack.MascotPack
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Draws one mood from its rendered frames: frame *n* of the atlas, plus the mood's procedural
 * [MascotMotion]. It animates only in short bursts, like the drawn rig, so a parked mascot costs
 * nothing (a desktop window repaints whole on any frame):
 *
 * - **walking** plays for as long as it walks, [mirrored] when it walks left;
 * - a **one-shot** ([loop] false) plays for [MascotMotion.ONE_SHOT_SECONDS], then [onFinished];
 * - a **held** mood shows its `still` frame, and every 6–10 s plays its frames and motion for a
 *   ~1.5 s fidget — so a mood with a few keyframes (a wave up / down) waves now and then.
 *
 * [animate] false shows the `still` frame without motion (reduced motion, dragging, hidden).
 * During a burst it ticks at ~30 fps and only the canvas redraws.
 */
@Composable
fun MascotSprite(
    resolved: MascotPack.Resolved,
    loaded: LoadedPack,
    dark: Boolean,
    cache: AtlasCache,
    loop: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    walking: Boolean = false,
    mirrored: Boolean = false,
    onFinished: (() -> Unit)? = null,
) {
    val path = resolved.atlasPath(dark)
    val art = resolved.art
    // Keep drawing the previous atlas until the next one is decoded, so a mood change never blinks.
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var bitmapPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path, loaded.pack.version) {
        if (path == null) {
            bitmap = null
        } else {
            cache.get(loaded.files, path, loaded.pack.version)?.let { bitmap = it; bitmapPath = path }
        }
    }

    val finished by rememberUpdatedState(onFinished)
    // time < 0 = at rest: the still frame, no motion
    var time by remember(resolved.momentKey, resolved.moodName) { mutableFloatStateOf(REST) }
    LaunchedEffect(resolved.momentKey, resolved.moodName, animate, loop, walking) {
        time = REST
        if (!animate) {
            if (!loop && !walking) {
                delay((MascotMotion.ONE_SHOT_SECONDS * 1000).toLong())
                finished?.invoke()
            }
            return@LaunchedEffect
        }
        suspend fun burst(seconds: Float) {
            var t = 0f
            while (t < seconds) {
                time = t
                delay(FRAME_MS)
                t += FRAME_MS / 1000f
            }
        }
        when {
            walking -> while (true) burst(10f)
            !loop -> {
                burst(MascotMotion.ONE_SHOT_SECONDS)
                time = REST
                finished?.invoke()
            }
            else -> while (true) {
                time = REST
                delay(Random.nextLong(6_000, 10_000))
                burst(FIDGET_SECONDS)
            }
        }
    }

    val image = bitmap ?: return
    val mood = if (bitmapPath == path && art != null) art else null
    val frameW = loaded.pack.frame.w
    val frameH = loaded.pack.frame.h
    val frameIndex = when {
        mood == null -> 0
        !animate || time < 0f || mood.frames <= 1 -> mood.still.coerceIn(0, (mood.frames - 1).coerceAtLeast(0))
        // a fidget runs the frames round as a loop, then settles back on the still frame
        else -> frameAt(time, mood.fps, mood.frames, mood.loopFrom, loop = loop || walking)
    }
    val cols = mood?.cols?.coerceAtLeast(1) ?: 1
    val motion = when {
        !animate || time < 0f -> "none"
        walking -> "trot"
        resolved.artIsClip -> art?.motion ?: "none"
        else -> resolved.mood.motion
    }

    Canvas(
        modifier.graphicsLayer {
            val p = MascotMotion.pose(motion, time.coerceAtLeast(0f))
            translationX = p.dx * size.width
            translationY = p.dy * size.height
            scaleX = p.scaleX
            scaleY = p.scaleY
            rotationZ = p.rotation
            if (mirrored) scaleX = -scaleX
            // the mascot stands on the bottom of its frame, so it squashes and turns around its feet
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    ) {
        drawImage(
            image = image,
            srcOffset = IntOffset((frameIndex % cols) * frameW, (frameIndex / cols) * frameH),
            srcSize = IntSize(frameW, frameH),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Frame for [time]: intro frames once, then the loop segment from [loopFrom]; a one-shot holds its last frame. */
internal fun frameAt(time: Float, fps: Int, frames: Int, loopFrom: Int, loop: Boolean): Int {
    if (frames <= 1) return 0
    val raw = (time * fps.coerceAtLeast(1)).toInt()
    if (raw < frames) return raw
    if (!loop) return frames - 1
    val start = loopFrom.coerceIn(0, frames - 1)
    return start + (raw - start) % (frames - start)
}

private const val FRAME_MS = 33L
private const val FIDGET_SECONDS = 1.5f
private const val REST = -1f
