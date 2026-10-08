package com.dfeverx.studioshare.mascot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import com.dfeverx.studioshare.mascot.director.MascotDirector
import com.dfeverx.studioshare.mascot.director.MascotPriority
import com.dfeverx.studioshare.mascot.director.MascotSettings

/**
 * Everything the mascot needs, as one app-wide object. The app creates it once (DI) and provides it
 * through [LocalMascot]; with no controller provided — or the mascot switched off — every hook
 * below is a no-op, so screens can call them unconditionally.
 */
class MascotController(
    val settings: MascotSettings,
    val director: MascotDirector = MascotDirector(),
) {
    /** Root-coordinate bounds of the places the mascot can walk to, by key. */
    internal val anchors = mutableStateMapOf<String, Rect>()

    /** Root-coordinate bounds the mascot must never cover, by owner. */
    internal val avoids = mutableStateMapOf<Any, Rect>()

    val isActive: Boolean get() = settings.enabled.value

    /** Last time the user touched, clicked or moved the pointer (ms since start); see [mascotActivity]. */
    internal val lastActivity = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private var activityMark = kotlin.time.TimeSource.Monotonic.markNow()

    /** Called on user input; coalesced to once a second so pointer moves cost nothing. */
    fun noteActivity() {
        val now = activityMark.elapsedNow().inWholeMilliseconds
        if (now - lastActivity.value >= 1_000) lastActivity.value = now
    }

    private var lastPlayAt = -1L

    /** A one-shot moment: plays once, then the mascot goes back to what it was doing. */
    fun play(moment: String, anchor: String? = null, priority: MascotPriority = MascotPriority.Normal) {
        if (!isActive) return
        lastPlayAt = activityMark.elapsedNow().inWholeMilliseconds
        director.play(moment, anchor, priority)
    }

    /**
     * A generic reaction (e.g. to any snackbar) that yields to a specific one a screen played just
     * before it: plays only if nothing else played in the last [quietMs].
     */
    fun playUnlessJustPlayed(moment: String, quietMs: Long = 1_500) {
        val now = activityMark.elapsedNow().inWholeMilliseconds
        if (lastPlayAt >= 0 && now - lastPlayAt < quietMs) return
        play(moment)
    }
}

val LocalMascot = staticCompositionLocalOf<MascotController?> { null }

/**
 * Holds [moment] while [active] is true and this call stays composed; releases it on leave. The one
 * hook a screen adds at an existing state branch, e.g.
 * `MascotCue(MascotMoments.CullRunning, active = stage == CullStage.CULLING, anchor = "cull.pane")`.
 */
@Composable
fun MascotCue(
    moment: String,
    active: Boolean = true,
    anchor: String? = null,
    priority: MascotPriority = MascotPriority.Normal,
) {
    val mascot = LocalMascot.current ?: return
    val owner = remember { Any() }
    DisposableEffect(mascot, owner, moment, active, anchor, priority) {
        if (active) mascot.director.hold(owner, moment, anchor, priority) else mascot.director.release(owner)
        onDispose { mascot.director.release(owner) }
    }
}

/** Marks this element as a place the mascot may walk to while a cue names [key]. */
fun Modifier.mascotAnchor(key: String): Modifier = composed {
    val mascot = LocalMascot.current ?: return@composed this
    DisposableEffect(mascot, key) { onDispose { mascot.anchors.remove(key) } }
    onGloballyPositioned {
        if (!mascot.isActive) return@onGloballyPositioned
        val bounds = it.boundsInRoot()
        // a list scrolling under an anchor re-lays it out every frame; only a real move is a write
        if (mascot.anchors[key] != bounds) mascot.anchors[key] = bounds
    }
}

/**
 * Lets the mascot notice the user is there (it dozes off after a while without input and wakes up
 * on the next one). Put it on a parent of the content: it only observes, never consumes, so every
 * click, drag and scroll underneath behaves exactly as before.
 */
fun Modifier.mascotActivity(): Modifier = composed {
    val mascot = LocalMascot.current ?: return@composed this
    pointerInput(mascot) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                mascot.noteActivity()
            }
        }
    }
}

/** Marks this element as something the mascot must never stand on (bars, primary buttons, sheets). */
fun Modifier.mascotAvoid(): Modifier = composed {
    val mascot = LocalMascot.current ?: return@composed this
    val owner = remember { Any() }
    DisposableEffect(mascot, owner) { onDispose { mascot.avoids.remove(owner) } }
    onGloballyPositioned {
        if (!mascot.isActive) return@onGloballyPositioned
        val bounds = it.boundsInRoot()
        if (mascot.avoids[owner] != bounds) mascot.avoids[owner] = bounds
    }
}
