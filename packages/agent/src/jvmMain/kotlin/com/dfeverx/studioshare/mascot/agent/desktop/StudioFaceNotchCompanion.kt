package com.dfeverx.studioshare.mascot.agent.desktop

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dfeverx.studioshare.mascot.agent.MascotAgent
import com.dfeverx.studioshare.mascot.agent.MascotAgentState
import com.dfeverx.studioshare.mascot.face.StudioFace
import com.dfeverx.studioshare.mascot.face.gazeToward
import kotlinx.coroutines.delay
import java.awt.MouseInfo
import kotlin.math.hypot

/** Width of the old under-the-menu-bar tab; the island's width on a screen without a notch. */
internal val COMPACT = DpSize(240.dp, 40.dp)
internal val EXPANDED = DpSize(400.dp, 120.dp)
/** Width of each side of the island that shows past the notch: the face on the left, a badge on the right. */
internal val EAR = 64.dp
internal const val NOTCH_WINDOW_TITLE = "StudioShare Notch Companion"
internal val INK = Color(0xFF000000)
private val LINE = Color.White.copy(alpha = 0.14f)
private val TEXT = Color.White
private val MUTED = Color.White.copy(alpha = 0.62f)

/**
 * The island's bottom corners. The one place the mascot breaks the no-radius rule: the island has
 * to read as the notch grown longer, and the notch is rounded.
 */
private val COMPACT_CORNER = 14.dp
private val EXPANDED_CORNER = 22.dp
private val FACE_EXPANDED = 76.dp
private const val CARD_PAD = 14

/** Room around the island for its spring to overshoot into, and for the face's glow. */
private val WINDOW_MARGIN = 16.dp

/** Opening springs out with a little overshoot; closing eases in, quicker and without one. */
private val openSpring: AnimationSpec<Dp> = spring(dampingRatio = 0.72f, stiffness = 158f)
private val closeEase: AnimationSpec<Dp> = tween(340, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f))

/** How long the opened island waits after the mouse leaves before it folds back into the notch. */
private const val COLLAPSE_AFTER_LEAVE_MS = 2_000L
/** Resting the mouse on the face this long makes it blush; then not again for a while. */
private const val LOVE_AFTER_MS = 1_900L
private const val LOVE_COOLDOWN_MS = 6_000L
private const val LOVE_HOLD_MS = 2_500L

/** The companion's window, so a [Face] inside it can work out where it sits on screen. */
private val LocalNotchWindow = compositionLocalOf<java.awt.Window?> { null }

/**
 * A notch companion: the StudioShare face living in the MacBook's notch, after Coucou's Mochi. The
 * island is black like the notch, flush with the top of the screen and above the menu bar, so the
 * notch looks like it grew a face on its left and a badge on its right; the face turns to watch the
 * mouse anywhere on screen. On a screen without a notch it is a small island at the top centre with
 * the face and one line of status.
 *
 * Click it and it springs open into a card; it folds back a couple of seconds after the mouse
 * leaves. A warning opens it until it is dealt with, an alert until the agent drops it. Rest the
 * mouse on the face and it grows a little, and after a moment blushes.
 *
 * The window is a fixed transparent stage a little bigger than the open card, and the island
 * animates inside it, so opening never resizes a native window. Everything but the island lets
 * clicks through to the apps underneath.
 *
 * It reads [MascotAgent.state] and nothing else — the same feed the mini HUD uses — so every upload,
 * warning and milestone the app already reports shows up here.
 */
