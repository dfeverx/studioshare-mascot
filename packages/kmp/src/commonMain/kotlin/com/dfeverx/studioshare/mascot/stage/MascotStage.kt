package com.dfeverx.studioshare.mascot.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dfeverx.studioshare.mascot.LocalMascot
import com.dfeverx.studioshare.mascot.MascotMoments
import com.dfeverx.studioshare.mascot.MascotMomentMoods
import com.dfeverx.studioshare.mascot.face.StudioFace
import com.dfeverx.studioshare.mascot.render.MascotMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.dfeverx.studioshare.mascot.director.MascotPriority
import kotlin.math.roundToInt

/**
 * The floating companion. One per window, laid over the content as the last child of a full-size
 * Box. It takes no space and no touches except on the mascot itself, so the screen underneath works
 * exactly as it does without it.
 *
 * - Rests at its dock (an edge the user drags it to), above [bottomInset] — the shell's bottom chrome.
 * - Walks to the anchor the current cue names when that anchor is on screen and a free perch
 *   exists beside it, and back to the dock when the cue ends.
 * - Never stands on a `mascotAvoid` element; on compact it hides while the keyboard is up.
 * - [visible] false fades it out (immersive viewers, the lock screen); nothing animates meanwhile.
 *
 * The mascot is the StudioShare face, drawn in code. Draws nothing at all when no controller is
 * provided or the mascot is off.
 */
@Composable
fun MascotStage(
    compact: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    bottomInset: Dp = 0.dp,
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    val mascot = LocalMascot.current ?: return
    val enabled by mascot.settings.enabled.collectAsState()
    val hiddenForSession by mascot.settings.hiddenForSession.collectAsState()
    val ready by mascot.settings.ready.collectAsState()
    LaunchedEffect(mascot, enabled) { mascot.director.setEnabled(enabled) }
    // Dozes off after a long stretch without input; the next touch or click wakes it with a wave.
    LaunchedEffect(mascot, enabled) {
        if (!enabled) return@LaunchedEffect
        var asleep = false
        mascot.lastActivity.collectLatest {
            if (asleep) {
                asleep = false
                mascot.director.release(SleepOwner)
                mascot.play(MascotMoments.AppWelcomeBack)
            }
            delay(LONG_IDLE_MS)
            asleep = true
            mascot.director.hold(SleepOwner, MascotMoments.AppLongIdle, priority = MascotPriority.Ambient)
        }
    }
    if (!ready || !enabled || hiddenForSession) return
    val scene by mascot.director.scene.collectAsState()
    val dock by mascot.settings.dock.collectAsState()

    val density = LocalDensity.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val shown = visible && !(compact && imeOpen)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (reducedMotion) 0 else 220))

    var stageOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { stageOrigin = it.positionInRoot() }) {
        // The face is square.
        val widthDp = if (compact) 48.dp else 60.dp
        val heightDp = widthDp
        val pxW = with(density) { widthDp.toPx() }
        val pxH = with(density) { heightDp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val inset = with(density) { bottomInset.toPx() }
        val stage = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val mascotSize = Size(pxW, pxH)
        val avoid = mascot.avoids.values.map { it.translate(-stageOrigin) }

        val dockOffset = MascotPlacement.dockOffset(stage, mascotSize, dock, margin, inset, avoid)
        val anchorRect = scene.anchor?.let { mascot.anchors[it] }?.translate(-stageOrigin)
        val perch = anchorRect?.let { MascotPlacement.perchBeside(it, stage, mascotSize, margin, inset, avoid) }
        val target = perch ?: dockOffset

        val position = remember { Animatable(target, Offset.VectorConverter) }
        var placed by remember { mutableStateOf(false) }
        var walking by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val walkSpeed = with(density) { WALK_SPEED.toPx() }

        LaunchedEffect(target, dragging, shown) {
            if (dragging) return@LaunchedEffect
            val distance = (position.value - target).getDistance()
            if (!placed || !shown || reducedMotion || distance < 2f) {
                position.snapTo(target)
                placed = true
                return@LaunchedEffect
            }
            walking = true
            try {
                val millis = (distance / walkSpeed * 1000).roundToInt().coerceIn(250, 1600)
                position.animateTo(target, tween(millis, easing = FastOutSlowInEasing))
            } finally {
                walking = false
            }
        }

        if (alpha == 0f) return@BoxWithConstraints

        // A once-per-account celebration is judged when its cue arrives, then remembered.
        val first = remember(scene.cueId) {
            MascotMomentMoods.moment(scene.momentKey)?.first?.takeIf { scene.oneShot }
        }
        val firstSeen = remember(scene.cueId) { first != null && mascot.settings.hasCelebrated(first) }
        LaunchedEffect(scene.cueId, first) {
            if (first != null && !firstSeen) mascot.settings.markCelebrated(first)
        }
        val sceneMood = remember(scene.momentKey, firstSeen) { MascotMomentMoods.moodFor(scene.momentKey, firstSeen) }
        // A one-shot holds the stage for its motion's length, then hands back to what was held.
        LaunchedEffect(scene.cueId) {
            if (scene.oneShot) {
                delay((MascotMotion.ONE_SHOT_SECONDS * 1000).toLong())
                mascot.director.finished(scene.cueId)
            }
        }
        val stageNow by rememberUpdatedState(stage)
        val insetNow by rememberUpdatedState(inset)

        Box(
            Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(widthDp, heightDp)
                .graphicsLayer { this.alpha = alpha }
                // decorative: hidden from screen readers; the Profile switch is its accessible control
                .clearAndSetSemantics { }
                .pointerInput(mascot) {
                    detectTapGestures(
                        onTap = { mascot.play(MascotMoments.AppTapped) },
                        onLongPress = { if (menu != null) menuOpen = true },
                    )
                }
                .pointerInput(mascot) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val newDock = MascotPlacement.dockFor(position.value, stageNow, mascotSize, margin, insetNow)
                            scope.launch { mascot.settings.setDock(newDock) }
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { position.snapTo(MascotPlacement.clamp(position.value + amount, stageNow, mascotSize)) }
                    }
                },
        ) {
            StudioFace(
                mood = if (walking) MascotMomentMoods.MOOD_WALK else sceneMood,
                animate = shown && !reducedMotion && !dragging,
                hands = MascotMomentMoods.handsFor(scene.momentKey, firstSeen).takeIf { scene.oneShot && !walking },
                handsId = scene.cueId,
                modifier = Modifier.fillMaxSize(),
            )
            if (menuOpen && menu != null) menu { menuOpen = false }
        }
    }
}

private object SleepOwner
private const val LONG_IDLE_MS = 3 * 60 * 1000L

/** Walking pace across the screen. */
private val WALK_SPEED = 420.dp