@Composable
fun StudioFaceNotchCompanion(
    agent: MascotAgent,
    visible: Boolean,
    onClose: () -> Unit,
    onOpenMainApp: () -> Unit,
) {
    if (!visible) return
    val state by agent.state.collectAsState()
    val notch = remember { MacNotch.geometry() }

    var opened by remember { mutableStateOf(false) }
    var inIsland by remember { mutableStateOf(false) }
    var overFace by remember { mutableStateOf(false) }
    var love by remember { mutableStateOf(false) }
    val expanded = opened || state is MascotAgentState.Warning || state is MascotAgentState.Alert

    val target = if (expanded) expandedSize(notch) else compactSize(notch)
    val stage = stageSize(notch)
    val windowState = rememberWindowState(size = stage, position = topCentre(notch, stage.width))

    // fold back once the mouse has been away a moment; coming back cancels it
    LaunchedEffect(opened, inIsland) {
        if (opened && !inIsland) {
            delay(COLLAPSE_AFTER_LEAVE_MS)
            opened = false
        }
    }
    // a face the mouse rests on blushes, now and then
    var lastLove by remember { mutableStateOf(0L) }
    LaunchedEffect(overFace) {
        if (!overFace) return@LaunchedEffect
        delay(LOVE_AFTER_MS)
        val now = System.currentTimeMillis()
        if (state is MascotAgentState.Idle && now - lastLove > LOVE_COOLDOWN_MS) {
            lastLove = now
            love = true
            delay(LOVE_HOLD_MS)
            love = false
        }
    }

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = NOTCH_WINDOW_TITLE,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) {
            // AWT can only float the window, which leaves it under the menu bar (and the notch);
            // lift it above, then pin it back to the very top in case AWT kept it below the bar
            MacNotch.raiseAboveMenuBar(NOTCH_WINDOW_TITLE)
            windowState.position = topCentre(notch, stage.width)
        }

        val spec = if (expanded) openSpring else closeEase
        val islandW by animateDpAsState(target.width, spec)
        val islandH by animateDpAsState(target.height, spec)
        val corner by animateDpAsState(
            (if (expanded) EXPANDED_CORNER else COMPACT_CORNER).coerceAtMost(target.height / 2), spec,
        )
        val face = facePlacement(notch, expanded)
        val faceX by animateDpAsState(face.centre.x, spec)
        val faceY by animateDpAsState(face.centre.y, spec)
        val faceSize by animateDpAsState(face.size, spec)
        val faceScale by animateFloatAsState(if (overFace) 1.08f else 1f, spring(0.6f, Spring.StiffnessMedium))
        val contentAlpha by animateFloatAsState(
            if (expanded) 1f else 0f,
            if (expanded) tween(220, delayMillis = 120) else tween(120),
        )

        // 60 Hz: where the mouse is, against the island and the face. Pointer events can't do
        // this — the window ignores the mouse wherever it isn't the island.
        LaunchedEffect(Unit) {
            var clickable = false
            while (true) {
                val m = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                if (m != null) {
                    val x = m.x - window.x.toFloat()
                    val y = m.y - window.y.toFloat()
                    val left = (stage.width.value - islandW.value) / 2
                    // a little slack around the island, except a resting no-notch island, which
                    // must not catch clicks meant for the app just under the menu bar
                    val slack = if (notch.hasNotch || expanded) 6f else 0f
                    val now = x >= left - slack && x <= left + islandW.value + slack && y <= islandH.value + slack
                    if (now != inIsland) inIsland = now
                    if (now != clickable) {
                        clickable = now
                        MacNotch.setClickThrough(NOTCH_WINDOW_TITLE, !now)
                    }
                    val over = hypot(x - (left + faceX.value), y - faceY.value) <= faceSize.value / 2 + 4
                    if (over != overFace) overFace = over
                }
                delay(16)
            }
        }

        CompositionLocalProvider(LocalNotchWindow provides window) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
                Box(
                    Modifier
                        .size(islandW, islandH)
                        .clip(shape)
                        .background(INK)
                        .then(if (notch.hasNotch) Modifier else Modifier.border(1.dp, LINE, shape))
                        .then(
                            if (expanded) Modifier
                            else Modifier.clickable(remember { MutableInteractionSource() }, null) { opened = true },
                        ),
                ) {
                    // contents keep their final size while the island grows around them, so
                    // nothing reflows mid-spring; the island's clip reveals them
                    if (contentAlpha > 0.01f) {
                        Column(Modifier.requiredSize(expandedSize(notch)).alpha(contentAlpha).align(Alignment.TopStart)) {
                            Spacer(Modifier.height(notch.bandHeight))
                            Expanded(agent, state, onOpenMainApp, showFace = false)
                        }
                    }
                    if (contentAlpha < 0.99f) {
                        Box(Modifier.requiredSize(compactSize(notch)).alpha(1f - contentAlpha).align(Alignment.TopStart)) {
                            if (notch.hasNotch) NotchBand(state, notch.notchWidth, onOpenMainApp, showFace = false)
                            else Compact(state, onOpenMainApp, showFace = false)
                        }
                    }
                    // one face for every state, so it glides between the ear and the card
                    Box(
                        Modifier
                            .offset(faceX - faceSize / 2, faceY - faceSize / 2)
                            .size(faceSize)
                            .scale(faceScale),
                    ) {
                        Face(
                            state, faceSize, onOpenMainApp.takeIf { expanded } ?: { opened = true },
                            mood = if (love) "shy" else null,
                        )
                    }
                }
            }
        }
    }
}

/** The island at rest: across the notch with an ear each side, or a small tab without one. */
internal fun compactSize(notch: NotchGeometry): DpSize =
    if (notch.hasNotch) DpSize(notch.notchWidth + EAR * 2, notch.bandHeight)
    else DpSize(COMPACT.width, notch.bandHeight)

internal fun expandedSize(notch: NotchGeometry): DpSize =
    DpSize(maxOf(EXPANDED.width, compactSize(notch).width), notch.bandHeight + EXPANDED.height)

/** The fixed window the island lives in. */
internal fun stageSize(notch: NotchGeometry): DpSize = expandedSize(notch).let {
    DpSize(it.width + WINDOW_MARGIN * 2, it.height + WINDOW_MARGIN)
}

internal data class FacePlacement(val centre: DpOffset, val size: Dp)

/** Where the face sits in the island: in the left ear at rest, at the head of the card when open. */
internal fun facePlacement(notch: NotchGeometry, expanded: Boolean): FacePlacement {
    val restSize = notch.bandHeight - 6.dp
    return when {
        expanded -> FacePlacement(
            DpOffset(CARD_PAD.dp + FACE_EXPANDED / 2, notch.bandHeight + EXPANDED.height / 2),
            FACE_EXPANDED,
        )
        notch.hasNotch -> FacePlacement(DpOffset(EAR / 2, notch.bandHeight / 2), restSize)
        else -> FacePlacement(DpOffset(8.dp + restSize / 2, notch.bandHeight / 2), restSize)
    }
}

/** The island across the notch: the face just left of it, a short badge just right of it. */
@Composable
internal fun NotchBand(
    state: MascotAgentState,
    notchWidth: Dp,
    onOpenMainApp: () -> Unit,
    showFace: Boolean = true,
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(EAR).fillMaxHeight().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
            if (showFace) Face(state, 34.dp, onOpenMainApp)
        }
        Spacer(Modifier.width(notchWidth))
        Box(Modifier.width(EAR).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val badge = earBadge(state)
            if (badge != null) {
                Text(
                    badge, color = if (state is MascotAgentState.Working) MUTED else TEXT, fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun Compact(state: MascotAgentState, onOpenMainApp: () -> Unit, showFace: Boolean = true) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showFace) Face(state, 30.dp, onOpenMainApp) else Spacer(Modifier.width(30.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            compactLine(state), color = TEXT, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        val w = state as? MascotAgentState.Working
        if (w != null) {
            Spacer(Modifier.width(8.dp))
            Text(w.activeTask.percentText, color = MUTED, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun Expanded(
    agent: MascotAgent,
    state: MascotAgentState,
    onOpenMainApp: () -> Unit,
    showFace: Boolean = true,
) {
    Row(Modifier.fillMaxSize().padding(CARD_PAD.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showFace) Face(state, FACE_EXPANDED, onOpenMainApp) else Spacer(Modifier.width(FACE_EXPANDED))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            when (state) {
                is MascotAgentState.Working -> {
                    val task = state.activeTask
                    Title(task.title)
                    if (task.detail.isNotBlank()) Body(task.detail, lines = 1)
                    Spacer(Modifier.height(8.dp))
                    if (task.isIndeterminate) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().height(3.dp), color = TEXT, trackColor = LINE,
                            strokeCap = StrokeCap.Butt, gapSize = 0.dp,
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(LINE)) {
                            Box(
                                Modifier.fillMaxWidth(task.progress.coerceIn(0f, 1f)).fillMaxHeight()
                                    .background(TEXT),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val queued = state.queuedTasks.size
                    Body(task.percentText + if (queued > 0) "  ·  $queued more queued" else "", lines = 1)
                }
                is MascotAgentState.Warning -> {
                    val warning = state.activeWarning
                    Title(warning.title)
                    Body(warning.message, lines = 2)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        val action = warning.onAction
                        if (warning.actionLabel != null && action != null) {
                            SquareButton(warning.actionLabel, filled = true) {
                                action()
                                agent.dismissWarning(warning.id)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        SquareButton("Dismiss", filled = false) { agent.dismissWarning(warning.id) }
                    }
                }
                is MascotAgentState.Alert -> {
                    Title(state.alert.title)
                    Body(state.alert.message, lines = 2)
                }
                is MascotAgentState.Idle -> {
                    Title("StudioShare")
                    Body("All quiet. I'll tell you when something happens.", lines = 2)
                }
            }
        }
    }
}

@Composable
private fun Face(state: MascotAgentState, size: Dp, onClick: () -> Unit, mood: String? = null) {
    val progress = (state as? MascotAgentState.Working)?.activeTask
        ?.takeUnless { it.isIndeterminate }?.progress
    val window = LocalNotchWindow.current
    val density = LocalDensity.current.density
    // the face's centre in the window, in px; written on layout, read every frame by the gaze
    val centre = remember { floatArrayOf(Float.NaN, Float.NaN) }
    StudioFace(
        mood = mood ?: state.currentMood,
        progress = progress,
        gaze = window?.let { w -> { cursorGaze(w, centre[0] / density, centre[1] / density) } },
        modifier = Modifier
            .size(size)
            .onGloballyPositioned { c -> c.boundsInWindow().center.let { centre[0] = it.x; centre[1] = it.y } }
            .clickable(onClick = onClick),
    )
}

/** Which way a face at ([x], [y]) points inside [window] has to look to see the cursor. */
private fun cursorGaze(window: java.awt.Window, x: Float, y: Float) =
    if (x.isNaN()) null
    else runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        ?.let { gazeToward(it.x - (window.x + x), it.y - (window.y + y)) }

@Composable
private fun Title(text: String) = Text(
    text, color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Body(text: String, lines: Int) = Text(
    text, color = MUTED, fontSize = 11.sp, maxLines = lines, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun SquareButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (filled) TEXT else INK, RectangleShape)
            .border(1.dp, if (filled) TEXT else LINE, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = if (filled) INK else TEXT, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** The few characters right of the notch: progress while working, how many warnings wait. */
internal fun earBadge(state: MascotAgentState): String? = when (state) {
    is MascotAgentState.Working -> state.activeTask.percentText.takeUnless { state.activeTask.isIndeterminate }
    is MascotAgentState.Warning -> if (state.allWarnings.size > 1) "${state.allWarnings.size} !" else "!"
    is MascotAgentState.Alert -> "•"
    is MascotAgentState.Idle -> null
}

internal fun compactLine(state: MascotAgentState): String = when (state) {
    is MascotAgentState.Working -> state.activeTask.title
    is MascotAgentState.Warning ->
        if (state.allWarnings.size > 1) "${state.allWarnings.size} things need you" else state.activeWarning.title
    is MascotAgentState.Alert -> state.alert.title
    is MascotAgentState.Idle -> "StudioShare"
}

/** Top centre of the screen, flush with its top edge: over the menu bar, and around the notch. */
private fun topCentre(notch: NotchGeometry, width: Dp) = WindowPosition(
    x = (notch.screenX + (notch.screenWidth - width.value) / 2).dp,
    y = notch.screenY.dp,
)
